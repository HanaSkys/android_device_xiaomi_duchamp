/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.batteryhealth

import android.app.AlarmManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import java.io.File
import java.util.Calendar

/**
 * Single owner of the charger driver's SMART_CHG_NAVIGATION mode (smart_chg).
 * When the battery reaches the threshold the driver stops charging while the
 * charger keeps powering the device, and resumes at threshold - 5%.
 *
 * Priority: bypass charging (20%) > min(custom limit, smart charging 80%).
 */
class ChargeControlService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastValue = -1
    private var pendingValue = -1

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            update()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            update()
        }
    }

    private val applyPending = Runnable { writeSmartChg(pendingValue) }

    override fun onCreate() {
        super.onCreate()
        // A game request must never survive a reboot.
        Settings.Secure.putInt(contentResolver, KEY_BYPASS_GAME, 0)

        OBSERVED_KEYS.forEach {
            contentResolver.registerContentObserver(Settings.Secure.getUriFor(it), false, observer)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

        update()
    }

    private fun getSecure(key: String, def: Int = 0) =
        Settings.Secure.getInt(contentResolver, key, def)

    private fun isPlugged(): Boolean {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    /** Overnight window: from 22:00 until one hour before the next alarm (or 06:00). */
    private fun isSmartWindowActive(): Boolean {
        val now = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, SMART_START_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis > now) add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis

        var end = start + DEFAULT_WINDOW_MS
        val alarm = getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
        if (alarm != null && alarm > start && alarm - start <= MAX_WINDOW_MS) {
            end = alarm - FULL_BEFORE_ALARM_MS
        }
        return now in start until end
    }

    private fun targetThreshold(): Int? {
        val plugged = isPlugged()
        if (plugged && (getSecure(KEY_BYPASS) == 1 || getSecure(KEY_BYPASS_GAME) == 1)) {
            return BYPASS_THRESHOLD
        }

        var threshold: Int? = null
        if (getSecure(KEY_LIMIT_ENABLED) == 1) {
            val limit = getSecure(KEY_LIMIT, DEFAULT_LIMIT).coerceIn(MIN_LIMIT, MAX_LIMIT)
            if (limit < MAX_LIMIT) threshold = limit
        }
        if (plugged && getSecure(KEY_SMART) == 1 && isSmartWindowActive()) {
            threshold = minOf(threshold ?: SMART_THRESHOLD, SMART_THRESHOLD)
        }
        return threshold
    }

    private fun update() {
        val target = targetThreshold()
        val value = if (target == null) VALUE_DISABLE else (target shl 16) or 0x3
        if (value == pendingValue) return

        handler.removeCallbacks(applyPending)
        pendingValue = value
        if (lastValue != -1 && lastValue != VALUE_DISABLE && value != VALUE_DISABLE) {
            // Driver quirk: changing the threshold while charging is held keeps the
            // charger disabled. Release it first, then apply the new threshold.
            writeSmartChg(VALUE_DISABLE)
            handler.postDelayed(applyPending, THRESHOLD_SWITCH_DELAY_MS)
        } else {
            writeSmartChg(value)
        }
    }

    private fun writeSmartChg(value: Int) {
        try {
            File(SMART_CHG_PATH).writeText(value.toString())
            lastValue = value
            Log.i(TAG, "smart_chg <- $value")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $SMART_CHG_PATH", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(applyPending)
        contentResolver.unregisterContentObserver(observer)
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    companion object {
        const val KEY_BYPASS = "duchamp_bypass_charging"
        const val KEY_BYPASS_GAME = "duchamp_bypass_charging_game"
        const val KEY_LIMIT_ENABLED = "duchamp_charge_limit_enabled"
        const val KEY_LIMIT = "duchamp_charge_limit"
        const val KEY_SMART = "duchamp_smart_charging"

        const val DEFAULT_LIMIT = 80
        const val MIN_LIMIT = 70
        const val MAX_LIMIT = 100

        private val OBSERVED_KEYS =
            listOf(KEY_BYPASS, KEY_BYPASS_GAME, KEY_LIMIT_ENABLED, KEY_LIMIT, KEY_SMART)

        private const val TAG = "ChargeControl"
        private const val SMART_CHG_PATH = "/sys/class/power_supply/battery/smart_chg"

        // smart_chg: bit0 = enable, bit1 = SMART_CHG_NAVIGATION, bits16+ = SoC threshold.
        private const val VALUE_DISABLE = 0x2
        private const val BYPASS_THRESHOLD = 20
        private const val SMART_THRESHOLD = 80
        private const val THRESHOLD_SWITCH_DELAY_MS = 2500L

        private const val SMART_START_HOUR = 22
        private const val DEFAULT_WINDOW_MS = 8 * 60 * 60 * 1000L      // 22:00 -> 06:00
        private const val MAX_WINDOW_MS = 14 * 60 * 60 * 1000L         // alarms until 12:00
        private const val FULL_BEFORE_ALARM_MS = 60 * 60 * 1000L
    }
}
