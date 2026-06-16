package com.example.nearme.outlier;

/**
 * Tuning for the detector. Defaults are sensible for small, noisy real-world
 * samples (the MAD method with a 3.5 threshold is a common robust choice).
 *
 * @param method            which statistic to use
 * @param threshold         cutoff: for MAD, modified z-score limit (≈3.5 typical);
 *                          for IQR, the whisker multiplier (1.5 typical, 3.0 strict)
 * @param minSampleSize     below this many comparable values, return inconclusive
 *                          rather than guess
 * @param absoluteFloor     optional hard lower sanity bound (NaN to disable) —
 *                          e.g. a gas price can never be <= 0
 * @param absoluteCeiling   optional hard upper sanity bound (NaN to disable)
 */
public record OutlierConfig(
        Method method,
        double threshold,
        int minSampleSize,
        double absoluteFloor,
        double absoluteCeiling
) {
    public enum Method { MAD, IQR }

    /** Reasonable general-purpose defaults. */
    public static OutlierConfig defaults() {
        return new OutlierConfig(Method.MAD, 3.5, 4, Double.NaN, Double.NaN);
    }

    public OutlierConfig withMethod(Method m) {
        return new OutlierConfig(m, threshold, minSampleSize, absoluteFloor, absoluteCeiling);
    }

    public OutlierConfig withThreshold(double t) {
        return new OutlierConfig(method, t, minSampleSize, absoluteFloor, absoluteCeiling);
    }

    public OutlierConfig withBounds(double floor, double ceiling) {
        return new OutlierConfig(method, threshold, minSampleSize, floor, ceiling);
    }

    public OutlierConfig withMinSampleSize(int n) {
        return new OutlierConfig(method, threshold, n, absoluteFloor, absoluteCeiling);
    }
}
