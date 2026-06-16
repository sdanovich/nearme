package com.example.nearme.data

import android.content.Context
import android.content.SharedPreferences
import com.example.nearme.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Holds the backend base URL the app currently talks to.
 *
 * The backend is exposed through an ephemeral Cloudflare quick tunnel whose
 * hostname changes every time the tunnel restarts, so the URL can't be fixed at
 * build time. It is seeded from [BuildConfig.API_BASE_URL], persisted across
 * launches, and — when a request can't reach it — re-discovered from a STABLE
 * anchor URL ([BuildConfig.TUNNEL_DISCOVERY_URL], a tiny text file the tunnel
 * host keeps current). See [TunnelRecoveryInterceptor] and [HostSelectionInterceptor].
 */
object BaseUrlProvider {

    private const val PREFS = "nearme_endpoint"
    private const val KEY_BASE_URL = "base_url"
    /** A burst of failed requests should trigger at most one anchor fetch. */
    private const val MIN_REFRESH_INTERVAL_MS = 5_000L

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var base: HttpUrl = BuildConfig.API_BASE_URL.toHttpUrlOrNull()
        ?: throw IllegalStateException("Invalid API_BASE_URL: ${BuildConfig.API_BASE_URL}")
    @Volatile private var lastRefreshMs = 0L

    private val discoveryClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Call once at app start, before the API client is built, to load any
     *  previously-discovered URL so we don't have to re-discover every launch. */
    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY_BASE_URL, null)?.toHttpUrlOrNull()?.let { base = it }
    }

    fun current(): HttpUrl = base

    /** Adopt [url] as the in-use base URL; persists it. Returns true if it changed. */
    @Synchronized
    fun update(url: HttpUrl): Boolean {
        if (url == base) return false
        base = url
        prefs?.edit()?.putString(KEY_BASE_URL, url.toString())?.apply()
        return true
    }

    /**
     * Fetch the latest URL from the discovery anchor and adopt it. Returns true if
     * the base URL actually changed. Rate-limited and synchronized so concurrent
     * failures collapse into a single fetch. Never throws.
     */
    @Synchronized
    fun refreshFromAnchor(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRefreshMs < MIN_REFRESH_INTERVAL_MS) return false
        lastRefreshMs = now

        val anchor = BuildConfig.TUNNEL_DISCOVERY_URL
        if (anchor.isBlank()) return false
        // NOTE: raw.githubusercontent.com keys its CDN cache on the path and IGNORES
        // the query string, so this "t" param does NOT force a fresh read — a
        // just-rotated URL is only visible after the edge object's max-age (≈5 min)
        // expires. We still send no-cache below and accept that bound; recovery
        // simply re-fires on subsequent failures until the anchor catches up.
        val anchorUrl = anchor.toHttpUrlOrNull()
            ?.newBuilder()?.addQueryParameter("t", now.toString())?.build()
            ?: return false

        val anchorRequest = Request.Builder().url(anchorUrl)
            .header("Cache-Control", "no-cache").build()
        return try {
            discoveryClient.newCall(anchorRequest).execute().use { resp ->
                val discovered = resp.body?.string()?.trim()?.toHttpUrlOrNull()
                if (resp.isSuccessful && discovered != null) update(discovered) else false
            }
        } catch (e: Exception) {
            false
        }
    }
}
