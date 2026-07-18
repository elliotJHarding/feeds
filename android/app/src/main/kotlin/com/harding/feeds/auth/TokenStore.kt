package com.harding.feeds.auth

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted-at-rest storage for the app's JWT pair. Refresh rotation means every successful
 * refresh replaces BOTH tokens - [store] is the only write path, so a rotated refresh token
 * can never be forgotten.
 */
class TokenStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "feeds_auth",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)

    val refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)

    val isLoggedIn: Boolean
        get() = refreshToken != null

    fun store(accessToken: String, refreshToken: String) = prefs.edit {
        putString(KEY_ACCESS, accessToken)
        putString(KEY_REFRESH, refreshToken)
    }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
