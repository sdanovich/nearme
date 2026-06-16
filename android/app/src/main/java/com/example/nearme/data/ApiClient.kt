package com.example.nearme.data

import com.example.nearme.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the Retrofit-backed NearMeApi against the configured base URL, with
 * client-credentials JWT auth: requests carry a Bearer token obtained from
 * /api/auth/token, refreshed automatically on 401.
 */
object ApiClient {

    val api: NearMeApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val converter = MoshiConverterFactory.create(moshi)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        // A separate, auth-free client used ONLY to obtain tokens — keeps the
        // token exchange from recursing back through the auth interceptor.
        // HostSelectionInterceptor makes token calls follow the live tunnel host,
        // so after the main client re-discovers a rotated URL, the next token
        // fetch automatically targets the new host too.
        val authClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(HostSelectionInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val authApi = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(authClient)
            .addConverterFactory(converter)
            .build()
            .create(AuthApi::class.java)
        val tokens = TokenProvider(authApi, BuildConfig.AUTH_CLIENT_SECRET)

        // The first nearby request for an area triggers a backend Overpass fetch,
        // which can take many seconds; allow generous read timeouts.
        // Interceptor order matters: TunnelRecovery must be OUTERMOST (it catches
        // connect/TLS failures and retries) and HostSelection INNERMOST (it
        // re-targets each attempt at the current tunnel host), so a retry after
        // re-discovery actually goes to the new URL.
        val client = OkHttpClient.Builder()
            .addInterceptor(TunnelRecoveryInterceptor())
            .addInterceptor(logging)
            .addInterceptor(AuthInterceptor(tokens))
            .addInterceptor(HostSelectionInterceptor())
            .authenticator(AuthAuthenticator(tokens))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(converter)
            .build()
            .create(NearMeApi::class.java)
    }
}
