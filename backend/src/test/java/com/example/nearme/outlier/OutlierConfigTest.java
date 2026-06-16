package com.example.nearme.outlier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutlierConfigTest {

    @Test
    void defaultsAreMadWithSensibleValues() {
        OutlierConfig c = OutlierConfig.defaults();
        assertThat(c.method()).isEqualTo(OutlierConfig.Method.MAD);
        assertThat(c.threshold()).isEqualTo(3.5);
        assertThat(c.minSampleSize()).isEqualTo(4);
        assertThat(c.absoluteFloor()).isNaN();
        assertThat(c.absoluteCeiling()).isNaN();
    }

    @Test
    void withersReturnModifiedCopiesAndLeaveOriginalUntouched() {
        OutlierConfig base = OutlierConfig.defaults();

        OutlierConfig changed = base
                .withMethod(OutlierConfig.Method.IQR)
                .withThreshold(1.5)
                .withMinSampleSize(10)
                .withBounds(0.10, 20.0);

        assertThat(changed.method()).isEqualTo(OutlierConfig.Method.IQR);
        assertThat(changed.threshold()).isEqualTo(1.5);
        assertThat(changed.minSampleSize()).isEqualTo(10);
        assertThat(changed.absoluteFloor()).isEqualTo(0.10);
        assertThat(changed.absoluteCeiling()).isEqualTo(20.0);

        // original is immutable / unchanged
        assertThat(base).isEqualTo(OutlierConfig.defaults());
    }

    @Test
    void recordEqualityIsByValue() {
        assertThat(OutlierConfig.defaults()).isEqualTo(OutlierConfig.defaults());
        assertThat(OutlierConfig.defaults().withThreshold(5.0))
                .isEqualTo(OutlierConfig.defaults().withThreshold(5.0));
    }
}
