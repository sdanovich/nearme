package com.example.nearme.data

import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class TokenRequestBody(val clientSecret: String)

/** Matches the backend TokenResponse. */
@JsonClass(generateAdapter = true)
data class TokenResponse(
    val token: String,
    val tokenType: String?,
    val expiresInSeconds: Long
)

/**
 * Token exchange endpoint. Used by [TokenProvider] over a plain (auth-free)
 * OkHttp client, so calls here never recurse back through the auth interceptor.
 * Synchronous Call — it's invoked from within an OkHttp interceptor/authenticator.
 */
interface AuthApi {
    @POST("/api/auth/token")
    fun token(@Body body: TokenRequestBody): Call<TokenResponse>
}
