package com.example.fittrack.domain.repository

/** Outcome of a sync attempt, for the UI to report. */
sealed class SyncResult {
    data class Success(val pulled: Int, val pushed: Int) : SyncResult()
    object NotSignedIn : SyncResult()
    data class Failure(val message: String) : SyncResult()
}

interface SyncRepository {
    /**
     * Merges the cloud copy into the local database, then uploads the result.
     * Run on sign-in: anything already on the device is adopted into the
     * account rather than discarded.
     */
    suspend fun syncOnSignIn(): SyncResult

    /** Uploads the current local state. No-op when signed out. */
    suspend fun pushLocalState(): SyncResult
}
