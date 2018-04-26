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

  public static final int MAX_FINGERS = 10;
  private Path[] mFingerPaths = new Path[MAX_FINGERS];
  private Paint[] mFingerPaints;

  private ArrayList<Path> mCompletedPaths;
  private ArrayList<Paint> mCompletedPaints;
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
          mCompletedPaints.clear();
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
    mCompletedPaints = new ArrayList<Paint>();

    final int colors[] = new int[] {
      Color.RED,
      Color.BLUE,
      0xff00c000,  // not quite so jarring green
      0xffff8000,  // orange
      0xff8000ff,  // purple
      Color.YELLOW,
      Color.CYAN,
      Color.MAGENTA,
      Color.BLACK,
      Color.GRAY,
    };
    CHECK_EQ(colors.length, MAX_FINGERS);
    mFingerPaints = new Paint[MAX_FINGERS];
    for (int i = 0; i < MAX_FINGERS; ++i) {
      mFingerPaints[i] = new Paint();
      mFingerPaints[i].setAntiAlias(true);
      mFingerPaints[i].setColor(colors[i]);
      mFingerPaints[i].setStyle(Paint.Style.STROKE);
      mFingerPaints[i].setStrokeWidth(6);
      mFingerPaints[i].setStrokeCap(Paint.Cap.BUTT);
    }

    Log.i(TAG, "        out PaintView onAttachedToWindow");
  }

  @Override
  protected void onDraw(Canvas canvas) {
    final int verboseLevel = 0;
    if (verboseLevel >= 1) Log.i(TAG, "            in PaintView onDraw");
    super.onDraw(canvas);

    CHECK_EQ(mCompletedPaths.size(), mCompletedPaints.size());
    for (int i = 0; i < mCompletedPaths.size(); ++i) {
      canvas.drawPath(mCompletedPaths.get(i), mCompletedPaints.get(i));
    }

    CHECK_EQ(mFingerPaths.length, mFingerPaints.length);
    for (int i = 0; i < mFingerPaths.length; ++i) {
      if (mFingerPaths[i] != null) {
          canvas.drawPath(mFingerPaths[i], mFingerPaints[i]);
      }
    }
    if (verboseLevel >= 1) Log.i(TAG, "            out PaintView onDraw");
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
        if (verboseLevel >= 1) Log.i(TAG, "                  starting path "+actionId+": moveTo "+event.getX(actionIndex)+", "+event.getY(actionIndex));
        mFingerPaths[actionId].moveTo(event.getX(actionIndex), event.getY(actionIndex));
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
        mCompletedPaths.add(mFingerPaths[actionId]);
        mCompletedPaints.add(mFingerPaints[actionId]);
        mFingerPaths[actionId].computeBounds(mPathBounds, true);
        invalidate((int) mPathBounds.left, (int) mPathBounds.top,
            (int) mPathBounds.right, (int) mPathBounds.bottom);
        mFingerPaths[actionId] = null;
      }

      if (verboseLevel >= 2) Log.i(TAG, "                out PaintView onTouchEvent");
      return true;
    }
  }
}
