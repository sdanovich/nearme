package com.example.nearme.dto;

/** Issued access token. {@code tokenType} is always "Bearer". */
public record TokenResponse(String token, String tokenType, long expiresInSeconds) {}
