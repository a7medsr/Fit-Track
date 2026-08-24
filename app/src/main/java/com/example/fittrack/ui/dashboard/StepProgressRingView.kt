package com.example.fittrack.ui.dashboard

import android.content.Context
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.fittrack.R

class StepProgressRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped == field) return
            field = clamped
            shaderProgress = null
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val strokeWidthPx = 20f * density
    private val glowWidthPx = strokeWidthPx + 10f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.ring_track)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
    }

    /** A soft halo behind the arc so the ring reads as lit rather than flat. */
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = glowWidthPx
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.brand)
        alpha = 38
    }

    private val deepColor = ContextCompat.getColor(context, R.color.brand_deep)
    private val brandColor = ContextCompat.getColor(context, R.color.brand)
    private val brightColor = ContextCompat.getColor(context, R.color.brand_bright)

    // Preallocated so onDraw stays allocation free.
    private val arcBounds = RectF()

    /** Progress the current shader was built for; null means it needs rebuilding. */
    private var shaderProgress: Float? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = glowWidthPx / 2
        arcBounds.set(inset, inset, w - inset, h - inset)
        shaderProgress = null
    }

    private fun updateShader() {
        if (shaderProgress == progress) return
        val safeProgress = progress.coerceAtLeast(0.01f) // avoid a 0-length gradient
        val cx = width / 2f
        val cy = height / 2f
        // Deep green through to a bright tip, so the leading edge reads as "now".
        val shader = SweepGradient(
            cx, cy,
            intArrayOf(deepColor, brandColor, brightColor, brightColor),
            floatArrayOf(0f, safeProgress / 2f, safeProgress, 1f)
        )
        // SweepGradient starts at 3 o'clock but the arc starts at 12, so rotate
        // the ramp to keep the bright end aligned with the leading edge.
        shader.setLocalMatrix(Matrix().apply { setRotate(-90f, cx, cy) })
        progressPaint.shader = shader
        shaderProgress = progress
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        canvas.drawArc(arcBounds, -90f, 360f, false, trackPaint)

        val sweep = 360f * progress
        if (sweep <= 0f) return

        updateShader()
        canvas.drawArc(arcBounds, -90f, sweep, false, glowPaint)
        canvas.drawArc(arcBounds, -90f, sweep, false, progressPaint)
    }
}
