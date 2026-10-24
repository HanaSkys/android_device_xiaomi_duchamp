/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.batteryhealth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.xiaomi.settings.R
import com.xiaomi.settings.utils.M3CardItemDecoration
import java.io.File

class BatteryHealthFragment : PreferenceFragmentCompat() {

    private var header: BatteryHealthHeaderPreference? = null
    private var lastBatteryIntent: Intent? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lastBatteryIntent = intent
            updateLiveState()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.battery_health, rootKey)

        header = findPreference(KEY_PREF_HEADER)
        header?.setHealth(readStateOfHealth(), readCycleCount())

        bindSwitch(KEY_PREF_SMART, ChargeControlService.KEY_SMART)
        bindSwitch(KEY_PREF_LIMIT_ENABLED, ChargeControlService.KEY_LIMIT_ENABLED)
        bindSwitch(KEY_PREF_BYPASS, ChargeControlService.KEY_BYPASS)

        findPreference<SeekBarPreference>(KEY_PREF_LIMIT)?.apply {
            value = getSecure(ChargeControlService.KEY_LIMIT, ChargeControlService.DEFAULT_LIMIT)
            setOnPreferenceChangeListener { _, newValue ->
                putSecure(ChargeControlService.KEY_LIMIT, newValue as Int)
                updateLiveState()
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(ColorDrawable(Color.TRANSPARENT))
        setDividerHeight(0)
        listView?.addItemDecoration(M3CardItemDecoration(requireContext()))
    }

    override fun onResume() {
        super.onResume()
        lastBatteryIntent = requireContext().registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        updateLiveState()
    }

    override fun onPause() {
        requireContext().unregisterReceiver(batteryReceiver)
        super.onPause()
    }

    private fun getSecure(key: String, def: Int = 0) =
        Settings.Secure.getInt(requireContext().contentResolver, key, def)

    private fun putSecure(key: String, value: Int) =
        Settings.Secure.putInt(requireContext().contentResolver, key, value)

    private fun bindSwitch(prefKey: String, settingKey: String) {
        findPreference<SwitchPreferenceCompat>(prefKey)?.apply {
            isChecked = getSecure(settingKey) == 1
            setOnPreferenceChangeListener { _, newValue ->
                putSecure(settingKey, if (newValue as Boolean) 1 else 0)
                updateLiveState()
                true
            }
        }
    }

    /** Mirrors ChargeControlService priorities to describe what the charger is doing. */
    private fun updateLiveState() {
        val intent = lastBatteryIntent ?: return
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale
        val batteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val temperature = if (tempTenths == Int.MIN_VALUE) null else tempTenths / 10f

        val limitEnabled = getSecure(ChargeControlService.KEY_LIMIT_ENABLED) == 1
        val limit = getSecure(ChargeControlService.KEY_LIMIT, ChargeControlService.DEFAULT_LIMIT)
        val bypass = getSecure(ChargeControlService.KEY_BYPASS) == 1 ||
            getSecure(ChargeControlService.KEY_BYPASS_GAME) == 1

        val status = when {
            !plugged -> getString(R.string.battery_state_on_battery)
            bypass -> getString(R.string.battery_state_bypass)
            limitEnabled && limit < ChargeControlService.MAX_LIMIT && level >= limit ->
                getString(R.string.battery_state_paused, limit)
            batteryStatus == BatteryManager.BATTERY_STATUS_FULL || level >= 100 ->
                getString(R.string.battery_state_full)
            else -> getString(R.string.battery_state_charging)
        }
        header?.setLiveState(status, temperature)
    }

    /** State of health from the health HAL, falling back to the fuel gauge node. */
    private fun readStateOfHealth(): Int? {
        val bm = requireContext().getSystemService(BatteryManager::class.java)
        val soh = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATE_OF_HEALTH)
            ?: Int.MIN_VALUE
        if (soh in 1..100) return soh
        return readSysfsInt(BMS_SOH_PATH)?.takeIf { it in 1..100 }
    }

    private fun readCycleCount(): Int? {
        val status = requireContext().registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val cycles = status?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        if (cycles >= 0) return cycles
        return readSysfsInt(BATTERY_CYCLE_COUNT_PATH)?.takeIf { it >= 0 }
    }

    private fun readSysfsInt(path: String): Int? =
        try {
            File(path).readText().trim().toInt()
        } catch (e: Exception) {
            null
        }

    companion object {
        private const val KEY_PREF_HEADER = "battery_health_header"
        private const val KEY_PREF_SMART = "smart_charging"
        private const val KEY_PREF_LIMIT_ENABLED = "charge_limit_enabled"
        private const val KEY_PREF_LIMIT = "charge_limit"
        private const val KEY_PREF_BYPASS = "bypass_charging"

        private const val BMS_SOH_PATH = "/sys/class/power_supply/bms/soh"
        private const val BATTERY_CYCLE_COUNT_PATH = "/sys/class/power_supply/battery/cycle_count"
    }
}
