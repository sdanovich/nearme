package com.example.nearme.data

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Matches NearbyStationResponse from the backend. */
@JsonClass(generateAdapter = true)
data class NearbyPlace(
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
    // true when the price is a real user report (vs. a seeded regional average)
    val crowdsourced: Boolean = false
)

@JsonClass(generateAdapter = true)
data class PriceReportBody(
    val stationId: Long,
    val fuelType: String,
    val price: Double,
    val reporterId: String?
)

/** Matches CreateStationRequest on the backend. */
@JsonClass(generateAdapter = true)
data class CreatePlaceBody(
    val name: String,
    val brand: String?,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val rating: Double?,
    val openingHours: String?
)

interface NearMeApi {
    /**
     * Nearby places of one category. priceType only matters for GAS (the fuel
     * grade); for other categories the backend ignores it and returns no price.
     */
    @GET("/api/stations/nearby")
    suspend fun nearby(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("category") category: String = "GAS",
        @Query("priceType") priceType: String = "REGULAR",
        @Query("radiusMeters") radiusMeters: Double = 32000.0,
        @Query("limit") limit: Int = 20
    ): List<NearbyPlace>

    @POST("/api/prices")
    suspend fun reportPrice(@Body body: PriceReportBody): retrofit2.Response<Unit>

    @POST("/api/stations")
    suspend fun createPlace(@Body body: CreatePlaceBody): retrofit2.Response<Unit>
}
