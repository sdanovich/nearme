package com.example.nearme.provider;

import com.example.nearme.cache.RedisCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionResolverTest {

    @Mock RedisCache cache;

    @Test
    void cachedRegionIsReturnedWithoutNetworkLookup() {
        RegionResolver resolver = new RegionResolver(
                new ObjectMapper(), cache,
                "https://nominatim.example/reverse", "NUS");
        when(cache.get(anyString())).thenReturn(Optional.of("SFL"));

        String region = resolver.resolveDuoarea(27.9, -82.3);

        assertThat(region).isEqualTo("SFL");
        verify(cache).get(anyString());
        verify(cache, never()).put(anyString(), anyString(), any());
    }

    @Test
    void fallsBackToConfiguredRegionOnLookupFailure() {
        // A malformed endpoint makes URI building throw before any network I/O,
        // exercising the resilient fallback path deterministically (offline).
        RegionResolver resolver = new RegionResolver(
                new ObjectMapper(), cache,
                "http://bad endpoint with spaces", "NUS");
        when(cache.get(anyString())).thenReturn(Optional.empty());

        String region = resolver.resolveDuoarea(27.9, -82.3);

        assertThat(region).isEqualTo("NUS");
        verify(cache, never()).put(anyString(), anyString(), any());
    }
}
