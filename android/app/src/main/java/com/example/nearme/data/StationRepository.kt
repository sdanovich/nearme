package com.example.nearme.data

import com.example.nearme.data.local.CachedPlace
import com.example.nearme.data.local.PlaceDao
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the UI. Reads always come from the Room cache
 * (a Flow, so the screen updates the instant a refresh writes new data).
 * Postgres is the backend source of truth; refresh() reads from it and writes
 * through to Room. This is read-only caching: refresh never writes to Postgres.
 * The only thing that writes to Postgres is reportPrice() — an explicit user action.
 */
class StationRepository(
    private val api: NearMeApi,
    private val dao: PlaceDao
) {
    /** Cached places for a category as a live stream — the UI observes this. */
    fun observePlaces(category: String): Flow<List<CachedPlace>> =
        dao.observePlaces(category)

    /**
     * One refresh: fetch nearby places of the category from the backend, write
     * through to the cache. priceType only affects GAS (fuel grade); the backend
     * ignores it for other categories.
     */
    suspend fun refresh(lat: Double, lon: Double, category: String, priceType: String = "REGULAR") {
        val now = System.currentTimeMillis()
        val remote = api.nearby(lat = lat, lon = lon, category = category, priceType = priceType)

        val cached = remote.map {
            CachedPlace(
                stationId = it.stationId,
                name = it.name, brand = it.brand, address = it.address,
                latitude = it.latitude, longitude = it.longitude,
                distanceMeters = it.distanceMeters,
                rating = it.rating, openingHours = it.openingHours,
                price = it.price, priceReportedAt = it.priceReportedAt,
                crowdsourced = it.crowdsourced,
                category = category, cachedAt = now
            )
        }
        // Replace this category's cached set so stale places from a previous
        // location don't linger in the list.
        dao.clearCategory(category)
        dao.upsertPlaces(cached)
    }

    /** Explicit user write — the one path that enriches Postgres. Gas only. */
    suspend fun reportPrice(stationId: Long, priceType: String, price: Double) {
        api.reportPrice(PriceReportBody(stationId, priceType, price, "android"))
    }

    /** Explicit user write: create a new place of the given category in Postgres. */
    suspend fun createPlace(
        name: String, brand: String?, address: String?,
        lat: Double, lon: Double, category: String,
        rating: Double?, openingHours: String?
    ) {
        api.createPlace(
            CreatePlaceBody(name, brand, address, lat, lon, category, rating, openingHours)
        )
    }
}
