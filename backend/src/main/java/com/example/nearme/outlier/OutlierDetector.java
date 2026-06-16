package com.example.nearme.outlier;

import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Domain-agnostic statistical outlier detector. It knows nothing about gas,
 * prices, or any domain — give it a candidate number and a sample of comparable
 * numbers, and it tells you whether the candidate is anomalous.
 *
 * Two robust methods:
 *  - MAD (Median Absolute Deviation): modified z-score against the median.
 *    Resistant to outliers in the sample itself; good default for small data.
 *  - IQR (interquartile range): the classic box-plot whisker rule.
 *
 * Both are pure arithmetic — no model, no external calls, instant.
 *
 * Usage from any domain:
 *   var result = detector.evaluate(candidate, comparableValues, OutlierConfig.defaults());
 *   if (result.outlier()) { ...reject or flag... }
 */
@Component
public class OutlierDetector {

    private static final double MAD_SCALE = 1.4826; // makes MAD a std-dev estimator for normal data

    public OutlierResult evaluate(double candidate, double[] sample, OutlierConfig cfg) {
        // 1. Hard sanity bounds first — these don't need a sample.
        if (!Double.isNaN(cfg.absoluteFloor()) && candidate < cfg.absoluteFloor()) {
            return new OutlierResult(true, Double.POSITIVE_INFINITY,
                    cfg.absoluteFloor(), cfg.absoluteCeiling(), Double.NaN,
                    sample == null ? 0 : sample.length,
                    "Below absolute floor " + cfg.absoluteFloor());
        }
        if (!Double.isNaN(cfg.absoluteCeiling()) && candidate > cfg.absoluteCeiling()) {
            return new OutlierResult(true, Double.POSITIVE_INFINITY,
                    cfg.absoluteFloor(), cfg.absoluteCeiling(), Double.NaN,
                    sample == null ? 0 : sample.length,
                    "Above absolute ceiling " + cfg.absoluteCeiling());
        }

        // 2. Need enough comparable data to judge statistically.
        if (sample == null || sample.length < cfg.minSampleSize()) {
            return OutlierResult.inconclusive(sample == null ? 0 : sample.length);
        }

        double[] sorted = sample.clone();
        Arrays.sort(sorted);
        double median = median(sorted);

        return switch (cfg.method()) {
            case MAD -> evaluateMad(candidate, sorted, median, cfg);
            case IQR -> evaluateIqr(candidate, sorted, median, cfg);
        };
    }

    private OutlierResult evaluateMad(double candidate, double[] sorted, double median, OutlierConfig cfg) {
        // MAD = median of |x_i - median|
        double[] absDev = new double[sorted.length];
        for (int i = 0; i < sorted.length; i++) {
            absDev[i] = Math.abs(sorted[i] - median);
        }
        Arrays.sort(absDev);
        double mad = median(absDev);

        double lower, upper, score;
        if (mad == 0.0) {
            // All comparable values identical; any deviation is suspicious.
            // Fall back to: outlier iff candidate != median.
            boolean differs = candidate != median;
            lower = median;
            upper = median;
            score = differs ? Double.POSITIVE_INFINITY : 0.0;
            return new OutlierResult(differs, score, lower, upper, median, sorted.length,
                    differs ? "Differs from a constant sample" : "Matches constant sample");
        }

        double modifiedZ = MAD_SCALE * (candidate - median) / mad; // signed
        double absZ = Math.abs(modifiedZ);
        double margin = cfg.threshold() * mad / MAD_SCALE; // invert to value units
        lower = median - margin;
        upper = median + margin;
        boolean outlier = absZ > cfg.threshold();
        String reason = outlier
                ? String.format("MAD z-score %.2f exceeds %.2f", modifiedZ, cfg.threshold())
                : String.format("Within MAD threshold (z=%.2f)", modifiedZ);
        return new OutlierResult(outlier, absZ, lower, upper, median, sorted.length, reason);
    }

    private OutlierResult evaluateIqr(double candidate, double[] sorted, double median, OutlierConfig cfg) {
        double q1 = percentile(sorted, 25);
        double q3 = percentile(sorted, 75);
        double iqr = q3 - q1;
        double lower = q1 - cfg.threshold() * iqr;
        double upper = q3 + cfg.threshold() * iqr;

        boolean outlier = candidate < lower || candidate > upper;
        // Score: how many IQRs past the nearest fence (0 if inside).
        double score = 0.0;
        if (iqr > 0) {
            if (candidate < lower) score = (lower - candidate) / iqr;
            else if (candidate > upper) score = (candidate - upper) / iqr;
        } else if (outlier) {
            score = Double.POSITIVE_INFINITY;
        }
        String reason = outlier
                ? String.format("Outside IQR fences [%.3f, %.3f]", lower, upper)
                : "Within IQR fences";
        return new OutlierResult(outlier, score, lower, upper, median, sorted.length, reason);
    }

    // --- helpers ---

    /** Median of an already-sorted array. */
    private double median(double[] sorted) {
        int n = sorted.length;
        if (n == 0) return Double.NaN;
        int mid = n / 2;
        return (n % 2 == 0) ? (sorted[mid - 1] + sorted[mid]) / 2.0 : sorted[mid];
    }

    /** Linear-interpolation percentile of an already-sorted array. */
    private double percentile(double[] sorted, double p) {
        int n = sorted.length;
        if (n == 1) return sorted[0];
        double rank = (p / 100.0) * (n - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) return sorted[lo];
        double frac = rank - lo;
        return sorted[lo] + frac * (sorted[hi] - sorted[lo]);
    }
}
