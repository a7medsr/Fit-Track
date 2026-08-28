package com.example.fittrack.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Profile pictures on the VPS. The image file lives in a folder on the server;
 * only the returned URL is stored in Firestore.
 */
interface AvatarApi {

    /**
     * Uploads one image. The server decides the stored filename from the
     * authenticated user, never from the client's filename -- a client-supplied
     * name is how "../../" ends up writing outside the upload folder.
     */
    @Multipart
    @POST("avatar")
    suspend fun upload(
        @Header("Authorization") authorization: String,
        @Part image: MultipartBody.Part
    ): AvatarUploadResponse

    /** Fetches an avatar by absolute URL, for a device that has not cached it. */
    @Streaming
    @GET
    suspend fun download(@Url url: String): ResponseBody
}

data class AvatarUploadResponse(
    val url: String? = null,
    val error: String? = null
)
