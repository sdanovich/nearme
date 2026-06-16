package com.example.nearme.controller;

import com.example.nearme.dto.CreateStationRequest;
import com.example.nearme.dto.NearbyStationResponse;
import com.example.nearme.dto.PricePoint;
import com.example.nearme.dto.PriceReportRequest;
import com.example.nearme.model.FuelType;
import com.example.nearme.model.Place;
import com.example.nearme.model.PlaceCategory;
import com.example.nearme.model.PriceReport;
import com.example.nearme.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StationControllerTest {

    @Mock StationService service;

    private StationController controller;

    @BeforeEach
    void setUp() {
        controller = new StationController(service);
    }

    @Test
    void nearbyDelegatesAllParamsToService() {
        List<NearbyStationResponse> expected = List.of();
        when(service.findNearby(37.42, -122.08, PlaceCategory.GAS, "REGULAR", 5000, 48, 20))
                .thenReturn(expected);

        List<NearbyStationResponse> out =
                controller.nearby(37.42, -122.08, PlaceCategory.GAS, "REGULAR", 5000, 48, 20);

        assertThat(out).isSameAs(expected);
        verify(service).findNearby(37.42, -122.08, PlaceCategory.GAS, "REGULAR", 5000, 48, 20);
    }

    @Test
    void historyDelegatesToService() {
        List<PricePoint> expected = List.of(new PricePoint(new BigDecimal("3.50"), null));
        when(service.history(7L, "PREMIUM")).thenReturn(expected);

        assertThat(controller.history(7L, "PREMIUM")).isSameAs(expected);
    }

    @Test
    void createReturnsOnlyTheNewId() {
        CreateStationRequest req = new CreateStationRequest(
                "Shell", null, null, 37.0, -122.0, PlaceCategory.GAS, null, null);
        Place place = mock(Place.class);
        when(place.getId()).thenReturn(123L);
        when(service.createStation(req)).thenReturn(place);

        Map<String, Long> body = controller.create(req);

        assertThat(body).containsExactly(Map.entry("id", 123L));
    }

    @Test
    void reportDelegatesToService() {
        PriceReportRequest req = new PriceReportRequest(
                1L, FuelType.REGULAR, new BigDecimal("3.79"), "user-1");
        PriceReport saved = new PriceReport();
        when(service.reportPrice(req)).thenReturn(saved);

        assertThat(controller.report(req)).isSameAs(saved);
    }
}
