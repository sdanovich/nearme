package com.example.nearme.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/auth/token — the app's shared client secret. */
public record TokenRequest(@NotBlank String clientSecret) {}
