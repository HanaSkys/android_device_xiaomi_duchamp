/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.gamespace

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Insets
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import com.xiaomi.settings.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The in-game windows: an edge handle that opens the side panel, the panel itself
 * (live metrics + quick toggles) and the always-on mini monitor. They are system
 * overlays owned by XiaomiParts and never take focus away from the game.
 */
class GameOverlay(private val service: GameSpaceService) {

    interface Actions {
        fun isBypassOn(): Boolean
        fun setBypass(on: Boolean)
        fun isTouchSupported(): Boolean
        fun isTouchOn(): Boolean
        fun setTouch(on: Boolean)
        fun isSilenceOn(): Boolean
        fun setSilence(on: Boolean)
        fun isBrightnessLocked(): Boolean
        fun setBrightnessLocked(on: Boolean)
        fun isPerformanceOn(): Boolean
        fun setPerformance(on: Boolean)
    }

    private val context: Context =
        ContextThemeWrapper(service, android.R.style.Theme_DeviceDefault)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val inflater = LayoutInflater.from(context)
    private val density = context.resources.displayMetrics.density
    private val prefs = GameSpaceSettings.prefs(service)

    private var handle: View? = null
    private var panel: View? = null
    private var mini: TextView? = null
    private var gameLabel: CharSequence = ""
    private var lastMetrics: GameMetrics? = null

    private val tiles = mutableListOf<Pair<View, () -> Boolean>>()

    fun onSessionStart(label: CharSequence) {
        gameLabel = label
        applyStyle()
    }

    fun onSessionEnd() {
        removeAll()
    }

    val isShowingMetrics get() = panel != null || mini != null

    /** Adds or removes windows to match the current options. */
    fun applyStyle() {
        val panelOn = GameSpaceSettings.option(service, GameSpaceSettings.KEY_PANEL)
        val miniOn = GameSpaceSettings.option(service, GameSpaceSettings.KEY_MINI)

        if (panelOn && handle == null) addHandle()
        if (!panelOn) {
            closePanel()
            remove(handle); handle = null
        }

        // Size and position changes need a new window.
        remove(mini); mini = null
        if (miniOn) addMini()

        panel?.let { styleBackgrounds(it) }
        lastMetrics?.let { update(it) }
    }

    fun update(m: GameMetrics) {
        lastMetrics = m
        mini?.text = miniText(m)
        val p = panel ?: return
        p.findViewById<TextView>(R.id.ov_fps).text = m.fps?.toString() ?: DASH
        m.fps?.let { p.findViewById<FpsGraphView>(R.id.ov_graph).push(it) }
        p.findViewById<TextView>(R.id.ov_cpu).text = m.cpuGhz?.let { "%.1f".format(it) } ?: DASH
        p.findViewById<TextView>(R.id.ov_gpu).text = m.gpuMhz?.toString() ?: DASH
        p.findViewById<TextView>(R.id.ov_temp).text = m.tempC?.let { "%.0f°".format(it) } ?: DASH
        p.findViewById<TextView>(R.id.ov_watts).text = m.watts?.let { "%.1f".format(it) } ?: DASH
    }

    // --- Handle --------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun addHandle() {
        val view = inflater.inflate(R.layout.hanita_overlay_handle, null)
        var downX = 0f
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> downX = e.rawX
                MotionEvent.ACTION_UP -> {
                    // A tap or a swipe towards the screen opens the panel.
                    Log.d(TAG, "handle touched (dx=${e.rawX - downX})")
                    if (abs(e.rawX - downX) < 8 * density || e.rawX > downX) openPanel()
                }
            }
            true
        }
        view.contentDescription = service.getString(R.string.hub_ov_open_panel)
        // With gesture navigation the screen edges belong to "back". Reserve just the
        // handle's own strip so a swipe there reaches us; the rest of the edge keeps "back".
        view.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            v.systemGestureExclusionRects = listOf(Rect(0, 0, r - l, b - t))
        }
        val lp = baseParams(touchable = true).apply {
            width = dp(HANDLE_WIDTH_DP)
            height = dp(HANDLE_HEIGHT_DP)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        if (add(view, lp)) handle = view
    }

    // --- Panel ---------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun openPanel() {
        if (panel != null) return
        val view = inflater.inflate(R.layout.hanita_overlay_panel, null)
        view.findViewById<TextView>(R.id.ov_game).text = gameLabel
        tiles.clear()
        val a = service.overlayActions
        bindTile(view, R.id.ov_t_bypass, a::isBypassOn) { a.setBypass(it) }
        if (a.isTouchSupported()) {
            bindTile(view, R.id.ov_t_touch, a::isTouchOn) { a.setTouch(it) }
        } else {
            view.findViewById<View>(R.id.ov_t_touch).visibility = View.GONE
        }
        bindTile(view, R.id.ov_t_silence, a::isSilenceOn) { a.setSilence(it) }
        bindTile(view, R.id.ov_t_brightness, a::isBrightnessLocked) { a.setBrightnessLocked(it) }
        bindTile(view, R.id.ov_t_perf, a::isPerformanceOn) { a.setPerformance(it) }
        bindTile(view, R.id.ov_t_mini,
            { GameSpaceSettings.option(service, GameSpaceSettings.KEY_MINI) }) {
            prefs.edit().putBoolean(GameSpaceSettings.KEY_MINI, it).apply()
        }
        styleBackgrounds(view)

        view.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) closePanel()
            false
        }
        val lp = baseParams(touchable = true).apply {
            width = dp(328)
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = dp(12) + systemInsets().left
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        if (add(view, lp)) {
            panel = view
            handle?.visibility = View.GONE
            lastMetrics?.let { update(it) }
            service.onOverlayVisibilityChanged()
        }
    }

    private fun closePanel() {
        val p = panel ?: return
        remove(p)
        panel = null
        tiles.clear()
        handle?.visibility = View.VISIBLE
        service.onOverlayVisibilityChanged()
    }

    private fun bindTile(root: View, id: Int, state: () -> Boolean, set: (Boolean) -> Unit) {
        val tile = root.findViewById<View>(id)
        tile.isSelected = state()
        tile.setOnClickListener {
            set(!state())
            refreshTiles()
        }
        tiles += tile to state
    }

    fun refreshTiles() {
        tiles.forEach { (tile, state) -> tile.isSelected = state() }
        panel?.let { styleBackgrounds(it) }
    }

    // --- Mini monitor --------------------------------------------------------------------------

    private fun addMini() {
        val view = inflater.inflate(R.layout.hanita_overlay_mini, null) as TextView
        val size = GameSpaceSettings.miniSize(service)
        view.textSize = when (size) {
            GameSpaceSettings.SIZE_SMALL -> 11f
            GameSpaceSettings.SIZE_LARGE -> 16f
            else -> 13f
        }
        view.background = view.background.mutate().also { it.alpha = alphaByte() }
        val right = GameSpaceSettings.miniPosition(service) == GameSpaceSettings.POS_RIGHT
        val insets = systemInsets()
        val lp = baseParams(touchable = false).apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or if (right) Gravity.END else Gravity.START
            x = dp(12) + if (right) insets.right else insets.left
            y = dp(6) + insets.top
        }
        if (add(view, lp)) {
            mini = view
            view.text = lastMetrics?.let { miniText(it) } ?: DASH
            service.onOverlayVisibilityChanged()
        }
    }

    private fun miniText(m: GameMetrics): String {
        val parts = mutableListOf(service.getString(R.string.hub_ov_fps_value, m.fps?.toString() ?: DASH))
        m.tempC?.let { parts += "%.0f°C".format(it) }
        m.watts?.let { parts += "%.1f W".format(it) }
        return parts.joinToString(" · ")
    }

    // --- Helpers -------------------------------------------------------------------------------

    /**
     * Status bar and cutout, even while the game hides the bars, so the windows never
     * sit under the clock or the camera.
     */
    private fun systemInsets(): Insets = try {
        wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
    } catch (e: Exception) {
        Insets.NONE
    }

    private fun alphaByte() = (GameSpaceSettings.opacity(service) * 255 / 100f).roundToInt()

    /** Opacity applies to the surfaces; text stays solid (with a shadow) so it is always readable. */
    private fun styleBackgrounds(root: View) {
        val a = alphaByte()
        fun tint(v: View?) {
            val bg: Drawable = v?.background ?: return
            v.background = bg.mutate().also { it.alpha = a }
        }
        tint(root)
        listOf(R.id.ov_m_cpu, R.id.ov_m_gpu, R.id.ov_m_temp, R.id.ov_m_watts).forEach {
            tint(root.findViewById(it))
        }
        tiles.forEach { (tile, _) -> tint(tile) }
    }

    /**
     * Touchable windows (handle, panel) are application overlays: secure system overlays
     * are forced untouchable by the window manager. Windows from XiaomiParts are still
     * trusted overlays (INTERNAL_SYSTEM_WINDOW), so they never block the game's touches.
     * The mini monitor never takes touches, so it uses the secure overlay above everything.
     */
    private fun baseParams(touchable: Boolean) = WindowManager.LayoutParams(
        if (touchable) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "HanitaGameSpace"
    }

    private fun add(view: View, lp: WindowManager.LayoutParams): Boolean = try {
        wm.addView(view, lp)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Cannot add overlay window", e)
        false
    }

    private fun remove(view: View?) {
        if (view == null) return
        try { wm.removeViewImmediate(view) } catch (e: Exception) { Log.w(TAG, "removeView", e) }
    }

    private fun removeAll() {
        closePanel()
        remove(handle); handle = null
        remove(mini); mini = null
        lastMetrics = null
        service.onOverlayVisibilityChanged()
    }

    private fun dp(v: Int) = (v * density).roundToInt()

    companion object {
        private const val TAG = "HanitaGameOverlay"
        private const val DASH = "—"
        private const val HANDLE_WIDTH_DP = 36
        private const val HANDLE_HEIGHT_DP = 160
    }
}
