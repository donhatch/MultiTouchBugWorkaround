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
    private ArrayList<Path> mCompletedPaths = new ArrayList<Path>();
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

    if (true) {
      setOnTouchListener(new FixedOnTouchListener(new MyTouchListener2()));
    } else if (true) {
      setOnTouchListener(new FixedOnTouchListener(new MyTouchListener()));
    } else {
      setOnTouchListener(new MyTouchListener());
    }

    addView(new Button(context) {{
      setText("Clear");
      setOnClickListener(new Button.OnClickListener() {
        @Override
        public void onClick(View button) {
          unfixedStuff.mCompletedPaths.clear();
          fixedStuff.mCompletedPaths.clear();
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

    Log.i(TAG, "        out PaintView onAttachedToWindow");
  }

  @Override
  protected void onDraw(Canvas canvas) {
    final int verboseLevel = 0;
    if (verboseLevel >= 1) Log.i(TAG, "            in PaintView onDraw");
    super.onDraw(canvas);

    if (mShowUnfixed) {
      CHECK_EQ(unfixedStuff.mCompletedPaths.size(), unfixedStuff.mCompletedPaints.size());
      for (int i = 0; i < unfixedStuff.mCompletedPaths.size(); ++i) {
        canvas.drawPath(unfixedStuff.mCompletedPaths.get(i), unfixedStuff.mCompletedPaints.get(i));
      }
      CHECK_EQ(fixedStuff.mFingerPaths.length, mFingerPaints.length);
      for (int i = 0; i < fixedStuff.mFingerPaths.length; ++i) {
        if (fixedStuff.mFingerPaths[i] != null) {
            canvas.drawPath(fixedStuff.mFingerPaths[i], mFingerPaints[i]);
        }
      }
    } else {
      CHECK_EQ(fixedStuff.mCompletedPaths.size(), fixedStuff.mCompletedPaints.size());
      for (int i = 0; i < fixedStuff.mCompletedPaths.size(); ++i) {
        canvas.drawPath(fixedStuff.mCompletedPaths.get(i), fixedStuff.mCompletedPaints.get(i));
      }
      CHECK_EQ(unfixedStuff.mFingerPaths.length, mFingerPaints.length);
      for (int i = 0; i < unfixedStuff.mFingerPaths.length; ++i) {
        if (unfixedStuff.mFingerPaths[i] != null) {
            canvas.drawPath(unfixedStuff.mFingerPaths[i], mFingerPaints[i]);
        }
      }
    }

    if (verboseLevel >= 1) Log.i(TAG, "            out PaintView onDraw");
  }


  class MyTouchListener implements View.OnTouchListener {
    private FixedOnTouchListener.OnTouchListener2 onTouchListener2 = new MyTouchListener2();
    @Override
    public boolean onTouch(View view, MotionEvent touchEvent) {
      return onTouchListener2.onTouch(view, null, touchEvent);
    }
  }


  class MyTouchListener2 implements FixedOnTouchListener.OnTouchListener2 {
    @Override
    public boolean onTouch(View view_unused, MotionEvent unfixedEvent, MotionEvent fixedEvent) {
      final int verboseLevel = 0;  // 0:nothing, 1: in, 2: and out and more detail

      if (unfixedEvent != null) applyEventToStuff(unfixedEvent, unfixedStuff);
      if (fixedEvent != null) applyEventToStuff(fixedEvent, fixedStuff);

      return true;
    }

    private void applyEventToStuff(MotionEvent event, Stuff stuff) {

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
        stuff.mFingerPaths[actionId] = new Path();
        if (verboseLevel >= 1) Log.i(TAG, "                  starting path "+actionId+": moveTo "+event.getX(actionIndex)+", "+event.getY(actionIndex));
        stuff.mFingerPaths[actionId].moveTo(event.getX(actionIndex), event.getY(actionIndex));
        stuff.startX[actionId] = event.getX(actionIndex);
        stuff.startY[actionId] = event.getY(actionIndex);
        stuff.prevX[actionId] = stuff.startX[actionId];
        stuff.prevY[actionId] = stuff.startY[actionId];
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
              if (verboseLevel >= 1) Log.i(TAG, "                  adding to path "+id+": lineTo "+x+", "+y+"  (h="+h+"/"+historySize+")");
              stuff.mFingerPaths[id].lineTo(x, y);
              if (pointerCount == 2) {
                boolean omitPerCalledStrategy1 = (x == stuff.startX[id] && y == stuff.startY[id]);
                boolean omitPerCalledStrategy2 = (x == stuff.prevX[id] && y == stuff.prevY[id]);
                if (verboseLevel >= 1) Log.i(TAG, "                      omitPerCalledStrategy1 = "+omitPerCalledStrategy1);
                if (verboseLevel >= 1) Log.i(TAG, "                      omitPerCalledStrategy2 = "+omitPerCalledStrategy2);

                //CHECK(!omitPerCalledStrategy1 || omitPerCalledStrategy2);
                if (omitPerCalledStrategy1 && !omitPerCalledStrategy2) {
                  // happens sometimes... only just after starting a path or something?
                  if (verboseLevel >= 1) Log.i(TAG, "                      HEY!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                }
              }

              stuff.prevX[id] = x;
              stuff.prevY[id] = y;
              stuff.mFingerPaths[id].computeBounds(mPathBounds, true);
              invalidate((int) mPathBounds.left, (int) mPathBounds.top,
                  (int) mPathBounds.right, (int) mPathBounds.bottom);
            }
          }
        }
      }

      if ((action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) && actionId < MAX_FINGERS) {
        if (verboseLevel >= 1) Log.i(TAG, "                  ending path "+actionId+": setLastPoint "+event.getX(actionIndex)+", "+event.getY(actionIndex));
        stuff.mFingerPaths[actionId].setLastPoint(event.getX(actionIndex), event.getY(actionIndex));
        stuff.mCompletedPaths.add(stuff.mFingerPaths[actionId]);
        stuff.mCompletedPaints.add(mFingerPaints[actionId]);
        stuff.mFingerPaths[actionId].computeBounds(mPathBounds, true);
        invalidate((int) mPathBounds.left, (int) mPathBounds.top,
            (int) mPathBounds.right, (int) mPathBounds.bottom);
        stuff.mFingerPaths[actionId] = null;
      }
    }  // applyEventToStuff
  }
}
