package com.example.nearme.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <jwt>` to every request, fetching a token first
 * if needed. The token endpoint itself is exempt (it must stay unauthenticated,
 * and is served by a separate auth-free client anyway).
 */
class AuthInterceptor(private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.endsWith("/api/auth/token")) {
            return chain.proceed(request)
        }
        val token = tokens.currentToken()
        val authed = if (token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        return chain.proceed(authed)
    }
}
