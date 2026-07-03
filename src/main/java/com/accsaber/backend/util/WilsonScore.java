package com.accsaber.backend.util;

public final class WilsonScore {

    private static final double Z = 1.959963984540054;
    private static final double Z_SQUARED = Z * Z;

    private WilsonScore() {
    }

    public static double lowerBound(long positive, long total) {
        if (total <= 0) {
            return 0.0;
        }
        double phat = (double) positive / total;
        double centre = phat + Z_SQUARED / (2 * total);
        double margin = Z * Math.sqrt((phat * (1 - phat) + Z_SQUARED / (4 * total)) / total);
        return (centre - margin) / (1 + Z_SQUARED / total);
    }
}
