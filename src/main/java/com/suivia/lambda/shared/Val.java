package com.suivia.lambda.shared;

/** Null-safe coercions mirroring Python's str()/float()/int() leniency. */
public final class Val {

    private Val() {}

    public static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    public static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    public static double dbl(Object o) {
        if (o == null) {
            return 0.0;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static long lng(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
