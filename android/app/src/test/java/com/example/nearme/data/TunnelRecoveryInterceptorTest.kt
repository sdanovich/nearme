package com.example.nearme.data

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/**
 * Verifies the self-heal behavior: when a request can't reach the backend (the
 * symptom of a rotated tunnel URL), the recovery interceptor re-discovers the URL
 * and retries once against the new host — transparently to the caller.
 */
class TunnelRecoveryInterceptorTest {

    /** A URL whose port is closed, so connecting to it raises an IOException. */
    private fun deadHttpUrl(): HttpUrl {
        val s = MockWebServer()
        s.start()
        val u = s.url("/")
        s.shutdown()
        return u
    }

    private fun clientWith(hostHolder: () -> HttpUrl, recover: () -> Boolean) =
        OkHttpClient.Builder()
            .addInterceptor(TunnelRecoveryInterceptor(recover))   // outermost: catches + retries
            .addInterceptor(HostSelectionInterceptor(hostHolder)) // innermost: targets current host
            .build()

    private fun request() =
        Request.Builder().url("http://placeholder.invalid/api/x").build()

    @Test
    fun recoversAndRetriesAgainstNewHostOnConnectionFailure() {
        val live = MockWebServer().apply {
            start()
            enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        }
        try {
            var host = deadHttpUrl() // stale tunnel: connections refused
            val client = clientWith(
                hostHolder = { host },
                recover = { host = live.url("/"); true } // "discover" the new URL
            )

            client.newCall(request()).execute().use { resp ->
                assertEquals(200, resp.code)
                assertEquals("ok", resp.body?.string())
            }

            // The retried request reached the live host with its path intact.
            assertEquals(1, live.requestCount)
            assertEquals("/api/x", live.takeRequest().path)
        } finally {
            live.shutdown()
        }
    }

    @Test
    fun propagatesErrorWhenRecoveryReportsNoChange() {
        val host = deadHttpUrl()
        val client = clientWith(hostHolder = { host }, recover = { false })
        assertThrows(IOException::class.java) { client.newCall(request()).execute() }
    }

    @Test
    fun recoversAndRetriesOnCloudflareTunnelDownStatus() {
        // The old host still resolves and answers, but the tunnel behind it is gone,
        // so Cloudflare's edge returns 530 — a *successful* HTTP response, no
        // IOException. Recovery must still fire and retry against the new host.
        val stale = MockWebServer().apply {
            start()
            enqueue(MockResponse().setResponseCode(530).setBody("error code: 1033"))
        }
        val live = MockWebServer().apply {
            start()
            enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        }
        try {
            var host = stale.url("/")
            val client = clientWith(
                hostHolder = { host },
                recover = { host = live.url("/"); true }
            )

            client.newCall(request()).execute().use { resp ->
                assertEquals(200, resp.code)
                assertEquals("ok", resp.body?.string())
            }
            assertEquals(1, stale.requestCount)
            assertEquals(1, live.requestCount)
        } finally {
            stale.shutdown()
            live.shutdown()
        }
    }

    @Test
    fun returnsCloudflareErrorWhenRecoveryReportsNoChange() {
        // 530 but nothing newer to discover: surface the 530, don't loop.
        val stale = MockWebServer().apply {
            start()
            enqueue(MockResponse().setResponseCode(530))
        }
        try {
            val host = stale.url("/")
            val client = clientWith(hostHolder = { host }, recover = { false })
            client.newCall(request()).execute().use { resp ->
                assertEquals(530, resp.code)
            }
            assertEquals(1, stale.requestCount)
        } finally {
            stale.shutdown()
        }
    }

    @Test
    fun passesThroughBackendErrorStatusWithoutRecovering() {
        // A real backend error (e.g. 500 from /api/auth/token) must NOT trigger
        // rediscovery — only Cloudflare edge codes (520-527, 530) do.
        val backend = MockWebServer().apply {
            start()
            enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        }
        try {
            val host = backend.url("/")
            var recoveries = 0
            val client = clientWith(
                hostHolder = { host },
                recover = { recoveries++; true }
            )
            client.newCall(request()).execute().use { resp ->
                assertEquals(500, resp.code)
            }
            assertEquals(0, recoveries)
            assertEquals(1, backend.requestCount)
        } finally {
            backend.shutdown()
        }
    }

    @Test
    fun retriesAtMostOnce() {
        // recover claims success, but the new host is also dead: exactly one retry,
        // then the IOException propagates (no infinite loop).
        val dead2 = deadHttpUrl()
        var host = deadHttpUrl()
        var recoveries = 0
        val client = clientWith(
            hostHolder = { host },
            recover = { recoveries++; host = dead2; true }
        )

        assertThrows(IOException::class.java) { client.newCall(request()).execute() }
        assertEquals(1, recoveries)
    }
}
