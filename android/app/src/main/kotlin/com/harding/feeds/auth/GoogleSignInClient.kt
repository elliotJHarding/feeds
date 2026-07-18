package com.harding.feeds.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialUnknownException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.harding.feeds.BuildConfig

/**
 * Obtains a Google ID token via Android Credential Manager. The token is then exchanged for
 * app JWTs at /auth/login - no Google SDK state is kept beyond this one call.
 */
class GoogleSignInClient {

    /** Must be called with an Activity context - Credential Manager shows UI. */
    suspend fun fetchIdToken(activityContext: Context): String {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

        val credential = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
            .credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw GetCredentialUnknownException("Unexpected credential type: ${credential.type}")
    }
}
