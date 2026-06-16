package com.example.nearme.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Self-heals a stale tunnel URL. When a request can't reach the backend, this
 * re-discovers the current URL via [recover] and retries the request once. Because
 * the retry flows back through [HostSelectionInterceptor], it goes to the new host.
 * A successful retry is transparent — the caller (and the user) never sees the error.
 *
 * The quick-tunnel hostname having rotated surfaces in two distinct ways, and we
 * must handle BOTH:
 *  - As an [IOException] (connection refused, DNS failure, TLS/trust error) once the
 *    old hostname stops resolving — the request never completes.
 *  - As a successful HTTP response carrying a Cloudflare edge status (530, or the
 *    520–527 range) while the old hostname still resolves to Cloudflare's edge but
 *    the tunnel behind it is gone. No exception is thrown, so this path is easy to
 *    miss — that was the original bug: the app stayed pinned to the dead host,
 *    showing 530 forever, because recovery only fired on [IOException]. These codes
 *    are emitted only by Cloudflare's edge, never by the NearMe backend, so it is
 *    safe to treat them as "rediscover and retry".
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
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            // Old host no longer reachable at all: rediscover and retry once.
            if (recover()) return chain.proceed(request) else throw e
        }
        // Old host still resolves but the tunnel is down: Cloudflare returns its own
        // edge status. Rediscover, and if the URL actually changed, retry once.
        if (isCloudflareTunnelDown(response.code) && recover()) {
            response.close()
            return chain.proceed(request)
        }
        return response
    }

    private fun isCloudflareTunnelDown(code: Int): Boolean =
        code == 530 || code in 520..527
}
