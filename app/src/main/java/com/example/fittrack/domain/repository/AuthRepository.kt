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
     * The only way in. Google is the sole provider, so the first sign-in
     * creates the account and every one after it returns to the same one --
     * there is no separate sign-up, and no password to reset.
     */
    suspend fun signInWithGoogle(idToken: String): AuthOutcome

    /** Renames the account everywhere, including on the user's other devices. */
    suspend fun updateDisplayName(displayName: String): AuthOutcome

    /**
     * Short-lived Firebase ID token, for the VPS to verify server-side. Null
     * when signed out.
     */
    suspend fun currentIdToken(): String?

    fun signOut()
}
