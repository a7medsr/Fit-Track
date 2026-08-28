package com.example.fittrack.domain.repository

/** Outcome of a sync attempt, for the UI to report. */
sealed class SyncResult {
    data class Success(val pulled: Int, val pushed: Int) : SyncResult()
    object NotSignedIn : SyncResult()
    data class Failure(val message: String) : SyncResult()
}

interface SyncRepository {
    /**
     * Timestamp of the last successful pull. Screens observe this to refresh
     * anything that lives outside Room -- the avatar, for instance -- once new
     * remote state has landed.
     */
    val lastPullAt: kotlinx.coroutines.flow.StateFlow<Long>

    /**
     * Fetches remote state and merges it in, without pushing.
     *
     * Runs on every app start while signed in. Without it the only pull is at
     * sign-in, so a device that already had a session would never see anything
     * created on another device.
     */
    suspend fun pullRemoteState(): SyncResult

    /**
     * Merges the cloud copy into the local database, then uploads the result.
     * Run on sign-in: anything already on the device is adopted into the
     * account rather than discarded.
     */
    suspend fun syncOnSignIn(): SyncResult

    /** Uploads the current local state. No-op when signed out. */
    suspend fun pushLocalState(): SyncResult
}
