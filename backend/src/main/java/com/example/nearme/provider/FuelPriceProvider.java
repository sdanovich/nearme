package com.example.nearme.provider;

import com.example.nearme.cache.RedisCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Looks up average retail fuel prices from the U.S. EIA Open Data API
 * (https://www.eia.gov/opendata/). EIA publishes weekly averages by region —
 * not per-station — so this is a sensible default price for a discovered gas
 * station, which a crowdsourced report can later override.
 *
 * Requires a free EIA API key in {@code nearme.eia.api-key}. With no key the
 * provider is disabled and returns empty (callers then leave prices blank).
 * Values are cached per grade since EIA only updates weekly.
 */
@Component
public class FuelPriceProvider {

    private static final Logger log = LoggerFactory.getLogger(FuelPriceProvider.class);
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper;
    private final RedisCache cache;
    private final String apiKey;
    private final String endpoint;

    public FuelPriceProvider(ObjectMapper mapper,
                             RedisCache cache,
                             @Value("${nearme.eia.api-key:}") String apiKey,
                             @Value("${nearme.eia.endpoint:https://api.eia.gov/v2/petroleum/pri/gnd/data/}") String endpoint) {
        this.mapper = mapper;
        this.cache = cache;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** EIA product code for one of the app's fuel grades, or null if unsupported. */
    private static String productCode(String grade) {
        return switch (grade == null ? "" : grade.toUpperCase()) {
            case "REGULAR" -> "EPMR";
            case "MIDGRADE" -> "EPMM";
            case "PREMIUM" -> "EPMP";
            case "DIESEL" -> "EPD2D";
            default -> null;
        };
    }

    /** Latest weekly average retail price for the grade+region in $/gal, if available. */
    public Optional<BigDecimal> averagePrice(String grade, String duoarea) {
        if (!isEnabled()) return Optional.empty();
        String product = productCode(grade);
        if (product == null) return Optional.empty();

        String cacheKey = "eiaprice:" + product + ":" + duoarea;
        Optional<String> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            return Optional.of(new BigDecimal(cached.get()));
        }

        try {
            // Brackets aren't legal raw in a URI; encode them.
            String url = (endpoint
                    + "?api_key=" + apiKey
                    + "&frequency=weekly&data[0]=value"
                    + "&facets[product][]=" + product
                    + "&facets[duoarea][]=" + duoarea
                    + "&facets[process][]=PTE"
                    + "&sort[0][column]=period&sort[0][direction]=desc&length=1")
                    .replace("[", "%5B").replace("]", "%5D");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "NearMe/1.0")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("EIA returned HTTP {} for {}", resp.statusCode(), product);
                return Optional.empty();
            }

            JsonNode data = mapper.readTree(resp.body()).path("response").path("data");
            if (!data.isArray() || data.isEmpty()) return Optional.empty();
            String value = data.get(0).path("value").asText(null);
            if (value == null || value.isBlank()) return Optional.empty();

            BigDecimal price = new BigDecimal(value);
            cache.put(cacheKey, price.toPlainString(), CACHE_TTL);
            log.info("EIA {} ({}) average = {}/gal", grade, duoarea, price);
            return Optional.of(price);
        } catch (Exception e) {
            log.warn("EIA price lookup failed for {}: {}", grade, e.toString());
            return Optional.empty();
        }
    }
}
