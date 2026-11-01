/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.graphics.Point
import android.os.UserHandle
import android.util.Log
import android.view.Display
import android.view.IWindowManager
import android.view.WindowManagerGlobal
import com.xiaomi.settings.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Display resolution switching. The panel only exposes 1220x2712 modes, so this
 * uses the forced display size/density (same mechanism as `wm size`/`wm density`):
 * the UI is rendered at the lower resolution and the compositor scales it up.
 * The density is scaled in proportion, keeping the user's display size choice.
 */
object DisplayResolution {
    private const val TAG = "HanitaResolution"
    private const val DISPLAY = Display.DEFAULT_DISPLAY

    enum class Option(
        val width: Int,
        val height: Int,
        val labelRes: Int,
        val noteRes: Int,
        /** Only for the preview: how soft the magnified detail looks. */
        val previewSharpness: Float,
    ) {
        WQHD(1220, 2712, R.string.hub_res_wqhd, R.string.hub_res_wqhd_note, 1f),
        FHD(1080, 2400, R.string.hub_res_fhd, R.string.hub_res_fhd_note, 0.5f),
        HD(960, 2134, R.string.hub_res_hd, R.string.hub_res_hd_note, 0.33f),
    }

    private val wm: IWindowManager
        get() = WindowManagerGlobal.getWindowManagerService()
            ?: throw IllegalStateException("Window manager service unavailable")

    private fun physicalSize(): Point = Point().also { wm.getInitialDisplaySize(DISPLAY, it) }

    private fun currentSize(): Point = Point().also { wm.getBaseDisplaySize(DISPLAY, it) }

    fun current(): Option {
        val w = try { currentSize().x } catch (e: Exception) { return Option.WQHD }
        return Option.values().minByOrNull { abs(it.width - w) } ?: Option.WQHD
    }

    /** Returns false if the window manager refused the change. */
    fun apply(option: Option): Boolean = try {
        val physical = physicalSize()
        val current = currentSize()
        val initialDensity = wm.getInitialDisplayDensity(DISPLAY)
        val baseDensity = wm.getBaseDisplayDensity(DISPLAY)
        // Density the user would have at native resolution (keeps "Display size").
        val nativeDensity = (baseDensity.toFloat() * physical.x / current.x).roundToInt()

        if (option.width >= physical.x) {
            wm.clearForcedDisplaySize(DISPLAY)
            if (abs(nativeDensity - initialDensity) <= 1) {
                wm.clearForcedDisplayDensityForUser(DISPLAY, UserHandle.USER_CURRENT)
            } else {
                wm.setForcedDisplayDensityForUser(DISPLAY, nativeDensity, UserHandle.USER_CURRENT)
            }
        } else {
            val density = (nativeDensity.toFloat() * option.width / physical.x).roundToInt()
            wm.setForcedDisplayDensityForUser(DISPLAY, density, UserHandle.USER_CURRENT)
            wm.setForcedDisplaySize(DISPLAY, option.width, option.height)
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to switch resolution to $option", e)
        false
    }
}
