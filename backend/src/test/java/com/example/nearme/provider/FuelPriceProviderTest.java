package com.example.nearme.provider;

import com.example.nearme.cache.RedisCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuelPriceProviderTest {

    @Mock RedisCache cache;

    private FuelPriceProvider withKey(String key) {
        return new FuelPriceProvider(new ObjectMapper(), cache, key,
                "https://api.eia.example/data/");
    }

    @Test
    void isEnabledReflectsApiKeyPresence() {
        assertThat(withKey("").isEnabled()).isFalse();
        assertThat(withKey("   ").isEnabled()).isFalse();
        assertThat(withKey(null).isEnabled()).isFalse();
        assertThat(withKey("secret").isEnabled()).isTrue();
    }

    @Test
    void disabledProviderReturnsEmptyWithoutCacheLookup() {
        FuelPriceProvider provider = withKey("");
        assertThat(provider.averagePrice("REGULAR", "SCA")).isEmpty();
        verifyNoInteractions(cache);
    }

    @Test
    void unsupportedGradeReturnsEmptyWithoutCacheLookup() {
        FuelPriceProvider provider = withKey("secret");
        assertThat(provider.averagePrice("PLUTONIUM", "SCA")).isEmpty();
        verifyNoInteractions(cache);
    }

    @Test
    void cachedPriceIsReturnedWithoutNetworkCall() {
        FuelPriceProvider provider = withKey("secret");
        // REGULAR -> product code EPMR; key shape is eiaprice:<product>:<duoarea>
        lenient().when(cache.get(anyString())).thenReturn(Optional.of("3.499"));

        Optional<java.math.BigDecimal> price = provider.averagePrice("REGULAR", "SCA");

        assertThat(price).isPresent();
        assertThat(price.get()).isEqualByComparingTo("3.499");
    }
}
