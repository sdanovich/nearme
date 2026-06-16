package com.example.nearme.outlier;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pure-math unit tests for the domain-agnostic outlier detector. No mocks —
 * the detector has no collaborators; it's all arithmetic.
 */
class OutlierDetectorTest {

    private final OutlierDetector detector = new OutlierDetector();

    @Nested
    class InsufficientData {

        @Test
        void nullSampleIsInconclusive() {
            OutlierResult r = detector.evaluate(3.0, null, OutlierConfig.defaults());
            assertThat(r.outlier()).isFalse();
            assertThat(r.sampleSize()).isZero();
            assertThat(r.score()).isZero();
            assertThat(r.reason()).contains("Not enough comparable data");
            assertThat(r.center()).isNaN();
        }

        @Test
        void sampleSmallerThanMinIsInconclusive() {
            // default minSampleSize is 4
            double[] sample = {3.0, 3.1, 3.2};
            OutlierResult r = detector.evaluate(3.1, sample, OutlierConfig.defaults());
            assertThat(r.outlier()).isFalse();
            assertThat(r.sampleSize()).isEqualTo(3);
            assertThat(r.reason()).contains("Not enough comparable data");
        }

        @Test
        void sampleExactlyAtMinIsJudged() {
            double[] sample = {3.0, 3.1, 3.2, 3.0};
            OutlierResult r = detector.evaluate(3.1, sample, OutlierConfig.defaults());
            // judged (not the inconclusive sentinel)
            assertThat(r.reason()).doesNotContain("Not enough comparable data");
            assertThat(r.sampleSize()).isEqualTo(4);
        }
    }

    @Nested
    class AbsoluteBounds {

        @Test
        void belowFloorIsRejectedWithInfiniteScore() {
            OutlierConfig cfg = OutlierConfig.defaults().withBounds(0.10, 20.0);
            OutlierResult r = detector.evaluate(0.0, new double[]{3, 3, 3, 3}, cfg);
            assertThat(r.outlier()).isTrue();
            assertThat(r.score()).isInfinite();
            assertThat(r.reason()).contains("Below absolute floor");
        }

        @Test
        void aboveCeilingIsRejectedWithInfiniteScore() {
            OutlierConfig cfg = OutlierConfig.defaults().withBounds(0.10, 20.0);
            OutlierResult r = detector.evaluate(999.0, new double[]{3, 3, 3, 3}, cfg);
            assertThat(r.outlier()).isTrue();
            assertThat(r.score()).isInfinite();
            assertThat(r.reason()).contains("Above absolute ceiling");
        }

        @Test
        void boundsCheckedBeforeSampleSize() {
            // No sample at all, but the hard floor still fires.
            OutlierConfig cfg = OutlierConfig.defaults().withBounds(0.10, 20.0);
            OutlierResult r = detector.evaluate(-5.0, null, cfg);
            assertThat(r.outlier()).isTrue();
            assertThat(r.score()).isInfinite();
        }

        @Test
        void valueWithinBoundsFallsThroughToStatistics() {
            OutlierConfig cfg = OutlierConfig.defaults().withBounds(0.10, 20.0);
            OutlierResult r = detector.evaluate(3.1, new double[]{3.0, 3.1, 3.2, 3.0, 3.1}, cfg);
            assertThat(r.outlier()).isFalse();
            assertThat(r.score()).isFinite();
        }
    }

    @Nested
    class MadMethod {

        @Test
        void typicalValueIsNotOutlier() {
            double[] sample = {3.00, 3.10, 3.20, 3.00, 3.10};
            OutlierResult r = detector.evaluate(3.10, sample, OutlierConfig.defaults());
            assertThat(r.outlier()).isFalse();
            assertThat(r.center()).isCloseTo(3.10, within(1e-9));
            assertThat(r.score()).isLessThan(OutlierConfig.defaults().threshold());
            assertThat(r.lowerBound()).isLessThan(3.10);
            assertThat(r.upperBound()).isGreaterThan(3.10);
        }

        @Test
        void extremeValueIsOutlier() {
            double[] sample = {3.00, 3.10, 3.20, 3.00, 3.10};
            OutlierResult r = detector.evaluate(10.0, sample, OutlierConfig.defaults());
            assertThat(r.outlier()).isTrue();
            assertThat(r.score()).isGreaterThan(OutlierConfig.defaults().threshold());
            assertThat(r.center()).isCloseTo(3.10, within(1e-9));
            assertThat(r.upperBound()).isLessThan(10.0);
            assertThat(r.reason()).contains("MAD");
        }

        @Test
        void constantSampleMatchingCandidateIsNotOutlier() {
            double[] sample = {5.0, 5.0, 5.0, 5.0};
            OutlierResult r = detector.evaluate(5.0, sample, OutlierConfig.defaults());
            assertThat(r.outlier()).isFalse();
            assertThat(r.score()).isZero();
            assertThat(r.center()).isCloseTo(5.0, within(1e-9));
            assertThat(r.reason()).contains("constant sample");
        }

        @Test
        void constantSampleDifferingCandidateIsOutlier() {
            double[] sample = {5.0, 5.0, 5.0, 5.0};
            OutlierResult r = detector.evaluate(6.0, sample, OutlierConfig.defaults());
            assertThat(r.outlier()).isTrue();
            assertThat(r.score()).isInfinite();
            assertThat(r.lowerBound()).isCloseTo(5.0, within(1e-9));
            assertThat(r.upperBound()).isCloseTo(5.0, within(1e-9));
        }

        @Test
        void higherThresholdToleratesMoreSpread() {
            double[] sample = {3.00, 3.10, 3.20, 3.00, 3.10};
            double candidate = 3.6;
            OutlierResult strict = detector.evaluate(candidate, sample, OutlierConfig.defaults().withThreshold(3.5));
            OutlierResult loose = detector.evaluate(candidate, sample, OutlierConfig.defaults().withThreshold(50.0));
            assertThat(strict.outlier()).isTrue();
            assertThat(loose.outlier()).isFalse();
        }
    }

    @Nested
    class IqrMethod {

        private OutlierConfig iqr() {
            return OutlierConfig.defaults().withMethod(OutlierConfig.Method.IQR).withThreshold(1.5);
        }

        @Test
        void valueInsideFencesIsNotOutlier() {
            double[] sample = {1, 2, 3, 4, 5, 6, 7, 8};
            OutlierResult r = detector.evaluate(5.0, sample, iqr());
            assertThat(r.outlier()).isFalse();
            assertThat(r.score()).isZero();
            assertThat(r.reason()).contains("Within IQR");
        }

        @Test
        void valueOutsideFencesIsOutlier() {
            double[] sample = {1, 2, 3, 4, 5, 6, 7, 8};
            OutlierResult r = detector.evaluate(100.0, sample, iqr());
            assertThat(r.outlier()).isTrue();
            assertThat(r.score()).isGreaterThan(0.0);
            assertThat(r.upperBound()).isLessThan(100.0);
            assertThat(r.reason()).contains("Outside IQR");
        }

        @Test
        void constantSampleViaIqrFlagsAnyDeviation() {
            double[] sample = {5, 5, 5, 5};
            OutlierResult r = detector.evaluate(6.0, sample, iqr());
            assertThat(r.outlier()).isTrue();
            assertThat(r.score()).isInfinite();
        }
    }
}
