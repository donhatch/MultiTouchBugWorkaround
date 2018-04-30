// The infamous Oreo 8.1 multitouch bug
//
// https://android-review.googlesource.com/c/platform/frameworks/native/+/640606/
//
// TODO: allow querying number-of-bugging-pointers, either via callback or by querying the listener
// TODO: ui that displays, e.g.   num buggy pointers: 1  max since last clear:  2  max ever: 3
// TODO: be able to actually play back the stuff parsed from a dump file:
//   - be able to convert from LogicalMotionEvent(s) to MotionEvent
// TODO: dump the gesture if possible when an exception is being thrown?  more generally, when something rare and interesting happens that I want to trace
// TODO: investigate whether I can get ACTION_CANCEL and/or ACTION_OUTSIDE.
// TODO: for screenshot, have a fake-ish mode that decimates to some fraction of the spines
// BUG: look for "XXX I've seen this fail" in PaintView.java-- that is, got DOWN or POINTER_DOWN when we thought it was down already.  Oh hmm, maybe I miss events sometimes?
//
// Conjecture: the delayed forbid id is always greater than the others.  (to test this, would have to have a robust measure of whether we think the bug is happening, though)
// Conjecture: if any other POINTER_DOWN(id) with id > the delayed forbid id happens at the same time as the other POINTER_DOWNs, the bug won't happen

package com.example.donhatch.multitouchbugworkaround;

import android.util.Log;
import android.view.MotionEvent;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.MotionEvent.ACTION_CANCEL;
import android.view.View;
import java.util.ArrayList;
import java.util.Arrays;

import static com.example.donhatch.multitouchbugworkaround.CHECK.*;
import static com.example.donhatch.multitouchbugworkaround.STRINGIFY.STRINGIFY;

public class FixedOnTouchListener implements View.OnTouchListener {

  private static final String TAG = FixedOnTouchListener.class.getSimpleName();

  // If caller sets these, tracing info will be appended to them on each onTouch.
  public ArrayList<String> mAnnotationsOrNull = null;
  public ArrayList<ForbidRecord> mForbidRecordsOrNull = null;

  private View.OnTouchListener wrapped = null;
  public FixedOnTouchListener(View.OnTouchListener wrapped) {
    this.wrapped = wrapped;
  }


  // Note, MotionEvent has an actionToString(), but it takes an unmasked action;
  // we want to take an unmasked action.
  // CBB: this is sort of polluting the interface, but I want it in FixedOnTouchListener too
  public static String actionToString(int action) {
    if (action == ACTION_DOWN) return "DOWN";
    if (action == ACTION_POINTER_DOWN) return "POINTER_DOWN";
    if (action == ACTION_MOVE) return "MOVE";
    if (action == ACTION_POINTER_UP) return "POINTER_UP";
    if (action == ACTION_UP) return "UP";
    //if (action == ACTION_CANCEL) return "CANCEL";  // I've never seen this, I don't think
    //if (action == ACTION_OUTSIDE) return "OUTSIDE";  // I've never seen this, I don't think
    throw new AssertionError("unrecognized MotionEvent action "+action);
  }

  // The sequence of events that arms the fixer is two consecutive events:
  //    1. N(>=1) consecutive POINTER_DOWNs when there was previously exactly one other pointer already down
  //            (each of the newly-down id's anchor x,y is the x,y of the respective POINTER_DOWN event)
  //       immediately followed by:
  //    2. MOVE, with all N+1 pointers still down.
  //            (the originally-down id's anchor x,y is the primary (non-historical) x,y of this move event)
  //    From then on, it's symmetric: each of the N+1 participating ids
  //    keep bugging until it goes up, or until it is the only one left
  //    (in which case it may keep bugging on the other guy's POINTER_UP
  //    and/or a small handful of subsequent lone MOVEs).
  // While armed:
  //    - if any of the bugging ids' x,y appears to be its anchor, change it to its previous value
  // A pattern that disarms the fixer is either:
  //    - if a participating id experiences the same x,y twice in a row in a MOVE
  //      that is *not* the respective anchor position, then the bug isn't happening; disarm.
  //      EXCEPT don't do this if it's history-less MOVE, since that has been observed
  //      to give false alarms.
  // Note, we do *not* disarm the fixer on:
  //     - additional pointers down or up (that doesn't affect the bug)
  //     - when a bugging id goes POINTER_UP, that id stops participating in the bug,
  //       but the bug can still effect the other one(s), both on that POINTER_UP event itself
  //       and on subsequent events, generally for only about 1/10 of a second, but who knows.

  // Caller can query this to find out the current list of bugging ids.
  public int[] buggingIds() {
    return mFixerState.buggingIdsExternalSlow();  // I have mixed feelings about this.
  }

  private static class FixerState {
    private static int MAX_FINGERS = 10;  // CBB: might crash if this is exceeded
    private float[] mCurrentX = new float[MAX_FINGERS];  // indexed by id
    private float[] mCurrentY = new float[MAX_FINGERS];  // indexed by id
    private float[] mForbiddenX = new float[MAX_FINGERS]; // indexed by id
    private float[] mForbiddenY = new float[MAX_FINGERS]; // indexed by id
    private long[] fixCount = new long[MAX_FINGERS]; // indexed by id
    private int mWhoNeedsForbidden = -1;  // id that was down when a second pointer went down, whose forbidden position isn't yet known.
    { Arrays.fill(mCurrentX, Float.NaN); }
    { Arrays.fill(mCurrentY, Float.NaN); }
    { Arrays.fill(mForbiddenX, Float.NaN); }
    { Arrays.fill(mForbiddenY, Float.NaN); }

    // Notion of which ids are bugging for internal use.
    private int[] buggingIdsInternalSlow() {
      int n = 0;
      for (int id = 0; id < MAX_FINGERS; ++id) {
        if (!Float.isNaN(mForbiddenX[id])) {
          CHECK(!Float.isNaN(mForbiddenY[id]));
          n++;
        } else {
          CHECK(Float.isNaN(mForbiddenY[id]));
        }
      }
      final int[] answer = new int[n];
      int i = 0;
      for (int id = 0; id < MAX_FINGERS; ++id) {
        if (!Float.isNaN(mForbiddenX[id])) {
          answer[i++] = id;
        }
      }
      CHECK_EQ(i, n);
      return answer;
    }
    // Notion of which ids are bugging for external use.  Less alarmist.
    private int[] buggingIdsExternalSlow() {
      // While arming, which is speculative, return empty.
      if (mWhoNeedsForbidden != -1) return new int[] {};

      final int threshold = 1;

      int n = 0;
      for (int id = 0; id < MAX_FINGERS; ++id) {
        if (fixCount[id] >= threshold) {
          n++;
        }
      }
      final int[] answer = new int[n];
      int i = 0;
      for (int id = 0; id < MAX_FINGERS; ++id) {
        if (fixCount[id] >= threshold) {
          answer[i++] = id;
        }
      }
      CHECK_EQ(i, n);
      return answer;
    }
    private String fixCountsString() {
      final StringBuilder sb = new StringBuilder();
      sb.append("{");
      int n = 0;
      for (int id = 0; id < MAX_FINGERS; ++id) {
        if (fixCount[id] != 0) {
          if (n++ == 0) sb.append(", ");
          sb.append(id);
          sb.append(":");
          sb.append(fixCount[id]);
        }
      }
      sb.append("}");
      return sb.toString();
    }
  }  // class FixerState

  // CBB: maybe just use int[3] instead of this?
  public static class ForbidRecord {
    public int index;
    public int id;
    public int increment;  // +1 for forbidding, -1 for unforbidding, 0 for close call
    public ForbidRecord(int index, int id, int increment) {
      this.index = index;
      this.id = id;
      this.increment = increment;
    }
    public String toString() {
      return "[index:"+index+" id:"+id+" incr="+increment+"]";
    }
  }

  private FixerState mFixerState = new FixerState();
  private void correctPointerCoordsUsingState(
      MotionEvent unfixed,
      int historyIndex,
      long eventTime,
      MotionEvent.PointerCoords pointerCoords[],
      ArrayList<String> annotationsOrNull,  // if not null, we append exactly one item.
      ArrayList<ForbidRecord> forbidRecordsOrNull  // if not null, we append zero or more forbid records, whenever things get forbidden or unforbidden or close calls.
      ) {
    final int verboseLevel = 0;
    if (verboseLevel >= 1) Log.i(TAG, "        in correctPointerCoordsUsingState(historyIndex="+historyIndex+"/"+unfixed.getHistorySize()+", eventTime="+(eventTime-unfixed.getDownTime())/1000.+")");

    CHECK_EQ(annotationsOrNull!=null, forbidRecordsOrNull!=null);
    StringBuilder annotationOrNull = annotationsOrNull!=null ? new StringBuilder() : null; // CBB: maybe wasteful since most lines don't get annotated
    final int indexForForbidRecord = annotationsOrNull!=null ? annotationsOrNull.size() : -1;

    // If annotation ends up nonempty, we'll prepend before/after bugging ids to it.
    final int[] buggingIdsBefore = annotationsOrNull!=null ? mFixerState.buggingIdsInternalSlow() : null;

    final int action = unfixed.getActionMasked();
    final int actionIndex = unfixed.getActionIndex();
    final int actionId = unfixed.getPointerId(actionIndex);
    final int pointerCount = unfixed.getPointerCount();
    final int historySize = unfixed.getHistorySize();

    if (action == ACTION_DOWN) {
      // Make sure state is pristine, nothing is forbidden.
      mFixerState.mWhoNeedsForbidden = -1;
      mFixerState.mForbiddenX[actionId] = Float.NaN;
      mFixerState.mForbiddenY[actionId] = Float.NaN;
      mFixerState.fixCount[actionId] = 0L;
    } else if (action == ACTION_POINTER_DOWN) {
      if (pointerCount == 2 || mFixerState.mWhoNeedsForbidden != -1) {

        // CBB: I'm pretty sure the bug can only be initiated when the already-down id is > all the other participating ids.  Not sure exactly how to filter out non-cases of this, though, so not trying, yet.  Well ok, maybe try.
        boolean allowIt = pointerCount==2 ? actionIndex==0 : actionId < mFixerState.mWhoNeedsForbidden;

        if (allowIt) {
          if (annotationsOrNull != null) {
            annotationOrNull.append("(FORBIDDING id "+actionId+" to go to "+pointerCoords[actionIndex].x+","+pointerCoords[actionIndex].y+")");
            forbidRecordsOrNull.add(new ForbidRecord(/*index=*/indexForForbidRecord, /*id=*/actionId, /*increment=*/+1));
          }
          mFixerState.mForbiddenX[actionId] = pointerCoords[actionIndex].x;
          mFixerState.mForbiddenY[actionId] = pointerCoords[actionIndex].y;
          mFixerState.fixCount[actionId] = 0L;
          // In the case that there was exactly one previous pointer down,
          // that pointer's forbidden is not known until the end of the next MOVE packet.
          if (pointerCount == 2) {
            CHECK_EQ(mFixerState.mWhoNeedsForbidden, -1);
            mFixerState.mWhoNeedsForbidden = unfixed.getPointerId(1 - actionIndex);
          }
        } else {
          if (annotationsOrNull != null) {
            annotationOrNull.append("(NOT forbidding id "+actionId+" nor "+unfixed.getPointerId(1-actionIndex)+" because out of order)");
            forbidRecordsOrNull.add(new ForbidRecord(/*index=*/indexForForbidRecord, /*id=*/actionId, /*increment=*/0));
          }
          // CBB: abort arming sequence!  argh this sucks, am I now letting things through inconsistently?
        }
      }
    } else if (action == ACTION_MOVE || action == ACTION_POINTER_UP || action == ACTION_UP) {
      if (action == ACTION_POINTER_UP || action == ACTION_UP) {
        if (mFixerState.mWhoNeedsForbidden != -1) {
          // It wasn't arming after all.
          if (annotationOrNull != null) {
            annotationOrNull.append("(never mind, it wasn't arming after all. unforbidding everything.)");
          }
          mFixerState.mWhoNeedsForbidden = -1;
          // Unforbid everything.
          for (int id = 0; id < FixerState.MAX_FINGERS; ++id) {
            if (!Float.isNaN(mFixerState.mForbiddenX[id])) {
              mFixerState.mForbiddenX[id] = Float.NaN;
              mFixerState.mForbiddenY[id] = Float.NaN;
              mFixerState.fixCount[id] = 0L;
              if (annotationsOrNull != null) {
                annotationOrNull.append("(RELEASING id "+id+" along with everyone else");
                forbidRecordsOrNull.add(new ForbidRecord(/*index=*/indexForForbidRecord, /*id=*/id, /*increment=*/-1));
              }
            }
          }
        }
        // It might need correcting, so don't un-set things yet.
      }
      for (int index = 0; index < pointerCount; ++index) {
        final int id = unfixed.getPointerId(index);
        if (id == mFixerState.mWhoNeedsForbidden) {
          if (action == ACTION_MOVE
           && historyIndex == historySize) {
            if (annotationsOrNull != null) {
              annotationOrNull.append("(FORBIDDING (delayed) id "+mFixerState.mWhoNeedsForbidden+" to go to "+pointerCoords[index].x+","+pointerCoords[index].y+")");
              forbidRecordsOrNull.add(new ForbidRecord(/*index=*/indexForForbidRecord, /*id=*/id, /*increment=*/+1));
            }
            mFixerState.mForbiddenX[mFixerState.mWhoNeedsForbidden] = pointerCoords[index].x;
            mFixerState.mForbiddenY[mFixerState.mWhoNeedsForbidden] = pointerCoords[index].y;
            mFixerState.fixCount[mFixerState.mWhoNeedsForbidden] = 0L;
            mFixerState.mWhoNeedsForbidden = -1;
          }
        } else if (!Float.isNaN(mFixerState.mForbiddenX[id])) {
          // This pointer has a forbidden position (that wasn't just now established).
          if (pointerCoords[index].x == mFixerState.mForbiddenX[id]
           && pointerCoords[index].x == mFixerState.mForbiddenX[id]) {
            // This pointer moved to (or stayed still at) its forbidden position.
            // We think it meant to stay at its current position
            // (which may actually still be the forbidden position,
            // shortly after the forbidden position was established; that's fine).
            // Fix it.
            if (pointerCoords[index].x != mFixerState.mCurrentX[id]
             || pointerCoords[index].y != mFixerState.mCurrentY[id]) {
              pointerCoords[index].x = mFixerState.mCurrentX[id];
              pointerCoords[index].y = mFixerState.mCurrentY[id];
              mFixerState.fixCount[id]++;
            }
          } else {
            // This pointer is not at its forbidden x,y.
            // There is a reliable (I believe) indicator
            // that the bug is *not* currently affecting a given id
            // (i.e. it either never was, or is no longer):
            // that is, when it's a MOVE in which this id actually stayed stationary
            // at an x,y that is *not* that id's forbidden x,y,
            // and it's in a MOVE packet with history.
            // Q: should we also require it's the primary (i.e. last, i.e. non-history) sub-event in the MOVE packet?  I think that's the only case I've observed;  think about what's the safe course of acetion here.
            if (action == ACTION_MOVE
             && pointerCoords[index].x == mFixerState.mCurrentX[id]
             && pointerCoords[index].y == mFixerState.mCurrentY[id]) {
              if (historySize == 0) {
                // Woops! No history.
                // This may be a false alarm-- I have seen cases
                // where the bug *is* still happening in this situation.
                // At this point we're not sure whether it's bugging or not;
                // err on the side of assuming it's still bugging.
                // (when not really bugging, we'll get evidence of not bugging soon enough via
                // another instance of this criterion anyway).
                // (Well, except when there's only one remaining pointer down,
                // in which case we'll can be considered bugging indefinitely anyway; oh well!)
                if (annotationsOrNull != null) {
                  annotationOrNull.append("(NOT releasing id "+id+" even though it stayed stationary at a non-forbidden x,y, sice no history which sometimes means false alarm)");
                  forbidRecordsOrNull.add(new ForbidRecord(/*index=*/indexForForbidRecord, /*id=*/id, /*increment=*/0));
                }
              } else {
                if (annotationsOrNull != null) {
                  annotationOrNull.append("(RELEASING id "+id+" because it stayed stationary at a non-forbidden x,y; I think it wasn't bugging or is no longer)");
                  forbidRecordsOrNull.add(new ForbidRecord(/*index=*/indexForForbidRecord, /*id=*/id, /*increment=*/-1));
                }
                mFixerState.mForbiddenX[id] = Float.NaN;
                mFixerState.mForbiddenY[id] = Float.NaN;
                mFixerState.fixCount[id] = 0L;
              }
            }  // stationary at non-forbidden x,y
          }  // not at forbidden x,y
        }  // if this id already had a forbidden position
      }  // for index

      if (action == ACTION_POINTER_UP || action == ACTION_UP) {
        if (!Float.isNaN(mFixerState.mForbiddenX[actionId])) {
          if (annotationsOrNull != null) {
            annotationOrNull.append("(RELEASING id "+actionId+" on "+actionToString(action)+", after possibly fixing)");
            forbidRecordsOrNull.add(new ForbidRecord(/*index=*/indexForForbidRecord, /*id=*/actionId, /*increment=*/-1));
          }
          mFixerState.mForbiddenX[actionId] = Float.NaN;
          mFixerState.mForbiddenY[actionId] = Float.NaN;
          mFixerState.fixCount[actionId] = 0L;
        }
      }

      // end of case MOVE or POINTER_UP or UP
    } else {
      // XXX Unexpected action-- what is it?
    }

    // Update current state, for next time.
    for (int index = 0; index < pointerCount; ++index) {
      final int id = unfixed.getPointerId(index);
      mFixerState.mCurrentX[id] = pointerCoords[index].x;
      mFixerState.mCurrentY[id] = pointerCoords[index].y;
    }

    if (annotationsOrNull != null) {
      if (annotationOrNull.length() > 0) {
        final int[] buggingIdsAfter = mFixerState.buggingIdsInternalSlow();
        annotationsOrNull.add(STRINGIFY(buggingIdsBefore)+" " + annotationOrNull.toString() + " "+STRINGIFY(buggingIdsAfter));
      } else {
        annotationsOrNull.add(annotationOrNull.toString());
      }
    }

    if (verboseLevel >= 1) Log.i(TAG, "        out correctPointerCoordsUsingState(historyIndex="+historyIndex+"/"+unfixed.getHistorySize()+", eventTime="+(eventTime-unfixed.getDownTime())/1000.+")");
  }  // correctPointerCoordsUsingState

  // Framework calls this with the original unfixed MotionEvent;
  // we create a fixed MotionEvent and call the wrapped listener on it.
  @Override
  public boolean onTouch(View view, MotionEvent unfixedEvent) {
    final int verboseLevel = 0;
    if (verboseLevel >= 1) Log.i(TAG, "    in FixedOnTouchListener onTouch");

    boolean answer = false;
    {
      final int pointerCount = unfixedEvent.getPointerCount();
      final int historySize = unfixedEvent.getHistorySize();

      final MotionEvent.PointerProperties[] pointerPropertiesScratch = new MotionEvent.PointerProperties[pointerCount];  // CBB: reuse
      for (int index = 0; index < pointerCount; ++index) {
        pointerPropertiesScratch[index] = new MotionEvent.PointerProperties();
        unfixedEvent.getPointerProperties(index, pointerPropertiesScratch[index]);
      }

      final MotionEvent.PointerCoords[] pointerCoordsScratch = new MotionEvent.PointerCoords[pointerCount];  // CBB: reuse
      MotionEvent fixedEvent = null; 
      try {
        for (int h = 0; h < historySize+1; ++h) {
          for (int index = 0; index < pointerCount; ++index) {
            pointerCoordsScratch[index] = new MotionEvent.PointerCoords();
            if (h==historySize) {
              unfixedEvent.getPointerCoords(index, pointerCoordsScratch[index]);
            } else {
              unfixedEvent.getHistoricalPointerCoords(index, h, pointerCoordsScratch[index]);
            }
          }
          long subEventTime = h==historySize ? unfixedEvent.getEventTime() : unfixedEvent.getHistoricalEventTime(h);

          int previousSize = mAnnotationsOrNull!=null ? mAnnotationsOrNull.size() : -1;
          correctPointerCoordsUsingState(unfixedEvent, h, subEventTime, pointerCoordsScratch, mAnnotationsOrNull, mForbidRecordsOrNull);
          if (mAnnotationsOrNull != null) {
            // It must have appended exactly one annotation (possibly null)
              CHECK_EQ(mAnnotationsOrNull.size(), previousSize+1);
          }

          int historicalMetaState = unfixedEvent.getMetaState(); // huh? this can't be right, but I don't see any way to query metaState per-history
          if (h == 0) {
            fixedEvent = MotionEvent.obtain(
                unfixedEvent.getDownTime(),
                subEventTime,
                unfixedEvent.getAction(),
                pointerCount,
                pointerPropertiesScratch,
                pointerCoordsScratch,
                historicalMetaState,
                unfixedEvent.getButtonState(),
                unfixedEvent.getXPrecision(),
                unfixedEvent.getYPrecision(),
                unfixedEvent.getDeviceId(),
                unfixedEvent.getEdgeFlags(),
                unfixedEvent.getSource(),
                unfixedEvent.getFlags());
            // Make sure we got some of the tricky ones right...
            CHECK_EQ(fixedEvent.getAction(), unfixedEvent.getAction());
            CHECK_EQ(fixedEvent.getActionMasked(), unfixedEvent.getActionMasked());
            CHECK_EQ(fixedEvent.getActionIndex(), unfixedEvent.getActionIndex());
          } else {
            fixedEvent.addBatch(subEventTime,
                           pointerCoordsScratch,
                           historicalMetaState);
          }
        }
        answer = wrapped.onTouch(view, fixedEvent);
      } finally {
        if (fixedEvent != null) {
          fixedEvent.recycle();
        }
      }
    }
    if (verboseLevel >= 1) Log.i(TAG, "    out FixedOnTouchListener onTouch, returning "+answer);
    return answer;
  }  // onTouch
}  // class FixedOnTouchListener
