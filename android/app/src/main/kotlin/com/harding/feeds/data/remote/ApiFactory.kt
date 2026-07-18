package com.harding.feeds.data.remote

import com.harding.feeds.BuildConfig
import com.harding.feeds.auth.AuthInterceptor
import com.harding.feeds.auth.TokenRefreshAuthenticator
import com.harding.feeds.auth.TokenStore
import com.harding.feeds.client.apis.AuthenticationApi
import com.harding.feeds.client.apis.BabiesApi
import com.harding.feeds.client.apis.FamilyGroupApi
import com.harding.feeds.client.apis.FeedsApi
import com.harding.feeds.client.infrastructure.Serializer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Builds the generated Retrofit interfaces. Serialization uses the generated [Serializer]
 * gson (it carries the java.time adapters the generated models need).
 *
 * Two clients on purpose: [authenticationApi] is unauthenticated (its endpoints are on the
 * server's public chain, and the refresh authenticator must not recurse into itself); every
 * other API goes through the bearer interceptor + refresh-on-401 authenticator.
 */
class ApiFactory(baseUrl: String, tokenStore: TokenStore) {

    private val gson = Serializer.gsonBuilder.create()
    private val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(normalizedBaseUrl)
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
        else HttpLoggingInterceptor.Level.NONE
    }

    val authenticationApi: AuthenticationApi =
        retrofit(OkHttpClient.Builder().addInterceptor(logging).build())
            .create(AuthenticationApi::class.java)

    private val authenticatedRetrofit = retrofit(
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenRefreshAuthenticator(tokenStore, authenticationApi))
            .addInterceptor(logging)
            .build()
    )

    val feedsApi: FeedsApi = authenticatedRetrofit.create(FeedsApi::class.java)
    val babiesApi: BabiesApi = authenticatedRetrofit.create(BabiesApi::class.java)
    val familyGroupApi: FamilyGroupApi = authenticatedRetrofit.create(FamilyGroupApi::class.java)
}
