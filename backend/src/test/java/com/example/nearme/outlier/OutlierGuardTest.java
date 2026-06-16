package com.example.nearme.outlier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The guard is a thin facade — these tests verify it delegates to the detector
 * and supplies default config for the convenience overload.
 */
@ExtendWith(MockitoExtension.class)
class OutlierGuardTest {

    @Mock
    OutlierDetector detector;

    @Test
    void customConfigOverloadDelegatesVerbatim() {
        OutlierGuard guard = new OutlierGuard(detector);
        double[] sample = {1, 2, 3};
        OutlierConfig cfg = OutlierConfig.defaults().withThreshold(9.0);
        OutlierResult expected = OutlierResult.inconclusive(3);
        when(detector.evaluate(eq(2.0), same(sample), same(cfg))).thenReturn(expected);

        OutlierResult actual = guard.check(2.0, sample, cfg);

        assertThat(actual).isSameAs(expected);
        verify(detector).evaluate(2.0, sample, cfg);
    }

    @Test
    void defaultOverloadSuppliesDefaultConfig() {
        OutlierGuard guard = new OutlierGuard(detector);
        double[] sample = {1, 2, 3};
        when(detector.evaluate(eq(2.0), same(sample), eq(OutlierConfig.defaults())))
                .thenReturn(OutlierResult.inconclusive(3));

        guard.check(2.0, sample);

        ArgumentCaptor<OutlierConfig> cfg = ArgumentCaptor.forClass(OutlierConfig.class);
        verify(detector).evaluate(eq(2.0), same(sample), cfg.capture());
        assertThat(cfg.getValue()).isEqualTo(OutlierConfig.defaults());
    }
}
