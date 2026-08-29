package com.example.fittrack.domain.repository

import android.net.Uri
import java.io.File

sealed class AvatarResult {
    /** Saved locally; [remoteUrl] is null when the upload has not landed. */
    data class Success(val file: File, val remoteUrl: String?) : AvatarResult()
    data class Failure(val message: String) : AvatarResult()
}

interface AvatarRepository {
    /** The cached picture for the signed-in user, or null. */
    fun localAvatar(): File?

    /** URL of the avatar on the server, mirrored through the profile sync. */
    fun remoteAvatarUrl(): String?

    /**
     * Saves the picked image locally first, then uploads. A failed upload still
     * returns Success with a null URL: the picture is already on the device and
     * the app stays usable offline.
     */
    suspend fun setAvatar(source: Uri): AvatarResult

    /** Pulls the avatar down when the profile has a URL this device lacks. */
    suspend fun refreshFromRemote(): File?

    suspend fun clearLocal()
}
