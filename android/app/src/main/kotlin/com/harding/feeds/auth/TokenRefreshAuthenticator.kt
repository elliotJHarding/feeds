package com.harding.feeds.auth

import com.harding.feeds.client.apis.AuthenticationApi
import com.harding.feeds.client.models.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

fun Request.withBearer(accessToken: String): Request =
    newBuilder().header("Authorization", "Bearer $accessToken").build()

/**
 * Reacts to a 401 by exchanging the refresh token at /auth/refresh, then retries the request.
 *
 * Rotation-aware: the server revokes the presented refresh token and issues a new one, so the
 * whole new pair is stored. Synchronized so concurrent 401s perform a single refresh - the
 * losers notice the access token has already changed and just retry with it.
 */
class TokenRefreshAuthenticator(
    private val tokenStore: TokenStore,
    private val authApi: AuthenticationApi,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorAttemptCount() >= MAX_RETRIES) return null
        val failedWith = response.request.header("Authorization")?.removePrefix("Bearer ")

        synchronized(this) {
            val current = tokenStore.accessToken
            if (current != null && current != failedWith) {
                // Another call already refreshed while we waited on the lock.
                return response.request.withBearer(current)
            }

            val refreshToken = tokenStore.refreshToken ?: return null
            val refreshed = runCatching {
                runBlocking { authApi.refreshToken(RefreshTokenRequest(refreshToken)) }
            }.getOrNull()

            return when {
                refreshed == null -> null // network failure - give up, WorkManager retries later
                refreshed.isSuccessful -> {
                    val tokens = refreshed.body() ?: return null
                    tokenStore.store(tokens.accessToken, tokens.refreshToken)
                    response.request.withBearer(tokens.accessToken)
                }
                else -> {
                    // Refresh token invalid, expired or revoked - session is over.
                    tokenStore.clear()
                    null
                }
            }
        }
    }

    private fun Response.priorAttemptCount(): Int =
        generateSequence(priorResponse) { it.priorResponse }.count()

    private companion object {
        const val MAX_RETRIES = 2
    }
}
