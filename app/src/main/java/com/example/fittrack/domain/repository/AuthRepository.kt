package com.example.fittrack.domain.repository

import com.example.fittrack.domain.model.AuthOutcome
import com.example.fittrack.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Synchronous snapshot, for deciding which screen to open. */
    val currentUser: AuthUser?

    /** Emits on every sign-in and sign-out, starting with the current state. */
    fun observeUser(): Flow<AuthUser?>

    /**
     * [displayName] is required at sign-up because a community shows it to
     * strangers. Without one, the only name available is the email address,
     * and publishing that is a leak, not a fallback.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): AuthOutcome

    /** Renames the account everywhere, including on the user's other devices. */
    suspend fun updateDisplayName(displayName: String): AuthOutcome
    suspend fun signInWithEmail(email: String, password: String): AuthOutcome
    suspend fun signInWithGoogle(idToken: String): AuthOutcome
    suspend fun sendPasswordReset(email: String): AuthOutcome
    /**
     * Short-lived Firebase ID token, for the VPS to verify server-side. Null
     * when signed out.
     */
    suspend fun currentIdToken(): String?

    fun signOut()
}
