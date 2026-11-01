/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.xiaomi.settings.R

/**
 * Hand-drawn sakura landscape with a magnifier over a torii gate. [sharpness]
 * (1 = native) softens the magnified detail to show what a lower resolution
 * does. Purely illustrative.
 */
class ResolutionPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var sharpness = 1f
        set(value) { field = value; lens = null; invalidate() }

    var selectedOption = false
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val radius = 18 * density
    private var scene: Bitmap? = null
    private var lens: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFFFFFFFF.toInt()
    }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        color = context.getColor(R.color.hub_accent)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 2.15f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scene?.recycle()
        scene = if (w > 0 && h > 0) drawScene(w, h) else null
        lens = null
    }

    private val inset get() = 6 * density

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val bmp = scene ?: drawScene(width, height).also { scene = it; lens = null }
        val r = RectF(inset, inset, width - inset, height - inset)
        clip.reset()
        clip.addRoundRect(r, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(bmp, null, r, paint)

        // Magnifier: 2x over the torii, softened by downscaling first.
        val cx = width * 0.42f
        val cy = height * 0.53f
        val lr = width * 0.27f
        val src = Rect((cx - lr / 2).toInt(), (cy - lr / 2).toInt(),
            (cx + lr / 2).toInt(), (cy + lr / 2).toInt())
        val l = lens ?: makeLens(bmp, src).also { lens = it }
        val dst = RectF(cx - lr, cy - lr, cx + lr, cy + lr)
        val circle = Path().apply { addCircle(cx, cy, lr, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(circle)
        canvas.drawBitmap(l, null, dst, paint)
        canvas.restore()
        canvas.drawCircle(cx, cy, lr, ringPaint)
        canvas.restore()

        if (selectedOption) {
            val s = selectPaint.strokeWidth / 2
            canvas.drawRoundRect(RectF(s, s, width - s, height - s),
                radius + inset, radius + inset, selectPaint)
        }
    }

    private fun makeLens(scene: Bitmap, src: Rect): Bitmap {
        val crop = Bitmap.createBitmap(scene, src.left, src.top, src.width(), src.height())
        if (sharpness >= 1f) return crop
        val w = (src.width() * sharpness).toInt().coerceAtLeast(8)
        val h = (src.height() * sharpness).toInt().coerceAtLeast(8)
        return Bitmap.createScaledBitmap(crop, w, h, true)
    }

    /** Sky, mountains, hills, a sakura tree and a torii, all in code. */
    private fun drawScene(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val W = w.toFloat()
        val H = h.toFloat()

        p.shader = LinearGradient(0f, 0f, 0f, H * 0.5f,
            0xFF8EC5FF.toInt(), 0xFFFFD9E8.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p)
        p.shader = null

        p.color = 0xFFFFF4D6.toInt()
        c.drawCircle(W * 0.78f, H * 0.12f, W * 0.09f, p)

        fun ridge(color: Int, base: Float, vararg peaks: Pair<Float, Float>) {
            val path = Path().apply {
                moveTo(0f, H * base)
                peaks.forEach { (x, y) -> lineTo(W * x, H * y) }
                lineTo(W, H * base); lineTo(W, H); lineTo(0f, H); close()
            }
            p.color = color
            c.drawPath(path, p)
        }
        ridge(0xFF9AA7D9.toInt(), 0.40f, 0.15f to 0.22f, 0.32f to 0.33f, 0.55f to 0.18f,
            0.75f to 0.30f, 0.92f to 0.24f)
        // Snow caps
        p.color = 0xFFF6F8FF.toInt()
        c.drawPath(Path().apply {
            moveTo(W * 0.55f, H * 0.18f); lineTo(W * 0.50f, H * 0.215f)
            lineTo(W * 0.60f, H * 0.215f); close()
        }, p)
        ridge(0xFF6E86C8.toInt(), 0.46f, 0.08f to 0.36f, 0.30f to 0.42f, 0.48f to 0.33f,
            0.70f to 0.41f, 1f to 0.35f)

        p.shader = LinearGradient(0f, H * 0.45f, 0f, H,
            0xFF8FD18A.toInt(), 0xFF3E8E57.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(Path().apply {
            moveTo(0f, H * 0.50f)
            quadTo(W * 0.5f, H * 0.44f, W, H * 0.52f)
            lineTo(W, H); lineTo(0f, H); close()
        }, p)
        p.shader = null

        // Path to the torii
        p.color = 0xFFE9D8C0.toInt()
        c.drawPath(Path().apply {
            moveTo(W * 0.38f, H * 0.585f); lineTo(W * 0.46f, H * 0.585f)
            lineTo(W * 0.80f, H); lineTo(W * 0.10f, H); close()
        }, p)

        // Torii gate (the magnified detail)
        val gx = W * 0.42f
        val gy = H * 0.53f
        val gw = W * 0.16f
        p.color = 0xFFD8343F.toInt()
        c.drawRect(gx - gw * 0.38f, gy - gw * 0.28f, gx - gw * 0.30f, gy + gw * 0.42f, p)
        c.drawRect(gx + gw * 0.30f, gy - gw * 0.28f, gx + gw * 0.38f, gy + gw * 0.42f, p)
        c.drawRect(gx - gw * 0.46f, gy - gw * 0.20f, gx + gw * 0.46f, gy - gw * 0.14f, p)
        p.color = 0xFF2B1A1F.toInt()
        c.drawPath(Path().apply {
            moveTo(gx - gw * 0.58f, gy - gw * 0.40f)
            quadTo(gx, gy - gw * 0.30f, gx + gw * 0.58f, gy - gw * 0.40f)
            lineTo(gx + gw * 0.52f, gy - gw * 0.30f)
            quadTo(gx, gy - gw * 0.22f, gx - gw * 0.52f, gy - gw * 0.30f)
            close()
        }, p)
        p.color = 0xFFD8343F.toInt()
        c.drawRect(gx - gw * 0.04f, gy - gw * 0.28f, gx + gw * 0.04f, gy - gw * 0.14f, p)

        // Sakura trees
        fun tree(x: Float, y: Float, s: Float) {
            p.color = 0xFF6B4636.toInt()
            c.drawRect(x - s * 0.05f, y, x + s * 0.05f, y + s * 0.55f, p)
            val blooms = intArrayOf(0xFFF7A8C4.toInt(), 0xFFFFC7DA.toInt(), 0xFFF28DB2.toInt())
            val dots = arrayOf(0f to -0.05f, -0.28f to 0.08f, 0.28f to 0.06f,
                -0.15f to -0.22f, 0.17f to -0.20f, 0f to 0.15f)
            dots.forEachIndexed { i, (dx, dy) ->
                p.color = blooms[i % blooms.size]
                c.drawCircle(x + dx * s, y + dy * s, s * 0.26f, p)
            }
        }
        tree(W * 0.16f, H * 0.60f, W * 0.30f)
        tree(W * 0.80f, H * 0.66f, W * 0.36f)
        tree(W * 0.62f, H * 0.52f, W * 0.14f)

        // Falling petals
        p.color = 0xFFFFB3CE.toInt()
        val petals = arrayOf(0.10f to 0.30f, 0.30f to 0.44f, 0.55f to 0.62f, 0.70f to 0.48f,
            0.88f to 0.80f, 0.40f to 0.86f, 0.22f to 0.74f, 0.64f to 0.92f)
        petals.forEach { (x, y) -> c.drawOval(W * x, H * y, W * x + W * 0.025f, H * y + W * 0.015f, p) }
        return bmp
    }

    override fun onDetachedFromWindow() {
        scene?.recycle(); scene = null; lens = null
        super.onDetachedFromWindow()
    }
}
