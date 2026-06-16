package com.example.nearme.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

/** The host swap must redirect to the current base URL while preserving the
 *  request's path and query (so `/api/...` reaches the backend unchanged). */
class HostSelectionInterceptorTest {

    @Test
    fun rewritesHostButPreservesPathAndQuery() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setResponseCode(200))
        }
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(HostSelectionInterceptor { server.url("/") })
                .build()

            val req = Request.Builder()
                .url("https://placeholder.invalid/api/stations/nearby?lat=1.5&lon=-2.5")
                .build()
            client.newCall(req).execute().use { assertEquals(200, it.code) }

            val recorded = server.takeRequest()
            assertEquals("/api/stations/nearby?lat=1.5&lon=-2.5", recorded.path)
            assertEquals(server.hostName, recorded.requestUrl?.host)
        } finally {
            server.shutdown()
        }
    }
}
