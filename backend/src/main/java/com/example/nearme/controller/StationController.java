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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StationController {

    private final StationService service;

    public StationController(StationService service) {
        this.service = service;
    }

    /**
     * Nearest places to a GPS position. Defaults to GAS / REGULAR so the
     * existing gas behavior is unchanged; category & priceType are generic so
     * the same endpoint serves coffee/restaurants later.
     * GET /api/stations/nearby?lat=&lon=&category=GAS&priceType=REGULAR
     */
    @GetMapping("/stations/nearby")
    public List<NearbyStationResponse> nearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "GAS") PlaceCategory category,
            @RequestParam(defaultValue = "REGULAR") String priceType,
            @RequestParam(defaultValue = "32000") double radiusMeters,
            @RequestParam(defaultValue = "48") int maxAgeHours,
            @RequestParam(defaultValue = "20") int limit) {
        return service.findNearby(lat, lon, category, priceType, radiusMeters, maxAgeHours, limit);
    }

    /** Price history for a place + price type (newest first). */
    @GetMapping("/stations/{id}/history")
    public List<PricePoint> history(
            @PathVariable Long id,
            @RequestParam(defaultValue = "REGULAR") String priceType) {
        return service.history(id, priceType);
    }

    /**
     * Create a place of any category. Returns the new id only — the Place entity
     * carries a JTS geometry that isn't JSON-serializable without extra modules.
     */
    @PostMapping("/stations")
    public Map<String, Long> create(@Valid @RequestBody CreateStationRequest req) {
        Place place = service.createStation(req);
        return Map.of("id", place.getId());
    }

    @PostMapping("/prices")
    public PriceReport report(@Valid @RequestBody PriceReportRequest req) {
        return service.reportPrice(req);
    }
}
