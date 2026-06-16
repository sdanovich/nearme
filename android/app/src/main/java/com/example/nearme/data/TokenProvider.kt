package com.example.nearme.data

/**
 * Holds the current JWT and fetches/refreshes it on demand. The app authenticates
 * with a shared client secret (client-credentials), so there's a single token for
 * the whole process. Thread-safe: the OkHttp interceptor and authenticator may
 * both touch it from different request threads.
 */
class TokenProvider(
    private val authApi: AuthApi,
    private val clientSecret: String
) {
    @Volatile private var token: String? = null
    @Volatile private var expiresAtMs: Long = 0L

    /** A valid token, fetching one if missing or near expiry. Null if auth fails. */
    @Synchronized
    fun currentToken(): String? {
        val t = token
        if (t != null && System.currentTimeMillis() < expiresAtMs - REFRESH_SKEW_MS) return t
        return fetch()
    }

    /** Forget the current token (e.g. after a 401) so the next call re-fetches. */
    @Synchronized
    fun invalidate() {
        token = null
        expiresAtMs = 0L
    }

    private fun fetch(): String? = try {
        val resp = authApi.token(TokenRequestBody(clientSecret)).execute()
        val body = resp.body()
        if (resp.isSuccessful && body != null) {
            token = body.token
            expiresAtMs = System.currentTimeMillis() + body.expiresInSeconds * 1000
            body.token
        } else {
            token = null; expiresAtMs = 0L; null
        }
    } catch (e: Exception) {
        token = null; expiresAtMs = 0L; null
    }

    companion object {
        /** Refresh a little before actual expiry to avoid racing the clock. */
        private const val REFRESH_SKEW_MS = 10_000L
    }
}
