package com.example.nearme.data

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Refreshes the token when the server answers 401 (e.g. the JWT expired), then
 * retries the original request once. Gives up after a single retry so an
 * unrecoverable 401 doesn't loop forever.
 */
class AuthAuthenticator(private val tokens: TokenProvider) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // never try to re-auth the token endpoint, and only retry once
        if (response.request.url.encodedPath.endsWith("/api/auth/token")) return null
        if (responseCount(response) >= 2) return null

        tokens.invalidate()
        val token = tokens.currentToken() ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
