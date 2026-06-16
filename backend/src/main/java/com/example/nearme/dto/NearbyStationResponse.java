package com.example.nearme.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One place in nearby results. `price`/`priceReportedAt` are populated only for
 * GAS; `rating`/`openingHours` apply to all categories when known.
 *
 * `crowdsourced` is true only when `price` is the median of this station's own
 * user reports — a real crowdsourced price. It is false for the fallback
 * estimates (nearby-stations median or the seeded regional average), which the
 * client de-emphasizes. The client surfaces real reports prominently.
 */
public record NearbyStationResponse(
        Long stationId,
        String name,
        String brand,
        String address,
        double latitude,
        double longitude,
        double distanceMeters,
        Double rating,
        String openingHours,
        BigDecimal price,
        Instant priceReportedAt,
        boolean crowdsourced
) {}
