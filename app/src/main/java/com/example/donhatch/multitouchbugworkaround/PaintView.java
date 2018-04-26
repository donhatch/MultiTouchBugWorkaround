// This is nuts!
// I thought there was a bug in the example,
// but there's a bug in the framework!
//   https://www.xda-developers.com/android-8-1-oreo-multi-touch-bug-fixed-june-update/
//
// Ok, how to work around it?
// Well, I need to put some kind of interception between my OnTouchListener and the real one.
// I think it'll go like this:
//
//      replace this:
//         setOnTouchListener(someTouchListener)
//      with this:
//         setOnTouchListener(new MultiTouchListenerBugFixWrapper(someTouchListener))
//
// Unfortunately MotionEvent is not public,
// so the touch listener will have to be modified to take a FixedMotionEvent instead of a MotionEvent.
// Given that, I'm going to arrange it differently.  That is:
// That is:
//      abstract class FixedOnTouchListener implements View.OnTouchListener {
//         @Override
//         final boolean onTouch(View view, MotionEvent motionEvent) {
//           FixedMotionEvent fixedMotionEvent = ...;
//           return onFixedTouch(view, fixedMotionEvent);
//         }
//      }
//
//
// Wow, the behavior is elusive.
// Strategy for debugging it:
//      Keep entire history
//      Process entire history
//      See if it fixed all major discontinuities

package com.example.donhatch.multitouchbugworkaround;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.HashMap;

import static com.example.donhatch.multitouchbugworkaround.CHECK.*;
import static com.example.donhatch.multitouchbugworkaround.STRINGIFY.STRINGIFY;

// https://gist.github.com/ErikHellman/6069322
public class PaintView extends LinearLayout {  // CBB: I wanted android.support.constraint.ConstraintLayout but not sure how to add it to project

  private static final String TAG = MultiTouchBugWorkaroundActivity.class.getSimpleName();

  public static final int MAX_FINGERS = 5;
  private Path[] mFingerPaths = new Path[MAX_FINGERS];
  private Path[] mFingerCorrectedPaths = new Path[MAX_FINGERS];
  private Paint mFingerPaint;
  private Paint mCompletedPaint;
  private Paint mCompletedPaintCorrected;
  private Paint[] mFingerPaints;
  private ArrayList<Path> mCompletedPaths;
  private ArrayList<Path> mCompletedCorrectedPaths;
  private RectF mPathBounds = new RectF();
  private float[] startX = new float[MAX_FINGERS];
  private float[] startY = new float[MAX_FINGERS];
  private float[] prevX = new float[MAX_FINGERS];
  private float[] prevY = new float[MAX_FINGERS];

  public PaintView(Context context) {
    super(context);
    Log.i(TAG, "    in PaintView ctor");
    setOnTouchListener(new MyTouchListener());
    addView(new Button(context) {{
      setText("Clear");
      setOnClickListener(new Button.OnClickListener() {
        @Override
        public void onClick(View button) {
          mCompletedPaths.clear();
          mCompletedCorrectedPaths.clear();
          PaintView.this.invalidate();
        }
      });
    }});
    Log.i(TAG, "    out PaintView ctor");
  }

  public PaintView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public PaintView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @Override
  protected void onAttachedToWindow() {
    Log.i(TAG, "        in PaintView onAttachedToWindow");
    super.onAttachedToWindow();
    mCompletedPaths = new ArrayList<Path>();
    mCompletedCorrectedPaths = new ArrayList<Path>();

    mFingerPaint = new Paint();
    mFingerPaint.setAntiAlias(true);
    mFingerPaint.setColor(Color.RED);
    mFingerPaint.setStyle(Paint.Style.STROKE);
    mFingerPaint.setStrokeWidth(6);
    mFingerPaint.setStrokeCap(Paint.Cap.BUTT);

    mCompletedPaint = new Paint();
    mCompletedPaint.setAntiAlias(true);
    mCompletedPaint.setColor(Color.BLACK);
    mCompletedPaint.setStyle(Paint.Style.STROKE);
    mCompletedPaint.setStrokeWidth(6);
    mCompletedPaint.setStrokeCap(Paint.Cap.BUTT);

    mCompletedPaintCorrected = new Paint();
    mCompletedPaintCorrected.setAntiAlias(true);
    mCompletedPaintCorrected.setColor(Color.MAGENTA);
    mCompletedPaintCorrected.setStyle(Paint.Style.STROKE);
    mCompletedPaintCorrected.setStrokeWidth(6);
    mCompletedPaintCorrected.setStrokeCap(Paint.Cap.BUTT);

    final int colors[] = new int[] {
      Color.RED,
      Color.BLUE,
      Color.GREEN,
      Color.CYAN,
      Color.YELLOW,
    };
    mFingerPaints = new Paint[MAX_FINGERS];
    for (int i = 0; i < MAX_FINGERS; ++i) {
      mFingerPaints[i] = new Paint();
      mFingerPaints[i].setAntiAlias(true);
      mFingerPaints[i].setColor(colors[i]);
      mFingerPaints[i].setStyle(Paint.Style.STROKE);
      mFingerPaints[i].setStrokeWidth(6);
      mFingerPaints[i].setStrokeCap(Paint.Cap.BUTT);
    }

    if (false) {
      double points[][] = new double[][] {
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1086.6227, 795.4476},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1012.64844, 735.48926},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {921.68, 697.5156},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {808.71924, 687.5225},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
        {1170.5936, 959.3338},
      };
      Path path = new Path();
      path.moveTo((float)points[0][0], (float)points[0][1]);
      for (int i = 0; i < points.length; ++i) {
        path.lineTo((float)points[i][0], (float)points[i][1]);
      }
      path.setLastPoint((float)points[points.length-1][0], (float)points[points.length-1][1]);

      mCompletedPaths.add(path);
    }

    Log.i(TAG, "        out PaintView onAttachedToWindow");
  }

  @Override
  protected void onDraw(Canvas canvas) {
    final int verboseLevel = 0;
    if (verboseLevel >= 1) Log.i(TAG, "            in PaintView onDraw");
    super.onDraw(canvas);

    for (Path completedPath : mCompletedPaths) {
      canvas.drawPath(completedPath, mCompletedPaint);
    }
    for (Path completedCorrectedPath : mCompletedCorrectedPaths) {
      canvas.drawPath(completedCorrectedPath, mCompletedPaintCorrected);
    }

    for (int i = 0; i < mFingerPaths.length; ++i) {
      if (mFingerPaths[i] != null) {
          canvas.drawPath(mFingerPaths[i], mFingerPaints[i]);
      }
    }
    if (verboseLevel >= 1) Log.i(TAG, "            out PaintView onDraw");
  }

  private static String actionToString(int action) {
    if (action == MotionEvent.ACTION_DOWN) return "DOWN";
    if (action == MotionEvent.ACTION_POINTER_DOWN) return "POINTER_DOWN";
    if (action == MotionEvent.ACTION_MOVE) return "MOVE";
    if (action == MotionEvent.ACTION_POINTER_UP) return "POINTER_UP";
    if (action == MotionEvent.ACTION_UP) return "UP";
    throw new AssertionError("unrecognized MotionEvent action "+action);
  }

  public abstract static class FixedOnTouchListener implements View.OnTouchListener {
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
            //    


            for (int i0 = 0; i0 < n;) {
              // advance i0 to something that has a fingerprint
              while (i0 < n && index2fingerprint[i0] == null) i0++;
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
              for (int i = 0; i < n; ++i) {
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

    /*
      Desired printing:
        in intercepting onTouch MOVE {0,1}
          0/2:  0: 1632.4332, 538.626   1: 111.111, 222.222
          1/2:  0: 3333.3333, 444.444   1: 555.555, 666.666
          now: 
        out intercepting onTouch MOVE {0,1}
        in intercepting onTouch MOVE {0,1}
          0/2 0:1632.4332,538.626 1:111.111,222.222
          1/2 0:3333.3333,444.444 1:555.555,666.666
          now 0:1632.4332,538.626 1:555.555,666.666
        out intercepting onTouch MOVE {0,1}
        in intercepting onTouch UP(0) {0,1}
          now 0:1632.4332,538.626 UP, 1:555.555, 666.666
        out intercepting onTouch MOVE {0,1}
    */

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

  /*
  class MyTouchListener implements View.OnTouchListener {
    @Override
    public boolean onTouch(View view, MotionEvent event) {
    */

  class MyTouchListener extends FixedOnTouchListener {
    @Override
    public boolean onFixedTouch(View view, FixedMotionEvent event) {

      final int verboseLevel = 1;  // 0:nothing, 1: in, 2: and out and more detail
      int pointerCount = event.getPointerCount();
      int cappedPointerCount = pointerCount > MAX_FINGERS ? MAX_FINGERS : pointerCount;
      int actionIndex = event.getActionIndex();
      int action = event.getActionMasked();
      int actionId = event.getPointerId(actionIndex);

      int[] pointerIds = new int[pointerCount];
      for (int i = 0; i < pointerCount; ++i) {
        pointerIds[i] = event.getPointerId(i);
        CHECK_EQ(event.findPointerIndex(pointerIds[i]), i);
      }

      if (verboseLevel >= 1) Log.i(TAG, "                in PaintView onTouchEvent (hs="+event.getHistorySize()+") pc="+pointerCount+" ai="+actionIndex+" a="+actionToString(action)+" actionId="+actionId+" "+STRINGIFY(pointerIds));

      if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && actionId < MAX_FINGERS) {
        mFingerPaths[actionId] = new Path();
        mFingerCorrectedPaths[actionId] = new Path();
        if (verboseLevel >= 1) Log.i(TAG, "                  starting path "+actionId+": moveTo "+event.getX(actionIndex)+", "+event.getY(actionIndex));
        mFingerPaths[actionId].moveTo(event.getX(actionIndex), event.getY(actionIndex));
        mFingerCorrectedPaths[actionId].moveTo(event.getX(actionIndex), event.getY(actionIndex));
        startX[actionId] = event.getX(actionIndex);
        startY[actionId] = event.getY(actionIndex);
        prevX[actionId] = startX[actionId];
        prevY[actionId] = startY[actionId];
      }

      final int calledStrategy = 2;
      if (action == MotionEvent.ACTION_MOVE) {
        // honor historical motion
        final int historySize = event.getHistorySize();
        for (int h = 0; h < historySize+1; ++h) {
          for (int index = 0; index < pointerCount; ++index) {
            int id = event.getPointerId(index);
            if (id < MAX_FINGERS && mFingerPaths[id] != null) {
              final float x = h==historySize ? event.getX(index) : event.getHistoricalX(index, h);
              final float y = h==historySize ? event.getY(index) : event.getHistoricalY(index, h);
              if (verboseLevel >= 1) Log.i(TAG, "                  adding to path "+id+": lineTo "+x+", "+y+"  (h="+h+"/"+historySize+")");
              mFingerPaths[id].lineTo(x, y);
              if (calledStrategy == 0) {
              } else if (calledStrategy == 1) {
                if (x != startX[id] || y != startY[id]) {
                  if (verboseLevel >= 1) Log.i(TAG, "                      (and to corrected path)  (h="+h+"/"+historySize+")");
                  mFingerCorrectedPaths[id].lineTo(x, y);
                } else {
                  if (verboseLevel >= 1) Log.i(TAG, "                      (but NOT to corrected path by strategy=1)  (h="+h+"/"+historySize+")");
                }
              } else if (calledStrategy == 2) {
                if (x != prevX[id] || y != prevY[id]) {
                  if (verboseLevel >= 1) Log.i(TAG, "                      (and to corrected path)  (h="+h+"/"+historySize+")");
                  mFingerCorrectedPaths[id].lineTo(x, y);
                } else {
                  if (verboseLevel >= 1) Log.i(TAG, "                      (but NOT to corrected path by strategy=2)  (h="+h+"/"+historySize+")");
                }
              }

              if (pointerCount == 2) {
                boolean omitPerCalledStrategy1 = (x == startX[id] && y == startY[id]);
                boolean omitPerCalledStrategy2 = (x == prevX[id] && y == prevY[id]);
                if (verboseLevel >= 1) Log.i(TAG, "                      omitPerCalledStrategy1 = "+omitPerCalledStrategy1);
                if (verboseLevel >= 1) Log.i(TAG, "                      omitPerCalledStrategy2 = "+omitPerCalledStrategy2);

                //CHECK(!omitPerCalledStrategy1 || omitPerCalledStrategy2);
                if (omitPerCalledStrategy1 && !omitPerCalledStrategy2) {
                  // happens sometimes... only just after starting a path or something?
                  if (verboseLevel >= 1) Log.i(TAG, "                      HEY!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                }
              }

              prevX[id] = x;
              prevY[id] = y;
              mFingerPaths[id].computeBounds(mPathBounds, true);
              invalidate((int) mPathBounds.left, (int) mPathBounds.top,
                  (int) mPathBounds.right, (int) mPathBounds.bottom);
            }
          }
        }
      }

      if ((action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) && actionId < MAX_FINGERS) {
        if (verboseLevel >= 1) Log.i(TAG, "                  ending path "+actionId+": setLastPoint "+event.getX(actionIndex)+", "+event.getY(actionIndex));
        mFingerPaths[actionId].setLastPoint(event.getX(actionIndex), event.getY(actionIndex));
        if (calledStrategy == 0) {
        } else if (calledStrategy == 1) {
          if (event.getX(actionIndex) != startX[actionId] || event.getY(actionIndex) != startY[actionId]) {
            mFingerCorrectedPaths[actionId].setLastPoint(event.getX(actionIndex), event.getY(actionIndex));
          } else {
            // it doesn't get a last point.  does it matter?
          }
        } else if (calledStrategy == 2) {
          if (event.getX(actionIndex) != prevX[actionId] || event.getY(actionIndex) != prevY[actionId]) {
            mFingerCorrectedPaths[actionId].setLastPoint(event.getX(actionIndex), event.getY(actionIndex));
          } else {
            // it doesn't get a last point.  does it matter?
          }
        }
        mCompletedPaths.add(mFingerPaths[actionId]);
        mCompletedCorrectedPaths.add(mFingerCorrectedPaths[actionId]);
        mFingerPaths[actionId].computeBounds(mPathBounds, true);
        invalidate((int) mPathBounds.left, (int) mPathBounds.top,
            (int) mPathBounds.right, (int) mPathBounds.bottom);
        mFingerPaths[actionId] = null;
        mFingerCorrectedPaths[actionId] = null;
      }

      if (verboseLevel >= 2) Log.i(TAG, "                out PaintView onTouchEvent");
      return true;
    }
  }
}
