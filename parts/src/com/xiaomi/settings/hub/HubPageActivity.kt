/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import com.xiaomi.settings.R

/**
 * Shared frame of every Hanita Mods screen: edge-to-edge insets, back button,
 * entrance animation and press feedback. Layouts must provide hub_scroll,
 * hub_content and hub_back.
 */
abstract class HubPageActivity : Activity() {

    protected val handler = Handler(Looper.getMainLooper())

    protected fun setupPage(layoutRes: Int) {
        setContentView(layoutRes)
        val content = findViewById<ViewGroup>(R.id.hub_content)
        val pad = content.paddingStart
        val padBottom = content.paddingBottom
        findViewById<View>(R.id.hub_scroll).setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            content.setPadding(pad + bars.left, bars.top, pad + bars.right, padBottom + bars.bottom)
            WindowInsets.CONSUMED
        }
        findViewById<View>(R.id.hub_back).setOnClickListener { finish() }
    }

    /** Runs after the subclass has hidden what the device does not support. */
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (savedInstanceState == null) animateEntrance(findViewById(R.id.hub_content))
    }

    protected fun getSecure(key: String, def: Int = 0) =
        Settings.Secure.getInt(contentResolver, key, def)

    protected fun putSecure(key: String, value: Boolean) =
        Settings.Secure.putInt(contentResolver, key, if (value) 1 else 0)

    /** Cards rise in one after another. */
    private fun animateEntrance(content: ViewGroup) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val offset = 28 * resources.displayMetrics.density
        val easing = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        var index = 0
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            child.alpha = 0f
            child.translationY = offset
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(50L * index++)
                .setDuration(480L)
                .setInterpolator(easing)
                .start()
        }
    }

    /** Gentle squeeze while a card is pressed. */
    protected fun addPressFeedback(vararg views: View) {
        views.forEach { card ->
            card.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(120L).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(200L).start()
                }
                false
            }
        }
    }
}
