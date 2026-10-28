/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.PathInterpolator
import android.widget.Checkable
import android.widget.Switch
import com.xiaomi.settings.R

/** Pill switch in the Hanita Mods palette (thumb grows when on, like M3). */
class SakuraToggle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), Checkable {

    /** Called only for changes made by the user, never for [setChecked]. */
    var onUserToggle: ((Boolean) -> Unit)? = null

    private var checked = false
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val density = resources.displayMetrics.density
    private val trackW = 52 * density
    private val trackH = 32 * density
    private val outline = 2 * density
    private val rect = RectF()
    private val argb = ArgbEvaluator()

    private val trackOff = context.getColor(R.color.hub_toggle_track_off)
    private val trackOn = context.getColor(R.color.hub_accent)
    private val outlineOff = context.getColor(R.color.hub_toggle_outline)
    private val thumbOff = context.getColor(R.color.hub_toggle_thumb_off)
    private val thumbOn = context.getColor(R.color.hub_toggle_thumb_on)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = outline
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { toggleByUser() }
    }

    fun toggleByUser() {
        setChecked(!checked, animate = true)
        onUserToggle?.invoke(checked)
    }

    override fun isChecked() = checked

    override fun setChecked(value: Boolean) = setChecked(value, animate = false)

    override fun toggle() = setChecked(!checked, animate = true)

    fun setChecked(value: Boolean, animate: Boolean) {
        if (value == checked) return
        checked = value
        val target = if (value) 1f else 0f
        animator?.cancel()
        animator = null
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || !isAttachedToWindow) {
            progress = target
            invalidate()
        } else {
            animator = ValueAnimator.ofFloat(progress, target).apply {
                duration = 260L
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        refreshDrawableState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((trackW + paddingLeft + paddingRight).toInt(), widthMeasureSpec),
            resolveSize((trackH + paddingTop + paddingBottom).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val left = paddingLeft + (width - paddingLeft - paddingRight - trackW) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - trackH) / 2f
        rect.set(left, top, left + trackW, top + trackH)
        val radius = trackH / 2f

        trackPaint.color = argb.evaluate(progress, trackOff, trackOn) as Int
        canvas.drawRoundRect(rect, radius, radius, trackPaint)
        if (progress < 1f) {
            outlinePaint.color = outlineOff
            outlinePaint.alpha = ((1f - progress) * 255).toInt()
            val inset = outline / 2f
            rect.inset(inset, inset)
            canvas.drawRoundRect(rect, radius - inset, radius - inset, outlinePaint)
            rect.inset(-inset, -inset)
        }

        val thumbR = (8 + 4 * progress) * density
        val startX = left + radius
        val endX = left + trackW - radius
        thumbPaint.color = argb.evaluate(progress, thumbOff, thumbOn) as Int
        canvas.drawCircle(startX + (endX - startX) * progress, top + radius, thumbR, thumbPaint)
    }

    override fun getAccessibilityClassName(): CharSequence = Switch::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isCheckable = true
        info.isChecked = checked
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
