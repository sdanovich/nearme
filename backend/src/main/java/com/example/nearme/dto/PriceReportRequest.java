package com.example.nearme.dto;

import com.example.nearme.model.FuelType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** A user submitting a price. fuelType maps to the generic priceType for GAS. */
public record PriceReportRequest(
        @NotNull Long stationId,
        @NotNull FuelType fuelType,
        @NotNull @DecimalMin("0.10") @DecimalMax("99.999") BigDecimal price,
        String reporterId
) {}
