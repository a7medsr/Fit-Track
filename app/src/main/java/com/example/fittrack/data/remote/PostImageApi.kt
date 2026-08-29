package com.example.fittrack.data.remote

import okhttp3.MultipartBody
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Community post photos on the VPS, alongside the avatars.
 *
 * Post images differ from avatars in the two ways that matter: there are many
 * per user rather than one, and they can be deleted. Both are why they get
 * their own endpoints instead of reusing the avatar route.
 */
interface PostImageApi {

    /**
     * Uploads one image and returns the id needed to delete it later. As with
     * avatars, the stored filename is decided by the server from the verified
     * token, never from anything the client sends.
     */
    @Multipart
    @POST("post-image")
    suspend fun upload(
        @Header("Authorization") authorization: String,
        @Part image: MultipartBody.Part
    ): PostImageResponse

    /**
     * Removes the file from disk.
     *
     * [communityId] is only needed when an admin deletes somebody else's photo:
     * the server checks the claim against Firestore rather than believing it.
     * Deleting your own needs nothing beyond the token.
     */
    @DELETE("post-image/{imageId}")
    suspend fun delete(
        @Header("Authorization") authorization: String,
        @Path("imageId") imageId: String,
        @Query("communityId") communityId: String? = null
    ): PostImageDeleteResponse
}

data class PostImageResponse(
    val imageId: String? = null,
    val url: String? = null,
    val error: String? = null
)

data class PostImageDeleteResponse(
    val ok: Boolean = false,
    val error: String? = null
)
