/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import android.widget.TextView
import android.widget.Toast
import com.xiaomi.settings.R
import com.xiaomi.settings.batteryhealth.ChargeControlService
import com.xiaomi.settings.touch.HighTouchPolling
import java.io.File

/** 🌸 Hanita Mods: home of everything HanaSkys added on top of crDroid. */
class HanitaHubActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var batteryIntent: Intent? = null

    private lateinit var petals: SakuraPetalsView
    private lateinit var chipBattery: TextView
    private lateinit var chipMode: TextView
    private lateinit var chipTouch: TextView
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
    private lateinit var touchToggle: SakuraToggle
    private lateinit var touchState: TextView

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
        setContentView(R.layout.hanita_hub)

        val content = findViewById<ViewGroup>(R.id.hub_content)
        val pad = content.paddingStart
        val padBottom = content.paddingBottom
        findViewById<View>(R.id.hub_scroll).setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            content.setPadding(pad + bars.left, bars.top, pad + bars.right, padBottom + bars.bottom)
            WindowInsets.CONSUMED
        }

        petals = findViewById(R.id.hub_petals)
        chipBattery = findViewById(R.id.chip_battery)
        chipMode = findViewById(R.id.chip_mode)
        chipTouch = findViewById(R.id.chip_touch)
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
        touchToggle = findViewById(R.id.toggle_touch)
        touchState = findViewById(R.id.touch_state)

        findViewById<View>(R.id.hub_back).setOnClickListener { finish() }

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
        findViewById<View>(R.id.card_bypass).setOnClickListener { bypassToggle.toggleByUser() }
        findViewById<View>(R.id.card_smart).setOnClickListener { smartToggle.toggleByUser() }
        findViewById<View>(R.id.card_limit).setOnClickListener { limitToggle.toggleByUser() }

        if (HighTouchPolling.isSupported()) {
            touchToggle.onUserToggle = { enabled ->
                if (!HighTouchPolling.setEnabled(this, enabled)) {
                    touchToggle.setChecked(!enabled, animate = true)
                    Toast.makeText(this, R.string.hub_touch_error, Toast.LENGTH_SHORT).show()
                }
                refresh()
            }
            findViewById<View>(R.id.card_touch).setOnClickListener { touchToggle.toggleByUser() }
        } else {
            findViewById<View>(R.id.section_touch).visibility = View.GONE
            findViewById<View>(R.id.card_touch).visibility = View.GONE
        }

        listOf(R.id.card_bypass, R.id.card_smart, R.id.card_touch)
            .forEach { addPressFeedback(findViewById(it)) }

        bindOption(R.id.opt_tcp_cubic) { PerformanceTuning.setTcp(PerformanceTuning.TCP_CUBIC) }
        bindOption(R.id.opt_tcp_bbr) { PerformanceTuning.setTcp(PerformanceTuning.TCP_BBR) }
        bindOption(R.id.opt_zram_lz4) { PerformanceTuning.setZram(PerformanceTuning.ZRAM_LZ4) }
        bindOption(R.id.opt_zram_zstd) { PerformanceTuning.setZram(PerformanceTuning.ZRAM_ZSTD) }

        if (savedInstanceState == null) animateEntrance(content)
    }

    override fun onResume() {
        super.onResume()
        batteryIntent = registerReceiver(batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        SECURE_KEYS.forEach {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(it), false, settingsObserver)
        }
        contentResolver.registerContentObserver(
            HighTouchPolling.SYSTEM_TABLE_URI, true, settingsObserver)
        refreshHealth()
        refresh()
        refreshPerformance()
        petals.start()
    }

    override fun onPause() {
        petals.stop()
        unregisterReceiver(batteryReceiver)
        contentResolver.unregisterContentObserver(settingsObserver)
        super.onPause()
    }

    private fun getSecure(key: String, def: Int = 0) =
        Settings.Secure.getInt(contentResolver, key, def)

    private fun bindSecureToggle(toggle: SakuraToggle, key: String) {
        toggle.onUserToggle = { checked ->
            Settings.Secure.putInt(contentResolver, key, if (checked) 1 else 0)
            refresh()
        }
    }

    /** Live state; mirrors the priorities used by ChargeControlService. */
    private fun refresh() {
        val intent = batteryIntent
        val plugged = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val scale = (intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val level = (intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0) * 100 / scale
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE

        val bypass = getSecure(ChargeControlService.KEY_BYPASS) == 1
        val bypassActive = bypass || getSecure(ChargeControlService.KEY_BYPASS_GAME) == 1
        val smart = getSecure(ChargeControlService.KEY_SMART) == 1
        val limitEnabled = getSecure(ChargeControlService.KEY_LIMIT_ENABLED) == 1
        val limit = getSecure(ChargeControlService.KEY_LIMIT, ChargeControlService.DEFAULT_LIMIT)
            .coerceIn(ChargeControlService.MIN_LIMIT, ChargeControlService.MAX_LIMIT)
        val touch = HighTouchPolling.isSupported() && HighTouchPolling.isEnabled(this)

        bypassToggle.setChecked(bypass, animate = true)
        smartToggle.setChecked(smart, animate = true)
        limitToggle.setChecked(limitEnabled, animate = true)
        touchToggle.setChecked(touch, animate = true)

        bypassState.text = getString(when {
            !bypass -> R.string.hub_off
            plugged -> R.string.hub_bypass_on_plugged
            else -> R.string.hub_bypass_on_unplugged
        })
        smartState.text = getString(if (smart) R.string.hub_smart_on else R.string.hub_off)
        limitSlider.setValue(limit)
        limitSlider.isEnabled = limitEnabled
        limitValue.text = getString(R.string.hub_capacity_value, limitSlider.value)
        limitControls.alpha = if (limitEnabled) 1f else 0.5f
        limitValue.alpha = if (limitEnabled) 1f else 0.6f
        limitState.text = if (limitEnabled && limit < ChargeControlService.MAX_LIMIT) {
            getString(R.string.hub_limit_on, limit, limit - 5)
        } else {
            getString(R.string.hub_off)
        }
        touchState.text = getString(if (touch) R.string.hub_touch_on else R.string.hub_touch_off)

        val charging = plugged && status == BatteryManager.BATTERY_STATUS_CHARGING
        chipBattery.text = getString(
            if (charging) R.string.hub_chip_charging else R.string.hub_chip_battery, level)
        when {
            bypassActive -> {
                chipMode.text = getString(R.string.hub_chip_bypass)
                chipMode.visibility = View.VISIBLE
            }
            limitEnabled && limit < ChargeControlService.MAX_LIMIT -> {
                chipMode.text = getString(R.string.hub_chip_limit, limit)
                chipMode.visibility = View.VISIBLE
            }
            else -> chipMode.visibility = View.GONE
        }
        chipTouch.visibility = if (touch) View.VISIBLE else View.GONE

        val state = getString(when {
            !plugged -> R.string.hub_state_on_battery
            bypassActive -> R.string.hub_state_bypass
            limitEnabled && limit < ChargeControlService.MAX_LIMIT && level >= limit ->
                R.string.hub_state_paused
            status == BatteryManager.BATTERY_STATUS_FULL || level >= 100 -> R.string.hub_state_full
            else -> R.string.hub_state_charging
        }, limit)
        healthStatus.text = state
        statTemp.text = if (tempTenths == Int.MIN_VALUE) getString(R.string.hub_health_unknown)
            else getString(R.string.hub_temp_value, tempTenths / 10f)
    }

    private fun bindOption(id: Int, apply: () -> Boolean) {
        findViewById<View>(id).setOnClickListener {
            if (it.isSelected) return@setOnClickListener
            if (!apply()) {
                Toast.makeText(this, R.string.hub_option_error, Toast.LENGTH_SHORT).show()
            }
            refreshPerformance()
        }
    }

    private fun refreshPerformance() {
        val tcp = PerformanceTuning.tcp()
        val bbr = tcp == PerformanceTuning.TCP_BBR
        findViewById<View>(R.id.opt_tcp_cubic).isSelected = !bbr
        findViewById<View>(R.id.opt_tcp_bbr).isSelected = bbr
        findViewById<TextView>(R.id.tcp_state).text = getString(
            if (bbr) R.string.hub_tcp_bbr_summary else R.string.hub_tcp_cubic_summary)

        val zram = PerformanceTuning.zram()
        val zstd = zram == PerformanceTuning.ZRAM_ZSTD
        findViewById<View>(R.id.opt_zram_lz4).isSelected = !zstd
        findViewById<View>(R.id.opt_zram_zstd).isSelected = zstd
        findViewById<TextView>(R.id.zram_state).text = getString(
            if (zstd) R.string.hub_zram_zstd_summary else R.string.hub_zram_lz4_summary)
        val note = findViewById<TextView>(R.id.zram_note)
        val active = PerformanceTuning.activeZram()
        if (active == null) {
            note.visibility = View.GONE
        } else {
            note.visibility = View.VISIBLE
            note.text = getString(
                if (active == zram) R.string.hub_zram_note_same else R.string.hub_zram_note_reboot,
                active.uppercase())
        }
    }

    /** State of health and cycles change slowly; read them once per resume. */
    private fun refreshHealth() {
        val soh = readStateOfHealth()
        val cycles = readCycleCount()
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

    private fun readStateOfHealth(): Int? {
        val soh = getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATE_OF_HEALTH) ?: Int.MIN_VALUE
        if (soh in 1..100) return soh
        return readSysfsInt(BMS_SOH_PATH)?.takeIf { it in 1..100 }
    }

    private fun readCycleCount(): Int? {
        val cycles = batteryIntent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        if (cycles >= 0) return cycles
        return readSysfsInt(BATTERY_CYCLE_COUNT_PATH)?.takeIf { it >= 0 }
    }

    private fun readSysfsInt(path: String): Int? =
        try { File(path).readText().trim().toInt() } catch (e: Exception) { null }

    /** Cards rise in one after another. */
    private fun animateEntrance(content: ViewGroup) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val offset = 28 * resources.displayMetrics.density
        val easing = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        var index = 0
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            child.alpha = 0f
            child.translationY = offset
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(60L * index++)
                .setDuration(480L)
                .setInterpolator(easing)
                .start()
        }
    }

    /** Gentle squeeze while a card is pressed. */
    private fun addPressFeedback(card: View) {
        card.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(120L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200L).start()
            }
            false
        }
    }

    companion object {
        private val SECURE_KEYS = listOf(
            ChargeControlService.KEY_BYPASS,
            ChargeControlService.KEY_BYPASS_GAME,
            ChargeControlService.KEY_SMART,
            ChargeControlService.KEY_LIMIT_ENABLED,
            ChargeControlService.KEY_LIMIT,
        )
        private const val BMS_SOH_PATH = "/sys/class/power_supply/bms/soh"
        private const val BATTERY_CYCLE_COUNT_PATH = "/sys/class/power_supply/battery/cycle_count"
    }
}
