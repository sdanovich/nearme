package com.example.nearme.provider;

import com.example.nearme.model.Place;
import com.example.nearme.model.PlaceCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers real-world places from OpenStreetMap via the Overpass API.
 *
 * Overpass is free and keyless. We query nodes + ways tagged for the requested
 * category within a radius, and map them to (unsaved) Place entities keyed by a
 * stable osmId so the caller can dedupe/insert only what's new. OSM has no
 * ratings, so rating is always null here; opening_hours is mapped when present.
 */
@Component
public class OverpassPlaceProvider {

    private static final Logger log = LoggerFactory.getLogger(OverpassPlaceProvider.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper;
    private final GeometryFactory geometryFactory;
    private final String endpoint;

    public OverpassPlaceProvider(ObjectMapper mapper,
                                 GeometryFactory geometryFactory,
                                 @Value("${nearme.overpass.endpoint:https://overpass-api.de/api/interpreter}") String endpoint) {
        this.mapper = mapper;
        this.geometryFactory = geometryFactory;
        this.endpoint = endpoint;
    }

    /**
     * OSM tag selectors that identify each category. A category may list several
     * selectors; results are unioned (and later deduped by osmId). COFFEE matches
     * cafes OR places whose cuisine includes "breakfast" (case-insensitive).
     */
    private static List<String> tagSelectors(PlaceCategory category) {
        return switch (category) {
            case GAS -> List.of("\"amenity\"=\"fuel\"");
            case COFFEE -> List.of("\"amenity\"=\"cafe\"", "\"cuisine\"~\"breakfast\",i");
            case RESTAURANT -> List.of("\"amenity\"=\"restaurant\"");
            case HOTEL -> List.of("\"tourism\"=\"hotel\"");
            case MECHANIC -> List.of("\"shop\"=\"car_repair\"");
            case HOSPITAL -> List.of("\"amenity\"=\"hospital\"");
        };
    }

    /**
     * Fetch places of {@code category} within {@code radiusMeters} of (lat,lon).
     * Returns unsaved Place entities (osmId set). Throws on network/parse errors
     * so the caller can decide to serve cached data instead.
     */
    public List<Place> fetch(double lat, double lon, PlaceCategory category, double radiusMeters)
            throws IOException, InterruptedException {

        // Double.toString keeps a '.' decimal regardless of server locale.
        String around = "(around:" + radiusMeters + "," + lat + "," + lon + ")";
        StringBuilder union = new StringBuilder();
        for (String sel : tagSelectors(category)) {
            union.append("node[").append(sel).append("]").append(around).append(";");
            union.append("way[").append(sel).append("]").append(around).append(";");
        }
        String ql = "[out:json][timeout:25];(" + union + ");out center tags;";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "NearMe/1.0")
                .POST(HttpRequest.BodyPublishers.ofString("data=" + URLEncoder.encode(ql, StandardCharsets.UTF_8)))
                .build();

        HttpResponse<String> resp = sendWithRetry(req);
        JsonNode elements = mapper.readTree(resp.body()).path("elements");
        List<Place> out = new ArrayList<>();
        for (JsonNode el : elements) {
            Place p = toPlace(el, category);
            if (p != null) out.add(p);
        }
        return out;
    }

    /**
     * Send the query, retrying transient failures. Overpass returns 429 (rate
     * limit) and 504/502/503 (gateway/load timeouts) under pressure — these are
     * usually transient, especially for large-radius queries on dense categories.
     */
    private HttpResponse<String> sendWithRetry(HttpRequest req)
            throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code == 200) return resp;
                if (code == 429 || code == 502 || code == 503 || code == 504) {
                    last = new IOException("Overpass returned HTTP " + code);
                } else {
                    throw new IOException("Overpass returned HTTP " + code);
                }
            } catch (java.net.http.HttpTimeoutException e) {
                last = new IOException("Overpass request timed out", e);
            }
            if (attempt < 3) {
                Thread.sleep(1000L * attempt); // 1s, then 2s backoff
            }
        }
        throw last;
    }

    private Place toPlace(JsonNode el, PlaceCategory category) {
        JsonNode tags = el.path("tags");
        String name = text(tags, "name");
        if (name == null) return null; // unnamed POIs aren't useful to show

        double elLat, elLon;
        if (el.has("lat") && el.has("lon")) {                 // node
            elLat = el.get("lat").asDouble();
            elLon = el.get("lon").asDouble();
        } else if (el.has("center")) {                        // way/relation
            elLat = el.path("center").path("lat").asDouble();
            elLon = el.path("center").path("lon").asDouble();
        } else {
            return null;
        }

        // Node and way id spaces overlap in OSM; negate ways to keep osmId unique.
        long id = el.path("id").asLong();
        long osmId = "way".equals(el.path("type").asText()) ? -id : id;

        Point point = geometryFactory.createPoint(new Coordinate(elLon, elLat));

        Place place = new Place();
        place.setOsmId(osmId);
        place.setCategory(category);
        place.setName(name);
        place.setBrand(text(tags, "brand"));
        place.setAddress(composeAddress(tags));
        place.setLocation(point);
        place.setOpeningHours(text(tags, "opening_hours"));
        place.setCreatedAt(Instant.now());
        return place;
    }

    /** Build a "<number> <street>, <city>" address from OSM addr:* tags. */
    private static String composeAddress(JsonNode tags) {
        String house = text(tags, "addr:housenumber");
        String street = text(tags, "addr:street");
        String city = text(tags, "addr:city");
        StringBuilder sb = new StringBuilder();
        if (house != null) sb.append(house).append(' ');
        if (street != null) sb.append(street);
        if (city != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String text(JsonNode tags, String key) {
        JsonNode n = tags.path(key);
        return n.isMissingNode() || n.asText().isBlank() ? null : n.asText();
    }
}
