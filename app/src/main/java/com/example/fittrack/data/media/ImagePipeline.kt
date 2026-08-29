package com.example.fittrack.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Decoding a picked photo safely, shared by avatars and community posts.
 *
 * Both need the same three awkward things -- decode without running out of
 * memory, honour the orientation tag, and drop every other tag -- and getting
 * any of them subtly wrong looks like a broken image rather than a bug.
 */
object ImagePipeline {

    /**
     * Two passes: measure first, then decode with inSampleSize, so a 12 MP
     * photo never lands in memory at full size just to be shrunk.
     *
     * [maxPx] is the longest edge the caller intends to keep. The sample factor
     * deliberately stops one step short of it, leaving the final resize to work
     * from more pixels than it needs rather than fewer.
     */
    fun decodeDownscaled(context: Context, uri: Uri, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream returns null on purpose in bounds-only mode, so success
        // is judged by outWidth. Treating that null as failure is what makes an
        // otherwise fine image look unreadable.
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxPx * 2 && bounds.outHeight / sample > maxPx * 2) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val pixelStream = context.contentResolver.openInputStream(uri) ?: return null
        return pixelStream.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /**
     * Rotates the pixels to match the EXIF orientation tag.
     *
     * Read before re-encoding, and applied rather than copied: re-encoding from
     * a decoded bitmap drops every tag, so a portrait photo whose orientation
     * was only ever a tag would come out sideways and stay that way.
     */
    fun applyOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
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

    /** Centre square, for a picture that will be shown in a circle. */
    fun cropToSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        if (bitmap.width == bitmap.height) return bitmap
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        return runCatching { Bitmap.createBitmap(bitmap, x, y, side, side) }.getOrDefault(bitmap)
    }

    /**
     * Scales so the longest edge is [maxPx], keeping the aspect ratio, and
     * never enlarges. A post photo is not a portrait: cropping it square would
     * cut the subject out of half of them.
     */
    fun fitInside(bitmap: Bitmap, maxPx: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxPx) return bitmap
        val scale = maxPx.toFloat() / longest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return runCatching {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }.getOrDefault(bitmap)
    }

    /** Recycles [this] unless it is the same instance as [keep]. */
    fun Bitmap.recycleUnless(keep: Bitmap) {
        if (this !== keep) recycle()
    }
}
