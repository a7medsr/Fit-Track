package com.example.fittrack.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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
        val source = decodeDownscaled(uri) ?: return null
        val upright = applyOrientation(uri, source)
        val square = cropToSquare(upright)
        val scaled = if (square.width > TARGET_PX) {
            Bitmap.createScaledBitmap(square, TARGET_PX, TARGET_PX, true)
        } else {
            square
        }

        val target = fileFor(userId)
        target.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }

        if (scaled !== square) scaled.recycle()
        if (square !== upright) square.recycle()
        if (upright !== source) upright.recycle()
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

    /**
     * Two passes: measure first, then decode with inSampleSize, so a 12 MP
     * photo never lands in memory at full size just to be shrunk.
     */
    private fun decodeDownscaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream returns null on purpose in bounds-only mode, so success
        // is judged by outWidth. Treating that null as failure is what makes an
        // otherwise fine image look unreadable.
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > TARGET_PX * 2 && bounds.outHeight / sample > TARGET_PX * 2) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val pixelStream = context.contentResolver.openInputStream(uri) ?: return null
        return pixelStream.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun applyOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        if (bitmap.width == bitmap.height) return bitmap
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        return runCatching { Bitmap.createBitmap(bitmap, x, y, side, side) }.getOrDefault(bitmap)
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
