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
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.HashMap;

import static com.example.donhatch.multitouchbugworkaround.CHECK.*;
import static com.example.donhatch.multitouchbugworkaround.STRINGIFY.STRINGIFY;

// https://gist.github.com/ErikHellman/6069322
public class PaintView extends LinearLayout {  // CBB: I wanted android.support.constraint.ConstraintLayout but not sure how to add it to project

  private static final String TAG = MultiTouchBugWorkaroundActivity.class.getSimpleName();

  public static final int MAX_FINGERS = 10;
  private Paint[] mFingerPaints = new Paint[MAX_FINGERS];;
  private RectF mPathBounds = new RectF();
  private boolean mShowUnfixed = true;

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

  public PaintView(Context context) {
    super(context);
    Log.i(TAG, "    in PaintView ctor");

    setOnTouchListener(new FixedOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View view, MotionEvent fixedEvent) {
        // Record the fixed event.
        applyEventToStuff(fixedEvent, fixedStuff, /*thisStuffIsVisible=*/mShowUnfixed);
        return true;
      }
    }) {
      @Override
      public boolean onTouch(View view, MotionEvent unfixedEvent) {
        // We first get the event, with the bug, here.
        // Record it...
        applyEventToStuff(unfixedEvent, unfixedStuff, /*thisStuffIsVisible=*/!mShowUnfixed);
        // Then pass it through to the FixedOnTouchListener,
        // which creates a fixed event which is passed
        // to our OnTouchListener's onTouch() above.
        return super.onTouch(view, unfixedEvent);
      }
    });

    addView(new Button(context) {{
      setText("Clear");
      setOnClickListener(new Button.OnClickListener() {
        @Override
        public void onClick(View button) {
          unfixedStuff.mPaths.clear();
          fixedStuff.mPaths.clear();
          unfixedStuff.mCompletedPaints.clear();
          fixedStuff.mCompletedPaints.clear();
          PaintView.this.invalidate();
        }
      });
    }});
    addView(new CheckBox(context) {{
      setText("Fix");
      setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          mShowUnfixed = !isChecked;
          PaintView.this.invalidate();
        }
      });
    }});

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
    for (int i = 0; i < MAX_FINGERS; ++i) {
      mFingerPaints[i] = new Paint();
      mFingerPaints[i].setAntiAlias(true);
      mFingerPaints[i].setColor(colors[i]);
      mFingerPaints[i].setStyle(Paint.Style.STROKE);
      mFingerPaints[i].setStrokeWidth(6);
      mFingerPaints[i].setStrokeCap(Paint.Cap.BUTT);
    }

    Log.i(TAG, "    out PaintView ctor");
  }

  @Override
  protected void onDraw(Canvas canvas) {
    final int verboseLevel = 0;
    if (verboseLevel >= 1) Log.i(TAG, "            in PaintView onDraw");
    super.onDraw(canvas);
    if (mShowUnfixed) {
      drawStuff(canvas, unfixedStuff);
    } else {
      drawStuff(canvas, fixedStuff);
    }
    if (verboseLevel >= 1) Log.i(TAG, "            out PaintView onDraw");
  }

  private void drawStuff(Canvas canvas, Stuff stuff) {
    CHECK_EQ(stuff.mPaths.size(), stuff.mCompletedPaints.size());
    for (int i = 0; i < stuff.mPaths.size(); ++i) {
      canvas.drawPath(stuff.mPaths.get(i), stuff.mCompletedPaints.get(i));
    }
  }
  private void applyEventToStuff(MotionEvent event, Stuff stuff, boolean thisStuffIsVisible) {

    final int verboseLevel = 0;

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
      CHECK(stuff.mFingerPaths[actionId] == null);
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

    final int calledStrategy = 2;
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
                  stuff.mFingerPaths[id].computeBounds(mPathBounds, true);
                  invalidate((int) mPathBounds.left, (int) mPathBounds.top,
                      (int) mPathBounds.right, (int) mPathBounds.bottom);
                } else if (false){
                  // CBB: actually probably need to add half line width in all directions.
                  // But I'm not sure this selective invalidation actually does anything;
                  // I think it's the same as calling invalidate() with no args.
                  invalidate((int)Math.min(stuff.prevX[id], x),
                             (int)Math.min(stuff.prevY[id], y),
                             (int)Math.max(stuff.prevX[id], x),
                             (int)Math.max(stuff.prevY[id], y));
                } else {
                  // This seems to be adequate.  (?!)
                  invalidate(10,10, 11,11);
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
      stuff.mFingerPaths[actionId].computeBounds(mPathBounds, true);
      invalidate((int) mPathBounds.left, (int) mPathBounds.top,
          (int) mPathBounds.right, (int) mPathBounds.bottom);
      stuff.mFingerPaths[actionId] = null;
    }
  }  // applyEventToStuff

}
