package com.example.nearme.service;

import com.example.nearme.dto.CreateStationRequest;
import com.example.nearme.dto.NearbyStationResponse;
import com.example.nearme.dto.PricePoint;
import com.example.nearme.dto.PriceReportRequest;
import com.example.nearme.model.FuelType;
import com.example.nearme.model.Place;
import com.example.nearme.model.PlaceCategory;
import com.example.nearme.model.PriceReport;
import com.example.nearme.repository.PlaceRepository;
import com.example.nearme.repository.PriceReportRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class StationService {

    private final PlaceRepository placeRepo;
    private final PriceReportRepository reportRepo;
    private final GeometryFactory geometryFactory;
    private final com.example.nearme.outlier.OutlierGuard outlierGuard;
    private final PlaceDiscoveryService discoveryService;
    private final FuelPriceService fuelPriceService;

    public StationService(PlaceRepository placeRepo,
                          PriceReportRepository reportRepo,
                          GeometryFactory geometryFactory,
                          com.example.nearme.outlier.OutlierGuard outlierGuard,
                          PlaceDiscoveryService discoveryService,
                          FuelPriceService fuelPriceService) {
        this.placeRepo = placeRepo;
        this.reportRepo = reportRepo;
        this.geometryFactory = geometryFactory;
        this.outlierGuard = outlierGuard;
        this.discoveryService = discoveryService;
        this.fuelPriceService = fuelPriceService;
    }

    /** Nearest places of a category with latest non-stale price for the type. */
    public List<NearbyStationResponse> findNearby(double lat, double lon, PlaceCategory category,
                                                  String priceType, double radiusMeters,
                                                  int maxAgeHours, int limit) {
        // Discover real-world places from OSM and persist new ones first, so the
        // query below returns freshly-found services. Best-effort: never blocks
        // the response on a provider failure.
        discoveryService.discover(lat, lon, category, radiusMeters);

        // Gas stations have no price from OSM; seed nearby ones with the EIA
        // regional average (best-effort, only if a key is configured). User
        // reports override it by recency.
        if (category == PlaceCategory.GAS) {
            fuelPriceService.seedAverages(priceType, lat, lon, radiusMeters, maxAgeHours);
        }

        return placeRepo.findNearby(lon, lat, category.name(), radiusMeters, priceType, maxAgeHours, limit)
                .stream()
                .map(r -> new NearbyStationResponse(
                        r.getId(), r.getName(), r.getBrand(), r.getAddress(),
                        r.getLat(), r.getLon(), r.getDistanceM(),
                        r.getRating(), r.getOpeningHours(),
                        r.getPrice(), r.getPriceReportedAt()))
                .toList();
    }

    /** Price history for a place + price type, newest first. */
    public List<PricePoint> history(Long placeId, String priceType) {
        return reportRepo.history(placeId, priceType).stream()
                .map(r -> new PricePoint(r.getPrice(), r.getReportedAt()))
                .toList();
    }

    @Transactional
    public Place createStation(CreateStationRequest req) {
        Point p = geometryFactory.createPoint(new Coordinate(req.longitude(), req.latitude()));
        Place place = new Place();
        place.setCategory(req.category());
        place.setName(req.name());
        place.setBrand(req.brand());
        place.setAddress(req.address());
        place.setLocation(p);
        place.setRating(req.rating());
        place.setOpeningHours(req.openingHours());
        place.setCreatedAt(Instant.now());
        return placeRepo.save(place);
    }

    @Transactional
    public PriceReport reportPrice(PriceReportRequest req) {
        Place place = placeRepo.findById(req.stationId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown place: " + req.stationId()));

        // Outlier guard: gather recent comparable prices for this place + type,
        // and reject values that are impossible or wildly off. Gas can't be <= 0
        // or absurdly high; a relaxed MAD threshold tolerates normal price spread.
        String priceType = req.fuelType().name();
        java.util.List<java.math.BigDecimal> recent =
                reportRepo.recentPricesFor(req.stationId(), priceType, 24 * 14, 50);
        double[] sample = recent.stream().mapToDouble(java.math.BigDecimal::doubleValue).toArray();

        var cfg = com.example.nearme.outlier.OutlierConfig.defaults()
                .withThreshold(5.0)
                .withBounds(0.10, 20.0);
        var verdict = outlierGuard.check(req.price().doubleValue(), sample, cfg);

        // Hard-impossible (failed an absolute bound) -> reject outright.
        if (verdict.outlier() && Double.isInfinite(verdict.score())) {
            throw new IllegalArgumentException("Rejected implausible price: " + verdict.reason());
        }
        // Statistically suspicious but not impossible -> accept (could also flag).

        PriceReport report = new PriceReport();
        report.setPlace(place);
        report.setPriceType(priceType);
        report.setPrice(req.price());
        report.setReporterId(req.reporterId());
        report.setReportedAt(Instant.now());
        return reportRepo.save(report);
    }
}
