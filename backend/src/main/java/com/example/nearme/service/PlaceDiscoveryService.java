package com.example.nearme.service;

import com.example.nearme.cache.RedisCache;
import com.example.nearme.model.Place;
import com.example.nearme.model.PlaceCategory;
import com.example.nearme.provider.AddressGeocoder;
import com.example.nearme.provider.OverpassPlaceProvider;
import com.example.nearme.repository.PlaceRepository;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Populates the DB from OpenStreetMap on demand. When the UI asks for nearby
 * places, we fetch the area from Overpass and INSERT anything new (keyed by
 * osmId), so subsequent PostGIS queries surface freshly-discovered services.
 *
 * Discovery is best-effort: if Overpass is slow or down, we log and return so
 * the request still serves whatever is already cached in Postgres.
 *
 * To avoid hammering Overpass on the app's periodic auto-refresh, each
 * (category, ~area, radius) is only re-fetched after a short TTL.
 */
@Service
public class PlaceDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(PlaceDiscoveryService.class);
    private static final Duration TTL = Duration.ofMinutes(10);

    /**
     * Cap the radius we hand to Overpass. A full 20-mile query for dense
     * categories (cafes/restaurants) overwhelms the public Overpass servers and
     * returns 504s. The DB query still uses the caller's full radius, so results
     * out to 20 mi are returned — coverage just fills in over repeated requests
     * as the user moves. In sparse areas this cap makes no practical difference.
     */
    private static final double MAX_DISCOVERY_RADIUS_M = 10_000;

    /**
     * Cap reverse-geocode lookups per discovery run. Each network lookup is
     * throttled to ~1/s (Nominatim policy), so this bounds how long discovery can
     * spend filling addresses; the rest fill in on later requests as the TTL for
     * this area expires. Cached lookups are cheap but still count toward the cap.
     */
    private static final int MAX_GEOCODE_PER_RUN = 10;

    private final OverpassPlaceProvider provider;
    private final PlaceRepository placeRepo;
    private final RedisCache cache;
    private final AddressGeocoder geocoder;

    public PlaceDiscoveryService(OverpassPlaceProvider provider, PlaceRepository placeRepo,
                                 RedisCache cache, AddressGeocoder geocoder) {
        this.provider = provider;
        this.placeRepo = placeRepo;
        this.cache = cache;
        this.geocoder = geocoder;
    }

    /** Discover + persist new places for the area. Never throws to the caller. */
    @Transactional
    public void discover(double lat, double lon, PlaceCategory category, double radiusMeters) {
        double fetchRadius = Math.min(radiusMeters, MAX_DISCOVERY_RADIUS_M);
        String key = throttleKey(lat, lon, category, fetchRadius);
        if (cache.exists(key)) {
            return; // recently refreshed this area — serve from DB
        }

        try {
            List<Place> found = provider.fetch(lat, lon, category, fetchRadius);

            // Dedupe within the batch, then drop ones we already store.
            Map<Long, Place> byOsmId = new LinkedHashMap<>();
            for (Place p : found) byOsmId.putIfAbsent(p.getOsmId(), p);

            if (!byOsmId.isEmpty()) {
                Set<Long> existing = new HashSet<>(placeRepo.findExistingOsmIds(byOsmId.keySet()));
                List<Place> toInsert = byOsmId.values().stream()
                        .filter(p -> !existing.contains(p.getOsmId()))
                        .toList();

                // Reverse-geocode any address-less new places before insert.
                int budget = fillAddresses(toInsert, MAX_GEOCODE_PER_RUN);
                for (Place p : toInsert) {
                    Point loc = p.getLocation();
                    // JTS Point is (x=lon, y=lat). ON CONFLICT keeps a concurrent
                    // run's duplicate osmId from failing this transaction.
                    placeRepo.insertIfAbsent(
                            p.getCategory().name(), p.getOsmId(), p.getName(), p.getBrand(),
                            p.getAddress(), loc.getX(), loc.getY(), p.getCreatedAt(),
                            p.getRating(), p.getOpeningHours());
                }
                log.info("Overpass {}: found {}, inserted {} new near {},{} r={}m",
                        category, byOsmId.size(), toInsert.size(), lat, lon, (long) fetchRadius);

                // Spend any leftover budget backfilling addresses on places we
                // already stored without one, so coverage converges over time.
                if (budget > 0) {
                    List<Place> stale = placeRepo.findMissingAddressNear(
                            lon, lat, category.name(), fetchRadius, budget);
                    fillAddresses(stale, budget);
                    long filled = stale.stream().filter(p -> p.getAddress() != null).count();
                    if (filled > 0) {
                        placeRepo.saveAll(stale);
                        log.info("Backfilled {} address(es) for stored {} near {},{}",
                                filled, category, lat, lon);
                    }
                }
            }
            cache.put(key, "1", TTL);
        } catch (Exception e) {
            // Best-effort: do not fail the nearby request if discovery fails.
            log.warn("Overpass discovery failed for {} near {},{}: {}", category, lat, lon, e.toString());
        }
    }

    /**
     * Reverse-geocode an address onto any address-less place, up to {@code budget}
     * attempts. Returns the remaining budget. Each place with no address consumes
     * one attempt whether or not a lookup succeeds (cache hits included), bounding
     * the throttled work per run.
     */
    private int fillAddresses(List<Place> places, int budget) {
        for (Place p : places) {
            if (budget <= 0) break;
            if (p.getAddress() != null) continue;
            Point loc = p.getLocation();
            if (loc == null) continue;
            budget--;
            // JTS Point is (x=lon, y=lat).
            geocoder.reverse(loc.getY(), loc.getX()).ifPresent(p::setAddress);
        }
        return budget;
    }

    /** Bucket coordinates to ~100 m so near-identical requests share a TTL entry. */
    private static String throttleKey(double lat, double lon, PlaceCategory category, double radiusMeters) {
        long latB = Math.round(lat * 1000);
        long lonB = Math.round(lon * 1000);
        return "discover:" + category + ":" + latB + ":" + lonB + ":" + (long) radiusMeters;
    }
}
