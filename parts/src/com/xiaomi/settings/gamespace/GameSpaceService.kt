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
 * there, applies the Game Space automations. Every change is recorded before it
 * is made, so it is undone when the game closes, and also on the next start if
 * the process died in the middle of a session.
 */
class GameSpaceService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var listenerRegistered = false

    private var sessionPkg: String? = null
    private var sessionStart = 0L
    private var startLevel = -1
    private var maxTempTenths = Int.MIN_VALUE

    private val evaluate = Runnable { evaluateForeground() }
    private val endSession = Runnable { stopSession() }
    private val reapplyThermal = Runnable { applyThermalIfNeeded() }

    private val taskListener = object : TaskStackListener() {
        override fun onTaskStackChanged() {
            handler.post(evaluate)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // The thermal auto mode restores its own profile on screen on.
                Intent.ACTION_SCREEN_ON -> if (sessionPkg != null) {
                    handler.removeCallbacks(reapplyThermal)
                    handler.postDelayed(reapplyThermal, THERMAL_REAPPLY_DELAY_MS)
                }
                Intent.ACTION_BATTERY_CHANGED -> if (sessionPkg != null) {
                    val b = BatteryInfo.Snapshot(intent)
                    if (b.hasTemp) maxTempTenths = maxOf(maxTempTenths, b.tempTenths)
                }
            }
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == GameSpaceSettings.KEY_ENABLED || key == GameSpaceSettings.KEY_GAMES) {
            handler.post(evaluate)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = GameSpaceSettings.prefs(this)
        // A previous session that never ended (crash, reboot): undo what it changed.
        if (prefs.contains(GameSpaceSettings.KEY_SESSION_PKG)) {
            Log.i(TAG, "Restoring settings left by an unfinished session")
            restoreTweaks()
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskListener)
            listenerRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot watch the foreground app", e)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
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
            // Short grace period: login dialogs or ads from other apps should not end the session.
            handler.removeCallbacks(endSession)
            handler.postDelayed(endSession, if (enabled) EXIT_DELAY_MS else 0L)
        }
    }

    private fun startSession(pkg: String) {
        Log.i(TAG, "Game started: $pkg")
        sessionPkg = pkg
        prefs.edit().putString(GameSpaceSettings.KEY_SESSION_PKG, pkg).commit()
        applyTweaks()
        resetStats()
    }

    private fun switchGame(pkg: String) {
        Log.i(TAG, "Game switched: $sessionPkg -> $pkg")
        saveStats()
        sessionPkg = pkg
        prefs.edit().putString(GameSpaceSettings.KEY_SESSION_PKG, pkg).commit()
        resetStats()
    }

    private fun stopSession() {
        if (sessionPkg == null) return
        Log.i(TAG, "Game closed: $sessionPkg")
        saveStats()
        restoreTweaks()
        sessionPkg = null
    }

    // --- Automations ---------------------------------------------------------------------------

    /** Records each change (synchronously) before making it. */
    private fun applyTweaks() {
        val cr = contentResolver

        if (GameSpaceSettings.option(this, GameSpaceSettings.KEY_AUTO_BYPASS)) {
            prefs.edit().putBoolean(GameSpaceSettings.KEY_SET_BYPASS, true).commit()
            Settings.Secure.putInt(cr, ChargeControlService.KEY_BYPASS_GAME, 1)
        }

        if (GameSpaceSettings.option(this, GameSpaceSettings.KEY_AUTO_TOUCH) &&
            HighTouchPolling.isSupported() && !HighTouchPolling.isEnabled(this)
        ) {
            prefs.edit().putBoolean(GameSpaceSettings.KEY_SET_TOUCH, true).commit()
            if (!HighTouchPolling.setEnabled(this, true)) {
                prefs.edit().remove(GameSpaceSettings.KEY_SET_TOUCH).commit()
            }
        }

        if (GameSpaceSettings.option(this, GameSpaceSettings.KEY_NO_HEADS_UP)) {
            val prev = Settings.Global.getInt(cr, HEADS_UP, 1)
            if (prev != 0) {
                prefs.edit().putInt(GameSpaceSettings.KEY_PREV_HEADS_UP, prev).commit()
                Settings.Global.putInt(cr, HEADS_UP, 0)
            }
        }

        if (GameSpaceSettings.option(this, GameSpaceSettings.KEY_LOCK_BRIGHTNESS)) {
            val mode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                prefs.edit().putBoolean(GameSpaceSettings.KEY_SET_BRIGHTNESS, true).commit()
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            }
        }

        val thermal = GameSpaceSettings.thermal(this)
        if (thermal != GameSpaceSettings.THERMAL_KEEP) {
            val current = readThermal()
            if (current != null && current != thermal) {
                prefs.edit().putInt(GameSpaceSettings.KEY_PREV_THERMAL, current).commit()
                writeThermal(thermal)
            }
        }
    }

    private fun applyThermalIfNeeded() {
        if (sessionPkg == null || !prefs.contains(GameSpaceSettings.KEY_PREV_THERMAL)) return
        val thermal = GameSpaceSettings.thermal(this)
        if (thermal != GameSpaceSettings.THERMAL_KEEP && readThermal() != thermal) {
            writeThermal(thermal)
        }
    }

    /** Undoes exactly what applyTweaks() recorded, then forgets it. */
    private fun restoreTweaks() {
        handler.removeCallbacks(reapplyThermal)
        val cr = contentResolver

        if (prefs.getBoolean(GameSpaceSettings.KEY_SET_BYPASS, false)) {
            Settings.Secure.putInt(cr, ChargeControlService.KEY_BYPASS_GAME, 0)
        }
        if (prefs.getBoolean(GameSpaceSettings.KEY_SET_TOUCH, false)) {
            HighTouchPolling.setEnabled(this, false)
        }
        val prevHeadsUp = prefs.getInt(GameSpaceSettings.KEY_PREV_HEADS_UP, -1)
        if (prevHeadsUp >= 0) {
            Settings.Global.putInt(cr, HEADS_UP, prevHeadsUp)
        }
        if (prefs.getBoolean(GameSpaceSettings.KEY_SET_BRIGHTNESS, false)) {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        }
        val prevThermal = prefs.getInt(GameSpaceSettings.KEY_PREV_THERMAL, -1)
        if (prevThermal > 0) {
            writeThermal(prevThermal)
        }

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

    private fun batteryNow() = BatteryInfo.Snapshot(
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))

    private fun resetStats() {
        val b = batteryNow()
        sessionStart = SystemClock.elapsedRealtime()
        startLevel = b.level
        maxTempTenths = if (b.hasTemp) b.tempTenths else Int.MIN_VALUE
    }

    private fun saveStats() {
        val pkg = sessionPkg ?: return
        val duration = SystemClock.elapsedRealtime() - sessionStart
        if (duration < MIN_SESSION_MS) return
        val b = batteryNow()
        prefs.edit()
            .putString(GameSpaceSettings.KEY_LAST_PKG, pkg)
            .putLong(GameSpaceSettings.KEY_LAST_END, System.currentTimeMillis())
            .putLong(GameSpaceSettings.KEY_LAST_DURATION, duration)
            .putInt(GameSpaceSettings.KEY_LAST_BATTERY, b.level - startLevel)
            .putInt(GameSpaceSettings.KEY_LAST_MAX_TEMP, maxTempTenths)
            .apply()
    }

    companion object {
        private const val TAG = "HanitaGameSpace"
        private const val HEADS_UP = "heads_up_notifications_enabled"
        private const val EXIT_DELAY_MS = 2_000L
        private const val THERMAL_REAPPLY_DELAY_MS = 1_500L
        private const val MIN_SESSION_MS = 60_000L

        fun start(context: Context) {
            context.startService(Intent(context, GameSpaceService::class.java))
        }
    }
}
