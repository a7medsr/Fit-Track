package com.example.fittrack.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.fittrack.data.media.ImagePipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a picked photo into a small square JPEG and keeps it on disk.
 *
 * The file is the app's own copy, so the avatar shows instantly and keeps
 * showing with no network -- the same local-first rule the rest of the app
 * follows.
 */
@Singleton
class AvatarStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun fileFor(userId: String): File = File(context.filesDir, "avatar_${safe(userId)}.jpg")

    fun existingFor(userId: String): File? = fileFor(userId).takeIf { it.exists() && it.length() > 0 }

    fun delete(userId: String) {
        fileFor(userId).delete()
    }

    /**
     * Decodes, rotates upright, crops to a centre square, scales down and
     * re-encodes as JPEG.
     *
     * Re-encoding from a decoded Bitmap drops every EXIF tag, which matters:
     * phone photos routinely carry GPS coordinates, and an avatar served from a
     * public URL would otherwise publish where the picture was taken. The
     * orientation tag is read first and applied as a rotation, because throwing
     * it away without acting on it is what leaves portrait photos sideways.
     */
    fun importFrom(uri: Uri, userId: String): File? = try {
        importOrThrow(uri, userId)
    } catch (e: OutOfMemoryError) {
        null // a pathologically large image must not take the app down
    } catch (e: Exception) {
        null
    }

    private fun importOrThrow(uri: Uri, userId: String): File? {
        val source = ImagePipeline.decodeDownscaled(context, uri, TARGET_PX) ?: return null
        val upright = ImagePipeline.applyOrientation(context, uri, source)
        val square = ImagePipeline.cropToSquare(upright)
        val scaled = if (square.width > TARGET_PX) {
            Bitmap.createScaledBitmap(square, TARGET_PX, TARGET_PX, true)
        } else {
            square
        }

        val target = fileFor(userId)
        target.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }

        with(ImagePipeline) {
            scaled.recycleUnless(square)
            square.recycleUnless(upright)
            upright.recycleUnless(source)
        }
        source.recycle()

        return target.takeIf { it.length() > 0 }
    }

    /** Writes bytes fetched from the server straight to the local cache. */
    fun writeBytes(userId: String, bytes: ByteArray): File? {
        if (bytes.isEmpty()) return null
        val target = fileFor(userId)
        target.writeBytes(bytes)
        return target
    }

    fun loadBitmap(userId: String): Bitmap? {
        val file = existingFor(userId) ?: return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /** A uid is used in a filename, so anything path-like is stripped. */
    private fun safe(userId: String): String =
        userId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
            .ifEmpty { "local" }

    companion object {
        const val TARGET_PX = 512
        private const val JPEG_QUALITY = 85
    }
}
