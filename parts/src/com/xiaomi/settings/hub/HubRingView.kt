/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.xiaomi.settings.R

/** Battery health gauge. */
class HubRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var stroke = 6f * resources.displayMetrics.density
    private val oval = RectF()
    private var sweep = 0f
    private var shown = -1
    private var animator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = context.getColor(R.color.hub_ring_track)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }

    fun setStrokeWidthDp(dp: Float) {
        stroke = dp * resources.displayMetrics.density
        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke
        invalidate()
    }

    fun setProgress(percent: Int, color: Int) {
        progressPaint.color = color
        if (percent == shown) {
            invalidate()
            return
        }
        shown = percent
        val target = percent.coerceIn(0, 100) * 3.6f
        animator?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            sweep = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(sweep, target).apply {
            duration = 900L
            startDelay = 150L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                sweep = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val half = stroke / 2f
        oval.set(half, half, width - half, height - half)
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)
        if (sweep > 0f) canvas.drawArc(oval, -90f, sweep, false, progressPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
