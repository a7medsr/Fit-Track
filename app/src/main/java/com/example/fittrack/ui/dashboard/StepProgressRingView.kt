package com.example.fittrack.ui.dashboard

import android.content.Context
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
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    private val strokeWidthPx = 28f * resources.displayMetrics.density

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

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val rect = RectF(
            strokeWidthPx / 2, strokeWidthPx / 2,
            width - strokeWidthPx / 2, height - strokeWidthPx / 2
        )
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)

        progressPaint.shader = SweepGradient(
            width / 2f, height / 2f,
            intArrayOf(
                ContextCompat.getColor(context, R.color.accent_green),
                ContextCompat.getColor(context, R.color.accent_blue)
            ),
            null
        )
        canvas.drawArc(rect, -90f, 360f * progress, false, progressPaint)
    }
}