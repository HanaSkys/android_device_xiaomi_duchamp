/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.gamespace

import android.app.ActivityTaskManager
import android.app.Service
import android.app.TaskStackListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.xiaomi.settings.batteryhealth.ChargeControlService
import com.xiaomi.settings.corecontrol.CoreControlProfilePolicy
import com.xiaomi.settings.hub.BatteryInfo
import com.xiaomi.settings.thermal.ThermalProfileFragment
import com.xiaomi.settings.touch.HighTouchPolling
import com.xiaomi.settings.utils.FileUtils

/**
 * Watches which app is in the foreground and, while one of the user's games is
 * there, applies the Game Space automations and shows the in-game overlay.
 * How everything was before the session is recorded first, so it is always put
 * back when the game closes, and also on the next start if the process died in
 * the middle of a session.
 */
class GameSpaceService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var overlay: GameOverlay
    private var listenerRegistered = false

    private var sessionPkg: String? = null
    private var sessionStart = 0L
    private var startLevel = -1
    private var maxTempTenths = Int.MIN_VALUE
    private var fpsSum = 0L
    private var fpsCount = 0

    private val evaluate = Runnable { evaluateForeground() }
    private val endSession = Runnable { stopSession() }
    private val reapplyThermal = Runnable { applyThermalIfNeeded() }
    private val poll = object : Runnable {
        override fun run() {
            if (sessionPkg == null) return
            val m = GameMetrics.read(this@GameSpaceService)
            m.fps?.let { fpsSum += it; fpsCount++ }
            val tenths = m.tempC?.let { (it * 10).toInt() }
                ?: BatteryInfo.Snapshot(batteryIntent()).takeIf { it.hasTemp }?.tempTenths
            tenths?.let { maxTempTenths = maxOf(maxTempTenths, it) }
            overlay.update(m)
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val taskListener = object : TaskStackListener() {
        override fun onTaskStackChanged() {
            handler.post(evaluate)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The thermal auto mode restores its own profile on screen on.
            if (intent.action == Intent.ACTION_SCREEN_ON && sessionPkg != null) {
                handler.removeCallbacks(reapplyThermal)
                handler.postDelayed(reapplyThermal, THERMAL_REAPPLY_DELAY_MS)
            }
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            GameSpaceSettings.KEY_ENABLED, GameSpaceSettings.KEY_GAMES -> handler.post(evaluate)
            GameSpaceSettings.KEY_PANEL, GameSpaceSettings.KEY_MINI,
            GameSpaceSettings.KEY_OPACITY, GameSpaceSettings.KEY_MINI_POS,
            GameSpaceSettings.KEY_MINI_SIZE -> handler.post {
                if (sessionPkg != null) {
                    overlay.applyStyle()
                    overlay.refreshTiles()
                }
            }
        }
    }

    /** What the panel's quick toggles change, live, for this session only. */
    val overlayActions = object : GameOverlay.Actions {
        override fun isBypassOn() =
            Settings.Secure.getInt(contentResolver, ChargeControlService.KEY_BYPASS_GAME, 0) == 1
        override fun setBypass(on: Boolean) {
            Settings.Secure.putInt(contentResolver, ChargeControlService.KEY_BYPASS_GAME, if (on) 1 else 0)
        }
        override fun isTouchSupported() = HighTouchPolling.isSupported()
        override fun isTouchOn() = HighTouchPolling.isEnabled(this@GameSpaceService)
        override fun setTouch(on: Boolean) {
            HighTouchPolling.setEnabled(this@GameSpaceService, on)
        }
        override fun isSilenceOn() = Settings.Global.getInt(contentResolver, HEADS_UP, 1) == 0
        override fun setSilence(on: Boolean) {
            Settings.Global.putInt(contentResolver, HEADS_UP, if (on) 0 else 1)
        }
        override fun isBrightnessLocked() = brightnessMode() ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        override fun setBrightnessLocked(on: Boolean) {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        }
        override fun isPerformanceOn() = readThermal() == GameSpaceSettings.THERMAL_PERFORMANCE
        override fun setPerformance(on: Boolean) {
            val target = if (on) GameSpaceSettings.THERMAL_PERFORMANCE
                else prefs.getInt(GameSpaceSettings.KEY_ORIG_THERMAL, -1)
            if (target > 0) writeThermal(target)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = GameSpaceSettings.prefs(this)
        overlay = GameOverlay(this)
        // A previous session that never ended (crash, reboot): put everything back.
        if (prefs.contains(GameSpaceSettings.KEY_SESSION_PKG)) {
            Log.i(TAG, "Restoring settings left by an unfinished session")
            restoreOriginals()
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskListener)
            listenerRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot watch the foreground app", e)
        }

        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED)
        evaluateForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(evaluate)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (sessionPkg != null) stopSession()
        if (listenerRegistered) {
            try {
                ActivityTaskManager.getService().unregisterTaskStackListener(taskListener)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterTaskStackListener failed", e)
            }
        }
        unregisterReceiver(receiver)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun onOverlayVisibilityChanged() {
        // Polling runs for the whole session (stats); nothing to change here for now.
    }

    private fun foregroundPackage(): String? = try {
        ActivityTaskManager.getService().getFocusedRootTaskInfo()?.topActivity?.packageName
    } catch (e: Exception) {
        Log.w(TAG, "Cannot read the foreground app", e)
        null
    }

    private fun evaluateForeground() {
        val enabled = GameSpaceSettings.isEnabled(this)
        val fg = if (enabled) foregroundPackage() else null
        val isGame = fg != null && fg in GameSpaceSettings.games(this)

        if (isGame) {
            handler.removeCallbacks(endSession)
            when (sessionPkg) {
                null -> startSession(fg!!)
                fg -> Unit
                else -> switchGame(fg!!)
            }
        } else if (sessionPkg != null) {
            // Short grace period: our own panel, login dialogs or ads should not end the session.
            handler.removeCallbacks(endSession)
            handler.postDelayed(endSession, if (enabled) EXIT_DELAY_MS else 0L)
        }
    }

    private fun startSession(pkg: String) {
        Log.i(TAG, "Game started: $pkg")
        sessionPkg = pkg
        recordOriginals(pkg)
        applyTweaks()
        resetStats()
        overlay.onSessionStart(labelOf(pkg))
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    private fun switchGame(pkg: String) {
        Log.i(TAG, "Game switched: $sessionPkg -> $pkg")
        saveStats()
        sessionPkg = pkg
        prefs.edit().putString(GameSpaceSettings.KEY_SESSION_PKG, pkg).commit()
        resetStats()
        overlay.onSessionStart(labelOf(pkg))
    }

    private fun stopSession() {
        if (sessionPkg == null) return
        Log.i(TAG, "Game closed: $sessionPkg")
        handler.removeCallbacks(poll)
        overlay.onSessionEnd()
        saveStats()
        restoreOriginals()
        sessionPkg = null
    }

    private fun labelOf(pkg: String): CharSequence = try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager)
    } catch (e: Exception) {
        pkg
    }

    // --- Automations ---------------------------------------------------------------------------

    private fun brightnessMode() = Settings.System.getInt(contentResolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)

    /** Saved synchronously before anything is changed. */
    private fun recordOriginals(pkg: String) {
        val e = prefs.edit()
            .putString(GameSpaceSettings.KEY_SESSION_PKG, pkg)
            .putInt(GameSpaceSettings.KEY_ORIG_HEADS_UP,
                Settings.Global.getInt(contentResolver, HEADS_UP, 1))
            .putInt(GameSpaceSettings.KEY_ORIG_BRIGHTNESS, brightnessMode())
        if (HighTouchPolling.isSupported()) {
            e.putBoolean(GameSpaceSettings.KEY_ORIG_TOUCH, HighTouchPolling.isEnabled(this))
        }
        readThermal()?.let { e.putInt(GameSpaceSettings.KEY_ORIG_THERMAL, it) }
        e.commit()
    }

    private fun applyTweaks() {
        val a = overlayActions
        if (GameSpaceSettings.option(this, GameSpaceSettings.KEY_AUTO_BYPASS)) a.setBypass(true)
        if (GameSpaceSettings.option(this, GameSpaceSettings.KEY_AUTO_TOUCH) &&
            a.isTouchSupported() && !a.isTouchOn()) a.setTouch(true)
        if (GameSpaceSettings.option(this, GameSpaceSettings.KEY_NO_HEADS_UP)) a.setSilence(true)
        if (GameSpaceSettings.option(this, GameSpaceSettings.KEY_LOCK_BRIGHTNESS) &&
            !a.isBrightnessLocked()) a.setBrightnessLocked(true)
        applyThermalIfNeeded()
    }

    private fun applyThermalIfNeeded() {
        if (sessionPkg == null) return
        val thermal = GameSpaceSettings.thermal(this)
        if (thermal != GameSpaceSettings.THERMAL_KEEP && readThermal() != thermal) {
            writeThermal(thermal)
        }
    }

    /** Puts back what recordOriginals() saved, then forgets it. */
    private fun restoreOriginals() {
        handler.removeCallbacks(reapplyThermal)
        val cr = contentResolver

        // Bypass for games is only ever ours: always off outside a session.
        Settings.Secure.putInt(cr, ChargeControlService.KEY_BYPASS_GAME, 0)

        if (prefs.contains(GameSpaceSettings.KEY_ORIG_TOUCH)) {
            val orig = prefs.getBoolean(GameSpaceSettings.KEY_ORIG_TOUCH, false)
            if (HighTouchPolling.isSupported() && HighTouchPolling.isEnabled(this) != orig) {
                HighTouchPolling.setEnabled(this, orig)
            }
        } else if (prefs.getBoolean("session_set_touch", false)) {
            HighTouchPolling.setEnabled(this, false) // left by a phase 1 session
        }

        val headsUp = prefs.getInt(GameSpaceSettings.KEY_ORIG_HEADS_UP,
            prefs.getInt("session_prev_heads_up", -1))
        if (headsUp >= 0) Settings.Global.putInt(cr, HEADS_UP, headsUp)

        if (prefs.contains(GameSpaceSettings.KEY_ORIG_BRIGHTNESS)) {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                prefs.getInt(GameSpaceSettings.KEY_ORIG_BRIGHTNESS,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
        } else if (prefs.getBoolean("session_set_brightness", false)) {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        }

        val thermal = prefs.getInt(GameSpaceSettings.KEY_ORIG_THERMAL,
            prefs.getInt("session_prev_thermal", -1))
        if (thermal > 0 && readThermal() != thermal) writeThermal(thermal)

        val editor = prefs.edit()
        GameSpaceSettings.SESSION_KEYS.forEach { editor.remove(it) }
        editor.commit()
    }

    /** Same path and core policy the thermal profiles of XiaomiParts use. */
    private fun readThermal(): Int? = try {
        val value: Int? = FileUtils.readLineInt(ThermalProfileFragment.THERMAL_PROFILE_PATH)
        value?.takeIf { it > 0 }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot read the thermal profile", e)
        null
    }

    private fun writeThermal(profile: Int) {
        try {
            FileUtils.writeLine(ThermalProfileFragment.THERMAL_PROFILE_PATH, profile)
            CoreControlProfilePolicy.applyForProfile(this, profile)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot set thermal profile $profile", e)
        }
    }

    // --- Session summary -----------------------------------------------------------------------

    private fun batteryIntent() = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun resetStats() {
        val b = BatteryInfo.Snapshot(batteryIntent())
        sessionStart = SystemClock.elapsedRealtime()
        startLevel = b.level
        maxTempTenths = Int.MIN_VALUE
        fpsSum = 0L
        fpsCount = 0
    }

    private fun saveStats() {
        val pkg = sessionPkg ?: return
        val duration = SystemClock.elapsedRealtime() - sessionStart
        if (duration < MIN_SESSION_MS) return
        val b = BatteryInfo.Snapshot(batteryIntent())
        prefs.edit()
            .putString(GameSpaceSettings.KEY_LAST_PKG, pkg)
            .putLong(GameSpaceSettings.KEY_LAST_END, System.currentTimeMillis())
            .putLong(GameSpaceSettings.KEY_LAST_DURATION, duration)
            .putInt(GameSpaceSettings.KEY_LAST_BATTERY, b.level - startLevel)
            .putInt(GameSpaceSettings.KEY_LAST_MAX_TEMP, maxTempTenths)
            .putInt(GameSpaceSettings.KEY_LAST_FPS, if (fpsCount > 0) (fpsSum / fpsCount).toInt() else -1)
            .apply()
    }

    companion object {
        private const val TAG = "HanitaGameSpace"
        private const val HEADS_UP = "heads_up_notifications_enabled"
        private const val EXIT_DELAY_MS = 2_000L
        private const val THERMAL_REAPPLY_DELAY_MS = 1_500L
        private const val MIN_SESSION_MS = 60_000L
        private const val POLL_MS = 1_000L

        fun start(context: Context) {
            context.startService(Intent(context, GameSpaceService::class.java))
        }
    }
}
