package com.example.fittrack.data.auth

import com.example.fittrack.domain.model.AuthOutcome
import com.example.fittrack.domain.model.AuthUser
import com.example.fittrack.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override val currentUser: AuthUser?
        get() = firebaseAuth.currentUser?.toAuthUser()

    override fun observeUser(): Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithGoogle(idToken: String): AuthOutcome =
        runAuth {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await().user
        }

    override suspend fun updateDisplayName(displayName: String): AuthOutcome = runAuth {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("Sign in before changing your name.")
        user.updateProfile(
            UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.trim())
                .build()
        ).await()
        user.reload().await()
        firebaseAuth.currentUser ?: user
    }

    override suspend fun currentIdToken(): String? = try {
        firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
    } catch (e: Exception) {
        null
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    private suspend inline fun runAuth(block: suspend () -> FirebaseUser?): AuthOutcome =
        try {
            val user = block()
            if (user == null) {
                AuthOutcome.Failure("Sign-in did not return an account. Try again.")
            } else {
                AuthOutcome.Success(user.toAuthUser())
            }
        } catch (e: Exception) {
            AuthOutcome.Failure(e.toMessage())
        }

    /**
     * No email fallback for the name. The part of an address before the @ is
     * usually a real name or a handle the user did not agree to publish, and
     * this value ends up on posts other people can read.
     */
    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName?.trim()?.takeIf { it.isNotEmpty() }
    )

    /**
     * Firebase messages are aimed at developers ("The supplied auth credential
     * is malformed or has expired"), so the cases a user can actually reach get
     * rewritten. The password ones went with the password form.
     */
    private fun Exception.toMessage(): String = when (this) {
        is FirebaseAuthUserCollisionException ->
            "That Google account is already linked to a different sign-in method."
        is IOException ->
            "No connection. Check your network and try again."
        else -> message ?: "Something went wrong. Try again."
    }
}
