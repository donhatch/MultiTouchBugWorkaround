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
// Q: what is the first evidence that it's happening?
//    - sometimes the non-0 pointer begins bugging before the 0 pointer even moves
//      from its down position, so we have to recognize it-- how?
//    - seems to always start with the non-0 repeating the thing it's going to get stuck on,
//      twice or more, while 0-id is still in its down position.
//    

package com.example.donhatch.multitouchbugworkaround;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;

import static com.example.donhatch.multitouchbugworkaround.CHECK.*;
import static com.example.donhatch.multitouchbugworkaround.STRINGIFY.STRINGIFY;

public abstract class FixedOnTouchListener implements View.OnTouchListener {

  private static final String TAG = MultiTouchBugWorkaroundActivity.class.getSimpleName();  // XXX

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
    public long eventTimeMillis;  // "in the uptimeMillis() time base"
    public int action;
    public int actionId;
    public int ids[];
    public float[/*max_id_occurring+1*/] x;
    public float[/*max_id_occurring+1*/] y;
    // all arrays assumed to be immutable.
    public LogicalMotionEvent(long eventTimeMillis, int action, int actionId, int ids[], float[] x, float[] y) {
      this.eventTimeMillis = eventTimeMillis;
      this.action = action;
      this.actionId = actionId;
      this.ids = ids;
      this.x = x;
      this.y = y;
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
        for (int id = 0; id < maxIdOccurring+1; ++id) {
          x[id] = Float.NaN;
          y[id] = Float.NaN;
        }
        for (int index = 0; index < pointerCount; ++index) {
          final int id = ids[index]; motionEvent.getPointerId(index);
          x[id] = h==historySize ? motionEvent.getX(index) : motionEvent.getHistoricalX(index, h);
          y[id] = h==historySize ? motionEvent.getY(index) : motionEvent.getHistoricalY(index, h);
        }
        list.add(new LogicalMotionEvent(eventTimeMillis, action, actionId, ids, x, y));
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
        sb.append(String.format("          %3d.%03d %4d/%d: %16s %-"+idsWidth+"s",
                                relativeTimeMillis/1000,
                                relativeTimeMillis%1000,
                                i, n,
                                (actionToString(e.action)+(e.action==MotionEvent.ACTION_MOVE?"  ":"("+e.actionId+")")),
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

  final private float[] XXXstartX = new float[MAX_FINGERS];
  final private float[] XXXstartY = new float[MAX_FINGERS];
  final private float[] XXXprevPrevX = new float[MAX_FINGERS];
  final private float[] XXXprevPrevY = new float[MAX_FINGERS];
  final private float[] XXXprevX = new float[MAX_FINGERS];
  final private float[] XXXprevY = new float[MAX_FINGERS];
  private float XXXmaxDelta = 0.f;
  private float XXXmaxDeltaX = 0.f;
  private float XXXmaxDeltaY = 0.f;

  final int strategy = 0;

  // Framework calls this; constructs a FixedMotionEvent and passes it to onFixedTouch (provided by a subclass).
  @Override
  final public boolean onTouch(View view, MotionEvent unfixed) {
    final int verboseLevel = 1;
    final int pointerCount = unfixed.getPointerCount();
    final int historySize = unfixed.getHistorySize();
    final int action = unfixed.getActionMasked();
    if (verboseLevel >= 1) {
      int[] pointerIds = new int[pointerCount];
      for (int index = 0; index < pointerCount; ++index) {
        pointerIds[index] = unfixed.getPointerId(index);
        CHECK_EQ(unfixed.findPointerIndex(pointerIds[index]), index);
      }
      Log.i(TAG, "    in intercepting onTouch "+actionToString(action)+(action==MotionEvent.ACTION_MOVE?"":"("+unfixed.getPointerId(unfixed.getActionIndex())+")")+" "+STRINGIFY(pointerIds));
      if (action == MotionEvent.ACTION_DOWN) {
        XXXmaxDelta = 0.f;
        XXXmaxDeltaX = 0.f;
        XXXmaxDeltaY = 0.f;
      }
      for (int h = 0; h < historySize+1; ++h) {
        boolean suspicious = false;
        StringBuilder suspicions = new StringBuilder();
        StringBuilder sb = new StringBuilder();
        sb.append((h==historySize ? "now" : h+"/"+historySize)+":");
        for (int index = 0; index < pointerCount; ++index) {
          final int id = unfixed.getPointerId(index);
          float x = h==historySize ? unfixed.getX(index) : unfixed.getHistoricalX(index, h);
          float y = h==historySize ? unfixed.getY(index) : unfixed.getHistoricalY(index, h);
          if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            XXXstartX[id] = x;
            XXXstartY[id] = y;
            XXXprevPrevX[id] = x;
            XXXprevPrevY[id] = y;
            XXXprevX[id] = x;
            XXXprevY[id] = y;
          }

          final float thisDelta = (float)Math.hypot(x-XXXprevX[id], y-XXXprevY[id]);
          if (thisDelta > XXXmaxDelta) {
            XXXmaxDelta = thisDelta;
            XXXmaxDeltaX = Math.abs(x-XXXprevX[id]);
            XXXmaxDeltaY = Math.abs(y-XXXprevY[id]);
          }

          if (x == XXXstartX[id] && y == XXXstartY[id] && !(x == XXXprevX[id] && y == XXXprevY[id])) {
            suspicious = true;
            suspicions.append("  ("+id+" suspicious by start logic)");
          }
          if (x == XXXprevPrevX[id] && y == XXXprevPrevY[id] && !(x == XXXprevX[id] && y == XXXprevY[id])) {
            suspicious = true;
            suspicions.append("  ("+id+" suspicious by prev logic)");
          }
          sb.append("  "+id+":"+x+","+y);

          XXXprevPrevX[id] = XXXprevX[id];
          XXXprevPrevY[id] = XXXprevY[id];
          XXXprevX[id] = x;
          XXXprevY[id] = y;
        }
        //if (suspicious) sb.append("  SUSPICIOUS!!!!!!!!!!!!!!!!!!!!!!!!!!");
        if (suspicious) sb.append("  "+suspicions.toString()+" !!!!!!!!!!!!!!!!!!!!!!!!");
        Log.i(TAG, "      "+sb.toString());
      }
      if (action == MotionEvent.ACTION_UP) {
        if (verboseLevel >= 1) Log.i(TAG, "      max delta occurring in whole thing = "+XXXmaxDeltaX+","+XXXmaxDeltaY+" -> "+XXXmaxDelta);
      }
    }

    final float[][] historicalAndActualX = new float[pointerCount][historySize+1];
    final float[][] historicalAndActualY = new float[pointerCount][historySize+1];
    for (int index = 0; index < pointerCount; ++index) {
      final int id = unfixed.getPointerId(index);
      CHECK_LT(id, MAX_FINGERS);
      for (int h = 0; h < historySize+1; ++h) {
        float xOrig = h==historySize ? unfixed.getX(index) : unfixed.getHistoricalX(index, h);
        float yOrig = h==historySize ? unfixed.getY(index) : unfixed.getHistoricalY(index, h);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
          startXorig[id] = xOrig;
          startYorig[id] = yOrig;
          prevPrevXorig[id] = xOrig;
          prevPrevYorig[id] = yOrig;
          prevXorig[id] = xOrig;
          prevYorig[id] = yOrig;
          prevPrevXcorrected[id] = xOrig;
          prevPrevYcorrected[id] = yOrig;
          prevXcorrected[id] = xOrig;
          prevYcorrected[id] = yOrig;
        }
        float xCorrected = xOrig;
        float yCorrected = yOrig;
        if (strategy == 1) {
          // do nothing
        } else if (strategy == 1) {
          if (xOrig == startXorig[id] && yOrig == startYorig[id]) {
            // assume x,y are bogus; replace them with previous x,y.
            xCorrected = prevXorig[id];
            yCorrected = prevYorig[id];
          }
        } else if (strategy == 2) {
          if (xOrig == prevPrevXorig[id] && yOrig == prevPrevYorig[id] && !(xOrig == prevXorig[id] && yOrig == prevYorig[id])) {
            Log.i(TAG, "      CORRECTED h="+h+" id="+id+" I think");
            xCorrected = prevXorig[id];
            yCorrected = prevYorig[id];
          }
        }
        historicalAndActualX[index][h] = xCorrected;
        historicalAndActualY[index][h] = yCorrected;

        prevPrevXorig[id] = prevXorig[id];
        prevPrevYorig[id] = prevYorig[id];
        prevXorig[id] = xOrig;
        prevYorig[id] = yOrig;

        prevPrevXcorrected[id] = prevXcorrected[id];
        prevPrevYcorrected[id] = prevYcorrected[id];
        prevXcorrected[id] = xCorrected;
        prevYcorrected[id] = yCorrected;
      }
    }
    // CBB: reuse the same FixedMotionEvent each time
    FixedMotionEvent fixedMotionEvent = new FixedMotionEvent(unfixed,
                                                             historicalAndActualX,
                                                             historicalAndActualY);
    final boolean answer = onFixedTouch(view, fixedMotionEvent);


    {
      LogicalMotionEvent.breakDown(unfixed, logicalMotionEventsSinceFirstDown);
      if (action == MotionEvent.ACTION_UP) {
        if (verboseLevel >= 1) {
          Log.i(TAG, "      ===============================================================");
          Log.i(TAG, "      LOGICAL SEQUENCE:");
          LogicalMotionEvent.dump(logicalMotionEventsSinceFirstDown);
          Log.i(TAG, "      ===============================================================");
        }

        logicalMotionEventsSinceFirstDown.clear();
      }
    }

    if (verboseLevel >= 1) Log.i(TAG, "    out intercepting onTouch, returning "+answer);
    return answer;
  }  // onTouch
  abstract public boolean onFixedTouch(View view, FixedMotionEvent fixedMotionEvent);
}

