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
import com.xiaomi.settings.R
import com.xiaomi.settings.batteryhealth.ChargeControlService

/** Hanita Mods > Battery: health, bypass, smart charging and charge limit. */
class HubBatteryActivity : HubPageActivity() {

    private var batteryIntent: Intent? = null

    private lateinit var healthRing: HubRingView
    private lateinit var healthRingText: TextView
    private lateinit var healthStatus: TextView
    private lateinit var statCycles: TextView
    private lateinit var statTemp: TextView
    private lateinit var statCondition: TextView
    private lateinit var bypassToggle: SakuraToggle
    private lateinit var bypassState: TextView
    private lateinit var smartToggle: SakuraToggle
    private lateinit var smartState: TextView
    private lateinit var limitToggle: SakuraToggle
    private lateinit var limitValue: TextView
    private lateinit var limitState: TextView
    private lateinit var limitControls: View
    private lateinit var limitSlider: SakuraSlider

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
        setupPage(R.layout.hanita_hub_battery)

        healthRing = findViewById(R.id.health_ring)
        healthRingText = findViewById(R.id.health_ring_text)
        healthStatus = findViewById(R.id.health_status)
        statCycles = findViewById(R.id.stat_cycles)
        statTemp = findViewById(R.id.stat_temp)
        statCondition = findViewById(R.id.stat_condition)
        healthRing.setStrokeWidthDp(14f)
        bypassToggle = findViewById(R.id.toggle_bypass)
        bypassState = findViewById(R.id.bypass_state)
        smartToggle = findViewById(R.id.toggle_smart)
        smartState = findViewById(R.id.smart_state)
        limitToggle = findViewById(R.id.toggle_limit)
        limitValue = findViewById(R.id.limit_value)
        limitState = findViewById(R.id.limit_state)
        limitControls = findViewById(R.id.limit_controls)
        limitSlider = findViewById(R.id.limit_slider)

        limitSlider.min = ChargeControlService.MIN_LIMIT
        limitSlider.max = ChargeControlService.MAX_LIMIT
        limitSlider.onValueChanging = { limitValue.text = getString(R.string.hub_capacity_value, it) }
        limitSlider.onValueCommitted = {
            // Written once on release: every change makes the driver re-arm its hold.
            Settings.Secure.putInt(contentResolver, ChargeControlService.KEY_LIMIT, it)
        }

        bindSecureToggle(bypassToggle, ChargeControlService.KEY_BYPASS)
        bindSecureToggle(smartToggle, ChargeControlService.KEY_SMART)
        bindSecureToggle(limitToggle, ChargeControlService.KEY_LIMIT_ENABLED)
        val cardBypass = findViewById<View>(R.id.card_bypass)
        val cardSmart = findViewById<View>(R.id.card_smart)
        cardBypass.setOnClickListener { bypassToggle.toggleByUser() }
        cardSmart.setOnClickListener { smartToggle.toggleByUser() }
        findViewById<View>(R.id.card_limit).setOnClickListener { limitToggle.toggleByUser() }
        addPressFeedback(cardBypass, cardSmart)
    }

    override fun onResume() {
        super.onResume()
        batteryIntent = registerReceiver(batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        BatteryInfo.SECURE_KEYS.forEach {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(it), false, settingsObserver)
        }
        refreshHealth()
        refresh()
    }

    override fun onPause() {
        unregisterReceiver(batteryReceiver)
        contentResolver.unregisterContentObserver(settingsObserver)
        super.onPause()
    }

    private fun bindSecureToggle(toggle: SakuraToggle, key: String) {
        toggle.onUserToggle = { checked ->
            putSecure(key, checked)
            refresh()
        }
    }

    private fun refresh() {
        val b = BatteryInfo.Snapshot(batteryIntent)
        val c = BatteryInfo.Charging(contentResolver)

        bypassToggle.setChecked(c.bypass, animate = true)
        smartToggle.setChecked(c.smart, animate = true)
        limitToggle.setChecked(c.limitEnabled, animate = true)

        bypassState.text = getString(when {
            !c.bypass -> R.string.hub_off
            b.plugged -> R.string.hub_bypass_on_plugged
            else -> R.string.hub_bypass_on_unplugged
        })
        smartState.text = getString(if (c.smart) R.string.hub_smart_on else R.string.hub_off)
        limitSlider.setValue(c.limit)
        limitSlider.isEnabled = c.limitEnabled
        limitValue.text = getString(R.string.hub_capacity_value, limitSlider.value)
        limitControls.alpha = if (c.limitEnabled) 1f else 0.5f
        limitValue.alpha = if (c.limitEnabled) 1f else 0.6f
        limitState.text = if (c.limitActive) {
            getString(R.string.hub_limit_on, c.limit, c.limit - 5)
        } else {
            getString(R.string.hub_off)
        }

        healthStatus.text = getString(BatteryInfo.state(b, c).longRes, c.limit)
        statTemp.text = if (b.hasTemp) getString(R.string.hub_temp_value, b.tempTenths / 10f)
            else getString(R.string.hub_health_unknown)
    }

    /** State of health and cycles change slowly; read them once per resume. */
    private fun refreshHealth() {
        val soh = BatteryInfo.stateOfHealth(this)
        val cycles = BatteryInfo.cycleCount(batteryIntent)
        val (label, color) = when {
            soh == null -> R.string.hub_health_unknown to R.color.hub_ring_track
            soh >= 80 -> R.string.hub_health_good to R.color.hub_good
            soh >= 60 -> R.string.hub_health_fair to R.color.hub_fair
            else -> R.string.hub_health_poor to R.color.hub_poor
        }
        healthRing.setProgress(soh ?: 0, getColor(color))
        healthRingText.text = if (soh != null) getString(R.string.hub_capacity_value, soh)
            else getString(R.string.hub_health_unknown)
        statCycles.text = cycles?.toString() ?: getString(R.string.hub_health_unknown)
        statCondition.text = getString(label)
        statCondition.setTextColor(getColor(if (soh == null) R.color.hub_text else color))
    }
}
