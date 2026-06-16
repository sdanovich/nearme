package com.example.nearme.data.local

import androidx.room.Entity

/**
 * Cached place record. Survives app restarts so the list shows instantly (and
 * offline), then refreshes when a background scan completes.
 *
 * Keyed by stationId + category because the same coordinates could host
 * different categories; the cache holds one row per place per category looked at.
 */
@Entity(tableName = "cached_place", primaryKeys = ["stationId", "category"])
data class CachedPlace(
    val stationId: Long,
    val name: String?,
    val brand: String?,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    val rating: Double?,
    val openingHours: String?,
    val price: Double?,
    val priceReportedAt: String?,
    val category: String,
    val cachedAt: Long
)
