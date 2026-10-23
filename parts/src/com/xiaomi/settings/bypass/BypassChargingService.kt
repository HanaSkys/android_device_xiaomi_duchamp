/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.bypass

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

/**
 * Bypass charging through the charger driver's SMART_CHG_NAVIGATION mode:
 * the battery stops charging while the charger keeps powering the device.
 * Requested either manually (Settings) or by GameBar while a game is running.
 */
class BypassChargingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastValue = -1

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            updateBypass()
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateBypass()
        }
    }

    override fun onCreate() {
        super.onCreate()
        // A game request must never survive a reboot.
        Settings.Secure.putInt(contentResolver, KEY_GAME, 0)

        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(KEY_MANUAL), false, observer)
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(KEY_GAME), false, observer)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        updateBypass()
    }

    private fun isPlugged(): Boolean {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    private fun updateBypass() {
        val requested =
            Settings.Secure.getInt(contentResolver, KEY_MANUAL, 0) == 1 ||
            Settings.Secure.getInt(contentResolver, KEY_GAME, 0) == 1
        val value = if (requested && isPlugged()) VALUE_ENABLE else VALUE_DISABLE
        if (value == lastValue) return

        try {
            File(SMART_CHG_PATH).writeText(value.toString())
            lastValue = value
            Log.i(TAG, "smart_chg <- $value (requested=$requested)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $SMART_CHG_PATH", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        unregisterReceiver(powerReceiver)
        super.onDestroy()
    }

    companion object {
        const val KEY_MANUAL = "duchamp_bypass_charging"
        const val KEY_GAME = "duchamp_bypass_charging_game"

        private const val TAG = "BypassCharging"
        private const val SMART_CHG_PATH = "/sys/class/power_supply/battery/smart_chg"

        // smart_chg: bit0 = enable, bit1 = SMART_CHG_NAVIGATION, bits16+ = SoC threshold.
        // The driver stops charging at SoC >= threshold and resumes at <= threshold - 5.
        private const val BYPASS_THRESHOLD = 20
        private const val VALUE_ENABLE = (BYPASS_THRESHOLD shl 16) or 0x3
        private const val VALUE_DISABLE = 0x2
    }
}
