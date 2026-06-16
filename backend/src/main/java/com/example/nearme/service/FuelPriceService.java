package com.example.nearme.service;

import com.example.nearme.model.Place;
import com.example.nearme.model.PriceReport;
import com.example.nearme.provider.FuelPriceProvider;
import com.example.nearme.provider.RegionResolver;
import com.example.nearme.repository.PlaceRepository;
import com.example.nearme.repository.PriceReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Seeds discovered gas stations with an external average price (EIA) so they
 * aren't blank before anyone reports. Written as a normal price_report with a
 * distinct reporterId, so a newer crowdsourced report naturally takes over (the
 * nearby query always uses the most recent non-stale price).
 */
@Service
public class FuelPriceService {

    private static final Logger log = LoggerFactory.getLogger(FuelPriceService.class);
    /** Cap how many of the nearest stations we seed per request. */
    private static final int SEED_LIMIT = 60;
    static final String SOURCE = "eia-avg";

    private final FuelPriceProvider provider;
    private final RegionResolver regionResolver;
    private final PlaceRepository placeRepo;
    private final PriceReportRepository reportRepo;

    public FuelPriceService(FuelPriceProvider provider,
                            RegionResolver regionResolver,
                            PlaceRepository placeRepo,
                            PriceReportRepository reportRepo) {
        this.provider = provider;
        this.regionResolver = regionResolver;
        this.placeRepo = placeRepo;
        this.reportRepo = reportRepo;
    }

    /** Best-effort: fill in average prices for nearby gas stations lacking one. */
    @Transactional
    public void seedAverages(String grade, double lat, double lon, double radiusMeters, int maxAgeHours) {
        if (!provider.isEnabled()) return;

        String region = regionResolver.resolveDuoarea(lat, lon);
        Optional<BigDecimal> avg = provider.averagePrice(grade, region);
        if (avg.isEmpty()) return;

        List<Long> ids = placeRepo.findGasIdsMissingRecentPrice(
                lon, lat, radiusMeters, grade, maxAgeHours, SEED_LIMIT);
        if (ids.isEmpty()) return;

        BigDecimal price = avg.get();
        Instant now = Instant.now();
        List<PriceReport> reports = placeRepo.findAllById(ids).stream().map(place -> {
            PriceReport r = new PriceReport();
            r.setPlace(place);
            r.setPriceType(grade);
            r.setPrice(price);
            r.setReporterId(SOURCE);
            r.setReportedAt(now);
            return r;
        }).toList();

        reportRepo.saveAll(reports);
        log.info("Seeded EIA {} {} average {}/gal onto {} gas stations", region, grade, price, reports.size());
    }
}
