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
// Wow, the behavior is elusive.
// Strategy for debugging it:
//      Keep entire history
//      On final UP, dump entire history, highlighting suspicous-looking correlations
//      

package com.example.donhatch.multitouchbugworkaround;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

import static com.example.donhatch.multitouchbugworkaround.CHECK.*;
import static com.example.donhatch.multitouchbugworkaround.STRINGIFY.STRINGIFY;

// https://gist.github.com/ErikHellman/6069322
public class PaintView extends FrameLayout {

  private static final String TAG = MultiTouchBugWorkaroundActivity.class.getSimpleName();

  public static final int MAX_FINGERS = 10;
  public static final float STROKE_WIDTH = 6.0f;
  //public static final float SHADOW_RADIUS = 3.f;  // looks cute but makes drawing take twice as long, which can be a bottleneck
  public static final float SHADOW_RADIUS = 0.f;

  private Paint[] mFingerPaints = new Paint[MAX_FINGERS];;
  private Paint mShadowPaint = null;
  private RectF mPathBoundsScratch = new RectF();
  private boolean mShowUnfixed = true;

  private View mTheTouchableDrawable;
  private FixedOnTouchListener mFixedOnTouchListener = null;
  private PrintWriter mTracePrintWriter = null;
  private boolean mTracing = false;  // togglable using checkbox

  private static class Stuff {
    private Path[] mFingerPaths = new Path[MAX_FINGERS];
    private ArrayList<Path> mPaths = new ArrayList<Path>();
    private ArrayList<Paint> mCompletedPaints = new ArrayList<Paint>();
    private float[] startX = new float[MAX_FINGERS];
    private float[] startY = new float[MAX_FINGERS];
    private float[] prevX = new float[MAX_FINGERS];
    private float[] prevY = new float[MAX_FINGERS];
  }
  private Stuff unfixedStuff = new Stuff();
  private Stuff fixedStuff = new Stuff();

  private int mMaxSinceLastClear = 0;
  private int mMaxSinceLastConstruct = 0;
  private int mMaxSinceProcessStarted = 0;

  public PaintView(final Context context) {
    super(context);
    Log.i(TAG, "    in PaintView ctor");

    mMaxSinceLastConstruct = 0;

    mTheTouchableDrawable = new android.view.View(context) {
      @Override
      protected void onDraw(Canvas canvas) {
        final int verboseLevel = 0;
        Rect clipBounds = new Rect();
        canvas.getClipBounds(clipBounds);
        if (verboseLevel >= 1) Log.i(TAG, "            in mTheTouchableDrawable onDraw clipBounds="+clipBounds);
        // CBB: could actually use clipBounds: cull away paths that don't intersect it
        super.onDraw(canvas);
        if (mShowUnfixed) {
          drawStuff(canvas, unfixedStuff, mShadowPaint);
        } else {
          drawStuff(canvas, fixedStuff, mShadowPaint);
        }
        if (verboseLevel >= 1) Log.i(TAG, "            out mTheTouchableDrawable onDraw clipBounds="+clipBounds);
      }
    };
    addView(mTheTouchableDrawable, new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));

    final TextView buggingTextView = new TextView(context) {{ setText("bugging: {}  "); }};
    final TextView maxSinceLastClearTextView = new TextView(context) {{ setText("max since last clear: "+mMaxSinceLastClear+"  "); }};
    final TextView maxSinceLastConstructTextView = new TextView(context) {{ setText("max since last construct: "+mMaxSinceLastConstruct+"  "); }};
    final TextView maxSinceProcessStartedTextView = new TextView(context) {{ setText("max since process started: "+mMaxSinceProcessStarted+"  "); }};
    if (true) {
      addView(new LinearLayout(context) {{
        addView(new Button(context) {{
          setText("Clear");
          setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View button) {
              unfixedStuff.mPaths.clear();
              fixedStuff.mPaths.clear();
              unfixedStuff.mCompletedPaints.clear();
              fixedStuff.mCompletedPaints.clear();
              mTheTouchableDrawable.invalidate();
              mMaxSinceLastClear = 0;
              maxSinceLastClearTextView.setText("max since last clear: "+mMaxSinceLastClear+"  ");  // CBB: method
            }
          });
        }});
        addView(new CheckBox(context) {{
          setText("Fix");
          setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
              mShowUnfixed = !isChecked;
              mTheTouchableDrawable.invalidate();
            }
          });
        }});
        addView(new CheckBox(context) {{
          setText("trace to file");
          setChecked(mTracing);
          setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
              mTracing = isChecked;
              mFixedOnTouchListener.setTracePrintWriter(isChecked ? mTracePrintWriter : null);
            }
          });
        }});
        addView(new TextView(context), new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT) {{
          weight = 1.f;
        }});
        addView(new LinearLayout(context) {{
          setOrientation(VERTICAL);
          addView(buggingTextView);
          addView(maxSinceLastClearTextView);
          addView(maxSinceLastConstructTextView);
          addView(maxSinceProcessStartedTextView);
        }});
      }});
    }


    mFixedOnTouchListener = new FixedOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View view, MotionEvent fixedEvent) {
        // This is our wrapped listener, after the fixer has done its fixing.
        // Record the fixed event.
        applyEventToStuff(fixedEvent, fixedStuff, /*thisStuffIsVisible=*/!mShowUnfixed);
        return true;
      }
    }) {
      @Override
      public boolean onTouch(View view, MotionEvent unfixedEvent) {
        // This is our interception of the event before the fixer sees it.
        // Record the unfixed event.
        applyEventToStuff(unfixedEvent, unfixedStuff, /*thisStuffIsVisible=*/mShowUnfixed);
        // Then pass it through to the FixedOnTouchListener,
        // which creates a fixed event which is passed
        // to our wrapped OnTouchListener's onTouch() above.
        boolean answer = super.onTouch(view, unfixedEvent);

        {
          // Before returning, query the fixer to find out
          // which ids are now bugging.
          final int[] buggingIds = buggingIds();
          buggingTextView.setText("bugging: "+STRINGIFY(buggingIds));
          if (buggingIds.length > mMaxSinceLastClear) {
            mMaxSinceLastClear = buggingIds.length;
            maxSinceLastClearTextView.setText("max since last clear: "+mMaxSinceLastClear+"  ");
            if (buggingIds.length > mMaxSinceLastConstruct) {
              mMaxSinceLastConstruct = buggingIds.length;
              maxSinceLastConstructTextView.setText("max since last construct: "+mMaxSinceLastConstruct+"  ");
              if (buggingIds.length > mMaxSinceProcessStarted) {
                mMaxSinceProcessStarted = buggingIds.length;
                maxSinceProcessStartedTextView.setText("max since process started: "+mMaxSinceProcessStarted+"  ");
              }
            }
          }
        }
        return answer;
      }
    };

    // Initialize mTracePrintWriter, but don't hook it up to the listener yet.
    {
      // See top of MultiTouchBugWorkaroundActivity.java for recipe on how to get this back out.
      Log.i(TAG, "      YOU ARE HERE==============================================================================================================================================================================================================================================================================================================================================");
      String traceFileName = "FixedOnTouchListener.trace.txt";
      String traceFilePathNameIThink = context.getFilesDir().getAbsolutePath()+"/"+traceFileName;
      java.io.FileOutputStream fileOutputStream = null;
      try {
        fileOutputStream = context.openFileOutput(traceFileName, 0);
      } catch (FileNotFoundException e) {
        throw new AssertionError("coudn't open file output "+traceFilePathNameIThink);
      }
      Log.i(TAG, "      setting fix trace fileOutputStream to:  "+traceFilePathNameIThink);
      mTracePrintWriter = new java.io.PrintWriter(fileOutputStream);
      mTracePrintWriter.write("hello world\n");
      mTracePrintWriter.flush();
    }

    if (mTracing) {
      mFixedOnTouchListener.setTracePrintWriter(mTracePrintWriter);
    }

    // TODO: delay this, see if I can catch it in the middle of a gesture
    mTheTouchableDrawable.setOnTouchListener(mFixedOnTouchListener);

    final int colors[] = new int[] {
      Color.RED,
      Color.BLUE,
      0xff00c000,  // not quite so jarring green
      Color.MAGENTA,
      Color.YELLOW,
      Color.CYAN,
      0xffff8000,  // orange
      0xffc000ff,  // purple, towards magenta
      Color.BLACK,
      Color.GRAY,
    };
    CHECK_EQ(colors.length, MAX_FINGERS);
    for (int i = 0; i < MAX_FINGERS; ++i) {
      mFingerPaints[i] = new Paint();
      mFingerPaints[i].setAntiAlias(true);
      mFingerPaints[i].setColor(colors[i]);
      mFingerPaints[i].setStyle(Paint.Style.STROKE);
      mFingerPaints[i].setStrokeWidth(STROKE_WIDTH);
      mFingerPaints[i].setStrokeCap(Paint.Cap.BUTT);

      // Hmm, setShadowLayer seems to be hard-to-control and/or buggy:
      // - can't seem to get it to be opaque-ish for any significant distance
      // - with hw accel on, its color seems to be ignored; it uses the stroke color
      //mFingerPaints[i].setShadowLayer(SHADOW_RADIUS, /*dx=*/0.f, /*dy=*/0.f, 0xfe8080ff);
      // So instead, we just draw a shadow explicitly on every draw of the path.
    }

    if (SHADOW_RADIUS > 0.f) {
      mShadowPaint = new Paint() {{
        setAntiAlias(true);
        setColor(0xffc0c0c0);  // CBB: same as background, should get from a common source
        setStyle(Paint.Style.STROKE);
        setStrokeWidth(STROKE_WIDTH + 2.0f*SHADOW_RADIUS);
        setStrokeCap(Paint.Cap.BUTT);
      }};
    }

    Log.i(TAG, "    out PaintView ctor");
  }

  private static void drawStuff(Canvas canvas, Stuff stuff, Paint shadowPaint) {
    CHECK_EQ(stuff.mPaths.size(), stuff.mCompletedPaints.size());
    for (int i = 0; i < stuff.mPaths.size(); ++i) {
      if (shadowPaint != null) {
        canvas.drawPath(stuff.mPaths.get(i), shadowPaint);
      }
      canvas.drawPath(stuff.mPaths.get(i), stuff.mCompletedPaints.get(i));
    }
  }
  private void applyEventToStuff(MotionEvent event, Stuff stuff, boolean thisStuffIsVisible) {

    final int verboseLevel = 0;
    if (verboseLevel >= 1) Log.i(TAG, "                in applyEventToStuff(stuff, thisStuffIsVisible="+thisStuffIsVisible+")");

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

    if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && actionId < MAX_FINGERS) {
      CHECK(stuff.mFingerPaths[actionId] == null);   // XXX I've seen this fail!  How to debug?
      stuff.mFingerPaths[actionId] = new Path();
      if (verboseLevel >= 1) Log.i(TAG, "                  starting path "+actionId+": moveTo "+event.getX(actionIndex)+", "+event.getY(actionIndex));
      stuff.mFingerPaths[actionId].moveTo(event.getX(actionIndex), event.getY(actionIndex));
      stuff.startX[actionId] = event.getX(actionIndex);
      stuff.startY[actionId] = event.getY(actionIndex);
      stuff.prevX[actionId] = stuff.startX[actionId];
      stuff.prevY[actionId] = stuff.startY[actionId];

      // It seems to be fine to draw it while in progress
      // (I'm not sure what the value of the final setLastPoint() is).
      stuff.mPaths.add(stuff.mFingerPaths[actionId]);
      stuff.mCompletedPaints.add(mFingerPaints[actionId]);
    }

    if (action == MotionEvent.ACTION_MOVE) {
      // honor historical motion
      final int historySize = event.getHistorySize();
      for (int h = 0; h < historySize+1; ++h) {
        for (int index = 0; index < pointerCount; ++index) {
          int id = event.getPointerId(index);
          if (id < MAX_FINGERS && stuff.mFingerPaths[id] != null) {
            final float x = h==historySize ? event.getX(index) : event.getHistoricalX(index, h);
            final float y = h==historySize ? event.getY(index) : event.getHistoricalY(index, h);
            if (x != stuff.prevX[id] || y != stuff.prevY[id]) {
              if (verboseLevel >= 1) Log.i(TAG, "                  adding to path "+id+": lineTo "+x+", "+y+"  (h="+h+"/"+historySize+")");
              stuff.mFingerPaths[id].lineTo(x, y);
              if (thisStuffIsVisible) {
                if (false) {
                  // Overkill
                  stuff.mFingerPaths[id].computeBounds(mPathBoundsScratch, true);
                  mTheTouchableDrawable.invalidate((int) mPathBoundsScratch.left, (int) mPathBoundsScratch.top,
                      (int) mPathBoundsScratch.right, (int) mPathBoundsScratch.bottom);
                } else {
                  // NOTE: if hw accel is on (which it is by default),
                  // then onDraw() never sees these reduced clip bounds.
                  // (We *do* still need to invalidate, but the region we give it is ignored)
                  //final int pad = (int)Math.ceil(STROKE_WIDTH/4.);  // too small, as expected (only observable with hw accel false)
                  final int pad = (int)Math.ceil(STROKE_WIDTH/2. + SHADOW_RADIUS);
                  int x0 = (int)Math.min(stuff.prevX[id], x)-pad;
                  int y0 = (int)Math.min(stuff.prevY[id], y)-pad;
                  int x1 = (int)Math.max(stuff.prevX[id], x)+pad;
                  int y1 = (int)Math.max(stuff.prevY[id], y)+pad;
                  if (verboseLevel >= 1) Log.i(TAG, "invalidating "+x0+","+y0+" .. "+x1+","+y1+"");
                  mTheTouchableDrawable.invalidate(x0,y0,x1,y1);
                }
              }
              stuff.prevX[id] = x;
              stuff.prevY[id] = y;
            }
          }
        }
      }
    }

    if ((action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) && actionId < MAX_FINGERS) {
      if (verboseLevel >= 1) Log.i(TAG, "                  ending path "+actionId+": setLastPoint "+event.getX(actionIndex)+", "+event.getY(actionIndex));
      stuff.mFingerPaths[actionId].setLastPoint(event.getX(actionIndex), event.getY(actionIndex));
      stuff.mFingerPaths[actionId].computeBounds(mPathBoundsScratch, true);
      mTheTouchableDrawable.invalidate(
          (int) mPathBoundsScratch.left,
          (int) mPathBoundsScratch.top,
          (int) mPathBoundsScratch.right,
          (int) mPathBoundsScratch.bottom);
      stuff.mFingerPaths[actionId] = null;
    }
    if (verboseLevel >= 1) Log.i(TAG, "                out applyEventToStuff(stuff, thisStuffIsVisible="+thisStuffIsVisible+")");
  }  // applyEventToStuff

}
