package com.example.fittrack.domain.repository

import com.example.fittrack.domain.model.AuthOutcome
import com.example.fittrack.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Synchronous snapshot, for deciding which screen to open. */
    val currentUser: AuthUser?

    /** Emits on every sign-in and sign-out, starting with the current state. */
    fun observeUser(): Flow<AuthUser?>

    suspend fun signUpWithEmail(email: String, password: String): AuthOutcome
    suspend fun signInWithEmail(email: String, password: String): AuthOutcome
    suspend fun signInWithGoogle(idToken: String): AuthOutcome
    suspend fun sendPasswordReset(email: String): AuthOutcome
    fun signOut()
}
