package com.example.nearme.service;

import com.example.nearme.cache.RedisCache;
import com.example.nearme.model.Place;
import com.example.nearme.model.PlaceCategory;
import com.example.nearme.provider.AddressGeocoder;
import com.example.nearme.provider.OverpassPlaceProvider;
import com.example.nearme.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceDiscoveryServiceTest {

    @Mock OverpassPlaceProvider provider;
    @Mock PlaceRepository placeRepo;
    @Mock RedisCache cache;
    @Mock AddressGeocoder geocoder;

    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
    private PlaceDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new PlaceDiscoveryService(provider, placeRepo, cache, geocoder);
    }

    private Place gasPlace(long osmId, double lon, double lat) {
        Place p = new Place();
        p.setCategory(PlaceCategory.GAS);
        p.setOsmId(osmId);
        p.setName("Station " + osmId);
        p.setBrand("Brand");
        p.setAddress("addr " + osmId); // pre-filled so geocoder isn't consulted
        p.setCreatedAt(Instant.parse("2026-06-16T00:00:00Z"));
        Point pt = gf.createPoint(new Coordinate(lon, lat));
        p.setLocation(pt);
        return p;
    }

    @Test
    void recentlyDiscoveredAreaIsServedFromCache() {
        when(cache.exists(anyString())).thenReturn(true);

        service.discover(37.0, -122.0, PlaceCategory.GAS, 5000);

        verifyNoInteractions(provider, geocoder);
        verify(placeRepo, never()).insertIfAbsent(anyString(), anyLong(), any(), any(), any(),
                anyDouble(), anyDouble(), any(), any(), any());
        verify(cache, never()).put(anyString(), anyString(), any());
    }

    @Test
    void insertsOnlyNewOsmIdsAndMarksAreaDone() throws Exception {
        when(cache.exists(anyString())).thenReturn(false);
        Place fresh = gasPlace(100L, -122.0, 37.0);
        Place existing = gasPlace(200L, -122.1, 37.1);
        when(provider.fetch(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(List.of(fresh, existing));
        when(placeRepo.findExistingOsmIds(any())).thenReturn(List.of(200L));
        when(placeRepo.findMissingAddressNear(anyDouble(), anyDouble(), anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of());

        service.discover(37.0, -122.0, PlaceCategory.GAS, 5000);

        // only the new osmId is inserted, via the idempotent path
        verify(placeRepo, times(1)).insertIfAbsent(anyString(), anyLong(), any(), any(), any(),
                anyDouble(), anyDouble(), any(), any(), any());
        verify(placeRepo).insertIfAbsent(eq("GAS"), eq(100L), eq("Station 100"), eq("Brand"),
                eq("addr 100"), eq(-122.0), eq(37.0), eq(fresh.getCreatedAt()), any(), any());
        verify(placeRepo, never()).insertIfAbsent(anyString(), eq(200L), any(), any(), any(),
                anyDouble(), anyDouble(), any(), any(), any());
        verify(cache).put(anyString(), eq("1"), any(Duration.class));
    }

    @Test
    void duplicateOsmIdsInBatchAreDeduped() throws Exception {
        when(cache.exists(anyString())).thenReturn(false);
        when(provider.fetch(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(List.of(gasPlace(100L, -122.0, 37.0), gasPlace(100L, -122.0, 37.0)));
        when(placeRepo.findExistingOsmIds(any())).thenReturn(List.of());
        when(placeRepo.findMissingAddressNear(anyDouble(), anyDouble(), anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of());

        service.discover(37.0, -122.0, PlaceCategory.GAS, 5000);

        verify(placeRepo, times(1)).insertIfAbsent(anyString(), eq(100L), any(), any(), any(),
                anyDouble(), anyDouble(), any(), any(), any());
    }

    @Test
    void providerFailureIsSwallowedAndAreaNotMarkedDone() throws Exception {
        when(cache.exists(anyString())).thenReturn(false);
        when(provider.fetch(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenThrow(new RuntimeException("overpass 504"));

        assertThatCode(() -> service.discover(37.0, -122.0, PlaceCategory.GAS, 5000))
                .doesNotThrowAnyException();

        verify(placeRepo, never()).insertIfAbsent(anyString(), anyLong(), any(), any(), any(),
                anyDouble(), anyDouble(), any(), any(), any());
        verify(cache, never()).put(anyString(), anyString(), any());
    }
}
