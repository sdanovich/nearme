package com.example.nearme.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One place in nearby results. `price`/`priceReportedAt` are populated only for
 * GAS; `rating`/`openingHours` apply to all categories when known.
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
        Instant priceReportedAt
) {}
