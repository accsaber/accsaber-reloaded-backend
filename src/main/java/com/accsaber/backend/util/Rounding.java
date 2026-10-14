package com.accsaber.backend.util;

public final class Rounding {

    private static final double[] POW10 = {
            1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10
    };

    private Rounding() {
    }

    public static double round(double value, int scale) {
        if (!Double.isFinite(value)) {
            return value;
        }
        double factor = POW10[scale];
        return Math.copySign(Math.floor(Math.abs(value) * factor + 0.5), value) / factor;
    }
}
