package com.example.nearme.service;

import com.example.nearme.model.Place;
import com.example.nearme.model.PriceReport;
import com.example.nearme.provider.FuelPriceProvider;
import com.example.nearme.provider.RegionResolver;
import com.example.nearme.repository.PlaceRepository;
import com.example.nearme.repository.PriceReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuelPriceServiceTest {

    @Mock FuelPriceProvider provider;
    @Mock RegionResolver regionResolver;
    @Mock PlaceRepository placeRepo;
    @Mock PriceReportRepository reportRepo;

    private FuelPriceService service;

    @BeforeEach
    void setUp() {
        service = new FuelPriceService(provider, regionResolver, placeRepo, reportRepo);
    }

    @Test
    void disabledProviderShortCircuits() {
        when(provider.isEnabled()).thenReturn(false);

        service.seedAverages("REGULAR", 37.0, -122.0, 5000, 48);

        verifyNoInteractions(regionResolver, reportRepo);
        verify(provider, never()).averagePrice(anyString(), anyString());
    }

    @Test
    void noAveragePriceMeansNoSeeding() {
        when(provider.isEnabled()).thenReturn(true);
        when(regionResolver.resolveDuoarea(37.0, -122.0)).thenReturn("SCA");
        when(provider.averagePrice("REGULAR", "SCA")).thenReturn(Optional.empty());

        service.seedAverages("REGULAR", 37.0, -122.0, 5000, 48);

        verify(placeRepo, never())
                .findGasIdsMissingRecentPrice(anyDouble(), anyDouble(), anyDouble(), anyString(), anyInt(), anyInt());
        verify(reportRepo, never()).saveAll(any());
    }

    @Test
    void noCandidateStationsMeansNoSeeding() {
        when(provider.isEnabled()).thenReturn(true);
        when(regionResolver.resolveDuoarea(37.0, -122.0)).thenReturn("SCA");
        when(provider.averagePrice("REGULAR", "SCA")).thenReturn(Optional.of(new BigDecimal("3.50")));
        when(placeRepo.findGasIdsMissingRecentPrice(anyDouble(), anyDouble(), anyDouble(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.seedAverages("REGULAR", 37.0, -122.0, 5000, 48);

        verify(reportRepo, never()).saveAll(any());
    }

    @Test
    void seedsEveryCandidateWithSourceTaggedReport() {
        when(provider.isEnabled()).thenReturn(true);
        when(regionResolver.resolveDuoarea(37.0, -122.0)).thenReturn("SCA");
        when(provider.averagePrice("REGULAR", "SCA")).thenReturn(Optional.of(new BigDecimal("3.50")));
        when(placeRepo.findGasIdsMissingRecentPrice(-122.0, 37.0, 5000, "REGULAR", 48, 60))
                .thenReturn(List.of(1L, 2L));
        Place p1 = new Place();
        Place p2 = new Place();
        when(placeRepo.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));

        service.seedAverages("REGULAR", 37.0, -122.0, 5000, 48);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PriceReport>> cap = ArgumentCaptor.forClass(List.class);
        verify(reportRepo).saveAll(cap.capture());
        List<PriceReport> reports = cap.getValue();
        assertThat(reports).hasSize(2);
        assertThat(reports).allSatisfy(r -> {
            assertThat(r.getPrice()).isEqualByComparingTo("3.50");
            assertThat(r.getPriceType()).isEqualTo("REGULAR");
            assertThat(r.getReporterId()).isEqualTo(FuelPriceService.SOURCE);
            assertThat(r.getReportedAt()).isNotNull();
            assertThat(r.getPlace()).isNotNull();
        });
        assertThat(reports).extracting(PriceReport::getPlace).containsExactly(p1, p2);
    }

    @Test
    void usesResolvedRegionAndGradeWhenQueryingProvider() {
        when(provider.isEnabled()).thenReturn(true);
        when(regionResolver.resolveDuoarea(40.7, -74.0)).thenReturn("SNY");
        when(provider.averagePrice("DIESEL", "SNY")).thenReturn(Optional.empty());

        service.seedAverages("DIESEL", 40.7, -74.0, 8000, 24);

        verify(regionResolver).resolveDuoarea(40.7, -74.0);
        verify(provider).averagePrice("DIESEL", "SNY");
    }
}
