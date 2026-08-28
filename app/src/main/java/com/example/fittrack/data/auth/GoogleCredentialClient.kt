package com.example.fittrack.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.fittrack.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What came back from the Google account chooser. */
sealed class GoogleCredentialResult {
    data class Success(val idToken: String) : GoogleCredentialResult()
    /** The user dismissed the sheet; not an error worth shouting about. */
    object Cancelled : GoogleCredentialResult()
    data class Failure(val message: String) : GoogleCredentialResult()
}

/**
 * Wraps Credential Manager, which replaced the deprecated GoogleSignInClient.
 *
 * The web client id comes from `default_web_client_id`, a string the
 * google-services plugin generates from google-services.json. It only exists
 * once Google sign-in is enabled in the Firebase console, so a missing value is
 * reported as a setup problem rather than a crash.
 */
class GoogleCredentialClient @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    /**
     * Must be given an Activity context: Credential Manager shows UI, and
     * passing the application context throws.
     */
    suspend fun requestIdToken(activityContext: Context): GoogleCredentialResult {
        val serverClientId = appContext.getString(R.string.default_web_client_id)
        if (serverClientId.isBlank() || serverClientId.startsWith("000000000000-placeholder")) {
            return GoogleCredentialResult.Failure(
                "Google sign-in is not configured yet. Add a real google-services.json " +
                    "and enable Google as a sign-in provider in Firebase."
            )
        }

        val option = GetGoogleIdOption.Builder()
            // false so the chooser also offers accounts that have never used
            // this app, which is what a first-time sign-in needs.
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleCredentialResult.Success(
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                )
            } else {
                GoogleCredentialResult.Failure("That credential type is not supported.")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleCredentialResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleCredentialResult.Failure(
                "No Google account available on this device. Add one in Android settings."
            )
        } catch (e: GetCredentialException) {
            GoogleCredentialResult.Failure(e.message ?: "Google sign-in failed.")
        }
    }
}
