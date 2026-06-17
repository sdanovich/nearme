package com.example.nearme.data

import com.danovich.platform.auth.BearerAuthInterceptor
import com.danovich.platform.auth.TokenProvider
import com.danovich.platform.auth.TokenRefreshInterceptor
import com.danovich.platform.auth.TokenStore
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
 * client-credentials JWT auth from the shared platform-stack module: requests
 * carry a Bearer token obtained from /api/auth/token, refreshed automatically on
 * a 401. The bearer/refresh/token-store code is the shared library; this object
 * only supplies the project-specific bits — the TokenProvider (which calls
 * NearMe's own token endpoint through NearMe's HTTP client) and the OkHttp
 * assembly that keeps the Cloudflare tunnel interceptors in the right order.
 */
object ApiClient {

    /** Path the bearer/refresh interceptors must leave unauthenticated. */
    private const val TOKEN_PATH = "/api/auth/token"

    val api: NearMeApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val converter = MoshiConverterFactory.create(moshi)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        // A separate, auth-free client used ONLY to obtain tokens — keeps the
        // token exchange from recursing back through the bearer/refresh
        // interceptors. HostSelectionInterceptor makes token calls follow the
        // live tunnel host, so after the main client re-discovers a rotated URL,
        // the next token fetch automatically targets the new host too.
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

        // Project-supplied TokenProvider (the one piece the library can't ship):
        // exchange the shared client secret at NearMe's token endpoint and return
        // the JWT, or null on any failure. Must not throw — runs on interceptor
        // threads. The platform interceptors hold the result in TokenStore.
        val tokens = TokenProvider {
            try {
                val resp = authApi.token(TokenRequestBody(BuildConfig.AUTH_CLIENT_SECRET)).execute()
                val body = resp.body()
                if (resp.isSuccessful && body != null) body.token else null
            } catch (e: Exception) {
                null
            }
        }

        // Prime a token off the main thread (ApiClient.api is first built in
        // Application.onCreate, so a synchronous fetch here would crash). This
        // avoids a guaranteed cold-start 401; if it misses, the refresh
        // interceptor below still recovers on the first 401.
        if (TokenStore.get() == null) {
            Thread { TokenStore.set(tokens.fetchFreshToken()) }.start()
        }

        // The first nearby request for an area triggers a backend Overpass fetch,
        // which can take many seconds; allow generous read timeouts.
        // Interceptor order (outer -> inner) matters:
        //   TunnelRecovery — OUTERMOST (preserved): catches connect/TLS failures
        //                    and retries after re-discovering the tunnel URL.
        //   TokenRefresh   — OUTER of bearer: on a 401, refreshes the token and
        //                    retries, so the retry re-enters the bearer below.
        //   Bearer         — stamps Authorization: Bearer <jwt>.
        //   HostSelection  — INNERMOST (preserved): re-targets each attempt at the
        //                    current tunnel host, so a retry goes to the new URL.
        val client = OkHttpClient.Builder()
            .addInterceptor(TunnelRecoveryInterceptor())
            .addInterceptor(logging)
            .addInterceptor(TokenRefreshInterceptor(tokens, TOKEN_PATH))
            .addInterceptor(BearerAuthInterceptor(TOKEN_PATH))
            .addInterceptor(HostSelectionInterceptor())
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
