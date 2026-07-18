package com.harding.feeds.auth

import okhttp3.Interceptor
import okhttp3.Response

/** Adds the bearer access token to every request on the authenticated client. */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = tokenStore.accessToken ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().withBearer(accessToken))
    }
}
