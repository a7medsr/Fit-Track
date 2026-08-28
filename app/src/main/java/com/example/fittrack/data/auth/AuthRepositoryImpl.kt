package com.example.fittrack.data.auth

import com.example.fittrack.domain.model.AuthOutcome
import com.example.fittrack.domain.model.AuthUser
import com.example.fittrack.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
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

    override suspend fun signUpWithEmail(email: String, password: String): AuthOutcome =
        runAuth {
            firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await().user
        }

    override suspend fun signInWithEmail(email: String, password: String): AuthOutcome =
        runAuth {
            firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await().user
        }

    override suspend fun signInWithGoogle(idToken: String): AuthOutcome =
        runAuth {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await().user
        }

    override suspend fun sendPasswordReset(email: String): AuthOutcome {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return AuthOutcome.Failure("Enter your email address first.")
        return try {
            firebaseAuth.sendPasswordResetEmail(trimmed).await()
            // No user to hand back; the caller only cares that it did not fail.
            AuthOutcome.Success(AuthUser(uid = "", email = trimmed, displayName = null))
        } catch (e: Exception) {
            AuthOutcome.Failure(e.toMessage())
        }
    }

    override suspend fun currentIdToken(): String? = try {
        firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
    } catch (e: Exception) {
        null
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    private inline fun runAuth(block: () -> FirebaseUser?): AuthOutcome =
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

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName ?: email?.substringBefore('@')
    )

    /**
     * Firebase messages are aimed at developers ("The supplied auth credential
     * is malformed or has expired"), so the common cases get rewritten.
     */
    private fun Exception.toMessage(): String = when (this) {
        is FirebaseAuthWeakPasswordException ->
            "That password is too weak. Use at least 6 characters."
        is FirebaseAuthUserCollisionException ->
            "An account already exists for that email. Try signing in instead."
        is FirebaseAuthInvalidUserException ->
            "No account found for that email."
        is FirebaseAuthInvalidCredentialsException ->
            "Wrong email or password."
        is IOException ->
            "No connection. Check your network and try again."
        else -> message ?: "Something went wrong. Try again."
    }
}
