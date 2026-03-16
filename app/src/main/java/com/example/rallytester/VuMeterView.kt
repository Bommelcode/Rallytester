package com.example.rallytester

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Horizontal segmented VU meter.
 *  - 40 segments: green (0–70%) → yellow (70–90%) → red (90–100%)
 *  - White peak-hold needle that slowly decays
 * Usage:
 *   vuMeter.setLevel(0f..1f)   — update current RMS level
 *   vuMeter.decayPeak()        — call every ~80ms to let peak fall
 */
class VuMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var level: Float = 0f
    private var peakLevel: Float = 0f

    private val bgPaint = Paint().apply { color = Color.parseColor("#1C1C1C") }
    private val segPaint = Paint().apply { isAntiAlias = true }
    private val peakPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#3A3A3A")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val bgRect  = RectF()
    private val segRect = RectF()

    companion object {
        private const val SEGMENTS = 40
        private const val GREEN_THRESHOLD  = 0.70f
        private const val YELLOW_THRESHOLD = 0.90f

        // Lit colours
        private val COL_GREEN  = Color.parseColor("#00E676")
        private val COL_YELLOW = Color.parseColor("#FFD600")
        private val COL_RED    = Color.parseColor("#FF1744")

        // Unlit (dim) colours
        private val DIM_GREEN  = Color.parseColor("#0A2A14")
        private val DIM_YELLOW = Color.parseColor("#2A2000")
        private val DIM_RED    = Color.parseColor("#2A0A0A")
    }

    fun setLevel(rms: Float) {
        level = rms.coerceIn(0f, 1f)
        if (level > peakLevel) peakLevel = level
        invalidate()
    }

    fun decayPeak(step: Float = 0.008f) {
        if (peakLevel > 0f) {
            peakLevel = (peakLevel - step).coerceAtLeast(0f)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 3f

        // Background pill
        bgRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(bgRect, r, r, bgPaint)

        // Segments
        val gap  = 1.5f
        val segW = (w - gap * (SEGMENTS - 1)) / SEGMENTS
        val litCount = (level * SEGMENTS).toInt()

        for (i in 0 until SEGMENTS) {
            val x = i * (segW + gap)
            segRect.set(x, 2f, x + segW, h - 2f)

            val frac = i.toFloat() / SEGMENTS
            val lit = i < litCount
            segPaint.color = when {
                frac >= YELLOW_THRESHOLD -> if (lit) COL_RED    else DIM_RED
                frac >= GREEN_THRESHOLD  -> if (lit) COL_YELLOW else DIM_YELLOW
                else                     -> if (lit) COL_GREEN  else DIM_GREEN
            }
            canvas.drawRoundRect(segRect, 1.5f, 1.5f, segPaint)
        }

        // Peak-hold needle
        if (peakLevel > 0f) {
            val px = (peakLevel * w).coerceIn(2f, w - 2f)
            canvas.drawLine(px, 1f, px, h - 1f, peakPaint)
        }

        // Border
        canvas.drawRoundRect(bgRect, r, r, borderPaint)
    }
}
