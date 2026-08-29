package com.example.fittrack.ui.common

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import coil.load
import coil.transform.CircleCropTransformation
import kotlin.math.absoluteValue

/**
 * Shows a member's photo, or their initials when they have not set one.
 *
 * The initials are not a placeholder while loading -- they are the answer for
 * everyone who never uploaded a picture, which in a new community is most
 * people. They stay visible behind the image so a slow or failed load never
 * leaves an empty circle.
 */
object AvatarLoader {

    /**
     * [image] and [initials] are expected to be stacked in a FrameLayout of the
     * same size, initials underneath.
     */
    fun bind(image: ImageView, initials: TextView, url: String?, name: String) {
        initials.text = initialsOf(name)
        initials.setBackgroundColor(colorFor(name))
        initials.visibility = View.VISIBLE

        if (url.isNullOrBlank()) {
            // Cancels any request still running against a recycled row, which
            // is what otherwise drops the previous member's face into this one.
            image.load(null as String?)
            image.visibility = View.GONE
            return
        }

        image.visibility = View.VISIBLE
        image.load(url) {
            crossfade(true)
            transformations(CircleCropTransformation())
            listener(
                onError = { _, _ -> image.visibility = View.GONE },
                onSuccess = { _, _ -> image.visibility = View.VISIBLE }
            )
        }
    }

    /** Up to two letters: first name and last name where both are present. */
    fun initialsOf(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
        }
    }

    /**
     * A stable colour per name, so the same person is the same colour on every
     * screen and every device. Hue only -- saturation and lightness are fixed
     * so the white initials on top always have enough contrast.
     */
    fun colorFor(name: String): Int {
        val hue = (name.hashCode().absoluteValue % 360).toFloat()
        return android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.42f))
    }

    /** For a plain circular image with no initials behind it. */
    fun loadCircle(image: ImageView, url: String?) {
        if (url.isNullOrBlank()) {
            image.load(null as String?)
            image.setImageDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            return
        }
        image.load(url) {
            crossfade(true)
            transformations(CircleCropTransformation())
        }
    }
}
