package com.example.fittrack.data.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prepares a picked photo for upload to a community post.
 *
 * The server re-encodes everything it receives anyway, so this is not a
 * security measure -- it is a bandwidth one. A modern phone photo is four to
 * eight megabytes, and sending that untouched over mobile data to produce a
 * 1080px feed image would be most of a megabyte of waste per post.
 *
 * The prepared file goes to the cache directory, not to files/: it is needed
 * only until the upload finishes, and the system may reclaim it afterwards.
 */
@Singleton
class PostImageStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Returns a temporary JPEG ready to upload, or null if it was unreadable. */
    fun prepare(uri: Uri): File? = try {
        prepareOrThrow(uri)
    } catch (e: OutOfMemoryError) {
        null // a pathologically large image must not take the app down
    } catch (e: Exception) {
        null
    }

    private fun prepareOrThrow(uri: Uri): File? {
        val source = ImagePipeline.decodeDownscaled(context, uri, TARGET_PX) ?: return null
        val upright = ImagePipeline.applyOrientation(context, uri, source)
        val fitted = ImagePipeline.fitInside(upright, TARGET_PX)

        val target = File(cacheDir(), "post_${System.currentTimeMillis()}.jpg")
        target.outputStream().use { out ->
            fitted.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }

        with(ImagePipeline) {
            fitted.recycleUnless(upright)
            upright.recycleUnless(source)
        }
        source.recycle()

        return target.takeIf { it.length() > 0 }
    }

    /**
     * Clears anything left behind by an upload that never completed. Called on
     * the way into the composer rather than on a timer, which is enough: these
     * are cache files, and the system will evict them under pressure anyway.
     */
    fun clearStale(olderThanMs: Long = STALE_AFTER_MS) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        cacheDir().listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    fun discard(file: File?) {
        file?.takeIf { it.exists() }?.delete()
    }

    private fun cacheDir(): File =
        File(context.cacheDir, DIR).apply { if (!exists()) mkdirs() }

    private companion object {
        const val DIR = "post_images"
        // Matches what the server produces, so it never has to enlarge or
        // shrink again -- the upload is already the final size.
        const val TARGET_PX = 1080
        const val JPEG_QUALITY = 85
        const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L
    }
}
