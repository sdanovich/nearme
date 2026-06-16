package com.example.nearme.outlier;

/**
 * A generic, reusable outlier-check facade. Any domain calls check() with the
 * candidate value and the comparable sample it gathered (however it gathers it),
 * and gets a verdict. This is the one method most callers need.
 *
 * The detector is the math; this is the convenience entry point so domains don't
 * each re-implement the "fetch sample -> evaluate" shape. Fetching the sample is
 * inherently domain-specific (a SQL query over the right grouping), so it stays
 * in the domain; everything after it is shared here.
 */
import org.springframework.stereotype.Service;

@Service
public class OutlierGuard {

    private final OutlierDetector detector;

    public OutlierGuard(OutlierDetector detector) {
        this.detector = detector;
    }

    /** Judge a candidate against a pre-gathered sample, with custom config. */
    public OutlierResult check(double candidate, double[] sample, OutlierConfig config) {
        return detector.evaluate(candidate, sample, config);
    }

    /** Judge with default config. */
    public OutlierResult check(double candidate, double[] sample) {
        return detector.evaluate(candidate, sample, OutlierConfig.defaults());
    }
}
