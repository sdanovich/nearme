package com.example.nearme.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One point in a place's price history. */
public record PricePoint(BigDecimal price, Instant reportedAt) {}
