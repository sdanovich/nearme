package com.example.nearme.provider;

import com.example.nearme.cache.RedisCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Maps a coordinate to the EIA {@code duoarea} region used for fuel-price
 * averages, so a device in NC gets Lower-Atlantic prices while one in FL gets
 * Florida prices — instead of a single fixed region.
 *
 * Resolution: reverse-geocode lat/lon to a US state (OSM Nominatim), then map
 * the state to its EIA series — a state-level series where EIA publishes one
 * (CA, CO, FL, MA, MN, NY, OH, TX, WA), otherwise the state's PADD region.
 * Falls back to {@code nearme.eia.duoarea} (default NUS) outside the US or on
 * any lookup failure. Results are cached per area since geography is stable.
 */
@Component
public class RegionResolver {

    private static final Logger log = LoggerFactory.getLogger(RegionResolver.class);
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /** US state/DC -> EIA duoarea (state series if available, else PADD region). */
    private static final Map<String, String> STATE_TO_DUOAREA = Map.ofEntries(
            // State-level EIA series.
            Map.entry("CA", "SCA"), Map.entry("CO", "SCO"), Map.entry("FL", "SFL"),
            Map.entry("MA", "SMA"), Map.entry("MN", "SMN"), Map.entry("NY", "SNY"),
            Map.entry("OH", "SOH"), Map.entry("TX", "STX"), Map.entry("WA", "SWA"),
            // PADD 1A (New England) = R1X.
            Map.entry("CT", "R1X"), Map.entry("ME", "R1X"), Map.entry("NH", "R1X"),
            Map.entry("RI", "R1X"), Map.entry("VT", "R1X"),
            // PADD 1B (Central Atlantic) = R1Y.
            Map.entry("DE", "R1Y"), Map.entry("DC", "R1Y"), Map.entry("MD", "R1Y"),
            Map.entry("NJ", "R1Y"), Map.entry("PA", "R1Y"),
            // PADD 1C (Lower Atlantic) = R1Z.
            Map.entry("GA", "R1Z"), Map.entry("NC", "R1Z"), Map.entry("SC", "R1Z"),
            Map.entry("VA", "R1Z"), Map.entry("WV", "R1Z"),
            // PADD 2 (Midwest) = R20.
            Map.entry("IL", "R20"), Map.entry("IN", "R20"), Map.entry("IA", "R20"),
            Map.entry("KS", "R20"), Map.entry("KY", "R20"), Map.entry("MI", "R20"),
            Map.entry("MO", "R20"), Map.entry("NE", "R20"), Map.entry("ND", "R20"),
            Map.entry("SD", "R20"), Map.entry("OK", "R20"), Map.entry("TN", "R20"),
            Map.entry("WI", "R20"),
            // PADD 3 (Gulf Coast) = R30.
            Map.entry("AL", "R30"), Map.entry("AR", "R30"), Map.entry("LA", "R30"),
            Map.entry("MS", "R30"), Map.entry("NM", "R30"),
            // PADD 4 (Rocky Mountain) = R40.
            Map.entry("ID", "R40"), Map.entry("MT", "R40"), Map.entry("UT", "R40"),
            Map.entry("WY", "R40"),
            // PADD 5 (West Coast) = R50.
            Map.entry("AK", "R50"), Map.entry("AZ", "R50"), Map.entry("HI", "R50"),
            Map.entry("NV", "R50"), Map.entry("OR", "R50")
    );

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper;
    private final RedisCache cache;
    private final String nominatimEndpoint;
    private final String fallback;

    public RegionResolver(ObjectMapper mapper,
                          RedisCache cache,
                          @Value("${nearme.nominatim.endpoint:https://nominatim.openstreetmap.org/reverse}") String nominatimEndpoint,
                          @Value("${nearme.eia.duoarea:NUS}") String fallback) {
        this.mapper = mapper;
        this.cache = cache;
        this.nominatimEndpoint = nominatimEndpoint;
        this.fallback = fallback;
    }

    /** EIA duoarea for the coordinate, or the configured fallback. */
    public String resolveDuoarea(double lat, double lon) {
        // Bucket to ~1 decimal (~11 km): same metro shares a cached region.
        String key = "region:" + Math.round(lat * 10) + "," + Math.round(lon * 10);
        Optional<String> cached = cache.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        String region = fallback;
        try {
            String url = nominatimEndpoint
                    + "?format=jsonv2&zoom=5&addressdetails=1&lat=" + lat + "&lon=" + lon;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "NearMe/1.0 (fuel price region lookup)")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode addr = mapper.readTree(resp.body()).path("address");
                String country = addr.path("country_code").asText("");
                String iso = addr.path("ISO3166-2-lvl4").asText(""); // e.g. "US-NC"
                if ("us".equalsIgnoreCase(country) && iso.startsWith("US-")) {
                    String state = iso.substring(3);
                    region = STATE_TO_DUOAREA.getOrDefault(state, fallback);
                }
                cache.put(key, region, CACHE_TTL);
                log.info("Region for {},{} -> {} ({})", lat, lon, region, iso.isBlank() ? country : iso);
            } else {
                log.warn("Nominatim returned HTTP {} for {},{}", resp.statusCode(), lat, lon);
            }
        } catch (Exception e) {
            log.warn("Region resolution failed for {},{}: {}", lat, lon, e.toString());
        }
        return region;
    }
}
