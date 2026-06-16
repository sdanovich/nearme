package com.example.nearme.dto;

import com.example.nearme.model.PlaceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStationRequest(
        @NotBlank String name,
        String brand,
        String address,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotNull PlaceCategory category,
        Double rating,
        String openingHours
) {}
