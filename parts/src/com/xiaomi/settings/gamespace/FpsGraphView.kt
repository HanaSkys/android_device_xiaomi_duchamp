/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.gamespace

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.xiaomi.settings.R

/** Small line graph of the last FPS readings. */
class FpsGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val values = ArrayDeque<Int>()
    private val path = Path()
    private val density = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.hub_ov_accent)
    }

    fun push(fps: Int) {
        values.addLast(fps)
        while (values.size > MAX_POINTS) values.removeFirst()
        invalidate()
    }

    fun clear() {
        values.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (values.size < 2) return
        val top = maxOf(60, values.maxOrNull() ?: 60).toFloat()
        val pad = line.strokeWidth
        val w = width - 2 * pad
        val h = height - 2 * pad
        path.reset()
        values.forEachIndexed { i, v ->
            val x = pad + w * i / (MAX_POINTS - 1)
            val y = pad + h * (1f - v.coerceAtLeast(0) / top)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, line)
    }

    companion object {
        private const val MAX_POINTS = 30
    }
}
