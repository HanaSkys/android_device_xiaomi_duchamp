/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.provider.Settings
import com.xiaomi.settings.R
import com.xiaomi.settings.batteryhealth.ChargeControlService
import java.io.File

/** Battery readings and charging settings shared by the hub screens. */
object BatteryInfo {
    private const val BMS_SOH_PATH = "/sys/class/power_supply/bms/soh"
    private const val BATTERY_CYCLE_COUNT_PATH = "/sys/class/power_supply/battery/cycle_count"

    /** Settings the hub observes; ChargeControlService reacts to the same keys. */
    val SECURE_KEYS = listOf(
        ChargeControlService.KEY_BYPASS,
        ChargeControlService.KEY_BYPASS_GAME,
        ChargeControlService.KEY_SMART,
        ChargeControlService.KEY_LIMIT_ENABLED,
        ChargeControlService.KEY_LIMIT,
    )

    /** One ACTION_BATTERY_CHANGED reading. */
    class Snapshot(intent: Intent?) {
        val plugged = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val level: Int
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        val charging get() = plugged && status == BatteryManager.BATTERY_STATUS_CHARGING
        val hasTemp get() = tempTenths != Int.MIN_VALUE

        init {
            val scale = (intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100)
                .coerceAtLeast(1)
            level = (intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0) * 100 / scale
        }
    }

    /** Charging options as stored in Settings.Secure. */
    class Charging(private val cr: ContentResolver) {
        private fun get(key: String, def: Int = 0) = Settings.Secure.getInt(cr, key, def)

        val bypass = get(ChargeControlService.KEY_BYPASS) == 1
        val bypassActive = bypass || get(ChargeControlService.KEY_BYPASS_GAME) == 1
        val smart = get(ChargeControlService.KEY_SMART) == 1
        val limitEnabled = get(ChargeControlService.KEY_LIMIT_ENABLED) == 1
        val limit = get(ChargeControlService.KEY_LIMIT, ChargeControlService.DEFAULT_LIMIT)
            .coerceIn(ChargeControlService.MIN_LIMIT, ChargeControlService.MAX_LIMIT)
        val limitActive get() = limitEnabled && limit < ChargeControlService.MAX_LIMIT
    }

    /** What the battery is doing; mirrors the priorities used by ChargeControlService. */
    enum class State(val longRes: Int, val shortRes: Int) {
        ON_BATTERY(R.string.hub_state_on_battery, R.string.hub_stat_on_battery),
        BYPASS(R.string.hub_state_bypass, R.string.hub_stat_bypass),
        PAUSED(R.string.hub_state_paused, R.string.hub_stat_paused),
        FULL(R.string.hub_state_full, R.string.hub_stat_full),
        CHARGING(R.string.hub_state_charging, R.string.hub_stat_charging),
    }

    fun state(b: Snapshot, c: Charging): State = when {
        !b.plugged -> State.ON_BATTERY
        c.bypassActive -> State.BYPASS
        c.limitActive && b.level >= c.limit -> State.PAUSED
        b.status == BatteryManager.BATTERY_STATUS_FULL || b.level >= 100 -> State.FULL
        else -> State.CHARGING
    }

    fun stateOfHealth(context: Context): Int? {
        val soh = context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATE_OF_HEALTH) ?: Int.MIN_VALUE
        if (soh in 1..100) return soh
        return readSysfsInt(BMS_SOH_PATH)?.takeIf { it in 1..100 }
    }

    fun cycleCount(intent: Intent?): Int? {
        val cycles = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        if (cycles >= 0) return cycles
        return readSysfsInt(BATTERY_CYCLE_COUNT_PATH)?.takeIf { it >= 0 }
    }

    private fun readSysfsInt(path: String): Int? =
        try { File(path).readText().trim().toInt() } catch (e: Exception) { null }
}
