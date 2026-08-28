package com.example.fittrack.data.avatar

import android.net.Uri
import com.example.fittrack.data.prefs.UserPreferences
import com.example.fittrack.data.remote.AvatarApi
import com.example.fittrack.domain.repository.AuthRepository
import com.example.fittrack.domain.repository.AvatarRepository
import com.example.fittrack.domain.repository.AvatarResult
import com.example.fittrack.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profile pictures: the image file goes to a folder on the VPS, and only the
 * URL it comes back with is stored (and synced through the profile).
 *
 * Local-first like the rest of the app -- the picked image is written to disk
 * and shown before the upload is attempted, so choosing a picture never appears
 * to fail just because the network did.
 */
@Singleton
class AvatarRepositoryImpl @Inject constructor(
    private val avatarApi: AvatarApi,
    private val avatarStore: AvatarStore,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val userPreferences: UserPreferences
) : AvatarRepository {

    private val userId: String
        get() = authRepository.currentUser?.uid ?: LOCAL_USER

    override fun localAvatar(): File? = avatarStore.existingFor(userId)

    override fun remoteAvatarUrl(): String? = userPreferences.getAvatarUrl(userId)

    override suspend fun setAvatar(source: Uri): AvatarResult = withContext(Dispatchers.IO) {
        val local = avatarStore.importFrom(source, userId)
            ?: return@withContext AvatarResult.Failure("That image couldn't be read.")

        val token = authRepository.currentIdToken()
            ?: return@withContext AvatarResult.Success(local, null).also {
                // Signed out: the picture is kept on the device only. There is
                // no account to file it under on the server.
                userPreferences.setAvatarUrl(LOCAL_USER, null)
            }

        val uploaded = upload(local, token)
        if (uploaded != null) {
            userPreferences.setAvatarUrl(userId, uploaded)
            // The local file IS this upload, so record it as already cached.
            userPreferences.setCachedAvatarUrl(userId, uploaded)
            // The URL lives in preferences, which SyncScheduler does not watch --
            // it only observes the Room flows. Without this push, the link would
            // reach Firestore only by chance, the next time a workout changed.
            runCatching { syncRepository.pushLocalState() }
        }
        AvatarResult.Success(local, uploaded)
    }

    override suspend fun refreshFromRemote(): File? = withContext(Dispatchers.IO) {
        // Signed out there is no account whose picture this would be, so there
        // is nothing to fetch. Without this guard the device would download the
        // previously signed-in user's avatar into the shared "local" slot.
        val currentUser = authRepository.currentUser?.uid ?: return@withContext null
        val url = userPreferences.getAvatarUrl(currentUser) ?: return@withContext null

        // Only reuse the cached file when it came from this exact URL. Uploads
        // carry a ?v= stamp, so a picture changed on another device produces a
        // different URL and is re-fetched instead of being assumed current.
        val cached = avatarStore.existingFor(userId)
        if (cached != null && userPreferences.getCachedAvatarUrl(currentUser) == url) {
            return@withContext cached
        }

        try {
            val bytes = avatarApi.download(url).bytes()
            avatarStore.writeBytes(userId, bytes)?.also {
                userPreferences.setCachedAvatarUrl(currentUser, url)
            }
        } catch (e: Exception) {
            // A failed refresh must not throw away a picture we already have.
            cached // a missing avatar is cosmetic; never surface it as an error
        }
    }

    override suspend fun clearLocal() = withContext(Dispatchers.IO) {
        avatarStore.delete(userId)
        userPreferences.setAvatarUrl(userId, null)
    }

    /**
     * The server names the stored file from the verified token, so the part
     * filename here is only a label and is deliberately constant rather than
     * anything derived from the user's own file.
     */
    private suspend fun upload(file: File, token: String): String? = try {
        val body = file.asRequestBody(JPEG.toMediaType())
        val part = MultipartBody.Part.createFormData("image", "avatar.jpg", body)
        avatarApi.upload("Bearer $token", part).url?.takeIf { it.isNotBlank() }
    } catch (e: HttpException) {
        null
    } catch (e: IOException) {
        null
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val LOCAL_USER = "local"
        const val JPEG = "image/jpeg"
    }
}
