package com.example.nearme.data

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites each outgoing request to the current dynamic base URL (scheme/host/
 * port), leaving the path and query intact. Retrofit's fixed baseUrl is only a
 * template; this is what actually directs requests at the live tunnel host, and
 * — crucially — it re-reads [currentBaseUrl] on every call, so a retry after
 * [TunnelRecoveryInterceptor] picks up a freshly-discovered URL.
 *
 * [currentBaseUrl] is injected (rather than calling [BaseUrlProvider] directly)
 * so the rewrite is unit-testable without the Android runtime.
 */
class HostSelectionInterceptor(
    private val currentBaseUrl: () -> HttpUrl = { BaseUrlProvider.current() }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = currentBaseUrl()
        val req = chain.request()
        val rewritten = req.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(req.newBuilder().url(rewritten).build())
    }
}
