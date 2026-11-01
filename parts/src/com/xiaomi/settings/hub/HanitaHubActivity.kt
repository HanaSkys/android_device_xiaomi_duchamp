/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.xiaomi.settings.R
import com.xiaomi.settings.batteryhealth.ChargeControlService
import com.xiaomi.settings.touch.HighTouchPolling

/**
 * 🌸 Hanita Mods home: live battery stats, quick toggles and the categories
 * with everything HanaSkys added on top of crDroid.
 */
class HanitaHubActivity : HubPageActivity() {

    private var batteryIntent: Intent? = null
    private var soh: Int? = null

    private lateinit var petals: SakuraPetalsView
    private lateinit var statBattery: TextView
    private lateinit var statBatteryLabel: TextView
    private lateinit var statHealth: TextView
    private lateinit var statTemp: TextView
    private lateinit var quickBypass: View
    private lateinit var quickTouch: View
    private lateinit var quickBbr: View
    private lateinit var quickSmart: View
    private lateinit var catBattery: TextView
    private lateinit var catDisplay: TextView
    private lateinit var catPerformance: TextView

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            batteryIntent = intent
            refresh()
        }
    }

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupPage(R.layout.hanita_hub)

        petals = findViewById(R.id.hub_petals)
        statBattery = findViewById(R.id.stat_battery)
        statBatteryLabel = findViewById(R.id.stat_battery_label)
        statHealth = findViewById(R.id.stat_health)
        statTemp = findViewById(R.id.stat_temp)
        quickBypass = findViewById(R.id.quick_bypass)
        quickTouch = findViewById(R.id.quick_touch)
        quickBbr = findViewById(R.id.quick_bbr)
        quickSmart = findViewById(R.id.quick_smart)
        catBattery = findViewById(R.id.cat_battery_status)
        catDisplay = findViewById(R.id.cat_display_status)
        catPerformance = findViewById(R.id.cat_performance_status)

        bindQuick(quickBypass) { putSecure(ChargeControlService.KEY_BYPASS, it) }
        bindQuick(quickSmart) { putSecure(ChargeControlService.KEY_SMART, it) }
        bindQuick(quickBbr) {
            PerformanceTuning.setTcp(if (it) PerformanceTuning.TCP_BBR else PerformanceTuning.TCP_CUBIC)
        }
        if (HighTouchPolling.isSupported()) {
            bindQuick(quickTouch) { HighTouchPolling.setEnabled(this, it) }
        } else {
            quickTouch.visibility = View.GONE
        }

        bindCategory(R.id.cat_battery, HubBatteryActivity::class.java)
        bindCategory(R.id.cat_display, HubDisplayActivity::class.java)
        bindCategory(R.id.cat_performance, HubPerformanceActivity::class.java)
    }

    override fun onResume() {
        super.onResume()
        batteryIntent = registerReceiver(batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        BatteryInfo.SECURE_KEYS.forEach {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(it), false, settingsObserver)
        }
        contentResolver.registerContentObserver(
            HighTouchPolling.SYSTEM_TABLE_URI, true, settingsObserver)
        // State of health changes slowly; read it once per resume.
        soh = BatteryInfo.stateOfHealth(this)
        refresh()
        petals.start()
    }

    override fun onPause() {
        petals.stop()
        unregisterReceiver(batteryReceiver)
        contentResolver.unregisterContentObserver(settingsObserver)
        super.onPause()
    }

    /** A quick toggle flips its option in place; [apply] returns false if it failed. */
    private fun bindQuick(view: View, apply: (Boolean) -> Boolean) {
        view.setOnClickListener {
            if (!apply(!view.isSelected)) {
                Toast.makeText(this, R.string.hub_option_error, Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
        addPressFeedback(view)
    }

    private fun setQuick(view: View, on: Boolean) {
        view.isSelected = on
        view.stateDescription = getString(if (on) R.string.hub_on else R.string.hub_off)
    }

    private fun bindCategory(id: Int, page: Class<*>) {
        val card = findViewById<View>(id)
        card.setOnClickListener { startActivity(Intent(this, page)) }
        addPressFeedback(card)
    }

    private fun refresh() {
        val b = BatteryInfo.Snapshot(batteryIntent)
        val c = BatteryInfo.Charging(contentResolver)
        val touch = HighTouchPolling.isSupported() && HighTouchPolling.isEnabled(this)
        val tcp = PerformanceTuning.tcp()

        // Hero stats
        statBattery.text = getString(
            if (b.charging) R.string.hub_stat_level_charging else R.string.hub_stat_level, b.level)
        statBatteryLabel.text = getString(BatteryInfo.state(b, c).shortRes)
        statHealth.text = soh?.let { getString(R.string.hub_capacity_value, it) }
            ?: getString(R.string.hub_health_unknown)
        statTemp.text = if (b.hasTemp) getString(R.string.hub_temp_short, b.tempTenths / 10f)
            else getString(R.string.hub_health_unknown)

        // Quick toggles
        setQuick(quickBypass, c.bypass)
        setQuick(quickSmart, c.smart)
        setQuick(quickBbr, tcp == PerformanceTuning.TCP_BBR)
        setQuick(quickTouch, touch)

        // Category summaries
        val battery = buildList {
            soh?.let { add(getString(R.string.hub_cat_health, it)) }
            when {
                c.bypassActive -> add(getString(R.string.hub_cat_bypass))
                c.limitActive -> add(getString(R.string.hub_cat_limit, c.limit))
                c.smart -> add(getString(R.string.hub_smart_title))
            }
        }
        catBattery.text = if (battery.isEmpty()) getString(R.string.hub_cat_battery_summary)
            else battery.joinToString(SEPARATOR)

        val res = DisplayResolution.current()
        catDisplay.text = buildList {
            add(getString(res.labelRes))
            if (touch) add(getString(R.string.hub_cat_touch_on))
        }.joinToString(SEPARATOR)

        catPerformance.text = listOf(tcp, PerformanceTuning.zram())
            .joinToString(SEPARATOR) { it.uppercase() }
    }

    companion object {
        private const val SEPARATOR = " · "
    }
}
