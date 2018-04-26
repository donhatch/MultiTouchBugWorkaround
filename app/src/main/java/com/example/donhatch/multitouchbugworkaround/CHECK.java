package com.example.donhatch.multitouchbugworkaround;

import static com.example.donhatch.multitouchbugworkaround.STRINGIFY.STRINGIFY;

public class CHECK {
  public static void CHECK(boolean cond) { if (!cond) throw new AssertionError("CHECK failed"); }
  public static void CHECK_EQUALS(Object lhs, Object rhs) { if (!(lhs==null ? lhs==rhs : lhs.equals(rhs))) throw new AssertionError("CHECK failed: lhs.equals(rhs) where lhs is "+STRINGIFY(lhs)+", rhs="+STRINGIFY(rhs)); }

  // TODO: investigate Arrays.equals vs. Arrays.deepEquals
  // TODO: figure out how to get Arrays.equals and/or Arrays.deepEquals for arrays of primitive types.  might need reflection
  public static void CHECK_DEEPEQUALS(Object[] lhs, Object[] rhs) { if (!java.util.Arrays.deepEquals(lhs, rhs)) throw new AssertionError("CHECK failed: lhs.equals(rhs) where lhs is "+STRINGIFY(lhs)+", rhs="+STRINGIFY(rhs)); }
  public static void CHECK_DEEPEQUALS(double[] lhs, double[] rhs) {
    if (!_DEEPEQUALS(lhs, rhs)) throw new AssertionError("CHECK failed: lhs deepquals rhs where lhs is "+STRINGIFY(lhs)+", rhs="+STRINGIFY(rhs));
  }
  private static boolean _DEEPEQUALS(double[] lhs, double[] rhs) {
    if (lhs.length != rhs.length) return false;
    for (int i = 0; i < lhs.length; ++i) {
      if (lhs[i] != rhs[i]) return false;
    }
    return true;
  }

  // Note, we purposely do *not* have CHECK_EQ for Objects.
  // That's to disallow mistakenly calling CHECK_EQ(someString, someOtherString);
  // instead, you must say:
  //    CHECK(someString == someOtherString);  // if you really want comparison of pointers, which is unlikely
  //    CHECK_EQUALS(someString, someOtherString);

  public static void CHECK_EQ(boolean lhs, boolean rhs) { if (!(lhs == rhs)) throw new AssertionError("CHECK failed: lhs==rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_NE(boolean lhs, boolean rhs) { if (!(lhs != rhs)) throw new AssertionError("CHECK failed: lhs!=rhs where lhs is "+lhs+", rhs="+rhs); }

  public static void CHECK_EQ(int lhs, int rhs) { if (!(lhs == rhs)) throw new AssertionError("CHECK failed: lhs==rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_LE(int lhs, int rhs) { if (!(lhs <= rhs)) throw new AssertionError("CHECK failed: lhs<=rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_GE(int lhs, int rhs) { if (!(lhs >= rhs)) throw new AssertionError("CHECK failed: lhs>=rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_LT(int lhs, int rhs) { if (!(lhs < rhs)) throw new AssertionError("CHECK failed: lhs<rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_GT(int lhs, int rhs) { if (!(lhs > rhs)) throw new AssertionError("CHECK failed: lhs>rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_NE(int lhs, int rhs) { if (!(lhs != rhs)) throw new AssertionError("CHECK failed: lhs!=rhs where lhs is "+lhs+", rhs="+rhs); }

  public static void CHECK_EQ(long lhs, long rhs) { if (!(lhs == rhs)) throw new AssertionError("CHECK failed: lhs==rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_LE(long lhs, long rhs) { if (!(lhs <= rhs)) throw new AssertionError("CHECK failed: lhs<=rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_GE(long lhs, long rhs) { if (!(lhs >= rhs)) throw new AssertionError("CHECK failed: lhs>=rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_LT(long lhs, long rhs) { if (!(lhs < rhs)) throw new AssertionError("CHECK failed: lhs<rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_GT(long lhs, long rhs) { if (!(lhs > rhs)) throw new AssertionError("CHECK failed: lhs>rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_NE(long lhs, long rhs) { if (!(lhs != rhs)) throw new AssertionError("CHECK failed: lhs!=rhs where lhs is "+lhs+", rhs="+rhs); }

  public static void CHECK_EQ(double lhs, double rhs) { if (!(lhs == rhs)) throw new AssertionError("CHECK failed: lhs==rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_LE(double lhs, double rhs) { if (!(lhs <= rhs)) throw new AssertionError("CHECK failed: lhs<=rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_GE(double lhs, double rhs) { if (!(lhs >= rhs)) throw new AssertionError("CHECK failed: lhs>=rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_LT(double lhs, double rhs) { if (!(lhs < rhs)) throw new AssertionError("CHECK failed: lhs<rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_GT(double lhs, double rhs) { if (!(lhs > rhs)) throw new AssertionError("CHECK failed: lhs>rhs where lhs is "+lhs+", rhs="+rhs); }
  public static void CHECK_NE(double lhs, double rhs) { if (!(lhs != rhs)) throw new AssertionError("CHECK failed: lhs!=rhs where lhs is "+lhs+", rhs="+rhs); }

  public static void CHECK_ALMOST_EQ(double lhs, double rhs, double tol) { if (!(Math.abs(rhs - lhs) <= tol)) throw new AssertionError("CHECK failed: lhs~=rhs to tol="+tol+" where lhs is "+lhs+", rhs="+rhs+": |"+(rhs-lhs)+"| = "+Math.abs(rhs-lhs)+" < tol="+tol+""); }

  // CBB: quality of information
  public static void CHECK_EQ_EQ(double a, double b, double c) {
    CHECK_EQ(a, b);
    CHECK_EQ(b, c);
  }

}
