package com.dtpos.salonmanager.services.account

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.dtpos.salonmanager.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** Why getting a Google ID token failed. */
enum class GoogleSignInError { CANCELLED, NO_ACCOUNT, NOT_CONFIGURED, FAILED }

class GoogleSignInException(val error: GoogleSignInError, cause: Throwable? = null) : Exception(error.name, cause)

/** "Continue with Google" through Android Credential Manager. */
object GoogleSignIn {

    /**
     * The OAuth web client id Firebase created for Google sign-in. It is generated from
     * google-services.json (default_web_client_id) once Google sign-in is enabled in Firebase.
     */
    fun webClientId(context: Context): String? {
        BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() }?.let { return it }
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }

    suspend fun requestIdToken(activity: Activity): String {
        val clientId = webClientId(activity) ?: throw GoogleSignInException(GoogleSignInError.NOT_CONFIGURED)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInException(GoogleSignInError.CANCELLED, e)
        } catch (e: NoCredentialException) {
            throw GoogleSignInException(GoogleSignInError.NO_ACCOUNT, e)
        } catch (e: GetCredentialException) {
            throw GoogleSignInException(GoogleSignInError.FAILED, e)
        }
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                return GoogleIdTokenCredential.createFrom(credential.data).idToken
            } catch (e: GoogleIdTokenParsingException) {
                throw GoogleSignInException(GoogleSignInError.FAILED, e)
            }
        }
        throw GoogleSignInException(GoogleSignInError.FAILED)
    }

    /** Forget the chosen Google account so the picker shows again next time. */
    suspend fun clear(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Best effort only.
        }
    }
}
