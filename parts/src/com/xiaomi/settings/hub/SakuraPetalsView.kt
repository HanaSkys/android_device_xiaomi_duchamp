/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.xiaomi.settings.R
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hero background: a large, slowly turning sakura blossom plus petals drifting
 * down. Animates only while visible and running, and falls back to a still
 * frame when animations are disabled system-wide.
 */
class SakuraPetalsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private class Petal(
        var x: Float, var y: Float, val size: Float, var angle: Float,
        val spin: Float, val fall: Float, val swayAmp: Float, val swayPhase: Float,
        val alpha: Int,
    )

    private val density = resources.displayMetrics.density
    private val random = Random(0x5A4B)
    private val petals = ArrayList<Petal>()

    /** Unit petal pointing up, centered on the origin, notch at the tip. */
    private val petalPath = Path().apply {
        moveTo(0f, 0.5f)
        cubicTo(-0.46f, 0.2f, -0.42f, -0.34f, -0.17f, -0.5f)
        lineTo(0f, -0.37f)
        lineTo(0.17f, -0.5f)
        cubicTo(0.42f, -0.34f, 0.46f, 0.2f, 0f, 0.5f)
        close()
    }

    private val petalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.hub_petal)
    }
    private val flowerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.hub_hero_flower)
    }

    private var running = false
    private var lastFrame = 0L
    private var flowerAngle = 0f

    fun start() {
        if (running) return
        running = true
        lastFrame = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        petals.clear()
        repeat(PETAL_COUNT) { petals += newPetal(w, h, random.nextFloat() * h) }
    }

    private fun newPetal(w: Int, h: Int, y: Float) = Petal(
        x = random.nextFloat() * w,
        y = y,
        size = (9 + random.nextFloat() * 9) * density,
        angle = random.nextFloat() * 360f,
        spin = (random.nextFloat() - 0.5f) * 70f,
        fall = (10 + random.nextFloat() * 14) * density,
        swayAmp = (6 + random.nextFloat() * 14) * density,
        swayPhase = random.nextFloat() * 2f * PI.toFloat(),
        alpha = 90 + random.nextInt(130),
    )

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val animate = running && ValueAnimator.areAnimatorsEnabled()
        val dt = if (animate) ((now - lastFrame).coerceIn(0L, 64L) / 1000f) else 0f
        lastFrame = now

        // Big blossom peeking from the right edge.
        flowerAngle = (flowerAngle + dt * 6f) % 360f
        val r = height * 0.62f
        canvas.save()
        canvas.translate(width - r * 0.35f, height * 0.42f)
        canvas.rotate(flowerAngle)
        repeat(5) {
            canvas.save()
            canvas.translate(0f, -r * 0.5f)
            canvas.scale(r * 0.95f, r)
            canvas.drawPath(petalPath, flowerPaint)
            canvas.restore()
            canvas.rotate(72f)
        }
        canvas.restore()

        val t = now / 1000f
        for (p in petals) {
            p.y += p.fall * dt
            p.angle += p.spin * dt
            if (p.y - p.size > height) {
                p.y = -p.size
                p.x = random.nextFloat() * width
            }
            val sway = p.swayAmp * sin(t * 0.9f + p.swayPhase)
            canvas.save()
            canvas.translate(p.x + sway, p.y)
            canvas.rotate(p.angle)
            canvas.scale(p.size, p.size)
            petalPaint.alpha = p.alpha
            canvas.drawPath(petalPath, petalPaint)
            canvas.restore()
        }

        if (animate) postInvalidateOnAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && running) {
            lastFrame = SystemClock.uptimeMillis()
            postInvalidateOnAnimation()
        }
    }

    companion object {
        private const val PETAL_COUNT = 14
    }
}
