package com.example.fittrack.ui.splash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.fittrack.R
import kotlin.math.max

/**
 * An ECG trace that draws itself across the screen, with a beat of light where
 * the spike lands.
 *
 * Drawn rather than shipped as a GIF or a Lottie file: it has to scale to any
 * screen width, tint from the brand colour, and be interruptible the instant
 * the user taps. A raster asset would do none of those.
 */
class PulseTraceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 0 = nothing drawn, 1 = the whole trace. */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val brand = ContextCompat.getColor(context, R.color.brand_bright)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = brand
    }

    /**
     * The glow is several passes of the same stroke rather than a
     * BlurMaskFilter, which would force this view onto a software layer and
     * drop the frame rate on exactly the screen that must not stutter.
     */
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = brand
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = brand
    }

    private val fullPath = Path()
    private val drawnPath = Path()
    private val measure = PathMeasure()
    private val position = FloatArray(2)

    private var pathLength = 0f
    private var strokeWidthPx = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        strokeWidthPx = max(w * STROKE_FRACTION, MIN_STROKE_DP * resources.displayMetrics.density)
        linePaint.strokeWidth = strokeWidthPx
        glowPaint.strokeWidth = strokeWidthPx
        ringPaint.strokeWidth = strokeWidthPx * 0.5f
        buildPath(w.toFloat(), h.toFloat())
    }

    /**
     * A stylised ECG: flat baseline, a small P wave, the QRS spike, then the
     * rounded T wave. Straight segments on purpose -- a smoothed curve stops
     * reading as a heartbeat and starts looking like a stock chart.
     */
    private fun buildPath(w: Float, h: Float) {
        val midY = h / 2f
        val amp = h * AMPLITUDE_FRACTION

        fullPath.reset()
        POINTS.forEachIndexed { index, (fraction, offset) ->
            val x = w * fraction
            val y = midY + offset * amp
            if (index == 0) fullPath.moveTo(x, y) else fullPath.lineTo(x, y)
        }

        measure.setPath(fullPath, false)
        pathLength = measure.length
    }

    override fun onDraw(canvas: Canvas) {
        if (pathLength <= 0f || progress <= 0f) return

        val end = pathLength * progress
        drawnPath.reset()
        measure.getSegment(0f, end, drawnPath, true)

        // Widest and faintest first, so the passes build up into a halo with
        // the crisp stroke sitting on top.
        for (pass in GLOW_PASSES downTo 1) {
            glowPaint.strokeWidth = strokeWidthPx * (1f + pass * 1.4f)
            glowPaint.alpha = (GLOW_ALPHA / pass).toInt()
            canvas.drawPath(drawnPath, glowPaint)
        }
        canvas.drawPath(drawnPath, linePaint)

        // The beat: a ring expanding out of the spike once the trace reaches it.
        if (progress > SPIKE_AT) {
            val beat = ((progress - SPIKE_AT) / (1f - SPIKE_AT)).coerceIn(0f, 1f)
            ringPaint.alpha = ((1f - beat) * RING_ALPHA).toInt()
            canvas.drawCircle(
                width * SPIKE_X,
                height / 2f - height * AMPLITUDE_FRACTION,
                beat * width * RING_MAX_FRACTION,
                ringPaint
            )
        }

        // The leading edge, so the line reads as being drawn rather than wiped.
        if (progress < 1f) {
            measure.getPosTan(end, position, null)
            canvas.drawCircle(position[0], position[1], strokeWidthPx * 0.9f, dotPaint)
        }
    }

    private companion object {
        /** x as a fraction of width, y as a multiple of the amplitude (negative is up). */
        val POINTS = listOf(
            0.00f to 0f,
            0.26f to 0f,
            0.32f to -0.18f,   // P wave
            0.37f to 0f,
            0.42f to 0.22f,    // Q
            0.47f to -1.00f,   // R, the spike
            0.52f to 0.42f,    // S
            0.57f to 0f,
            0.68f to 0f,
            0.75f to -0.30f,   // T wave
            0.82f to 0f,
            1.00f to 0f
        )

        const val SPIKE_X = 0.47f
        /** Where along the animation the spike is reached, matching SPIKE_X. */
        const val SPIKE_AT = 0.47f
        const val AMPLITUDE_FRACTION = 0.28f
        const val STROKE_FRACTION = 0.008f
        const val MIN_STROKE_DP = 3f
        const val GLOW_PASSES = 3
        const val GLOW_ALPHA = 70f
        const val RING_ALPHA = 150f
        const val RING_MAX_FRACTION = 0.42f
    }
}
