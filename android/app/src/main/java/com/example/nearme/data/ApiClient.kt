package com.example.nearme.data

import com.example.nearme.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/** Builds the Retrofit-backed NearMeApi against the configured base URL. */
object ApiClient {

    val api: NearMeApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        // The first nearby request for an area triggers a backend Overpass fetch,
        // which can take many seconds; allow generous read timeouts.
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NearMeApi::class.java)
    }
}
