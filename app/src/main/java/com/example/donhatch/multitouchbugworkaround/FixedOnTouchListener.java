//
// Possible strategies:
//      1. Make a straightforward wrapper.
//         Pros:
//              - don't need to tweak listeners
//         Cons:
//              - can't construct using historical data; have to unwrap,
//                so apps that use it won't be able to exercise historical data
//      2. Make a tweaked one, using an alternate class FixedMotionEvent.
//         Cons:
//              - requires tweaking listeners-- yeah, pretty much a mess
//              - I'll miss some pieces of the API
//      3. Provide both?
//         That is, provide:
//              FixedOnTouchListener
//                - is an OnTouchListener
//                - onTouch creates a FixedMotionEvent and calls subclass-provided onFixedTouch with it
//              OnTouchListenerBugWorkaroundWrapper
//                - is an OnTouchListener
//                - wraps an OnTouchListener, onTouch delegates to its onTouch
//                      (with historical events turned into main events, since it appears to be impossible to
//                      create a MotionEvent with historical stuff)
//                - implemented as a FixedOnTouchListener
//      OH WAIT!  Does addBatch add historical?  yes!!  so I can do it!
//      ISSUE: HOVER_MOVE?  don't need to handle it because it isn't a touch event
//      ISSUE: ACTION_CANCEL? never seen one, but have to handle it.
//      Q: can I tweak the incoming??
//         i.e. with setX(), setY(), getHistorical and tweak those... ?
//
// CONJECTURES:
//  - when it begins, it is always the case that:
//      - id 0 just went down for the not-first time
//      - exactly one other id is already down (can be 1, or 2, or...)
//      - the other id may have participated in the bug already, it doesn't matter
//  - it happens to the two participating ids, for almost exactly as long as both down;
//    as soon as one of them goes up: it stops happening to 0 (of course),
//    and the current run of the other is the last one where the bug occurs.
//  - so the anchor of id 0 is the down position!  And the anchor of the other one
//    is its value at the moment of that 0-down.  (FALSE-- not at the exact moment;
//    it might take another event or more for other id to settle into the bug)
//  - the anchor of the other has *not* appeared yet at the moment of 0-down.
//  - it's its *next* distinct position (after possible repeats of current) (FALSE-- can be 2 after, maybe even more, I'm not sure)
//  - the first bad non-0-id value is at the end of the first MOVE packet after the POINTER_DOWN(0).
// Q: what is the first evidence that it's happening?
//    - sometimes the non-0 pointer begins bugging before the 0 pointer even moves
//      from its down position, so we have to recognize it-- how?
//    - seems to always start with the non-0 repeating the thing it's going to get stuck on,
//      twice or more, while 0-id is still in its down position.
// Q: what other changes are happening that I'm not tracking, and can I use them to characterize?
//    -
//

package com.example.donhatch.multitouchbugworkaround;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;

import static com.example.donhatch.multitouchbugworkaround.CHECK.*;
import static com.example.donhatch.multitouchbugworkaround.STRINGIFY.STRINGIFY;


public class FixedOnTouchListener implements View.OnTouchListener {

  private View.OnTouchListener wrapped;

  public FixedOnTouchListener(View.OnTouchListener wrapped) {
    this.wrapped = wrapped;
  }


  public static class OnTouchListenerTestWrapper implements View.OnTouchListener {
    View.OnTouchListener wrapped;
    public OnTouchListenerTestWrapper(View.OnTouchListener wrapped) {
      this.wrapped = wrapped;
    }
    private void simpleDumpMotionEvent(MotionEvent motionEvent) {
      int historySize = motionEvent.getHistorySize();
      int pointerCount = motionEvent.getPointerCount();
      for (int h = 0; h < historySize+1; ++h) {
        if (h==historySize) {
          Log.i(TAG, "          now:");
        } else {
          Log.i(TAG, "          "+h+"/"+historySize+":");
        }
        for (int index = 0; index < pointerCount; ++index) {
          Log.i(TAG, "              id="+motionEvent.getPointerId(index)+": "+(h==historySize?motionEvent.getX():motionEvent.getHistoricalX(index,h))+","+(h==historySize?motionEvent.getY():motionEvent.getHistoricalY(index,h))+"");
        }
      }
    }
    @Override public boolean onTouch(View view, MotionEvent motionEvent) {
      Log.i(TAG, "    in OnTouchListenerTestWrapper.onTouch");
      Log.i(TAG, "      historySize = "+motionEvent.getHistorySize());

      if (false) {
        Log.i(TAG, "      before tweaking by -XXX,-XXX:");
        simpleDumpMotionEvent(motionEvent);

        if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN || motionEvent.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
          // Can I just tweak the motion event in place?
          // Let's try messing with its X and Y.
          motionEvent.setLocation(motionEvent.getX()-5000,
                                  motionEvent.getY()-5000);
          // Result: it seems to tweak *all* of the event! even the historical stuff!  wow!  and for all ids!? holy moly!
          // hmm, I guess that's what its doc says: "Applies offsetLocation(float, float) with a delta from the current location to the given new location."
        }

        Log.i(TAG, "      after tweaking by -XXX,-XXX:");
        simpleDumpMotionEvent(motionEvent);
      } 

      if (true) {
        Log.i(TAG, "      before tweaking a historical:");
        simpleDumpMotionEvent(motionEvent);

        //MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords);
        //getPointerCoords(pointerIndex, pointerCoords);
        // Bleah, I don't see how to tweak the historical.

        Log.i(TAG, "      after tweaking a historical:");
        simpleDumpMotionEvent(motionEvent);
      }



      Log.i(TAG, "      calling wrapped.onTouch");
      boolean answer = wrapped.onTouch(view, motionEvent);
      Log.i(TAG, "      returned from wrapped.onTouch: "+answer);
      Log.i(TAG, "    out OnTouchListenerTestWrapper.onTouch, returning "+answer);
      return answer;
    }
  }

  private static final String TAG = MultiTouchBugWorkaroundActivity.class.getSimpleName();  // XXX

  // Note, MotionEvent has an actionToString(), but it takes an unmasked action;
  // we want to take an unmasked action.
  public static String actionToString(int action) {
    if (action == MotionEvent.ACTION_DOWN) return "DOWN";
    if (action == MotionEvent.ACTION_POINTER_DOWN) return "POINTER_DOWN";
    if (action == MotionEvent.ACTION_MOVE) return "MOVE";
    if (action == MotionEvent.ACTION_POINTER_UP) return "POINTER_UP";
    if (action == MotionEvent.ACTION_UP) return "UP";
    //if (action == MotionEvent.ACTION_CANCEL) return "CANCEL";  // I've never seen this, I don't think
    throw new AssertionError("unrecognized MotionEvent action "+action);
  }

  final private int MAX_FINGERS = 10;
  final private float[] startXorig = new float[MAX_FINGERS];
  final private float[] startYorig = new float[MAX_FINGERS];
  final private float[] prevPrevXorig = new float[MAX_FINGERS];
  final private float[] prevPrevYorig = new float[MAX_FINGERS];
  final private float[] prevXorig = new float[MAX_FINGERS];
  final private float[] prevYorig = new float[MAX_FINGERS];
  final private float[] startXcorrected = new float[MAX_FINGERS];
  final private float[] startYcorrected = new float[MAX_FINGERS];
  final private float[] prevPrevXcorrected = new float[MAX_FINGERS];
  final private float[] prevPrevYcorrected = new float[MAX_FINGERS];
  final private float[] prevXcorrected = new float[MAX_FINGERS];
  final private float[] prevYcorrected = new float[MAX_FINGERS];
  public static class FixedMotionEvent {  // implements some of MotionEvent's methods

    // no one including FixedOnTouchListener should look at these
    final private MotionEvent unfixed;
    final private float[][] historicalAndActualX;
    final private float[][] historicalAndActualY;

    // FixedOnTouchListener may look at this; no one else should
    private FixedMotionEvent(MotionEvent unfixed,
                             float[/*pointerCount*/][/*historySize+1*/] historicalAndActualX,
                             float[/*pointerCount*/][/*historySize+1*/] historicalAndActualY) {
      this.unfixed = unfixed;
      this.historicalAndActualX = historicalAndActualX;
      this.historicalAndActualY = historicalAndActualY;
    }

    public int getPointerCount() { return unfixed.getPointerCount(); }
    public int getActionIndex() { return unfixed.getActionIndex(); }
    public int getActionMasked() { return unfixed.getActionMasked(); }
    public int getPointerId(int index) { return unfixed.getPointerId(index); }
    public int findPointerIndex(int id) { return unfixed.findPointerIndex(id); }
    public int getHistorySize() { return unfixed.getHistorySize(); }

    private final static boolean propagateFixFlag = true;
    public float getHistoricalX(int index, int h) {
      if (!propagateFixFlag) return unfixed.getHistoricalX(index, h);
      CHECK_LT(h, historicalAndActualX[index].length-1);
      return historicalAndActualX[index][h];
    }
    public float getHistoricalY(int index, int h) {
      if (!propagateFixFlag) return unfixed.getHistoricalY(index, h);
      CHECK_LT(h, historicalAndActualY[index].length-1);
      return historicalAndActualY[index][h];
    }
    public float getX(int index) {
      if (!propagateFixFlag) return unfixed.getX(index);
      return historicalAndActualX[index][historicalAndActualX[index].length-1];
    }
    public float getY(int index) {
      if (!propagateFixFlag) return unfixed.getY(index);
      return historicalAndActualY[index][historicalAndActualY[index].length-1];
    }
  }

  // To aid analysis.
  private static class LogicalMotionEvent {
    public boolean isHistorical;
    public long eventTimeMillis;  // "in the uptimeMillis() time base"
    public int action;
    public int actionId;
    public int ids[];
    public float[/*max_id_occurring+1*/] x;
    public float[/*max_id_occurring+1*/] y;
    public float[/*max_id_occurring+1*/] pressure;
    // all arrays assumed to be immutable.
    public LogicalMotionEvent(boolean isHistorical, long eventTimeMillis, int action, int actionId, int ids[], float[] x, float[] y, float[] pressure) {
      this.isHistorical = isHistorical;
      this.eventTimeMillis = eventTimeMillis;
      this.action = action;
      this.actionId = actionId;
      this.ids = ids;
      this.x = x;
      this.y = y;
      this.pressure = pressure;
    }
    // Breaks motionEvent down into LogicalMotionEvents and appends them to list.
    public static void breakDown(MotionEvent motionEvent, ArrayList<LogicalMotionEvent> list) {
      final int action = motionEvent.getActionMasked();
      final int actionId = motionEvent.getPointerId(motionEvent.getActionIndex());
      final int pointerCount = motionEvent.getPointerCount();
      final int historySize = motionEvent.getHistorySize();
      final int[] ids = new int[pointerCount];
      int maxIdOccurring = -1;
      for (int i = 0; i < pointerCount; ++i) {
        ids[i] = motionEvent.getPointerId(i);
        maxIdOccurring = Math.max(maxIdOccurring, ids[i]);
      }
      for (int h = 0; h < historySize+1; ++h) {
        final long eventTimeMillis = h==historySize ? motionEvent.getEventTime() : motionEvent.getHistoricalEventTime(h);
        final float[] x = new float[maxIdOccurring+1];
        final float[] y = new float[maxIdOccurring+1];
        final float[] pressure = new float[maxIdOccurring+1];
        for (int id = 0; id < maxIdOccurring+1; ++id) {
          x[id] = Float.NaN;
          y[id] = Float.NaN;
          pressure[id] = Float.NaN;
        }
        for (int index = 0; index < pointerCount; ++index) {
          final int id = ids[index]; motionEvent.getPointerId(index);
          x[id] = h==historySize ? motionEvent.getX(index) : motionEvent.getHistoricalX(index, h);
          y[id] = h==historySize ? motionEvent.getY(index) : motionEvent.getHistoricalY(index, h);
          pressure[id] = h==historySize ? motionEvent.getPressure(index) : motionEvent.getHistoricalPressure(index, h);
        }
        list.add(new LogicalMotionEvent(h<historySize, eventTimeMillis, action, actionId, ids, x, y, pressure));
      }
    }

    // A B C AA AB AC BA BB BC CA CB CC AAA AAB ...
    private static String GenerateLabel(int labelIndex) {
      int nDigits = 0;
      if (labelIndex < 26) {
        return String.format("%c", 'A'+labelIndex);
      }
      if (labelIndex < 26*26) {
        return String.format("%c%c", 'A'+(labelIndex/26), 'A'+(labelIndex%26));
      }
      if (labelIndex < 26*26*26) {
        return String.format("%c%c", 'A'+(labelIndex/26), 'A'+(labelIndex%26));
      }
      if (labelIndex < 26*26*26) {
        return String.format("%c%c%c", 'A'+(labelIndex/26/26), 'A'+(labelIndex/26%26), 'A'+(labelIndex%26));
      }
      if (labelIndex < 26*26*26*26) {
        return String.format("%c%c%c%c", 'A'+(labelIndex/26/26/26), 'A'+(labelIndex/26/26%26), 'A'+(labelIndex/26%26), 'A'+(labelIndex%26));
      }
      CHECK(false);
      return null;
    }

    public static void dump(ArrayList<LogicalMotionEvent> list) {
      int n = list.size();

      int idsWidth = 0;
      for (int i = 0; i < n; ++i) {
        idsWidth = Math.max(idsWidth, STRINGIFY(list.get(i).ids).length());
      }

      // For each id,
      // whenever there are 2 or more distinct runs with the same coords,
      // make a label for those coords.
      String[][] id2index2label;
      {
        int max_id_occurring = 9; // XXX fudge
        id2index2label = new String[max_id_occurring+1][n];  // nulls initially

        for (int id = 0; id <= max_id_occurring; ++id) {
          String[] index2label = new String[n];  // nulls initially

          String[] index2fingerprint = new String[n];  // nulls initially
          for (int i = 0; i < n; ++i) {
            LogicalMotionEvent e = list.get(i);
            if (id < e.x.length && !Float.isNaN(e.x[id])) {
              String fingerprint = e.x[id]+","+e.y[id];
              index2fingerprint[i] = fingerprint;
            }
          }

          // Each sequence of pointer-down,moves,pointer-up is its own label scope.
          //


          for (int i0 = 0; i0 < n;) {
            // advance i0, if necessary, to something that has a fingerprint
            while (i0 < n && index2fingerprint[i0] == null) i0++;
            if (i0 == n) break;
            int i1 = i0+1;
            // advance i1 to something that does *not* have a fingerprint
            while (i1 < n && index2fingerprint[i1] != null) i1++;

            // [i0,i1) is now a range beginning with pointer down and ending with up.
            // That's a scope; assign labels within it.

            HashMap<String,int[]> fingerprint2nRunsHolder = new HashMap<String,int[]>();
            // Initialize to zeros
            for (String fingerprint : index2fingerprint) {
              if (fingerprint != null) {
                if (fingerprint2nRunsHolder.get(fingerprint) == null) {
                  fingerprint2nRunsHolder.put(fingerprint, new int[]{0});
                }
              }
            }
            for (int i = i0; i < i1; ++i) {
              String fingerprint = index2fingerprint[i];
              if (fingerprint != null) {
                boolean isRunStart = (i==0 || index2fingerprint[i-1]==null || !fingerprint.equals(index2fingerprint[i-1]));
                if (isRunStart) {
                  fingerprint2nRunsHolder.get(fingerprint)[0]++;
                }
              }
            }
            HashMap<String,String> fingerprint2label = new HashMap<String,String>();
            int nLabels = 0;
            for (int i = 0; i < n; ++i) {
              String fingerprint = index2fingerprint[i];
              if (fingerprint != null) {
                boolean isRunStart = (i==0 || index2fingerprint[i-1]!=null || !fingerprint.equals(index2fingerprint[i-1]));
                if (isRunStart) {
                  int nRuns = fingerprint2nRunsHolder.get(fingerprint)[0];
                  if (nRuns >= 2) {
                    String label = fingerprint2label.get(fingerprint);
                    if (label == null) {
                      label = GenerateLabel(nLabels++);
                      fingerprint2label.put(fingerprint, label);
                    }
                  }
                }
                index2label[i] = fingerprint2label.get(fingerprint);  // may be null
              }
            }
            // Advance i0 to end of this sequence of things that have fingerprints.
            // Top of loop will advance it to beginning of next sequence that has fingerprints.
            i0 = i1;
          }


          id2index2label[id] = index2label;
        }
      }

      long refTimeMillis = list.get(0).eventTimeMillis;
      for (int i = 0; i < n;  ++i) {
        LogicalMotionEvent e = list.get(i);
        StringBuilder sb = new StringBuilder();
        long relativeTimeMillis = e.eventTimeMillis - refTimeMillis;
        if (e.isHistorical) CHECK_EQ(e.action, MotionEvent.ACTION_MOVE);
        sb.append(String.format("          %3d.%03d %4d/%d: %16s %-"+idsWidth+"s",
                                relativeTimeMillis/1000,
                                relativeTimeMillis%1000,
                                i, n,
                                (e.action==MotionEvent.ACTION_MOVE && e.isHistorical ? "" :
                                (actionToString(e.action)+(e.action==MotionEvent.ACTION_MOVE?"  ":"("+e.actionId+")"))),
                                STRINGIFY(e.ids)));
        for (int id = 0; id < e.x.length; ++id) {
          if (!Float.isNaN(e.x[id])) {
            String coordsString = String.format("%g,%g", e.x[id], e.y[id]);

            boolean parenthesized = false;
            if (i >= 1) {
              LogicalMotionEvent ePrev = list.get(i-1);
              if (id < ePrev.x.length && e.x[id] == ePrev.x[id]
                                      && e.y[id] == ePrev.y[id]) {
                coordsString = "("+coordsString+")";
                parenthesized = true;
              }
            }
            if (!parenthesized) {
                coordsString = " "+coordsString+" ";
            }

            String label = id2index2label[id][i];
            if (label != null) {
              coordsString += label;
            } else {
              coordsString += " ";
            }

            sb.append(String.format(" %2d:%-17s", id, coordsString));
          } else {
            sb.append(String.format(" %2s %-17s", "", ""));
          }
        }
        Log.i(TAG, sb.toString());
      }
    }
  }  // class LogicalMotionEvent

  private ArrayList<LogicalMotionEvent> logicalMotionEventsSinceFirstDown = new ArrayList<LogicalMotionEvent>();

  // Framework calls this; we create a fixed MotionEvent
  // and call the wrapped listener on it.
  @Override
  final public boolean onTouch(View view, MotionEvent unfixed) {
    final int verboseLevel = 1;  // 0: nothing, 1: dump entire sequence on final UP, 2: and in/out
    if (verboseLevel >= 2) Log.i(TAG, "    in FixedOnTouchListener onTouch");

    LogicalMotionEvent.breakDown(unfixed, logicalMotionEventsSinceFirstDown);

    boolean answer;
    {
      // Use this one I think:
      //   obtain(long downTime, long eventTime, int action, int pointerCount, PointerProperties[] pointerProperties, PointerCoords[] pointerCoords, int metaState, int buttonState, float xPrecision, float yPrecision, int deviceId, int edgeFlags, int source, int flags)
      // and use addBatch to create the historical stuff.

      final int pointerCount = unfixed.getPointerCount();
      final MotionEvent.PointerProperties pointerProperties[] = new MotionEvent.PointerProperties[pointerCount];  // CBB: reuse
      final MotionEvent.PointerCoords pointerCoords[] = new MotionEvent.PointerCoords[pointerCount];  // CBB: reuse
      for (int index = 0; index < pointerCount; ++index) {
        pointerProperties[index] = new MotionEvent.PointerProperties();
        unfixed.getPointerProperties(index, pointerProperties[index]);
        pointerCoords[index] = new MotionEvent.PointerCoords();
        unfixed.getPointerCoords(index, pointerCoords[index]);
      }
      MotionEvent fixed = MotionEvent.obtain(
          unfixed.getDownTime(),
          unfixed.getEventTime(),
          unfixed.getAction(),  // not getActionMasked(), apparently
          pointerCount,
          pointerProperties,
          pointerCoords,
          unfixed.getMetaState(),
          unfixed.getButtonState(),
          unfixed.getXPrecision(),
          unfixed.getYPrecision(),
          unfixed.getDeviceId(),
          unfixed.getEdgeFlags(),
          unfixed.getSource(),
          unfixed.getFlags());
      CHECK_EQ(fixed.getAction(), unfixed.getAction());
      CHECK_EQ(fixed.getActionMasked(), unfixed.getActionMasked());
      CHECK_EQ(fixed.getActionIndex(), unfixed.getActionIndex());
      try {
        final int historySize = unfixed.getHistorySize();
        for (int h = 0; h < historySize; ++h) {
          int historicalMetaState = unfixed.getMetaState(); // huh? this can't be right, but I don't see any way to query metaState per-history
          for (int index = 0; index < pointerCount; ++index) {
            unfixed.getHistoricalPointerCoords(index, h, pointerCoords[index]);
          }
          fixed.addBatch(unfixed.getHistoricalEventTime(h),
                         pointerCoords,
                         historicalMetaState);
        }
        answer = wrapped.onTouch(view, fixed);
      } finally {
        fixed.recycle();
      }
    }

    if (unfixed.getActionMasked() == MotionEvent.ACTION_UP) {
      if (verboseLevel >= 1) {
        Log.i(TAG, "      ===============================================================");
        Log.i(TAG, "      LOGICAL MOTION EVENT SEQUENCE:");
        LogicalMotionEvent.dump(logicalMotionEventsSinceFirstDown);
        Log.i(TAG, "      ===============================================================");
      }
      logicalMotionEventsSinceFirstDown.clear();
    }

    if (verboseLevel >= 2) Log.i(TAG, "    out FixedOnTouchListener onTouch, returning "+answer);
    return answer;
  }  // onTouch
}

