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
  private ArrayList<String> mFixedAnnotationsOrNull = null;
  private ArrayList<LogicalMotionEvent> mLogicalMotionEventsSinceFirstDown = null;
  private ArrayList<LogicalMotionEvent> mFixedLogicalMotionEventsSinceFirstDown = null;
  public void setTracePrintWriter(PrintWriter tracePrintWriter) {
    this.mTracePrintWriterOrNull = tracePrintWriter;
    this.mAnnotationsOrNull = tracePrintWriter!=null ? new ArrayList<String>() : null;
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
                                    boolean showAnnotations) {  // if false, use annotationsOrNull only for figuring out "+"/"-" marks
      final int verboseLevel = 0;  // 0: nothing, 1: in/out, 2: some gory details
      if (verboseLevel >= 1) Log.i(TAG, "in LogicalMotionEvent.dumpString("+list.size()+" logical events)");
      StringBuilder answer = new StringBuilder();

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

              // Sad super-expensive way of locating the anchors.
              // Note that we only get this information if we have annotations,
              // whereas it would be nice to get it even in the other two cases.
              if (annotationLine != null) {
                if (false) {  // old way
                  // Extract <id> from "<id> just went down" or "ARMED: theAlreadyDownOne=<id>" if any,
                  // in which case an anchor was established here.
                  // CBB: this is a twisted way of doing this logic.  is there a cleaner way?
                  Matcher matcher = mStaticJustWentDownPattern.matcher(annotationLine);
                  if (!matcher.find()) {
                    matcher = mStaticAlreadyDownPattern.matcher(annotationLine);
                    if (!matcher.find()) {
                      matcher = null;
                    }
                  }
                  if (matcher != null) {
                    int idThatJustWentDown = Integer.parseInt(matcher.group(1));
                    if (idThatJustWentDown == id) {
                      punc += "@";  // this is the anchor
                    }
                  }
                } else {
                  {
                    Matcher matcher = mStaticForbiddenEstablishedPattern.matcher(annotationLine);
                    if (matcher.find()) {
                      int idThatJustWentDown = Integer.parseInt(matcher.group("id"));
                      if (idThatJustWentDown == id) {
                        punc += "+";  // "this is now the forbidden x,y for this id"
                      }
                    }
                  }
                  {
                    Matcher matcher = mStaticForbiddenReleasedPattern.matcher(annotationLine);
                    while (matcher.find()) {
                      int idThatJustWentDown = Integer.parseInt(matcher.group("id"));
                      if (idThatJustWentDown == id) {
                        punc += "-";  // "releasing the forbidden x,y for this id"
                      }
                    }
                  }
                  {
                    Matcher matcher = mStaticForbiddenAlmostReleasedPattern.matcher(annotationLine);
                    while (matcher.find()) {
                      int idThatJustWentDown = Integer.parseInt(matcher.group("id"));
                      if (idThatJustWentDown == id) {
                        punc += ".";  // "almost releasing the forbidden x,y for this id"
                      }
                    }
                  }
                }
              }

              coordsString += (punc.length()==0 ? " " : punc);
            }

            lineBuilder.append(coordsString);
          }
        }
        answer.append(lineBuilder);
        answer.append("\n");
        if (showAnnotations && annotationLine != null && annotationLine.length() > 0) {
          answer.append(annotationLine);
          answer.append("\n");
        }
      }  // for i < n
      if (verboseLevel >= 1) Log.i(TAG, "out LogicalMotionEvent.dumpString("+list.size()+" logical events)");
      return answer.toString();
    }  // dump

    final static Pattern mStaticJustWentDownPattern = Pattern.compile(" (\\d+) just went down");  // fragile  // XXX GET RID
    final static Pattern mStaticAlreadyDownPattern = Pattern.compile("ARMED: theAlreadyDownOne=(\\d+)");  // very fragile  // XXX GET RID
    final static Pattern mStaticForbiddenEstablishedPattern = Pattern.compile("^\\(FORBIDDING( \\(delayed\\))? id (?<id>\\d+) ");  // fragile
    final static Pattern mStaticForbiddenReleasedPattern = Pattern.compile("RELEASING id (?<id>\\d+) ");  // fragile
    final static Pattern mStaticForbiddenAlmostReleasedPattern = Pattern.compile("NOT releasing id (?<id>\\d+) ");  // fragile

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
          String dumpStringOut = dumpString(parsed, /*punctuationWhereDifferentFromOther=*/null, /*other=*/null, /*annotationsOrNull=*/null, /*showAnnotations=*/false);

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
  //     1. POINTER_DOWN(0) with exactly one other pointer id1 already down  (anchor0x,anchor0y = the x,y of that event)
  //        followed by:
  //     2. MOVE, with 0 and id1 down  (anchor1x,anchor1y = the primary (non-historical) x,y of that event)
  // While armed:
  //        if id 0's x,y appears to be anchor0x,anchor0y, change it to its previous value
  //        if id id1's x,y appears to be anchor1x,anchor1y, change it to its previous value
  // The pattern that disarms the fixer is either:
  //     - a new different arming
  //     - if either id0 or id1 experiences the same x,y twice in a row
  //        that is *not* the respective anchor position, then the bug isn't happening; disarm
  // Note, we do *not* disarm the fixer on:
  //     - a third pointer down (that doesn't affect the bug)
  //     - id 0 or id1 UP (the bug can still effect the other one,
  //       both on that UP event and subsequent ones.
  //       generally only for about 1/10 of a second, but who knows)
  //     - we could disarm id 0 when it goes UP, and disarm id id1 when it goes up, though.
  // UPDATE: actually the going-down id is sometimes not zero (infrequently).  We call it id0.
  //
  // The sequence of events that arms the fixer is two consecutive events:
  //    1. N(>=1) consecutive POINTER_DOWNs when there was previously exactly one other pointer already down
  //            (each of the newly-down id's anchor x,y is the x,y of the respective POINTER_DOWN event)
  //       immediately followed by:
  //    2. MOVE, with all N+1 pointers still down.
  //            (the originally-down id's anchor x,y is the primary (non-historical) x,y of this move event)
  //    From then on, it's symmetric: each of the N+1 participating ids
  //    keep bugging until they go up, or until it is the only one left
  //    (in which case it may keep bugging on the other guy's POINTER_UP and/or a small handful of subsequent lone MOVEs).
  // While armed:
  //    - if any of the bugging ids' x,y appears to be its anchor, change it to its previous value
  // The pattern that disarms the fixer is either:
  //    - a new different arming
  //    - if a participating id experiences the same x,y twice in a row
  //      that is *not* the respective anchor psoition, then the bug isn't happening; disarm
  //      [ARGH! currently that doesn't seem reliable :-( ]
  // Note, we do *not* disarm the fixer on:
  //     - additional pointers down or up (that doesn't affect the bug)
  //     - when a bugging id goes POINTER_UP, that id stops participating in the bug,
  //       but the bug can still effect the other one(s), both on that POINTER_UP event itself
  //       and on subsequent events, generally for only about 1/10 of a second, but who knows.

  private static class FixerState {
    private final static int STATE_DISARMED = 0;
    private final static int STATE_ARMING = 1;
    private final static int STATE_ARMED = 2;
    private final static String stateToString(int state) {
      if (state == STATE_DISARMED) return "DISARMED";
      if (state == STATE_ARMING) return "ARMING";
      if (state == STATE_ARMED) return "ARMED";
      CHECK(false);
      return null;
    }
    private int mCurrentState = STATE_DISARMED;
    private int mTheAlreadyDownOne = -1;
    private int mNumAnchoredIds = 0;
    private int[] mAnchoredIds = new int[MAX_FINGERS];  // XXX do we need this?
    private float[] mAnchorXs = new float[MAX_FINGERS];  // indexed by id
    private float[] mAnchorYs = new float[MAX_FINGERS];  // indexed by id
    // Note, fixer state provides these, but caller maintains the valuess.
    private float[] mCurrentXs = new float[MAX_FINGERS];  // indexed by id
    private float[] mCurrentYs = new float[MAX_FINGERS];  // indexed by id
    { Arrays.fill(mAnchorXs, Float.NaN); }
    { Arrays.fill(mAnchorYs, Float.NaN); }
    { Arrays.fill(mCurrentXs, Float.NaN); }
    { Arrays.fill(mCurrentYs, Float.NaN); }
    // for debugging.
    private int[] anchoredIds() {
      final int[] answer = new int[mNumAnchoredIds];
      int nFound = 0;
      for (int id = 0; id < MAX_FINGERS; ++id) {
        if (!Float.isNaN(mAnchorXs[id])) {
          CHECK(!Float.isNaN(mAnchorYs[id]));
          CHECK_LT(nFound, answer.length);
          answer[nFound++] = id;
        } else {
          CHECK(Float.isNaN(mAnchorYs[id]));
        }
      }
      CHECK_EQ(nFound, mNumAnchoredIds);
      return answer;
    }
    private void disarmAll() {
      // This can happen from any state
      int numDisarmed = 0;
      if (mNumAnchoredIds > 0) {
        // We didn't keep an explicit list of who was armed
        // (since it would have been a pain to keep track and support deleting items,
        // so just go through them all at this point.
        for (int id = 0; id < MAX_FINGERS; ++id) {
          if (!Float.isNaN(mAnchorXs[id])) {
            CHECK(!Float.isNaN(mAnchorYs[id]));
            mAnchorXs[id] = Float.NaN;
            mAnchorYs[id] = Float.NaN;
            numDisarmed++;
          } else {
            CHECK(Float.isNaN(mAnchorYs[id]));
          }
        }
      }
      CHECK_EQ(numDisarmed, mNumAnchoredIds);
      mNumAnchoredIds = 0;
      mTheAlreadyDownOne = -1;
      mCurrentState = STATE_DISARMED;
    }
    private void armFirst(int whoWasAlreadyDown, int whoJustWentDown, float anchorX, float anchorY) {
      CHECK_EQ(mCurrentState, STATE_DISARMED);
      mCurrentState = STATE_ARMING;
      // unset and stays unset...
      CHECK(Float.isNaN(mAnchorXs[whoWasAlreadyDown]));
      CHECK(Float.isNaN(mAnchorYs[whoWasAlreadyDown]));
      // unset and we're about to set it...
      CHECK_EQ(mTheAlreadyDownOne, -1);
      mTheAlreadyDownOne = whoWasAlreadyDown;
      CHECK(Float.isNaN(mAnchorXs[whoJustWentDown]));
      CHECK(Float.isNaN(mAnchorYs[whoJustWentDown]));
      mAnchorXs[whoJustWentDown] = anchorX;
      mAnchorYs[whoJustWentDown] = anchorY;
      CHECK_EQ(mNumAnchoredIds, 0);
      mNumAnchoredIds++;
    }
    private void armOneMore(int whoJustWentDown, float anchorX, float anchorY) {
      CHECK_EQ(mCurrentState, STATE_ARMING);
      CHECK(Float.isNaN(mAnchorXs[whoJustWentDown]));
      CHECK(Float.isNaN(mAnchorYs[whoJustWentDown]));
      mAnchorXs[whoJustWentDown] = anchorX;
      mAnchorYs[whoJustWentDown] = anchorY;
      mNumAnchoredIds++;
    }
    private void armTheOneWhoWasAlreadyDown(float anchorX, float anchorY) {
      CHECK_EQ(mCurrentState, STATE_ARMING);
      mCurrentState = STATE_ARMED;
      CHECK(Float.isNaN(mAnchorXs[mTheAlreadyDownOne]));
      CHECK(Float.isNaN(mAnchorYs[mTheAlreadyDownOne]));
      mAnchorXs[mTheAlreadyDownOne] = anchorX;
      mAnchorYs[mTheAlreadyDownOne] = anchorY;
      mNumAnchoredIds++;
      mTheAlreadyDownOne = -1;  // forget who it was; it's symmetric from here on out
    }
    private void disarmOne(int whoJustWentUp) {
      CHECK_EQ(mCurrentState, STATE_ARMED);  // yes, require state to be armed. CBB: make this obvious from the function name
      CHECK(!Float.isNaN(mAnchorXs[whoJustWentUp]));
      CHECK(!Float.isNaN(mAnchorYs[whoJustWentUp]));
      mAnchorXs[whoJustWentUp] = Float.NaN;
      mAnchorYs[whoJustWentUp] = Float.NaN;
      --mNumAnchoredIds;
      if (mNumAnchoredIds == 0) {
        mCurrentState = STATE_DISARMED;
      }
    }
  }
  private FixerState mFixerState = new FixerState();


  private static class SIMPLERFixerState {
    private float[] mCurrentX = new float[MAX_FINGERS];  // indexed by id
    private float[] mCurrentY = new float[MAX_FINGERS];  // indexed by id
    private float[] mForbiddenX = new float[MAX_FINGERS]; // indexed by id
    private float[] mForbiddenY = new float[MAX_FINGERS]; // indexed by id
    private int mWhoNeedsForbidden = -1;  // id that was down when a second pointer went down, whose forbidden position isn't yet known.
    { Arrays.fill(mCurrentX, Float.NaN); }
    { Arrays.fill(mCurrentY, Float.NaN); }
    { Arrays.fill(mForbiddenX, Float.NaN); }
    { Arrays.fill(mForbiddenY, Float.NaN); }
    private int[] idsWithForbiddens() {
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
  };
  private SIMPLERFixerState mSimplerFixerState = new SIMPLERFixerState();
  private void SIMPLERcorrectPointerCoordsUsingState(
      MotionEvent unfixed,
      int historyIndex,
      long eventTime,
      MotionEvent.PointerCoords pointerCoords[],
      ArrayList<String> annotationsOrNull  // if not null, we append one item.
      ) {
    final int verboseLevel = 0;
    if (verboseLevel >= 1) Log.i(TAG, "        in SIMPLERcorrectPointerCoordsUsingState(historyIndex="+historyIndex+"/"+unfixed.getHistorySize()+", eventTime="+(eventTime-unfixed.getDownTime())/1000.+")  before: "+FixerState.stateToString(mFixerState.mCurrentState));

    StringBuilder annotationOrNull = annotationsOrNull!=null ? new StringBuilder() : null; // CBB: maybe wasteful since most lines don't get annotated

    if (annotationOrNull != null) annotationOrNull.append(STRINGIFY_COMPACT(mSimplerFixerState.idsWithForbiddens()));

    final int action = unfixed.getActionMasked();
    final int actionIndex = unfixed.getActionIndex();
    final int actionId = unfixed.getPointerId(actionIndex);
    final int pointerCount = unfixed.getPointerCount();
    final int historySize = unfixed.getHistorySize();

    if (action == ACTION_DOWN) {
      // Make sure state is pristine, nothing is forbidden.
      mSimplerFixerState.mWhoNeedsForbidden = -1;
      mSimplerFixerState.mForbiddenX[actionId] = Float.NaN;
      mSimplerFixerState.mForbiddenY[actionId] = Float.NaN;
    } else if (action == ACTION_POINTER_DOWN) {
      if (annotationOrNull != null) annotationOrNull.append("(FORBIDDING id "+actionId+" to go to "+pointerCoords[actionIndex].x+","+pointerCoords[actionIndex].y+")");
      mSimplerFixerState.mForbiddenX[actionId] = pointerCoords[actionIndex].x;
      mSimplerFixerState.mForbiddenY[actionId] = pointerCoords[actionIndex].y;
      // In the case that there was exactly one previous pointer down,
      // that pointer's forbidden is not known until the end of the next MOVE packet.
      if (pointerCount == 2) {
        mSimplerFixerState.mWhoNeedsForbidden = unfixed.getPointerId(1 - actionIndex);
      }
    } else if (action == ACTION_MOVE || action == ACTION_POINTER_UP || action == ACTION_UP) {
      if (action == ACTION_POINTER_UP || action == ACTION_UP) {
        if (mSimplerFixerState.mWhoNeedsForbidden != -1) {
          // It wasn't arming after all.
          if (annotationOrNull != null) annotationOrNull.append("(never mind, it wasn't arming after all. unforbidding everything.)");
          mSimplerFixerState.mWhoNeedsForbidden = -1;
          // Unforbid everything.
          for (int id = 0; id < MAX_FINGERS; ++id) {
            mSimplerFixerState.mForbiddenX[actionId] = Float.NaN;
            mSimplerFixerState.mForbiddenY[actionId] = Float.NaN;
          }
        }
      }
      for (int index = 0; index < pointerCount; ++index) {
        final int id = unfixed.getPointerId(index);
        if (id == mSimplerFixerState.mWhoNeedsForbidden) {
          if (action == ACTION_MOVE
           && historyIndex == historySize) {
            if (annotationOrNull != null) annotationOrNull.append("(FORBIDDING (delayed) id "+mSimplerFixerState.mWhoNeedsForbidden+" to go to "+pointerCoords[index].x+","+pointerCoords[index].y+")");
            mSimplerFixerState.mForbiddenX[mSimplerFixerState.mWhoNeedsForbidden] = pointerCoords[index].x;
            mSimplerFixerState.mForbiddenY[mSimplerFixerState.mWhoNeedsForbidden] = pointerCoords[index].y;
            mSimplerFixerState.mWhoNeedsForbidden = -1;
          }
        } else if (!Float.isNaN(mSimplerFixerState.mForbiddenX[id])) {
          // This pointer has a forbidden position (that wasn't just now established).
          if (pointerCoords[index].x == mSimplerFixerState.mForbiddenX[id]
           && pointerCoords[index].x == mSimplerFixerState.mForbiddenX[id]) {
            // This pointer moved to (or stayed still at) its forbidden position.
            // We think it meant to stay at its current position
            // (which may actually still be the forbidden position,
            // shortly after the forbidden position was established; that's fine).
            // Fix it.
            pointerCoords[index].x = mSimplerFixerState.mCurrentX[id];
            pointerCoords[index].y = mSimplerFixerState.mCurrentY[id];
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
             && pointerCoords[index].x == mSimplerFixerState.mCurrentX[id]
             && pointerCoords[index].y == mSimplerFixerState.mCurrentY[id]) {
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
                if (annotationOrNull != null) annotationOrNull.append("(NOT releasing id "+id+" even though it stayed stationary at a non-forbidden x,y, sice no history which sometimes means false alarm)");
              } else {
                if (annotationOrNull != null) annotationOrNull.append("(RELEASING id "+id+" because it stayed stationary at a non-forbidden x,y; I think it wasn't bugging or is no longer)");
                mSimplerFixerState.mForbiddenX[id] = Float.NaN;
                mSimplerFixerState.mForbiddenY[id] = Float.NaN;
              }
            }  // stationary at non-forbidden x,y
          }  // not at forbidden x,y
        }  // if this id already had a forbidden position
      }  // for index
      // end of case MOVE or POINTER_UP or UP
    } else {
      // XXX Unexpected action-- what is it?
    }

    // Update current state, for next time.
    for (int index = 0; index < pointerCount; ++index) {
      final int id = unfixed.getPointerId(index);
      mSimplerFixerState.mCurrentX[id] = pointerCoords[index].x;
      mSimplerFixerState.mCurrentY[id] = pointerCoords[index].y;
    }

    if (annotationOrNull != null) annotationOrNull.append(STRINGIFY_COMPACT(mSimplerFixerState.idsWithForbiddens()));

    if (annotationsOrNull != null) {
      annotationsOrNull.add(annotationOrNull.toString());
    }

    if (verboseLevel >= 1) Log.i(TAG, "        out SIMPLERcorrectPointerCoordsUsingState(historyIndex="+historyIndex+"/"+unfixed.getHistorySize()+", eventTime="+(eventTime-unfixed.getDownTime())/1000.+")  after: "+FixerState.stateToString(mFixerState.mCurrentState));
  }  // SIMPLERcorrectPointerCoordsUsingState

  private void correctPointerCoordsUsingState(
      MotionEvent unfixed,
      int historyIndex,
      long eventTime,
      MotionEvent.PointerCoords pointerCoords[],
      ArrayList<String> annotationsOrNull  // if not null, we append one item.
      ) {
    final int verboseLevel = 0;
    if (verboseLevel >= 1) Log.i(TAG, "        in correctPointerCoordsUsingState(historyIndex="+historyIndex+"/"+unfixed.getHistorySize()+", eventTime="+(eventTime-unfixed.getDownTime())/1000.+")  before: "+FixerState.stateToString(mFixerState.mCurrentState));

    StringBuilder annotationOrNull = annotationsOrNull!=null ? new StringBuilder() : null; // CBB: maybe wasteful since most lines don't get annotated

    final int action = unfixed.getActionMasked();
    final int actionIndex = unfixed.getActionIndex();
    final int actionId = unfixed.getPointerId(actionIndex);
    final int pointerCount = unfixed.getPointerCount();
    final int historySize = unfixed.getHistorySize();

    // No matter what state we're in,
    // if we see the first event of the arming sequence,
    // honor it.
    if (action == ACTION_POINTER_DOWN
     && pointerCount == 2) {
      final int whoJustWentDown = actionId;
      final int whoWasAlreadyDown = unfixed.getPointerId(1 - actionIndex);
      final float anchorX = pointerCoords[actionIndex].x;
      final float anchorY = pointerCoords[actionIndex].y;
      // This situation is pretty clear, so regardless of previous state, force it to the beginning of ARMING.
      if (mFixerState.mCurrentState != FixerState.STATE_DISARMED) {
        if (annotationOrNull != null) annotationOrNull.append("(DISARMING PREVIOUSLY ARMED "+STRINGIFY_COMPACT(mFixerState.anchoredIds())+")");
        mFixerState.disarmAll();
      }
      mFixerState.armFirst(whoWasAlreadyDown,
                           whoJustWentDown, 
                           anchorX, anchorY);
      if (annotationOrNull != null) annotationOrNull.append("(ARMING: "+whoWasAlreadyDown+" was already down, "+whoJustWentDown+" just went down with anchor="+anchorX+","+anchorY+")");
    } else if (mFixerState.mCurrentState == FixerState.STATE_ARMING) {
      if (action == ACTION_POINTER_DOWN) {
        final int whoJustWentDown = actionId;
        final float anchorX = pointerCoords[actionIndex].x;
        final float anchorY = pointerCoords[actionIndex].y;
        mFixerState.armOneMore(whoJustWentDown,
                               anchorX, anchorY);
        if (annotationOrNull != null) annotationOrNull.append("(ARMING ANOTHER: "+whoJustWentDown+" just went down with anchor="+anchorX+","+anchorY+")");
      } else if (action == ACTION_MOVE) {
        if (historyIndex == historySize) {  // i.e. this is the "primary" sub-event, not historical
          final int theAlreadyDownIndex = unfixed.findPointerIndex(mFixerState.mTheAlreadyDownOne);
          CHECK_NE(theAlreadyDownIndex, -1);
          final float anchorX = pointerCoords[theAlreadyDownIndex].x;
          final float anchorY = pointerCoords[theAlreadyDownIndex].y;
          if (annotationOrNull != null) annotationOrNull.append("(ARMED: theAlreadyDownOne="+mFixerState.mTheAlreadyDownOne+" with anchor="+anchorX+","+anchorY+")");  // must do this before calling arm, which clears mTheAlreadyDownOne
          mFixerState.armTheOneWhoWasAlreadyDown(anchorX, anchorY);
        } else {
          if (annotationOrNull != null) annotationOrNull.append("(in historical sub-event of what may be an arming event)");
          // We're in a historical sub-event of what may be the arming event.  Do nothing special.
        }
      } else {
        // Didn't see continuation of arming sequence; disarm.
        // CBB: I'm unclear on when and if this can happen, and if it's correct when 3 or more pointers are involved
        if (annotationOrNull != null) annotationOrNull.append("(DISARMED because didn't see continuation of arming sequence)");
        mFixerState.disarmAll();
      }
    } else if (mFixerState.mCurrentState == FixerState.STATE_ARMED) {  // i.e. if was already armed (not just now got armed)
      for (int index = 0; index < pointerCount; ++index) {
        final int id = unfixed.getPointerId(index);
        if (!Float.isNaN(mFixerState.mAnchorXs[id])) {
          // This id is currently believed to be bugging.
          if (pointerCoords[index].x == mFixerState.mAnchorXs[id]
           && pointerCoords[index].y == mFixerState.mAnchorYs[id]) {
            // The pointer moved to (or stayed still at) its anchor.
            // That's what happens when it meant to stay at its current position.
            // Correct it.
            pointerCoords[index].x = mFixerState.mCurrentXs[id];
            pointerCoords[index].y = mFixerState.mCurrentYs[id];
          } else {
            final boolean tryTheStationaryTest = true;
            // Argh!  Is it simply unreliable?  Examples (one was 3-pointer, the other was simply 2-pointer):
/*
------
          2.297  476/1287:           MOVE   {0,1}  0:[1008.64978,784.455200]A?  1:[1964.19446,892.755249]B?
          2.307  477/1287:                  {0,1}  0:[1008.64978,784.455200]A?  1: 1582.45056,756.474670
          2.307  478/1287:           MOVE   {0,1}  0:[1008.64978,784.455200]A?  1: 1964.19446,892.755249 B?
          2.318  479/1287:                  {0,1}  0: 708.753906,860.402466     1:[1964.19446,892.755249]B?
          2.318  480/1287:                  {0,1}  0: 1008.64978,784.455200 A?  1: 1583.45020,757.473938
          2.318  481/1287:                  {0,1}  0:[1008.64978,784.455200]A?  1: 1964.19446,892.755249 B?
          2.329  482/1287:           MOVE   {0,1}  0:[1008.64978,784.455200]A?  1: 1585.44958,760.471863
          2.329  483/1287:           MOVE   {0,1}  0:[1008.64978,784.455200]A?  1:[1585.44958,760.471863]
(DISARMING id 1 because it stayed the same and is *not* the anchor 1964.1945,892.75525.  I think bug isn't happening here (or isn't happening any more here))
          2.338  484/1287:                  {0,1}  0:(1008.64978,784.455200)A?  1: 1964.19446,892.755249 B
          2.338  485/1287:           MOVE   {0,1}  0:[1008.64978,784.455200]A?  1: 1587.44885,763.469788
          2.348  486/1287:                  {0,1}  0:(1008.64978,784.455200)A?  1: 1964.19446,892.755249 B
          2.348  487/1287:                  {0,1}  0:[1008.64978,784.455200]A?  1: 1590.44775,768.466309
          2.357  488/1287:                  {0,1}  0:(1008.64978,784.455200)A?  1: 1964.19446,892.755249 B
------
         19.748 4283/5135:                  {0,1}    0:[1478.48669,153.893127]A?  1:[1709.40649,481.665497]A?
         19.758 4284/5135:                  {0,1}    0:(1478.48669,153.893127)A?  1:[1709.40649,481.665497]A?
         19.769 4285/5135:                  {0,1}    0:(1478.48669,153.893127)A?  1:[1709.40649,481.665497]A?
         19.779 4286/5135:           MOVE   {0,1}    0: 403.859772,487.661346     1:[1709.40649,481.665497]A?
         19.779 4287/5135:           MOVE   {0,1}    0:[403.859772,487.661346]    1:[1709.40649,481.665497]A?
(DISARMING id 0 because it stayed the same and is *not* the anchor 1478.4867,153.89313.  I think bug isn't happening here (or isn't happening any more here))
         19.789 4288/5135:                  {0,1}    0: 404.859436,489.659943     1:[1709.40649,481.665497]A?
         19.789 4289/5135:                  {0,1}    0: 1478.48669,153.893127 A   1:[1709.40649,481.665497]A?
         19.800 4290/5135:                  {0,1}    0:(1478.48669,153.893127)A   1:[1709.40649,481.665497]A?
         19.800 4291/5135:                  {0,1}    0:[1478.48669,153.893127]A   1:[1709.40649,481.665497]A?
------
*/
            // IDEA: I notice this false alarm always seems to be history-less, which is actually unusual... is that a reliable indicator?
            //       In fact, the event looks completely identical to the (primary of the) previous event??
            //       That's pretty strange indeed.
            // Okay, that seems to work.  TODO: Clean this up at some point.
            // Thoughts for a simpler and stricter condition:
            //    - exonerate only on a very specific and common situation that I think is safe.  That is:
            //      - a MOVE with-history where the primary (non-history) x,y differs from the history x,y
            // There *might* be more inclusive criteria for exoneration,
            // but we'll err on the side of assuming it's still bugging
            // (when not really bugging, we'll get evidence of not bugging soon enough via
            // another instance of this criterion anyway).
            // (Well, except when the last finger down, in which case we can be considered bugging indefinitely anyway; oh well!)
            boolean trySuperSmartFalseAlarmProtection = true;

            if (tryTheStationaryTest) {
              if (pointerCoords[index].x == mFixerState.mCurrentXs[id]
               && pointerCoords[index].y == mFixerState.mCurrentYs[id]) {
                // The pointer stayed the same, at an x,y that is *not* the anchor.
                // The bug is not happening at this id (since, when the bug is happening at this id,
                // staying the same always gets botched into moving to the anchor).
                // CBB: in some cases, particularly in simple cases involving only 2 pointers, we could now decide the bug wasn't happening at all, and move all the way to DISARMED at this point.  Is that important?  Maybe not.
                //
                // Exception: I've seen a rogue stay-the-same-that's-not-the-anchor in the following situation:
                //    1 was moving
                //    0&2 down simultaneously, but only 0&1 started bugging
                //    in fact, the 2 down happened later enough that we were alreadly armed and correctly fixing id 1
                //    (this happened to be the last stationary of id 0's down).
                //    on that 2 down, the 1 did a rogue stay-the-same-that's-not-the-anchor.
                //    CBB: I'm not sure how to completely characterize this situation,
                //    but I'll err on the side of assuming it's still bugging (when really not bugging,
                //    we'll get evidence of not bugging soon enough anyway).
                if (action != ACTION_UP && action != ACTION_MOVE) {  // i.e. exoneration can only happen on MOVE or UP
                  if (annotationOrNull != null) annotationOrNull.append("(NOT DISARMING id "+id+" on rogue stay-the-same at non-anchor on action="+actionToString(action)+"("+actionId+")");
                } else if (trySuperSmartFalseAlarmProtection && action == ACTION_MOVE && historySize == 0) {
                  if (annotationOrNull != null) annotationOrNull.append("(NOT DISARMING id "+id+" on rogue stay-the-same at non-anchor because it's a history-less MOVE which I think means false alarm)");
                } else {
                  if (annotationOrNull != null) annotationOrNull.append("(DISARMING id "+id+" because it stayed the same and is *not* the anchor "+mFixerState.mAnchorXs[id]+","+mFixerState.mAnchorYs[id]+".  I think bug isn't happening here (or isn't happening any more here))");
                  mFixerState.disarmOne(id);
                }

                // so, condition under which we disarm:
                //      (action==ACTION_UP||action==ACTION_MOVE) and
                //      (action!=MOVE || historySize>0)
              }
            }
          }
        }
      }

      // Subtlety: when ARMED and one of the participating ids goes UP or POINTER_UP,
      // the bug may affect the coords of the UP event!  So we have to
      // do this in the right order:  correct first (which we just did),
      // and *then* remove it from the believed-to-be-bugging set
      // (which may actually set state to DISARMED, if this was the last one).
      if ((action == ACTION_POINTER_UP || action == ACTION_UP)
       && !Float.isNaN(mFixerState.mAnchorXs[actionId])) {
        if (annotationOrNull != null) annotationOrNull.append("(DISARMING id "+actionId+" on "+actionToString(action)+")");
        mFixerState.disarmOne(actionId);
      }

    }  // mFixerState.mCurrentState == FixerState.STATE_ARMED


    if (mFixerState.mCurrentState != FixerState.STATE_DISARMED) {
      // Remember all current coords (original or corrected)
      // that could possibly be relevant, for next time.
      for (int index = 0; index < pointerCount; ++index) {
        final int id = unfixed.getPointerId(index);
        mFixerState.mCurrentXs[id] = pointerCoords[index].x;
        mFixerState.mCurrentYs[id] = pointerCoords[index].y;
      }
    }

    if (annotationsOrNull != null) {
      annotationsOrNull.add(annotationOrNull.toString());
    }

    if (verboseLevel >= 1) Log.i(TAG, "        out correctPointerCoordsUsingState(historyIndex="+historyIndex+"/"+unfixed.getHistorySize()+", eventTime="+(eventTime-unfixed.getDownTime())/1000.+")  after: "+FixerState.stateToString(mFixerState.mCurrentState));
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

            SIMPLERcorrectPointerCoordsUsingState(unfixed, h, subEventTime, pointerCoords, mAnnotationsOrNull);
            //correctPointerCoordsUsingState(unfixed, h, subEventTime, pointerCoords, mAnnotationsOrNull);

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
          String beforeString = LogicalMotionEvent.dumpString(
            mLogicalMotionEventsSinceFirstDown,
            /*punctuationWhereDifferentFromOther=*/"?",
            /*other=*/mFixedLogicalMotionEventsSinceFirstDown,
            /*annotationsOrNull=*/null,
            /*showAnnotations=*/false);
          String duringString = LogicalMotionEvent.dumpString(
            mLogicalMotionEventsSinceFirstDown,
            /*punctuationWhereDifferentFromOther=*/"?",
            /*other=*/mFixedLogicalMotionEventsSinceFirstDown,
            mAnnotationsOrNull,
            /*showAnnotations=*/true);
          String afterString = LogicalMotionEvent.dumpString(
            mFixedLogicalMotionEventsSinceFirstDown,
            /*punctuationWhereDifferentFromOther=*/"!",
            /*other=*/mLogicalMotionEventsSinceFirstDown,
            mAnnotationsOrNull,
            /*showAnnotations=*/false);  // i.e. use the annotations only for figuring out "+" and "-" marks

          if (mTracePrintWriterOrNull != null) {
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
            mTracePrintWriterOrNull.flush();
          }
          if (mAnnotationsOrNull != null) {
            mAnnotationsOrNull.clear();
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
