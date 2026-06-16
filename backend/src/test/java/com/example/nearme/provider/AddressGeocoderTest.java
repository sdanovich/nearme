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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressGeocoderTest {

    @Mock RedisCache cache;

    private AddressGeocoder geocoder() {
        return new AddressGeocoder(new ObjectMapper(), cache,
                "https://nominatim.example/reverse");
    }

    @Test
    void cachedAddressIsReturnedWithoutNetworkCall() {
        AddressGeocoder geocoder = geocoder();
        when(cache.get(anyString())).thenReturn(Optional.of("123 Main St, Mountain View"));

        Optional<String> result = geocoder.reverse(37.42, -122.08);

        assertThat(result).contains("123 Main St, Mountain View");
    }

    @Test
    void cachedMissSentinelResolvesToEmpty() throws Exception {
        AddressGeocoder geocoder = geocoder();
        // The "no usable address" sentinel is a private constant; read it via
        // reflection so the test stays correct whatever exact char it uses.
        java.lang.reflect.Field f = AddressGeocoder.class.getDeclaredField("MISS");
        f.setAccessible(true);
        String miss = (String) f.get(null);
        when(cache.get(anyString())).thenReturn(Optional.of(miss));

        Optional<String> result = geocoder.reverse(37.42, -122.08);

        assertThat(result).isEmpty();
    }
}
