/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import com.xiaomi.settings.R
import kotlin.math.roundToInt

/** Integer slider in the Hanita Mods palette. */
class SakuraSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var min = 0
    var max = 100

    /** Fired continuously while dragging (for the live label). */
    var onValueChanging: ((Int) -> Unit)? = null

    /** Fired once when the user lets go (or uses accessibility actions). */
    var onValueCommitted: ((Int) -> Unit)? = null

    var value = 0
        private set

    private var dragging = false
    private val density = resources.displayMetrics.density
    private val trackH = 8 * density
    private val thumbR = 11 * density
    private val haloR = 20 * density

    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.hub_toggle_track_off)
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.hub_accent)
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.hub_ripple)
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.hub_accent)
    }

    init {
        isFocusable = true
    }

    fun setValue(newValue: Int) {
        if (dragging) return
        value = newValue.coerceIn(min, max)
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (haloR * 2 + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    private val trackStart get() = paddingLeft + haloR
    private val trackEnd get() = width - paddingRight - haloR

    private fun xFor(v: Int) =
        if (max <= min) trackStart else trackStart + (trackEnd - trackStart) * (v - min) / (max - min).toFloat()

    private fun valueAt(x: Float): Int {
        val f = ((x - trackStart) / (trackEnd - trackStart)).coerceIn(0f, 1f)
        return (min + f * (max - min)).roundToInt()
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val x = xFor(value)
        val r = trackH / 2f
        canvas.drawRoundRect(trackStart, cy - r, trackEnd, cy + r, r, r, inactivePaint)
        canvas.drawRoundRect(trackStart, cy - r, x, cy + r, r, r, activePaint)
        if (dragging) canvas.drawCircle(x, cy, haloR, haloPaint)
        canvas.drawCircle(x, cy, thumbR, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                update(event.x)
            }
            MotionEvent.ACTION_MOVE -> update(event.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                update(event.x)
                dragging = false
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                onValueCommitted?.invoke(value)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun update(x: Float) {
        val v = valueAt(x)
        if (v != value) {
            value = v
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onValueChanging?.invoke(v)
        }
        invalidate()
    }

    override fun getAccessibilityClassName(): CharSequence = SeekBar::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
            min.toFloat(), max.toFloat(), value.toFloat())
        if (isEnabled) {
            if (value > min) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            if (value < max) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (isEnabled) {
            val step = when (action) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> 1
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> -1
                else -> 0
            }
            if (step != 0) {
                value = (value + step).coerceIn(min, max)
                invalidate()
                onValueChanging?.invoke(value)
                onValueCommitted?.invoke(value)
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }
}
