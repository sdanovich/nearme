package com.example.nearme.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Self-heals a stale tunnel URL. When a request can't reach the backend
 * (connection refused, DNS failure, TLS/trust error — the symptoms of the
 * Cloudflare quick-tunnel hostname having rotated), this re-discovers the current
 * URL via [recover] and retries the request once. Because the retry flows back
 * through [HostSelectionInterceptor], it goes to the new host. A successful retry
 * is transparent — the caller (and the user) never sees the error.
 *
 * Must be installed as the OUTERMOST application interceptor, with
 * [HostSelectionInterceptor] innermost, so the retried request is re-targeted.
 *
 * [recover] returns true if it changed the base URL (worth retrying); it is
 * injected so this is unit-testable without the Android runtime.
 */
class TunnelRecoveryInterceptor(
    private val recover: () -> Boolean = { BaseUrlProvider.refreshFromAnchor() }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            chain.proceed(request)
        } catch (e: IOException) {
            if (recover()) {
                chain.proceed(request)
            } else {
                throw e
            }
        }
    }
}
