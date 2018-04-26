package com.example.donhatch.multitouchbugworkaround;

// Mutual dependency (but only for our unit test).
import static com.example.donhatch.multitouchbugworkaround.CHECK.*;

public class STRINGIFY {
  // Hmm, this actually covers primitive types (int,float,double,...); they get wrapped automatically. Cool!
  public static String STRINGIFY(Object x) {
    if (x == null) {
      return "null";
    } else if (x instanceof Float) {
      return x+"f";
    } else if (x instanceof Character) {
      return "'"+x+"'";  // CBB: escapify
    } else if (x instanceof String || x instanceof CharSequence) {
      return "\""+x+"\"";  // CBB: escapify
    } else if (x.getClass().isArray()) {
      StringBuilder sb = new StringBuilder("{");
      int n = java.lang.reflect.Array.getLength(x);
      for (int i = 0; i < n; ++i) {
        if (i != 0) sb.append(", ");
        Object item = java.lang.reflect.Array.get(x, i);
        sb.append(STRINGIFY(item));
      }
      sb.append("}");
      return sb.toString();
    } else {
      // Int, Double, Bool, and everything else falls into this case
      return x.toString();
    }
  }  // STRINGIFY

  public static String STRINGIFYnonCompact(Object x) {
    CHECK(x.getClass().isArray());
    StringBuilder sb = new StringBuilder("{\n");
    int n = java.lang.reflect.Array.getLength(x);
    for (int i = 0; i < n; ++i) {
      if (i != 0) sb.append(",\n");
      Object item = java.lang.reflect.Array.get(x, i);
      sb.append(STRINGIFY(item));
    }
    sb.append("}");
    return sb.toString();
  }

  public static void unitTest() {
    CHECK_EQUALS(STRINGIFY(null), "null");
    CHECK_EQUALS(STRINGIFY(false), "false");
    CHECK_EQUALS(STRINGIFY(true), "true");
    CHECK_EQUALS(STRINGIFY(1), "1");
    CHECK_EQUALS(STRINGIFY(1.), "1.0");
    CHECK_EQUALS(STRINGIFY(1.f), "1.0f");
    CHECK_EQUALS(STRINGIFY('a'), "'a'");
    CHECK_EQUALS(STRINGIFY("abc"), "\"abc\"");
    CHECK_EQUALS(STRINGIFY(new int[]{}), "{}");
    CHECK_EQUALS(STRINGIFY(new int[]{1}), "{1}");
    CHECK_EQUALS(STRINGIFY(new int[][][]{{{1,2},{3,4}},{{5,6},{7,8}}}), "{{{1, 2}, {3, 4}}, {{5, 6}, {7, 8}}}");
    CHECK_EQUALS(STRINGIFY(new int[][]{{1,2},{3,4}}), "{{1, 2}, {3, 4}}");
    CHECK_EQUALS(STRINGIFY(new int[][]{{1,2},null,{3,4}}), "{{1, 2}, null, {3, 4}}");
    //CHECK_DEEPEQUALS(new int[]{1,2,3}, new int[]{1,2,3});  // TODO: make this work
  }

}
