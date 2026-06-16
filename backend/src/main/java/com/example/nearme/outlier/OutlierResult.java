package com.example.nearme.outlier;

/**
 * The verdict on a single value. Domain-agnostic: the value could be a gas
 * price, a rent, a sensor reading — anything numeric.
 *
 * @param outlier      whether the value is flagged as anomalous
 * @param score        how far out it is, in robust deviations (MAD) or IQR
 *                     multiples; higher = more extreme. 0 when not enough data.
 * @param lowerBound   the computed acceptable lower bound (inclusive)
 * @param upperBound   the computed acceptable upper bound (inclusive)
 * @param center       the central reference (median) of the sample
 * @param sampleSize   how many comparable values were used
 * @param reason       short human-readable explanation
 */
public record OutlierResult(
        boolean outlier,
        double score,
        double lowerBound,
        double upperBound,
        double center,
        int sampleSize,
        String reason
) {
    /** Convenience for "we couldn't judge — too little data to compare against". */
    public static OutlierResult inconclusive(int sampleSize) {
        return new OutlierResult(false, 0.0, Double.NaN, Double.NaN, Double.NaN,
                sampleSize, "Not enough comparable data to judge");
    }
}
