package com.example.nearme.service;

import com.example.nearme.dto.CreateStationRequest;
import com.example.nearme.dto.NearbyStationResponse;
import com.example.nearme.dto.PricePoint;
import com.example.nearme.dto.PriceReportRequest;
import com.example.nearme.model.FuelType;
import com.example.nearme.model.Place;
import com.example.nearme.model.PlaceCategory;
import com.example.nearme.model.PriceReport;
import com.example.nearme.outlier.OutlierConfig;
import com.example.nearme.outlier.OutlierGuard;
import com.example.nearme.outlier.OutlierResult;
import com.example.nearme.repository.PlaceRepository;
import com.example.nearme.repository.PriceReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StationServiceTest {

    @Mock PlaceRepository placeRepo;
    @Mock PriceReportRepository reportRepo;
    @Mock OutlierGuard outlierGuard;
    @Mock PlaceDiscoveryService discoveryService;
    @Mock FuelPriceService fuelPriceService;

    // Real geometry factory (matches GeometryConfig) — no value in mocking it.
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    private StationService service;

    @BeforeEach
    void setUp() {
        service = new StationService(placeRepo, reportRepo, geometryFactory,
                outlierGuard, discoveryService, fuelPriceService);
    }

    @Test
    void findNearbyForGasDiscoversSeedsAndMapsRows() {
        PlaceRepository.NearbyPlaceRow row = mock(PlaceRepository.NearbyPlaceRow.class);
        when(row.getId()).thenReturn(42L);
        when(row.getName()).thenReturn("Valero");
        when(row.getBrand()).thenReturn("Valero");
        when(row.getAddress()).thenReturn("1 Main St");
        when(row.getLat()).thenReturn(37.42);
        when(row.getLon()).thenReturn(-122.08);
        when(row.getDistanceM()).thenReturn(123.4);
        when(row.getRating()).thenReturn(null);
        when(row.getOpeningHours()).thenReturn(null);
        when(row.getPrice()).thenReturn(new BigDecimal("3.99"));
        Instant when = Instant.parse("2026-06-16T00:00:00Z");
        when(row.getPriceReportedAt()).thenReturn(when);

        when(placeRepo.findNearby(anyDouble(), anyDouble(), anyString(), anyDouble(),
                anyString(), anyInt(), anyInt())).thenReturn(List.of(row));

        List<NearbyStationResponse> out = service.findNearby(
                37.42, -122.08, PlaceCategory.GAS, "REGULAR", 5000, 48, 20);

        assertThat(out).hasSize(1);
        NearbyStationResponse r = out.get(0);
        assertThat(r.stationId()).isEqualTo(42L);
        assertThat(r.name()).isEqualTo("Valero");
        assertThat(r.latitude()).isCloseTo(37.42, within(1e-9));
        assertThat(r.longitude()).isCloseTo(-122.08, within(1e-9));
        assertThat(r.distanceMeters()).isCloseTo(123.4, within(1e-9));
        assertThat(r.price()).isEqualByComparingTo("3.99");
        assertThat(r.priceReportedAt()).isEqualTo(when);

        // discovery always runs first; GAS also seeds averages
        verify(discoveryService).discover(37.42, -122.08, PlaceCategory.GAS, 5000);
        verify(fuelPriceService).seedAverages("REGULAR", 37.42, -122.08, 5000, 48);
        // repository called with (lon, lat, category, ...) order
        verify(placeRepo).findNearby(-122.08, 37.42, "GAS", 5000, "REGULAR", 48, 20);
    }

    @Test
    void findNearbyForNonGasDoesNotSeedPrices() {
        when(placeRepo.findNearby(anyDouble(), anyDouble(), anyString(), anyDouble(),
                anyString(), anyInt(), anyInt())).thenReturn(List.of());

        service.findNearby(37.42, -122.08, PlaceCategory.COFFEE, "REGULAR", 5000, 48, 20);

        verify(discoveryService).discover(37.42, -122.08, PlaceCategory.COFFEE, 5000);
        verify(fuelPriceService, never())
                .seedAverages(anyString(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void historyMapsReportsToPoints() {
        PriceReport rep = new PriceReport();
        rep.setPrice(new BigDecimal("3.50"));
        Instant when = Instant.parse("2026-06-15T12:00:00Z");
        rep.setReportedAt(when);
        when(reportRepo.history(7L, "REGULAR")).thenReturn(List.of(rep));

        List<PricePoint> points = service.history(7L, "REGULAR");

        assertThat(points).hasSize(1);
        assertThat(points.get(0).price()).isEqualByComparingTo("3.50");
        assertThat(points.get(0).reportedAt()).isEqualTo(when);
    }

    @Test
    void createStationBuildsPlaceWithGeometryAndSaves() {
        CreateStationRequest req = new CreateStationRequest(
                "Shell", "Shell", "5 Oak Ave", 37.0, -122.0,
                PlaceCategory.GAS, 4.5, "24/7");
        when(placeRepo.save(any(Place.class))).thenAnswer(i -> i.getArgument(0));

        service.createStation(req);

        ArgumentCaptor<Place> cap = ArgumentCaptor.forClass(Place.class);
        verify(placeRepo).save(cap.capture());
        Place saved = cap.getValue();
        assertThat(saved.getCategory()).isEqualTo(PlaceCategory.GAS);
        assertThat(saved.getName()).isEqualTo("Shell");
        assertThat(saved.getBrand()).isEqualTo("Shell");
        assertThat(saved.getAddress()).isEqualTo("5 Oak Ave");
        assertThat(saved.getRating()).isEqualTo(4.5);
        assertThat(saved.getOpeningHours()).isEqualTo("24/7");
        assertThat(saved.getCreatedAt()).isNotNull();
        // JTS point is (x=lon, y=lat)
        assertThat(saved.getLocation().getX()).isCloseTo(-122.0, within(1e-9));
        assertThat(saved.getLocation().getY()).isCloseTo(37.0, within(1e-9));
    }

    @Test
    void reportPriceUnknownPlaceThrows() {
        PriceReportRequest req = new PriceReportRequest(
                99L, FuelType.REGULAR, new BigDecimal("3.50"), "user-1");
        when(placeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reportPrice(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown place: 99");
        verify(reportRepo, never()).save(any());
    }

    @Test
    void reportPriceRejectsImpossibleValue() {
        PriceReportRequest req = new PriceReportRequest(
                1L, FuelType.REGULAR, new BigDecimal("0.01"), "user-1");
        when(placeRepo.findById(1L)).thenReturn(Optional.of(new Place()));
        when(reportRepo.recentPricesFor(eq(1L), eq("REGULAR"), eq(24 * 14), eq(50)))
                .thenReturn(List.of());
        // failed an absolute bound -> outlier with infinite score
        OutlierResult impossible = new OutlierResult(true, Double.POSITIVE_INFINITY,
                Double.NaN, Double.NaN, Double.NaN, 0, "Below absolute floor 0.1");
        when(outlierGuard.check(anyDouble(), any(double[].class), any(OutlierConfig.class)))
                .thenReturn(impossible);

        assertThatThrownBy(() -> service.reportPrice(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rejected implausible price");
        verify(reportRepo, never()).save(any());
    }

    @Test
    void reportPriceAcceptsPlausibleValueAndSavesWithGuardConfig() {
        Place place = new Place();
        PriceReportRequest req = new PriceReportRequest(
                1L, FuelType.REGULAR, new BigDecimal("3.79"), "user-1");
        when(placeRepo.findById(1L)).thenReturn(Optional.of(place));
        when(reportRepo.recentPricesFor(eq(1L), eq("REGULAR"), eq(24 * 14), eq(50)))
                .thenReturn(List.of(new BigDecimal("3.70"), new BigDecimal("3.80")));
        OutlierResult ok = new OutlierResult(false, 0.5, 3.0, 4.5, 3.75, 2, "within");
        when(outlierGuard.check(anyDouble(), any(double[].class), any(OutlierConfig.class)))
                .thenReturn(ok);
        when(reportRepo.save(any(PriceReport.class))).thenAnswer(i -> i.getArgument(0));

        service.reportPrice(req);

        // guard invoked with the relaxed threshold + hard bounds the service sets
        ArgumentCaptor<OutlierConfig> cfg = ArgumentCaptor.forClass(OutlierConfig.class);
        ArgumentCaptor<double[]> sample = ArgumentCaptor.forClass(double[].class);
        verify(outlierGuard).check(eq(3.79), sample.capture(), cfg.capture());
        assertThat(cfg.getValue().threshold()).isEqualTo(5.0);
        assertThat(cfg.getValue().absoluteFloor()).isEqualTo(0.10);
        assertThat(cfg.getValue().absoluteCeiling()).isEqualTo(20.0);
        assertThat(sample.getValue()).containsExactly(3.70, 3.80);

        ArgumentCaptor<PriceReport> rep = ArgumentCaptor.forClass(PriceReport.class);
        verify(reportRepo).save(rep.capture());
        PriceReport saved = rep.getValue();
        assertThat(saved.getPlace()).isSameAs(place);
        assertThat(saved.getPriceType()).isEqualTo("REGULAR");
        assertThat(saved.getPrice()).isEqualByComparingTo("3.79");
        assertThat(saved.getReporterId()).isEqualTo("user-1");
        assertThat(saved.getReportedAt()).isNotNull();
    }
}
