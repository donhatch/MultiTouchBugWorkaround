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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static com.example.donhatch.multitouchbugworkaround.CHECK.*;
import static com.example.donhatch.multitouchbugworkaround.STRINGIFY.STRINGIFY;


public class FixedOnTouchListener implements View.OnTouchListener {

  private static final String TAG = MultiTouchBugWorkaroundActivity.class.getSimpleName();  // XXX

  public View.OnTouchListener wrapped = null;
  public FixedOnTouchListener(View.OnTouchListener wrapped) {
    this.wrapped = wrapped;
  }

  private PrintWriter mTracePrintWriterOrNull = null;
  private ArrayList<String> mAnnotationsOrNull = null;
  private ArrayList<ForbidRecord> mForbidRecordsOrNull = null;
  private ArrayList<String> mFixedAnnotationsOrNull = null;
  private ArrayList<LogicalMotionEvent> mLogicalMotionEventsSinceFirstDown = null;
  private ArrayList<LogicalMotionEvent> mFixedLogicalMotionEventsSinceFirstDown = null;
  public void setTracePrintWriter(PrintWriter tracePrintWriter) {
    this.mTracePrintWriterOrNull = tracePrintWriter;
    this.mAnnotationsOrNull = tracePrintWriter!=null ? new ArrayList<String>() : null;
    this.mForbidRecordsOrNull = tracePrintWriter!=null ? new ArrayList<ForbidRecord>() : null;
    this.mLogicalMotionEventsSinceFirstDown = tracePrintWriter!=null ? new ArrayList<LogicalMotionEvent>() : null;
    this.mFixedLogicalMotionEventsSinceFirstDown = tracePrintWriter!=null ? new ArrayList<LogicalMotionEvent>() : null;
  }

  // Note, MotionEvent has an actionToString(), but it takes an unmasked action;
  // we want to take an unmasked action.
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

  final private static int MAX_FINGERS = 10;
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

  // STRINGIFY uses ", ", we want "," instead
  private static String STRINGIFY_COMPACT(int[] array) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < array.length; ++i) {
      if (i != 0) sb.append(",");
      sb.append(array[i]);
    }
    sb.append("}");
    return sb.toString();
  }

  // To aid analysis.
  private static class LogicalMotionEvent {
    public boolean isHistorical;
    public long eventTimeMillis;  // "in the uptimeMillis() time base"
    public int action;
    public int actionId;
    public int ids[];
    public MotionEvent.PointerCoords[] all_axis_values;

    // Convenience accessors for the commonly used ones
    public float x(int id) { return all_axis_values[id].getAxisValue(MotionEvent.AXIS_X); }
    public float y(int id) { return all_axis_values[id].getAxisValue(MotionEvent.AXIS_Y); }
    public float pressure(int id) { return all_axis_values[id].getAxisValue(MotionEvent.AXIS_PRESSURE); }
    public float size(int id) { return all_axis_values[id].getAxisValue(MotionEvent.AXIS_SIZE); }

    // all arrays assumed to be immutable.
    public LogicalMotionEvent(boolean isHistorical, long eventTimeMillis, int action, int actionId, int ids[], MotionEvent.PointerCoords[] all_axis_values) {
      this.isHistorical = isHistorical;
      this.eventTimeMillis = eventTimeMillis;
      this.action = action;
      this.actionId = actionId;
      this.ids = ids;
      this.all_axis_values = all_axis_values;
      for (int id : ids) {
        CHECK_LE(0, id);
        CHECK_LE(id, all_axis_values.length);
        CHECK(all_axis_values[id] != null);
      }
    }
    // Breaks motionEvent down into LogicalMotionEvents and appends them to list.
    public static void breakDown(MotionEvent motionEvent, ArrayList<LogicalMotionEvent> list) {
      final int action = motionEvent.getActionMasked();
      final int actionId = motionEvent.getPointerId(motionEvent.getActionIndex());
      final int pointerCount = motionEvent.getPointerCount();
      final int historySize = motionEvent.getHistorySize();
      final int[] ids = new int[pointerCount];
      int maxIdOccurring = -1;
      for (int index = 0; index < pointerCount; ++index) {
        ids[index] = motionEvent.getPointerId(index);
        maxIdOccurring = Math.max(maxIdOccurring, ids[index]);
      }
      for (int h = 0; h < historySize+1; ++h) {
        final long eventTimeMillis = h==historySize ? motionEvent.getEventTime() : motionEvent.getHistoricalEventTime(h);
        final float[] x = new float[maxIdOccurring+1];
        final float[] y = new float[maxIdOccurring+1];
        final float[] pressure = new float[maxIdOccurring+1];
        final float[] size = new float[maxIdOccurring+1];
        final MotionEvent.PointerCoords[] all_axis_values = new MotionEvent.PointerCoords[maxIdOccurring+1];
        for (int index = 0; index < pointerCount; ++index) {
          final int id = ids[index];
          CHECK(all_axis_values[id] == null);  // I guess this could fail if there are dup ids for some reason
          all_axis_values[id] = new MotionEvent.PointerCoords();
          if (h==historySize) {
            motionEvent.getPointerCoords(index, all_axis_values[id]);
          } else {
            motionEvent.getHistoricalPointerCoords(index, h, all_axis_values[id]);
          }
          // Empirically, AXIS_ORIENTATION is 0, +-pi/2, or pi, depending on current device orientation (of course)
          CHECK_EQ(all_axis_values[id].getAxisValue(MotionEvent.AXIS_RELATIVE_X), 0.f);  // XXX not sure if this is reliable on all devices, but it's what I always get
          CHECK_EQ(all_axis_values[id].getAxisValue(MotionEvent.AXIS_RELATIVE_Y), 0.f);  // XXX not sure if this is reliable on all devices, but it's what I always get
          // also, rawX()/rawY() doesn't seem to be helpful: (1) can't query it per-history nor per-pointer, (2) the bug apparently affects this too so it doesn't give any clues
        }
        list.add(new LogicalMotionEvent(h<historySize, eventTimeMillis, action, actionId, ids, all_axis_values));
      }
    }  // breakDown

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

    public static String dumpString(ArrayList<LogicalMotionEvent> list,
                                    String punctuationWhereDifferentFromOther,
                                    ArrayList<LogicalMotionEvent> other,
                                    ArrayList<String> annotationsOrNull,
                                    ArrayList<ForbidRecord> forbidRecordsOrNull) {
      final int verboseLevel = 0;  // 0: nothing, 1: in/out, 2: some gory details
      if (verboseLevel >= 1) Log.i(TAG, "in LogicalMotionEvent.dumpString("+list.size()+" logical events)");
      StringBuilder answer = new StringBuilder();
      //answer.append("[forbidRecords="+STRINGIFY(forbidRecordsOrNull)+"]\n");  // if I need to debug this

      if (annotationsOrNull != null) CHECK(forbidRecordsOrNull != null);
      int iNextForbidRecord = 0;
      ForbidRecord nextForbidRecord = (forbidRecordsOrNull!=null && !forbidRecordsOrNull.isEmpty()) ? forbidRecordsOrNull.get(0) : null;

      int n = list.size();

      int idsWidth = 0;
      int max_id_occurring = -1;
      for (int i = 0; i < n; ++i) {
        final int ids[] = list.get(i).ids;
        idsWidth = Math.max(idsWidth, STRINGIFY_COMPACT(ids).length());
        for (final int id: ids) {
          max_id_occurring = Math.max(max_id_occurring, id);
        }
      }

      // For each id,
      // whenever there are 2 or more distinct runs with the same coords
      // within a given down-period of that id,
      // make a label for those coords.
      final String[][] id2index2label = new String[max_id_occurring+1][n];  // nulls initially
      {
        for (int id = 0; id <= max_id_occurring; ++id) {
          if (verboseLevel >= 2) Log.i(TAG, "      processing id "+id);
          String[] index2label = new String[n];  // nulls initially

          String[] index2fingerprint = new String[n];  // nulls initially
          for (int i = 0; i < n; ++i) {
            LogicalMotionEvent e = list.get(i);
            if (id < e.all_axis_values.length && e.all_axis_values[id] != null) {
              String fingerprint = e.x(id)+","+e.y(id);
              index2fingerprint[i] = fingerprint;
            }
          }
          if (verboseLevel >= 2) Log.i(TAG, "          index2fingerprint = "+STRINGIFY(index2fingerprint));

          // Focusing on this id,
          // each sequence of pointer-down,moves,pointer-up
          // is its own label scope.

          for (int i0 = 0; i0 < n;) {
            // advance i0, if necessary, to something that has a fingerprint
            while (i0 < n && index2fingerprint[i0] == null) i0++;
            if (i0 == n) break;
            int i1 = i0+1;
            // advance i1 to something that does *not* have a fingerprint
            while (i1 < n && index2fingerprint[i1] != null) i1++;

            // [i0,i1) is now a range beginning with this pointer down
            // and ending with this pointer up.
            // That's a scope; assign labels within it.

            if (verboseLevel >= 2) Log.i(TAG, "              ["+i0+","+i1+")");

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
                boolean isRunStart = (i==i0 || index2fingerprint[i-1]==null || !fingerprint.equals(index2fingerprint[i-1]));
                if (isRunStart) {
                  fingerprint2nRunsHolder.get(fingerprint)[0]++;
                }
              }
            }
            if (verboseLevel >= 2) Log.i(TAG, "                  fingerprint2nRunsHolder = "+STRINGIFY(fingerprint2nRunsHolder));
            HashMap<String,String> fingerprint2label = new HashMap<String,String>();
            int nLabels = 0;
            for (int i = i0; i < i1; ++i) {
              String fingerprint = index2fingerprint[i];
              if (fingerprint != null) {
                boolean isRunStart = (i==i0 || index2fingerprint[i-1]!=null || !fingerprint.equals(index2fingerprint[i-1]));
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
          if (verboseLevel >= 2) Log.i(TAG, "          index2label = "+STRINGIFY(index2label));
          id2index2label[id] = index2label;
          if (verboseLevel >= 2) Log.i(TAG, "      done processing id "+id);
        }
      }

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
      if (annotationsOrNull != null) CHECK_EQ(annotationsOrNull.size(), n);
      for (int i = 0; i < n;  ++i) {
        LogicalMotionEvent e = list.get(i);
        String annotationLine = annotationsOrNull!=null ? annotationsOrNull.get(i) : null;

        boolean justNowGotCoordsOfInterestForId0 = false;
        boolean justNowGotCoordsOfInterestForIdNonzero = false;

        if (e.action == ACTION_POINTER_DOWN
         && e.actionId == 0
         && e.ids.length == 2) {
          CHECK_EQ(e.ids[0], 0);
          // Might be bugging.  Yellow alert.
          knownToBeSafe = false;
          knownToBeBugging = false;
          idOfInterest = e.ids[1];
          xOfInterestForId0 = e.x(0);
          yOfInterestForId0 = e.y(0);
          xOfInterestForIdNonzero = Float.NaN;
          yOfInterestForIdNonzero = Float.NaN;
          justNowGotCoordsOfInterestForId0 = true;
        }
        // If the next packet is not a POINTER_UP(id1),
        // then the next packet seems to always have size 2,
        // and the last (i.e. non-historical) entry in it is the buggy non-0-id item, if any
        if (i >= 2) {
          LogicalMotionEvent ePrevPrev = list.get(i-2);
          LogicalMotionEvent ePrev = list.get(i-1);
          if (ePrevPrev.action == ACTION_POINTER_DOWN
           && ePrevPrev.actionId == 0
           && ePrevPrev.ids.length == 2
           && ePrev.action == ACTION_MOVE) {
            CHECK_GE(idOfInterest, 0);
            CHECK_LT(idOfInterest, e.all_axis_values.length);
            xOfInterestForIdNonzero = e.x(idOfInterest);
            yOfInterestForIdNonzero = e.y(idOfInterest);
            justNowGotCoordsOfInterestForIdNonzero = true;
          }
        }

        StringBuilder lineBuilder = new StringBuilder();
        long relativeTimeMillis = e.eventTimeMillis - refTimeMillis;
        if (e.isHistorical) CHECK_EQ(e.action, ACTION_MOVE);
        if (e.action==ACTION_MOVE) CHECK_EQ(e.actionId, e.ids[0]);
        lineBuilder.append(String.format("          %3d.%03d %4d/%d: %16s %-"+idsWidth+"s",
                                         relativeTimeMillis/1000,
                                         relativeTimeMillis%1000,
                                         i, n,
                                         (e.action==ACTION_MOVE && e.isHistorical ? "" :
                                         (actionToString(e.action)+(e.action==ACTION_MOVE?"  ":"("+e.actionId+")"))),
                                         STRINGIFY_COMPACT(e.ids)));
        for (int id = 0; id < e.all_axis_values.length; ++id) {
          if (e.all_axis_values[id] == null) {
            lineBuilder.append(String.format("%29s", ""));
            //lineBuilder.append(String.format("%52s", "", ""));
          } else {
            String coordsString = String.format("%.9g,%.9g", e.x(id), e.y(id));
            //String coordsString = String.format("%.9g,%.9g,%.9g,%.9g", e.x(id), e.y(id), e.pressure(id), e.size(id));  // pressure&size turned out to be not indicators

            boolean parenthesized = false;
            if (i >= 1) {
              LogicalMotionEvent ePrev = list.get(i-1);
              if (id < ePrev.all_axis_values.length
               && ePrev.all_axis_values[id] != null
               && e.x(id) == ePrev.x(id)
               && e.y(id) == ePrev.y(id)) {
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
              String punc = "";
              if (other != null) {
                LogicalMotionEvent eOther = other.get(i);
                CHECK_EQ(eOther.all_axis_values.length, e.all_axis_values.length);
                if (e.x(id) != eOther.x(id)
                 || e.y(id) != eOther.y(id)) {
                  punc += punctuationWhereDifferentFromOther;
                }
              }

              if (nextForbidRecord != null && i == nextForbidRecord.index && id == nextForbidRecord.id) {
                final int increment = nextForbidRecord.increment;
                punc += increment<0 ? "-" : increment>0 ? "+" : ".";

                iNextForbidRecord++;
                nextForbidRecord = (iNextForbidRecord < forbidRecordsOrNull.size() ? forbidRecordsOrNull.get(iNextForbidRecord) : null);
              }

              coordsString += (punc.length()==0 ? " " : punc);
            }

            lineBuilder.append(coordsString);
          }
        }
        answer.append(lineBuilder);
        answer.append("\n");
        if (annotationLine != null && annotationLine.length() > 0) {
          answer.append(annotationLine);
          answer.append("\n");
        }
      }  // for i < n
      if (verboseLevel >= 1) Log.i(TAG, "out LogicalMotionEvent.dumpString("+list.size()+" logical events)");
      return answer.toString();
    }  // dump

    // Argh!  Calling Log.i on the multiline dump string causes it to be truncated *very* early (like, dozens instead of hundreds).
    // So don't do that; split it, instead.
    public static void LogMultiline(String tag, String s) {
      for (String line : s.split("\n")) {
        Log.i(tag, line);
      }
    }

    // Read back in a string produced by dumpString().
    public static ArrayList<LogicalMotionEvent> parseDump(String dumpString) throws java.text.ParseException {
      final int verboseLevel = 0;  // 0: nothing, 1: in/out, 2: gory details
      if (verboseLevel >= 1) Log.i(TAG, "            in parseDump");
      final ArrayList<LogicalMotionEvent> answer = new ArrayList<LogicalMotionEvent>();
      final String[] lines = dumpString.split("\n");
      // "0.189   28/217:                  {0}     0: 2241.22192,512.643982,1.27499998,0.246582031"
      final Pattern pattern = Pattern.compile(
          "^" +
          "\\s*" +
          "(?<logcatstuff>\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+)?" +  // 0
          "(?<timestamp>[\\d.]+)\\s+" +  // 1
          "(?<i>\\d+)/(?<n>\\d+):\\s+" +  // 2,3
          "((?<action>(\\S+))\\s+)?" +  // 4,5
          "(?<ids>\\{.*\\})" +  // 6
          "(?<perIdData>(?<lastPerIdData>\\s+(\\d+:[^:/]+))+)" +  // 7,8
          "\\s*(?<comment>//.*)?" +  // 9
          "$");
      final Pattern perIdDataPattern = Pattern.compile(
          // "0: 2288.70557,477.668274,1.28750002,0.246582031 AA"
          // "0:[2288.70557,477.668274,1.28750002,0.246582031]AA"
          "\\s*(?<id>\\d+):\\s*(?<stuff>[^ A-Z]+)(?<labelMaybe>\\s*[A-Z]+)?(?<punctuationMaybe>[!?])?(\\s*)"
      );
      // I wish: captured "+" and "*" would give arrays back.  That would simplify a lot.
      for (int iLine = 0; iLine < lines.length; ++iLine) {
        final String line = lines[iLine];
        if (verboseLevel >= 2) Log.i(TAG, "                  line "+iLine+"/"+lines.length+": "+STRINGIFY(line));
        final Matcher matcher = pattern.matcher(line);
        //if (verboseLevel >= 2) Log.i(TAG, "                      match = "+matcher);
        if (!matcher.matches()) {
          throw new java.text.ParseException("Unrecognized line "+STRINGIFY(line), 0);
        }
        CHECK_EQ(matcher.groupCount(), 12);
        if (verboseLevel >= 2) Log.i(TAG, "                      logcatstuff = "+STRINGIFY(matcher.group("logcatstuff")));
        if (verboseLevel >= 2) Log.i(TAG, "                      timestamp = "+STRINGIFY(matcher.group("timestamp")));
        if (verboseLevel >= 2) Log.i(TAG, "                      i = "+STRINGIFY(matcher.group("i")));
        if (verboseLevel >= 2) Log.i(TAG, "                      n = "+STRINGIFY(matcher.group("n")));
        if (verboseLevel >= 2) Log.i(TAG, "                      action = "+STRINGIFY(matcher.group("action")));
        if (verboseLevel >= 2) Log.i(TAG, "                      ids = "+STRINGIFY(matcher.group("ids")));
        if (verboseLevel >= 2) Log.i(TAG, "                      perIdData = "+STRINGIFY(matcher.group("perIdData")));
        if (verboseLevel >= 2) Log.i(TAG, "                      lastPerIdData = "+STRINGIFY(matcher.group("lastPerIdData")));
        if (verboseLevel >= 2) Log.i(TAG, "                      comment = "+STRINGIFY(matcher.group("comment")));


        ArrayList<Integer> idsArrayList = new ArrayList<Integer>();
        ArrayList<float[]> valuesArrayList = new ArrayList<float[]>();

        final String perIdData = matcher.group("perIdData");
        // perIdData = "0: 2332.19019,448.688416,1.30000007,0.248535156 A!    1:[976.660889,614.573181,1.77499998,0.309570313]"
        final Matcher perIdDataMatcher = perIdDataPattern.matcher(perIdData);
        int pos = 0;
        while (perIdDataMatcher.find()) {
          CHECK_EQ(perIdDataMatcher.start(), pos);  // i.e. didn't skip anything
          CHECK_EQ(perIdDataMatcher.groupCount(), 5);
          String perIdDatum = perIdDataMatcher.group();
          String id = perIdDataMatcher.group("id");
          String stuff = perIdDataMatcher.group("stuff");
          String labelMaybe = perIdDataMatcher.group("labelMaybe");
          String punctuationMaybe = perIdDataMatcher.group("punctuationMaybe");
          if ((stuff.startsWith("(") && stuff.endsWith(")"))
           || (stuff.startsWith("[") && stuff.endsWith("]"))) {
            stuff = stuff.substring(/*start=*/1, /*end=*/stuff.length()-1);
          }
          String[] stuffTokens = stuff.split(",");
          if (verboseLevel >= 2) Log.i(TAG, "                          perIdDatum = "+STRINGIFY(perIdDatum));
          if (verboseLevel >= 2) Log.i(TAG, "                              id = "+STRINGIFY(id));
          if (verboseLevel >= 2) Log.i(TAG, "                              stuff = "+STRINGIFY(stuff));
          if (verboseLevel >= 2) Log.i(TAG, "                                  stuffTokens="+STRINGIFY(stuffTokens));
          if (verboseLevel >= 2) Log.i(TAG, "                              labelMaybe = "+STRINGIFY(labelMaybe));
          if (verboseLevel >= 2) Log.i(TAG, "                              punctuationMaybe = "+STRINGIFY(punctuationMaybe));
          float[] values = new float[stuffTokens.length];
          for (int i = 0; i < stuffTokens.length; ++i) {
            values[i] = Float.parseFloat(stuffTokens[i]);
          }
          idsArrayList.add(Integer.parseInt(id));
          valuesArrayList.add(values);

          pos = perIdDataMatcher.end();
        }
        CHECK(perIdDataMatcher.hitEnd());

        final long eventTimeMillis = (long)Math.round(Double.parseDouble(matcher.group("timestamp")) * 1000.);
        final int[] ids = new int[idsArrayList.size()];
        int max_id_occurring = -1;
        for (int index = 0; index < ids.length; ++index) {
          ids[index] = idsArrayList.get(index);
          max_id_occurring = Math.max(max_id_occurring, ids[index]);
        }
        CHECK_LT(max_id_occurring, 100);  // make sure it's not ridiculous
        final MotionEvent.PointerCoords[] all_axis_values = new MotionEvent.PointerCoords[max_id_occurring+1];
        for (int index = 0; index < ids.length; ++index) {
          final int id = ids[index];
          all_axis_values[id] = new MotionEvent.PointerCoords();
          final float[] values = valuesArrayList.get(index);
          CHECK_EQ(values.length, 4);
          all_axis_values[id].setAxisValue(MotionEvent.AXIS_X, values[0]);
          all_axis_values[id].setAxisValue(MotionEvent.AXIS_Y, values[1]);
          all_axis_values[id].setAxisValue(MotionEvent.AXIS_PRESSURE, values[2]);
          all_axis_values[id].setAxisValue(MotionEvent.AXIS_SIZE, values[3]);
        }
        CHECK_GE(ids.length, 1);

        if (verboseLevel >= 2) Log.i(TAG, "                              ids = "+STRINGIFY_COMPACT(ids));
        if (true) {
          // Check that ids matches which columns were populated
          final String idsString = matcher.group("ids");
          CHECK(idsString.startsWith("{"));
          CHECK(idsString.endsWith("}"));
          final String[] idTokens = idsString.substring(/*start=*/1, /*end=*/idsString.length()-1).split(",\\s*");
          final int shouldBeIds[] = new int[idTokens.length];
          for (int i = 0; i < idTokens.length; ++i) {
            shouldBeIds[i] = Integer.parseInt(idTokens[i]);
          }
          if (verboseLevel >= 2) Log.i(TAG, "                              shouldBeIds = "+STRINGIFY_COMPACT(shouldBeIds));
          if (!STRINGIFY_COMPACT(ids).equals(STRINGIFY_COMPACT(shouldBeIds))) {
            throw new AssertionError("claimed ids "+STRINGIFY_COMPACT(shouldBeIds)+", actually occurring ids "+STRINGIFY_COMPACT(ids));

          }
        }

        boolean isHistorical;
        int action, actionId;
        {
          String actionString = matcher.group("action");
          if (actionString == null) {
            isHistorical = true;
            action = ACTION_MOVE;
            actionId = ids[0];
          } else if (actionString.equals("MOVE")) {
            isHistorical = false;
            action = ACTION_MOVE;
            actionId = ids[0];
          } else {
            isHistorical = false;
            CHECK(actionString.endsWith(")"));
            CHECK(actionString.charAt(actionString.length()-3) == '(');  // CBB: assumes max id <= 9
      if (verboseLevel >= 1) Log.i(TAG, "            WTF "+(actionString.length()-2));
            actionId = Integer.parseInt(actionString.substring(/*start=*/actionString.length()-2, /*end=*/actionString.length()-1));
            String actionName = actionString.substring(/*start=*/0, /*end=*/actionString.length()-3);
            if (actionName.equals("DOWN")) action = ACTION_DOWN;
            else if (actionName.equals("POINTER_DOWN")) action = ACTION_POINTER_DOWN;
            else if (actionName.equals("POINTER_UP")) action = ACTION_POINTER_UP;
            else if (actionName.equals("UP")) action = ACTION_UP;
            else throw new AssertionError("unrecognized action "+STRINGIFY(actionName));
          }
        }
        answer.add(new LogicalMotionEvent(isHistorical, eventTimeMillis, action, actionId, ids, all_axis_values));
      }
      if (verboseLevel >= 1) Log.i(TAG, "            out parseDump, returning "+answer.size()+" logical events");
      return answer;
    }  // parseDump

    // Tests a case that I originally had to debug: missing labels.
    public static void testParseDump() {
      final int verboseLevel = 1;  // reasonable to be 1 for real
      if (verboseLevel >= 1) Log.i(TAG, "        in LogicalMotionEvent.testParseDump");
      final String[] dumpStrings = {
          // This was a bug (missing labels), is no longer
          String.join("\n",
              "0.189   28/217:                  {0}     0: 2241.22192,512.643982,1.27499998,0.246582031",
              "0.194   29/217:           MOVE   {0}     0: 2250.21875,505.648834,1.27499998,0.246582031",
              "0.198   30/217:           MOVE   {0}     0: 2260.21533,498.653717,1.27499998,0.246582031",
              "0.206   31/217:  POINTER_DOWN(1) {0, 1}  0:[2260.21533,498.653717,1.27499998,0.246582031]    1: 976.660889,614.573181,1.87500000,0.322265625    // <- should be A",
              "0.206   32/217:                  {0, 1}  0: 2279.20874,484.663422,1.28750002,0.246582031     1:[976.660889,614.573181,1.87500000,0.322265625]    // <- should be A",
              "0.210   33/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.28750002,0.246582031 A   1:[976.660889,614.573181,1.87500000,0.322265625]    // <- should be A",
              "0.214   34/217:                  {0, 1}  0:[2288.70557,477.668274,1.28750002,0.246582031]A   1:(976.660889,614.573181,1.85000002,0.317382813)    // <- should be A",
              "0.214   35/217:           MOVE   {0, 1}  0: 2297.20239,471.672455,1.28750002,0.247558594     1:[976.660889,614.573181,1.85000002,0.317382813]    // <- should be A",
              "0.223   36/217:                  {0, 1}  0: 2288.70557,477.668274,1.28750002,0.247558594 A   1:(976.660889,614.573181,1.81250000,0.311523438)    // <- should be A",
              "0.223   37/217:           MOVE   {0, 1}  0: 2315.19629,459.680756,1.30000007,0.247558594     1:[976.660889,614.573181,1.81250000,0.311523438]    // <- should be A",
              "0.231   38/217:                  {0, 1}  0: 2288.70557,477.668274,1.30000007,0.247558594 A   1:(976.660889,614.573181,1.77499998,0.309570313)    // <- should be A",
              "0.231   39/217:           MOVE   {0, 1}  0: 2332.19019,448.688416,1.30000007,0.248535156     1:[976.660889,614.573181,1.77499998,0.309570313]    // <- should be A",
              "0.240   40/217:                  {0, 1}  0: 2288.70557,477.668274,1.30000007,0.248535156 A   1:(976.660889,614.573181,1.72500002,0.303710938)    // <- should be A",
              "0.240   41/217:                  {0, 1}  0: 2349.18433,438.695343,1.31250000,0.248535156     1:[976.660889,614.573181,1.72500002,0.303710938]    // <- should be A",
              "0.248   42/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.31250000,0.248535156 A   1: 938.674072,624.566284,1.68750000,0.301757813",
              "0.248   43/217:           MOVE   {0, 1}  0: 2365.17871,429.701599,1.32500005,0.247558594     1: 976.660889,614.573181,1.68750000,0.301757813     // <- should be A",
              "0.257   44/217:                  {0, 1}  0: 2288.70557,477.668274,1.32500005,0.247558594 A   1: 877.695251,645.551697,1.64999998,0.295898438",
              "0.257   45/217:           MOVE   {0, 1}  0: 2381.17334,421.707153,1.32500005,0.249511719     1: 976.660889,614.573181,1.64999998,0.295898438    // <- should be A",
              "0.265   46/217:                  {0, 1}  0: 2288.70557,477.668274,1.32500005,0.249511719 A   1: 829.711914,665.537842,1.61250007,0.293457031",
              "0.265   47/217:           MOVE   {0, 1}  0: 2396.16797,413.712708,1.33749998,0.249511719     1: 976.660889,614.573181,1.61250007,0.293457031    // <- should be A",
              "0.274   48/217:                  {0, 1}  0: 2288.70557,477.668274,1.33749998,0.249511719 A   1: 785.727173,689.521179,1.56250000,0.288574219",
              "0.274   49/217:                  {0, 1}  0: 2410.16309,406.717560,1.33749998,0.248046875     1: 976.660889,614.573181,1.56250000,0.288574219    // <- should be A",
              "0.282   50/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.33749998,0.248046875 A   1: 752.738647,713.504517,1.52499998,0.286132813",
              "0.282   51/217:           MOVE   {0, 1}  0: 2424.15845,400.721710,1.35000002,0.248046875     1: 976.660889,614.573181,1.52499998,0.286132813    // <- should be A",
              "0.291   52/217:                  {0, 1}  0: 2288.70557,477.668274,1.35000002,0.248046875 A   1: 733.745239,737.487854,1.48750007,0.281250000",
              "0.291   53/217:           MOVE   {0, 1}  0: 2437.15381,394.725891,1.35000002,0.249023438     1: 976.660889,614.573181,1.48750007,0.281250000    // <- should be A",
              "0.299   54/217:                  {0, 1}  0: 2288.70557,477.668274,1.35000002,0.249023438 A   1: 723.748718,761.471191,1.45000005,0.279296875",
              "0.299   55/217:           MOVE   {0, 1}  0: 2449.14966,389.729340,1.36250007,0.249023438     1: 976.660889,614.573181,1.45000005,0.279296875    // <- should be A",
              "0.308   56/217:                  {0, 1}  0: 2288.70557,477.668274,1.36250007,0.249023438 A   1: 718.750427,786.453857,1.41250002,0.274902344",
              "0.308   57/217:           MOVE   {0, 1}  0: 2460.14575,384.732819,1.36250007,0.249023438     1: 976.660889,614.573181,1.41250002,0.274902344    // <- should be A",
              "0.316   58/217:                  {0, 1}  0: 2288.70557,477.668274,1.36250007,0.249023438 A   1: 709.753601,809.437866,1.35000002,0.270019531",
              "0.316   59/217:           MOVE   {0, 1}  0: 2468.14307,379.736298,1.37500000,0.250000000     1: 976.660889,614.573181,1.35000002,0.270019531    // <- should be A",
              "0.323   60/217:           MOVE   {0, 1}  0: 2476.14038,375.739075,1.37500000,0.251953125     1:[976.660889,614.573181,1.35000002,0.270019531]    // <- should be A",
              "0.332   61/217:    POINTER_UP(1) {0, 1}  0:[2476.14038,375.739075,1.37500000,0.251953125]    1:[976.660889,614.573181,1.35000002,0.270019531]    // <- should be A",
              "0.332   62/217:                  {0}     0: 2484.13745,371.741852,1.38750005,0.250976563",
              "0.339   63/217:           MOVE   {0}     0: 2489.33521,369.143951,1.38750005,0.250976563",
              "0.339   63/217:           MOVE   {0, 1}  0: 2489.33521,369.143951,1.38750005,0.250976563 FAKELABEL? 1:[976.660889,614.573181,1.35000002,0.270019531] FAKE   // fake fake ",
            ""),
        // This was a bug (column 0 should have been empty)
          String.join("\n",
              "04-27 07:07:33.090  9196  9196 I MultiTouchBugWorkaroundActivity:             0.873  238/262:                  {0, 1, 2}  0: 1004.65118,1006.30115,1.00000000,0.211914063     1:[2156.25146,716.502441,0.750000000,0.208007813]    2:[2127.26147,1182.17908,0.837500036,0.196777344]",
              "04-27 07:07:33.091  9196  9196 I MultiTouchBugWorkaroundActivity:             0.881  239/262:           MOVE   {0, 1, 2}  0:[1004.65118,1006.30115,1.00000000,0.211914063]    1:[2156.25146,716.502441,0.750000000,0.208007813]    2: 2128.26099,1181.17969,0.850000024,0.197753906",
              "04-27 07:07:33.091  9196  9196 I MultiTouchBugWorkaroundActivity:             0.881  240/262:    POINTER_UP(0) {0, 1, 2}  0:[1004.65118,1006.30115,1.00000000,0.211914063]    1:[2156.25146,716.502441,0.750000000,0.208007813]    2:[2128.26099,1181.17969,0.850000024,0.197753906]",
              "04-27 07:07:33.091  9196  9196 I MultiTouchBugWorkaroundActivity:             0.889  241/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.208007813]    2:[2128.26099,1181.17969,0.850000024,0.197753906]",
              "04-27 07:07:33.091  9196  9196 I MultiTouchBugWorkaroundActivity:             0.897  242/262:                  {1, 2}                                                        1:(2156.25146,716.502441,0.750000000,0.205078125)    2:[2128.26099,1181.17969,0.850000024,0.197753906]",
              "04-27 07:07:33.092  9196  9196 I MultiTouchBugWorkaroundActivity:             0.897  243/262:           MOVE   {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.205078125]    2: 2129.26074,1179.18115,0.850000024,0.198730469",
              "04-27 07:07:33.092  9196  9196 I MultiTouchBugWorkaroundActivity:             0.904  244/262:                  {1, 2}                                                        1:(2156.25146,716.502441,0.750000000,0.204589844)    2:[2129.26074,1179.18115,0.850000024,0.198730469]",
              "04-27 07:07:33.092  9196  9196 I MultiTouchBugWorkaroundActivity:             0.904  245/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.204589844]    2: 2130.26050,1178.18176,0.837500036,0.201171875",
              "04-27 07:07:33.092  9196  9196 I MultiTouchBugWorkaroundActivity:             0.911  246/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.204589844]    2:[2130.26050,1178.18176,0.837500036,0.201171875]",
              "04-27 07:07:33.093  9196  9196 I MultiTouchBugWorkaroundActivity:             0.911  247/262:           MOVE   {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.204589844]    2: 2132.25977,1176.18323,0.837500036,0.202148438",
              "04-27 07:07:33.093  9196  9196 I MultiTouchBugWorkaroundActivity:             0.919  248/262:           MOVE   {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.204589844]    2: 2134.25903,1174.18457,0.837500036,0.203125000",
              "04-27 07:07:33.093  9196  9196 I MultiTouchBugWorkaroundActivity:             0.928  249/262:                  {1, 2}                                                        1:(2156.25146,716.502441,0.750000000,0.203613281)    2:[2134.25903,1174.18457,0.837500036,0.203125000]",
              "04-27 07:07:33.094  9196  9196 I MultiTouchBugWorkaroundActivity:             0.928  250/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.203613281]    2: 2136.25830,1173.18530,0.837500036,0.206054688",
              "04-27 07:07:33.094  9196  9196 I MultiTouchBugWorkaroundActivity:             0.936  251/262:                  {1, 2}                                                        1:(2156.25146,716.502441,0.737500012,0.204589844)    2:[2136.25830,1173.18530,0.837500036,0.206054688]",
              "04-27 07:07:33.094  9196  9196 I MultiTouchBugWorkaroundActivity:             0.936  252/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.737500012,0.204589844]    2: 2137.25806,1173.18530,0.837500036,0.206054688",
              "04-27 07:07:33.094  9196  9196 I MultiTouchBugWorkaroundActivity:             0.945  253/262:           MOVE   {1, 2}                                                        1:[2156.25146,716.502441,0.737500012,0.204589844]    2: 2138.25757,1174.18457,0.824999988,0.207031250",
              "04-27 07:07:33.094  9196  9196 I MultiTouchBugWorkaroundActivity:             0.953  254/262:                  {1, 2}                                                        1:(2156.25146,716.502441,0.737500012,0.201660156)    2:[2138.25757,1174.18457,0.824999988,0.207031250]",
              "04-27 07:07:33.095  9196  9196 I MultiTouchBugWorkaroundActivity:             0.953  255/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.737500012,0.201660156]    2: 2138.25757,1175.18384,0.824999988,0.209472656",
              "04-27 07:07:33.095  9196  9196 I MultiTouchBugWorkaroundActivity:             0.962  256/262:                  {1, 2}                                                        1: 2161.24951,718.501038,0.725000024,0.200683594     2:[2138.25757,1175.18384,0.824999988,0.209472656]",
              "04-27 07:07:33.095  9196  9196 I MultiTouchBugWorkaroundActivity:             0.962  257/262:           MOVE   {1, 2}                                                        1:[2161.24951,718.501038,0.725000024,0.200683594]    2:(2138.25757,1175.18384,0.824999988,0.210449219)",
              "04-27 07:07:33.096  9196  9196 I MultiTouchBugWorkaroundActivity:             0.970  258/262:                  {1, 2}                                                        1: 2162.24927,721.498962,0.712500036,0.199218750     2:[2138.25757,1175.18384,0.824999988,0.210449219]",
              "04-27 07:07:33.096  9196  9196 I MultiTouchBugWorkaroundActivity:             0.970  259/262:           MOVE   {1, 2}                                                        1:[2162.24927,721.498962,0.712500036,0.199218750]    2: 2139.25732,1176.18323,0.812500000,0.214355469",
              "04-27 07:07:33.096  9196  9196 I MultiTouchBugWorkaroundActivity:             0.977  260/262:    POINTER_UP(1) {1, 2}                                                        1:[2162.24927,721.498962,0.712500036,0.199218750]    2:[2139.25732,1176.18323,0.812500000,0.214355469]",
              "04-27 07:07:33.096  9196  9196 I MultiTouchBugWorkaroundActivity:             0.977  261/262:            UP(2) {2}                                                                                                              2:[2139.25732,1176.18323,0.812500000,0.214355469]",
          ""),
      };  // dumpStrings

      for (final String dumpStringIn : dumpStrings) {
        ArrayList<LogicalMotionEvent> parsed = null;
        try {
          parsed = parseDump(dumpStringIn);
        } catch (java.text.ParseException e) {
          throw new AssertionError(e);
        }
        if (parsed != null) {
          String dumpStringOut = dumpString(parsed, /*punctuationWhereDifferentFromOther=*/null, /*other=*/null, /*annotationsOrNull=*/null, /*forbidRecordsOrNull=*/null);

          Log.i(TAG, "          parseDump succeeded!");
          Log.i(TAG, "          Here's it back out:");
          Log.i(TAG, "          ============================");
          LogMultiline(TAG, dumpStringOut);
          Log.i(TAG, "          ============================");

          // XXX I'd like to print to mTracePrintWriter, but that's not static
        }
      }

      if (verboseLevel >= 1) Log.i(TAG, "        out LogicalMotionEvent.testParseDump");
    }  // testParseDump
  }  // class LogicalMotionEvent

  public static void unitTest() {
    int verboseLevel = 1;  // reasonable to be 1 for real
    if (verboseLevel >= 1) Log.i(TAG, "    in FixedOnTouchListener.unitTest");
    LogicalMotionEvent.testParseDump();
    if (verboseLevel >= 1) Log.i(TAG, "    out FixedOnTouchListener.unitTest");
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

  private static class ForbidRecord {
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
    final int indexForForbidRecord = annotationsOrNull.size();

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
          annotationOrNull.append("(NOT forbidding id "+actionId+" nor "+unfixed.getPointerId(1-actionIndex)+" because out of order)");
          forbidRecordsOrNull.add(new ForbidRecord(/*index=*/indexForForbidRecord, /*id=*/actionId, /*increment=*/0));
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
          for (int id = 0; id < MAX_FINGERS; ++id) {
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
        annotationsOrNull.add(STRINGIFY_COMPACT(buggingIdsBefore)+" " + annotationOrNull.toString() + " "+STRINGIFY_COMPACT(buggingIdsAfter));
      } else {
        annotationsOrNull.add(annotationOrNull.toString());
      }
    }

    if (verboseLevel >= 1) Log.i(TAG, "        out correctPointerCoordsUsingState(historyIndex="+historyIndex+"/"+unfixed.getHistorySize()+", eventTime="+(eventTime-unfixed.getDownTime())/1000.+")");
  }  // correctPointerCoordsUsingState

  // Framework calls this with the original unfixed MotionEvent;
  // we create a fixed MotionEvent and call the wrapped listener on it.
  @Override
  public boolean onTouch(View view, MotionEvent unfixed) {
    final int verboseLevel = 1;  // 0: nothing, 1: dump entire sequence on final UP, 2: and in/out
    if (verboseLevel >= 2) Log.i(TAG, "    in FixedOnTouchListener onTouch");

    try {

      LogicalMotionEvent.breakDown(unfixed, mLogicalMotionEventsSinceFirstDown);  // for post-mortem analysis

      boolean answer = false;
      {
        final int pointerCount = unfixed.getPointerCount();
        final int historySize = unfixed.getHistorySize();

        final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[pointerCount];  // CBB: reuse
        for (int index = 0; index < pointerCount; ++index) {
          pointerProperties[index] = new MotionEvent.PointerProperties();
          unfixed.getPointerProperties(index, pointerProperties[index]);
        }

        final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[pointerCount];  // CBB: reuse
        MotionEvent fixed = null;
        try {
          for (int h = 0; h < historySize+1; ++h) {
            for (int index = 0; index < pointerCount; ++index) {
              pointerCoords[index] = new MotionEvent.PointerCoords();
              if (h==historySize) {
                unfixed.getPointerCoords(index, pointerCoords[index]);
              } else {
                unfixed.getHistoricalPointerCoords(index, h, pointerCoords[index]);
              }
            }
            long subEventTime = h==historySize ? unfixed.getEventTime() : unfixed.getHistoricalEventTime(h);
            if (mAnnotationsOrNull != null) {
              CHECK_EQ(mAnnotationsOrNull.size(), mFixedLogicalMotionEventsSinceFirstDown.size() + h);
            }

            correctPointerCoordsUsingState(unfixed, h, subEventTime, pointerCoords, mAnnotationsOrNull, mForbidRecordsOrNull);

            if (mAnnotationsOrNull != null) {
              // It must have appended exactly one annotation (possibly null)
              CHECK_EQ(mAnnotationsOrNull.size(), mFixedLogicalMotionEventsSinceFirstDown.size() + h + 1);
            }
            int historicalMetaState = unfixed.getMetaState(); // huh? this can't be right, but I don't see any way to query metaState per-history
            if (h == 0) {
              fixed = MotionEvent.obtain(
                  unfixed.getDownTime(),
                  subEventTime,
                  unfixed.getAction(),
                  pointerCount,
                  pointerProperties,
                  pointerCoords,
                  historicalMetaState,
                  unfixed.getButtonState(),
                  unfixed.getXPrecision(),
                  unfixed.getYPrecision(),
                  unfixed.getDeviceId(),
                  unfixed.getEdgeFlags(),
                  unfixed.getSource(),
                  unfixed.getFlags());
              // Make sure we got some of the tricky ones right...
              CHECK_EQ(fixed.getAction(), unfixed.getAction());
              CHECK_EQ(fixed.getActionMasked(), unfixed.getActionMasked());
              CHECK_EQ(fixed.getActionIndex(), unfixed.getActionIndex());
            } else {
              fixed.addBatch(subEventTime,
                             pointerCoords,
                             historicalMetaState);
            }
          }
          LogicalMotionEvent.breakDown(fixed, mFixedLogicalMotionEventsSinceFirstDown);  // for post-mortem analysis
          answer = wrapped.onTouch(view, fixed);
        } finally {
          if (fixed != null) {
            fixed.recycle();
          }
        }
      }

      if (unfixed.getActionMasked() == ACTION_UP) {

        if (mTracePrintWriterOrNull != null) {
          long t0 = System.nanoTime();
          String beforeString = LogicalMotionEvent.dumpString(
            mLogicalMotionEventsSinceFirstDown,
            /*punctuationWhereDifferentFromOther=*/"?",
            /*other=*/mFixedLogicalMotionEventsSinceFirstDown,
            /*annotationsOrNull=*/null,
            /*forbidRecords=*/mForbidRecordsOrNull);
          String duringString = LogicalMotionEvent.dumpString(
            mLogicalMotionEventsSinceFirstDown,
            /*punctuationWhereDifferentFromOther=*/"?",
            /*other=*/mFixedLogicalMotionEventsSinceFirstDown,
            /*annotationsOrNull=*/mAnnotationsOrNull,
            /*forbidRecords=*/mForbidRecordsOrNull);
          String afterString = LogicalMotionEvent.dumpString(
            mFixedLogicalMotionEventsSinceFirstDown,
            /*punctuationWhereDifferentFromOther=*/"!",
            /*other=*/mLogicalMotionEventsSinceFirstDown,
            /*annotationsOrNull=*/null,
            /*forbidRecords=*/mForbidRecordsOrNull);
          long t1 = System.nanoTime();

          if (mTracePrintWriterOrNull != null) {
            long t0p = System.nanoTime();
            mTracePrintWriterOrNull.println("      ===============================================================");
            mTracePrintWriterOrNull.println("      LOGICAL MOTION EVENT SEQUENCE, BEFORE FIX:");
            mTracePrintWriterOrNull.print(beforeString);  // it ends with newline
            mTracePrintWriterOrNull.println("      ---------------------------------------------------------------");
            mTracePrintWriterOrNull.println("      LOGICAL MOTION EVENT SEQUENCE, DURING FIX:");
            mTracePrintWriterOrNull.print(duringString);  // it ends with newline
            mTracePrintWriterOrNull.println("      ---------------------------------------------------------------");
            mTracePrintWriterOrNull.println("      LOGICAL MOTION EVENT SEQUENCE, AFTER FIX:");
            mTracePrintWriterOrNull.print(afterString);  // it ends with newline
            mTracePrintWriterOrNull.println("      ===============================================================");
            long t1p = System.nanoTime();
            long t0f = System.nanoTime();
            mTracePrintWriterOrNull.flush();
            long t1f = System.nanoTime();
            mTracePrintWriterOrNull.println("      That took: dump:"+(t1-t0)/1e9+" print:"+(t1p-t0p)/1e9+" flush:"+(t1f-t0f)/1e9+" secs.");
            mTracePrintWriterOrNull.flush();
          }
          if (mAnnotationsOrNull != null) {
            mAnnotationsOrNull.clear();
            mForbidRecordsOrNull.clear();
          }
        }

        mLogicalMotionEventsSinceFirstDown.clear();
        mFixedLogicalMotionEventsSinceFirstDown.clear();

      }
      if (verboseLevel >= 2) Log.i(TAG, "    out FixedOnTouchListener onTouch, returning "+answer);
      return answer;
    } catch (Throwable throwable) {
      if (mTracePrintWriterOrNull != null) {
        // Highest priority is to re-throw,
        // so don't allow additional throws inside printStackTrace to thwart us.
        try {
          mTracePrintWriterOrNull.println("===========================================");
          mTracePrintWriterOrNull.println("Oh no!  FixedOnTouchListener.onTouch caught this:");
          throwable.printStackTrace(mTracePrintWriterOrNull);
          mTracePrintWriterOrNull.println("===========================================");
          mTracePrintWriterOrNull.flush();
        } catch (Throwable throwable2) {
          Log.i(TAG, "FixedOnTouchListener.onTouch: what the hell? caught while trying to print stack trace to trace print writer: throwable2");
        }
      }
      Log.i(TAG, "FixedOnTouchListener.onTouch caught exception "+throwable+", re-throwing");
      throw throwable;
    }
  }  // onTouch
}
