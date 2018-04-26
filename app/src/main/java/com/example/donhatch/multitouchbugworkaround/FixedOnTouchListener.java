// The infamous Oreo 8.1 multitouch bug
//
// https://android-review.googlesource.com/c/platform/frameworks/native/+/640606/
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
// Q: what is the first evidence that it's happening?
//    - sometimes the non-0 pointer begins bugging before the 0 pointer even moves
//      from its down position, so we have to recognize it-- how?
//    - seems to always start with the non-0 repeating the thing it's going to get stuck on,
//      twice or more, while 0-id is still in its down position.
// Q: what other changes are happening that I'm not tracking, and can I use them to characterize?
//    - pressure? size? axis? blah blah blah
//    - AXIS_HAT_X? AXIS_RELATIVE_X?
// Q: can the bug affect a *UP? or just moves?
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
//  - the first occurrence of the bad non-0-id value (which doesn't need to be corrected there) is at the end of the first MOVE packet after the POINTER_DOWN(0); and that packet always contains one additional history not equal to the stuck value. (what if that pointer isn't moving, you may ask?  well in that case the bug doesn't happen!)
//      Wait that's false-- that one historical can have the same value.
//  - smoking gun: if size,pressure are same as previous but x,y are *not* same as previous, then x,y very likely *should* be same as previous (corrected)
//    on the other hand, if size,pressure are *not* same as previous (corrected), then this particular x,y isn't being affected by the bug.
/*
3.614  805/997:                  {1}                             1: 2492.13,711.506,1.50000,0.323242
3.617  806/997:           MOVE   {1}                             1: 2490.36,711.062,1.50000,0.323242
3.622  807/997:                  {1}                             1: 2488.14,710.507,1.50000,0.320801 D
3.626  808/997:           MOVE   {1}                             1: 2486.14,710.007,1.50000,0.320801
3.631  809/997:  POINTER_DOWN(0) {0, 1}     0: 2363.18,1045.27,0.587500,0.159180 A  1: 2488.14,710.507,1.50000,0.320801 D
3.631  810/997:                  {0, 1}     0:(2363.18,1045.27,0.587500,0.159180)A  1: 2483.14,708.508,1.50000,0.317383 E
3.634  811/997:           MOVE   {0, 1}     0:(2363.18,1045.27,0.587500,0.159180)A  1:(2483.14,708.508,1.50000,0.317383)E
3.639  812/997:                  {0, 1}     0:(2363.18,1045.27,0.600000,0.162598)A  1:(2483.14,708.508,1.50000,0.317383)E
3.639  813/997:           MOVE   {0, 1}     0:(2363.18,1045.27,0.600000,0.162598)A  1: 2479.14,706.509,1.48750,0.314941
3.648  814/997:                  {0, 1}     0:(2363.18,1045.27,0.612500,0.166504)A  1: 2483.14,708.508,1.48750,0.314941 E
*/
//    ARGH! here is a counterexample:  size,pressure not same as previous,  but x,y *should* be corrected to previous corrected.
/*
1.567  338/368:           MOVE   {0, 1}  0: 811.718201,661.540588,1.11250007,0.239746094,-1.57079637 A?  1: 1434.50195,491.658569,1.20000005,0.250976563,-1.57079637
1.575  339/368:           MOVE   {0, 1}  0: 832.710876,1000.30536,1.11250007,0.238769531,-1.57079637     1: 1474.48804,435.697418,1.20000005,0.250976563,-1.57079637 B?
1.584  340/368:                  {0, 1}  0: 833.710510,1000.30536,1.11250007,0.238769531,-1.57079637     1:(1474.48804,435.697418,1.20000005,0.250976563,-1.57079637)B
1.584  341/368:           MOVE   {0, 1}  0: 811.718201,661.540588,1.11250007,0.238769531,-1.57079637 A?  1: 1434.50195,492.657867,1.20000005,0.250976563,-1.57079637
1.592  342/368:                  {0, 1}  0: 834.710205,1000.30536,1.11250007,0.238769531,-1.57079637     1: 1474.48804,435.697418,1.20000005,0.250976563,-1.57079637 B?
1.592  343/368:           MOVE   {0, 1}  0: 811.718201,661.540588,1.11250007,0.238769531,-1.57079637 A?  1: 1434.50195,493.657166,1.20000005,0.250976563,-1.57079637
1.601  344/368:           MOVE   {0, 1}  0: 835.709839,1001.30463,1.11250007,0.238769531,-1.57079637     1: 1474.48804,435.697418,1.20000005,0.250976563,-1.57079637 B?
1.609  345/368:           MOVE   {0, 1}  0: 811.718201,661.540588,1.11250007,0.238769531,-1.57079637 A?  1: 1434.50195,494.656494,1.20000005,0.251953125,-1.57079637
1.617  346/368:           MOVE   {0, 1}  0:(811.718201,661.540588,1.11250007,0.237792969,-1.57079637)A   1: 1474.48804,435.697418,1.20000005,0.251953125,-1.57079637 B?
1.634  347/368:           MOVE   {0, 1}  0: 836.709473,1001.30463,1.11250007,0.237792969,-1.57079637     1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.642  348/368:                  {0, 1}  0: 811.718201,661.540588,1.11250007,0.237304688,-1.57079637 A   1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.642  349/368:           MOVE   {0, 1}  0:(811.718201,661.540588,1.11250007,0.237304688,-1.57079637)A   1: 1434.50195,495.655792,1.20000005,0.251953125,-1.57079637
1.667  350/368:           MOVE   {0, 1}  0:(811.718201,661.540588,1.10000002,0.237304688,-1.57079637)A   1: 1474.48804,435.697418,1.20000005,0.251953125,-1.57079637 B?
1.676  351/368:                  {0, 1}  0:(811.718201,661.540588,1.10000002,0.237304688,-1.57079637)A   1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.676  352/368:           MOVE   {0, 1}  0:(811.718201,661.540588,1.10000002,0.237304688,-1.57079637)A   1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.684  353/368:           MOVE   {0, 1}  0:(811.718201,661.540588,1.10000002,0.237304688,-1.57079637)A   1: 1435.50159,495.655792,1.20000005,0.251953125,-1.57079637
1.701  354/368:           MOVE   {0, 1}  0: 837.709167,1001.30463,1.10000002,0.236328125,-1.57079637     1: 1474.48804,435.697418,1.20000005,0.251953125,-1.57079637 B?
1.709  355/368:           MOVE   {0, 1}  0: 838.708801,1001.30463,1.10000002,0.236328125,-1.57079637     1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.718  356/368:           MOVE   {0, 1}  0: 840.708130,1001.30463,1.10000002,0.235351563,-1.57079637     1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.726  357/368:           MOVE   {0, 1}  0: 842.707397,1001.30463,1.08749998,0.234375000,-1.57079637     1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
*/
//  - every time it gives a bad (stuck) x,y, the *correct* value is the *previous* x,y.
//    the current size,pressure are always (correctly) the same as th size,pressure
//    from that previous one.
// Ooh important-- the bug can persist on the 0 id for a short time after one of the participating ids went UP!  In fact it lasted for 2 logical events!  Seems there's little we can say
/*
8.973 1514/1552:                  {0, 1}  0:(1614.43945,884.385803,1.48750007,0.250976563,-1.57079637)A   1:(2393.16919,686.523193,1.28750002,0.237792969,-1.57079637)A
8.973 1515/1552:           MOVE   {0, 1}  0:(1614.43945,884.385803,1.48750007,0.250976563,-1.57079637)A   1:(2393.16919,686.523193,1.23750007,0.238769531,-1.57079637)A
8.981 1516/1552:           MOVE   {0, 1}  0:(1614.43945,884.385803,1.48750007,0.251953125,-1.57079637)A   1:(2393.16919,686.523193,1.23750007,0.238769531,-1.57079637)A
8.981 1517/1552:    POINTER_UP(1) {0, 1}  0:(1614.43945,884.385803,1.48750007,0.251953125,-1.57079637)A   1:(2393.16919,686.523193,1.23750007,0.238769531,-1.57079637)A
8.988 1518/1552:           MOVE   {0}     0:(1614.43945,884.385803,1.50000000,0.250488281,-1.57079637)A
9.005 1519/1552:           MOVE   {0}     0:(1614.43945,884.385803,1.50000000,0.250488281,-1.57079637)A
9.014 1520/1552:                  {0}     0: 469.836884,1031.28381,1.50000000,0.250488281,-1.57079637
*/
// - also seen the non-0 be bad on the 0-UP... and once in  while, it's even bad a bit after the 0-up
/*
2.574  519/574:                  {0, 1}  0: 314.890656,1050.27063,1.18750000,0.231445313,-1.57079637     1:(2020.29858,540.124939,1.47500002,0.258789063,-1.57079637)A
2.583  520/574:           MOVE   {0, 1}  0: 313.891022,1050.27063,1.14999998,0.231445313,-1.57079637     1:(2020.29858,540.124939,1.47500002,0.258789063,-1.57079637)A
2.591  521/574:    POINTER_UP(0) {0, 1}  0:(313.891022,1050.27063,1.14999998,0.231445313,-1.57079637)    1:(2020.29858,540.124939,1.47500002,0.258789063,-1.57079637)A
2.623  522/574:           MOVE   {1}                          1:(2020.29858,540.124939,1.47500002,0.258789063,-1.57079637)A
2.640  523/574:                  {1}                          1: 2193.23853,510.645386,1.47500002,0.258789063,-1.57079637
2.648  524/574:           MOVE   {1}                          1: 2193.23853,510.177124,1.47500002,0.258789063,-1.57079637
*/
//  - first appearance of wrongness is always in idNonzero, always during the initial static x,y of id0 on its POINTER_DOWN  (which always seems to be about 40 to 60 ms, regardless of whether bugging)
//  - that first bad x,y, which is in idNonzero as previously stated, is always 16 to 17 ms after id0's POINTER_DOWN  (or, occasionally, 25ms)
//  - when bugging, there are no dups other than the wrong x,y's  (because dups *turn into* the wrong x,y's)
//    therefore if we see a dup other than the anchor, the bug is NOT happening.
//  - when bugging, wrong x,y values always occur when it would otherwise be a dup-- which, I think, occur when some *other* id changed?  (or, maybe, same id pressure changed)
//  - when the bug happens, idNonzero's x,y always:
//      - starts at anchor, maybe stays there a while with same pressure&size, goes to 1 other value (same or diff p&s), immediately comes back to anchor with same p&s as prev.
// WRONG. counterexample:
/*
1.245  242/354:           MOVE   {1}                                                         1: 2491.13501,805.440674,1.57500005,0.319824219
1.254  243/354:  POINTER_DOWN(0) {0, 1}  0: 2131.26001,1080.24976,0.937500000,0.218261719 A!  1:(2491.13501,805.440674,1.57500005,0.319824219)
1.254  244/354:                  {0, 1}  0:(2131.26001,1080.24976,0.937500000,0.218261719)A   1: 2490.13550,807.439270,1.56250000,0.318359375
1.258  245/354:           MOVE   {0, 1}  0:(2131.26001,1080.24976,0.937500000,0.218261719)A   1: 2489.63574,808.438599,1.56250000,0.318359375 A!
1.262  246/354:                  {0, 1}  0:(2131.26001,1080.24976,0.937500000,0.219238281)A   1:(2489.63574,808.438599,1.56250000,0.318359375)A?
1.262  247/354:                  {0, 1}  0:(2131.26001,1080.24976,0.937500000,0.219238281)A   1: 2490.13550,808.438599,1.55000007,0.317382813
1.271  248/354:           MOVE   {0, 1}  0:(2131.26001,1080.24976,0.937500000,0.219238281)A   1: 2489.13574,810.437195,1.53750002,0.315917969
1.279  249/354:                  {0, 1}  0:(2131.26001,1080.24976,0.949999988,0.221191406)A   1: 2489.63574,808.438599,1.53750002,0.315917969 A?
1.279  250/354:                  {0, 1}  0:(2131.26001,1080.24976,0.949999988,0.221191406)A   1: 2489.13574,812.435791,1.53750002,0.314941406
1.288  251/354:                  {0, 1}  0:(2131.26001,1080.24976,0.949999988,0.222167969)A   1: 2489.63574,808.438599,1.53750002,0.314941406 A?
and another:
0.393   75/120:           MOVE   {1}                                                         1: 1211.57935,201.859818,1.22500002,0.260742188
0.402   76/120:  POINTER_DOWN(0) {0, 1}  0: 609.788269,479.666901,0.712500036,0.202636719 A!  1:(1211.57935,201.859818,1.22500002,0.260742188)
0.402   77/120:                  {0, 1}  0:(609.788269,479.666901,0.712500036,0.202636719)A   1: 1211.57935,203.858429,1.22500002,0.261718750
0.406   78/120:           MOVE   {0, 1}  0:(609.788269,479.666901,0.712500036,0.202636719)A   1: 1211.57935,204.857727,1.22500002,0.261718750 A!
0.410   79/120:                  {0, 1}  0:(609.788269,479.666901,0.725000024,0.204589844)A   1:(1211.57935,204.857727,1.22500002,0.261718750)A?
0.410   80/120:                  {0, 1}  0:(609.788269,479.666901,0.725000024,0.204589844)A   1: 1210.57971,205.857040,1.23750007,0.262695313
0.419   81/120:                  {0, 1}  0:(609.788269,479.666901,0.737500012,0.208007813)A   1: 1211.57935,204.857727,1.23750007,0.262695313 A?
0.419   82/120:           MOVE   {0, 1}  0:(609.788269,479.666901,0.737500012,0.208007813)A   1: 1209.58008,207.855652,1.25000000,0.263671875
0.427   83/120:                  {0, 1}  0:(609.788269,479.666901,0.762499988,0.210449219)A   1: 1211.57935,204.857727,1.25000000,0.263671875 A?
*/
//
// Correlation with timestamp:
//      - on id 0, when it affects x,y, timestamp has typically *not* advanced, but once in a while it has advanced (not consistent)
//      - on id non-0, when it affects x,y, timestamp has typically (I think maybe always?)  advanced by 8 or 9 ms
//        (except when it happened on 0's POINTER_UP, in which case there was no time-advace. argh!), in which case there was no time-advace. argh!)
// Correlation with packet boundaries:
//      - none.
// Correlation with  event-happening-immedately-after-POINTER_DOWN(0)-is-MOVE-of-idNonzero-with-1-historical:
//      - when bugging, seems to be always that syndrome  (x,y of the historical may be same, maybe different)
//      - when not bugging: can be that, or can be something else, e.g. 2 historical, 2nd same as first.
//
// * HOW DO I WRAP THIS UP?
//   Need a heuristic that always works in practice.
//   It'll include things like:
//      - consider things safe again N ms (or N events?) after id0 or idnonzero went up
//   - Sloppy proposal #1:
//       - Get the anchors (POINTER_DOWN x,y for id0, the primary x,y of the following event for idNonzero),
//         and simply consider them forbidden (i.e. force x,y to not move, instead) until/unless safety is declared (or until a new POINTER_DOWN(0) occurs).
//         That might be fine-- even if we get a false positive, forbidding one location on the screen is probably not a problem.
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
        // Bleah, I don't see how to tweak the historical.  I guess I'll create one from scratch then.

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
    public float[/*max_id_occurring+1*/] size;
    // orientation seems to be useless for this analysis; it's always +-1.57079637
    public float[/*max_id_occurring+1*/] orientation;
    // dammit! relative_x,relative_y always zero.
    public float[/*ditto*/] relative_x;
    public float[/*ditto*/] relative_y;
    // oh screw this, get them all!
    public MotionEvent.PointerCoords[] all_axis_values;
    // rawX()/rawY() doesn't seem to be helpful: (1) can't query it per-history nor per-pointer, (2) the bug apparently affects this too so it doesn't give any clues

    // all arrays assumed to be immutable.
    public LogicalMotionEvent(boolean isHistorical, long eventTimeMillis, int action, int actionId, int ids[], float[] x, float[] y, float[] pressure, float[] size, float[] orientation, float[] relative_x, float[] relative_y, MotionEvent.PointerCoords[] all_axis_values) {
      this.isHistorical = isHistorical;
      this.eventTimeMillis = eventTimeMillis;
      this.action = action;
      this.actionId = actionId;
      this.ids = ids;
      this.x = x;
      this.y = y;
      this.pressure = pressure;
      this.size = size;
      this.orientation = orientation;
      this.relative_x = relative_x;
      this.relative_y = relative_y;
      this.all_axis_values = all_axis_values;
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
        final float[] size = new float[maxIdOccurring+1];
        final float[] orientation = new float[maxIdOccurring+1];
        final float[] relative_x = new float[maxIdOccurring+1];
        final float[] relative_y = new float[maxIdOccurring+1];
        final MotionEvent.PointerCoords[] all_axis_values = new MotionEvent.PointerCoords[maxIdOccurring+1];
        for (int id = 0; id < maxIdOccurring+1; ++id) {
          x[id] = Float.NaN;
          y[id] = Float.NaN;
          pressure[id] = Float.NaN;
          size[id] = Float.NaN;
          orientation[id] = Float.NaN;
          relative_x[id] = Float.NaN;
          relative_y[id] = Float.NaN;
          all_axis_values[id] = new MotionEvent.PointerCoords();
        }
        for (int index = 0; index < pointerCount; ++index) {
          final int id = ids[index]; motionEvent.getPointerId(index);
          x[id] = h==historySize ? motionEvent.getX(index) : motionEvent.getHistoricalX(index, h);
          y[id] = h==historySize ? motionEvent.getY(index) : motionEvent.getHistoricalY(index, h);
          pressure[id] = h==historySize ? motionEvent.getPressure(index) : motionEvent.getHistoricalPressure(index, h);
          size[id] = h==historySize ? motionEvent.getSize(index) : motionEvent.getHistoricalSize(index, h);
          orientation[id] = h==historySize ? motionEvent.getOrientation(index) : motionEvent.getHistoricalOrientation(index, h);
          CHECK_EQ(Math.abs(orientation[id]), 1.57079637f);  // XXX not sure if this is reliable on all devices, but it's what I always get
          relative_x[id] = h==historySize ? motionEvent.getAxisValue(MotionEvent.AXIS_RELATIVE_X,index) : motionEvent.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, index, h);
          relative_y[id] = h==historySize ? motionEvent.getAxisValue(MotionEvent.AXIS_RELATIVE_Y,index) : motionEvent.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, index, h);
          CHECK_EQ(Math.abs(relative_x[id]), 0.f);  // XXX not sure if this is reliable on all devices, but it's what I always get
          CHECK_EQ(Math.abs(relative_y[id]), 0.f);  // XXX not sure if this is reliable on all devices, but it's what I always get
          if (h==historySize) {
            motionEvent.getPointerCoords(index, all_axis_values[id]);
          } else {
            motionEvent.getHistoricalPointerCoords(index, h, all_axis_values[id]);
          }
        }
        list.add(new LogicalMotionEvent(h<historySize, eventTimeMillis, action, actionId, ids, x, y, pressure, size, orientation, relative_x, relative_y, all_axis_values));
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

    // PointerCoords.equals() doesn't work (just tests pointer equality)
    private static boolean PointerCoordsEquals(MotionEvent.PointerCoords a, MotionEvent.PointerCoords b) {
      int verboseLevel = 0;
      if (verboseLevel >= 1) Log.i(TAG, "in PointerCoordsEquals(a, b)");
      boolean answer = true;  // until proven otherwise
      final int NUM_AXES = 64;  // empirically, getAxisValue(>=64) throws "java.lang.IllegalArgumentException: Axis out of range.".
      for (int axis = 0; axis < NUM_AXES; ++axis) {
        float aValue = a.getAxisValue(axis);
        float bValue = b.getAxisValue(axis);
        if (verboseLevel >= 1) {
          Log.i(TAG, "  "+axis+": "+aValue+" "+(aValue==bValue?"==":"!=")+" "+bValue+" ("+MotionEvent.axisToString(axis)+")");
          continue;
        }
        if (aValue != bValue) {
          answer = false;
          if (!(verboseLevel >= 1)) break;
        }
      }
      if (verboseLevel >= 1) Log.i(TAG, "out PointerCoordsEquals(a, b), returning "+answer);
      return answer;
    }

    public static void dump(ArrayList<LogicalMotionEvent> list) {
      int n = list.size();

      int idsWidth = 0;
      for (int i = 0; i < n; ++i) {
        idsWidth = Math.max(idsWidth, STRINGIFY(list.get(i).ids).length());
      }

      int max_id_occurring = 9; // XXX fudge

      // For each id,
      // whenever there are 2 or more distinct runs with the same coords,
      // make a label for those coords.
      String[][] id2index2label;
      {
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

      // States:
      //        - known to be safe
      //        - known to be bugging
      //        - known to be safe or safe soon (0 or idOfInterest went UP)
      int idOfInterest = -1; // never zero.  if positive, either might be bugging or definitely bugging.
      float xOfInterestForId0 = Float.NaN;  // if non-nan, might be bugging or definitely bugging.
      float yOfInterestForId0 = Float.NaN;  // if non-nan, might be bugging or definitely bugging.
      float xOfInterestForIdNonzero = Float.NaN;
      float yOfInterestForIdNonzero = Float.NaN;
      boolean knownToBeSafe = true;  // actually the same as idOfInterest==-1
      boolean knownToBeBugging = false;
      final float[] mostRecentNonSuspiciousX = new float[max_id_occurring+1];
      final float[] mostRecentNonSuspiciousY = new float[max_id_occurring+1];

      long refTimeMillis = list.get(0).eventTimeMillis;
      for (int i = 0; i < n;  ++i) {
        LogicalMotionEvent e = list.get(i);

        boolean justNowGotCoordsOfInterestForId0 = false;
        boolean justNowGotCoordsOfInterestForIdNonzero = false;

        if (e.action == MotionEvent.ACTION_POINTER_DOWN
         && e.actionId == 0
         && e.ids.length == 2) {
          CHECK_EQ(e.ids[0], 0);
          // Might be bugging.  Yellow alert.
          knownToBeSafe = false;
          knownToBeBugging = false;
          idOfInterest = e.ids[1];
          xOfInterestForId0 = e.x[0];
          yOfInterestForId0 = e.y[0];
          xOfInterestForIdNonzero = Float.NaN;
          yOfInterestForIdNonzero = Float.NaN;
          justNowGotCoordsOfInterestForId0 = true;
        }
        // And the very next packet seems to always have size 2,
        // and the last (primary) entry in it is the buggy non-0-id item, if any
        if (i >= 2) {
          LogicalMotionEvent ePrevPrev = list.get(i-2);
          LogicalMotionEvent ePrev = list.get(i-1);
          if (ePrevPrev.action == MotionEvent.ACTION_POINTER_DOWN
           && ePrevPrev.actionId == 0
           && ePrevPrev.ids.length == 2) {
            CHECK_GE(idOfInterest, 0);
            xOfInterestForIdNonzero = e.x[idOfInterest];
            yOfInterestForIdNonzero = e.y[idOfInterest];
            justNowGotCoordsOfInterestForIdNonzero = true;
          }
        }

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
          if (Float.isNaN(e.x[id])) {
            //sb.append(String.format("%29s", ""));
            sb.append(String.format("%52s", "", ""));
          } else {
            //String coordsString = String.format("%.9g,%.9g", e.x[id], e.y[id]);
            String coordsString = String.format("%.9g,%.9g,%.9g,%.9g", e.x[id], e.y[id], e.pressure[id], e.size[id]);

            boolean parenthesized = false;
            if (i >= 1) {
              LogicalMotionEvent ePrev = list.get(i-1);
              if (id < ePrev.x.length && e.x[id] == ePrev.x[id]
                                      && e.y[id] == ePrev.y[id]) {
                if (PointerCoordsEquals(e.all_axis_values[id], ePrev.all_axis_values[id])) {
                  coordsString = "["+coordsString+"]";
                } else {
                  coordsString = "("+coordsString+")";
                }
                parenthesized = true;
              }
            }
            if (!parenthesized) {
                coordsString = " "+coordsString+" ";
            }
            coordsString = String.format(" %2d:%s", id, coordsString);

            String label = id2index2label[id][i];
            if (label != null) {
              coordsString += label;
            } else {
              coordsString += " ";
            }

            if (true) {
              // Look for smoking gun: pressure,size same as previous but x,y *not* same as most-recent-good.
              // Bleah, it's not conclusive--- lots of false positives, i.e. pressure,size same as previous
              // and x,y legitimately different from previous.
              // Haven't seen any false negatives though.
              boolean foundSmokingGun = false;
              if (i > 0) {
                LogicalMotionEvent ePrev = list.get(i-1);
                // XXX this type need not be MOVE in order for bug to manifest..  must previous type be MOVE in order for bug to manifest?

                if (id < ePrev.x.length
                 && e.pressure[id] == ePrev.pressure[id]
                 && e.size[id] == ePrev.size[id]
                 //&& (e.x[id] != ePrev.x[id] || e.y[id] != ePrev.y[id])   // XXX wrong!  need to be comparing with previous *corrected*
                 && (e.x[id] != mostRecentNonSuspiciousX[id] || e.y[id] != mostRecentNonSuspiciousY[id])   // XXX wrong!  need to be comparing with previous *corrected*
                 ) {
                  foundSmokingGun = true;
                }
              }

              if (knownToBeSafe) {
                //Log.i(TAG, "    KILLING QUESTION MARK on "+id+" BECAUSE knownToBeSafe");
                foundSmokingGun = false;
              }
              if (id != 0 && id != idOfInterest) { // those are the only suspects
                //Log.i(TAG, "    KILLING QUESTION MARK on "+id+" BECAUSE not a suspect");
                foundSmokingGun = false;
              }
              if (id == 0 && (e.x[0] != xOfInterestForId0 || e.y[0] != yOfInterestForId0)) {  // id 0 only bugs on the coords on which it went down
                //Log.i(TAG, "    KILLING QUESTION MARK on "+id+" BECAUSE the 0 suepect and not the coords on which it went down");
                foundSmokingGun = false;
              }
              if (id != 0 && (e.x[id] != xOfInterestForIdNonzero || e.y[id] != yOfInterestForIdNonzero)) {  // id nonzero only bugs on those coords. when not decided yet, it doesn't bug
                //Log.i(TAG, "    KILLING QUESTION MARK on "+id+" BECAUSE the nonzero suspect and not the coords two after the POINTER_DOWN(0) event which are "+xOfInterestForIdNonzero+","+yOfInterestForIdNonzero+"");
                foundSmokingGun = false;
              }

              if (!foundSmokingGun) {
                mostRecentNonSuspiciousX[id] = e.x[id];
                mostRecentNonSuspiciousY[id] = e.y[id];
              }

              if (id==0 && justNowGotCoordsOfInterestForId0) {
                coordsString += "!";
              } else if (id!=0 && justNowGotCoordsOfInterestForIdNonzero) {
                coordsString += "!";
              } else if (foundSmokingGun) {
                coordsString += "?";
              } else {
                coordsString += " ";
              }
            }
            sb.append(coordsString);
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

