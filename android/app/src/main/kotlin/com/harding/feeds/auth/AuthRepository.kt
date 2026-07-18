package com.harding.feeds.auth

import android.content.Context
import com.harding.feeds.client.apis.AuthenticationApi
import com.harding.feeds.client.models.LoginRequest
import com.harding.feeds.data.local.FeedsDatabase
import com.harding.feeds.data.local.entity.UserEntity
import com.harding.feeds.data.remote.bodyOrThrow
import com.harding.feeds.data.remote.toEntity
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val googleSignInClient: GoogleSignInClient,
    private val authApi: AuthenticationApi,
    private val tokenStore: TokenStore,
    private val database: FeedsDatabase,
) {

    val isLoggedIn: Boolean
        get() = tokenStore.isLoggedIn

    fun currentUser(): Flow<UserEntity?> = database.sessionDao().selfUser()

    /**
     * Full sign-in: Credential Manager Google sign-in, then exchange the Google ID token for
     * app JWTs at /auth/login. Must be called with an Activity context (sign-in shows UI).
     */
    suspend fun signIn(activityContext: Context): UserEntity {
        val idToken = googleSignInClient.fetchIdToken(activityContext)
        val login = authApi.login(LoginRequest(idToken)).bodyOrThrow()
        tokenStore.store(login.accessToken, login.refreshToken)
        val user = login.user.toEntity(isSelf = true)
        database.sessionDao().upsertUser(user)
        return user
    }

    suspend fun signOut() {
        tokenStore.clear()
        database.clearAll()
    }
}
