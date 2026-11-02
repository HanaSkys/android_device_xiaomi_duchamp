/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.xiaomi.settings.R
import com.xiaomi.settings.gamespace.GameSpaceService
import com.xiaomi.settings.gamespace.GameSpaceSettings
import com.xiaomi.settings.touch.HighTouchPolling

/** Hanita Mods > Game Space: games and what happens while one is open. */
class HubGameSpaceActivity : HubPageActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var masterToggle: SakuraToggle
    private lateinit var masterState: TextView
    private lateinit var gamesRow: LinearLayout
    private val optionToggles = mutableMapOf<String, SakuraToggle>()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupPage(R.layout.hanita_hub_game)
        prefs = GameSpaceSettings.prefs(this)

        masterToggle = findViewById(R.id.toggle_gs)
        masterState = findViewById(R.id.gs_master_state)
        gamesRow = findViewById(R.id.gs_games)

        masterToggle.onUserToggle = { on ->
            prefs.edit().putBoolean(GameSpaceSettings.KEY_ENABLED, on).apply()
            if (on) GameSpaceService.start(this)
        }
        val master = findViewById<View>(R.id.card_gs_master)
        master.setOnClickListener { masterToggle.toggleByUser() }
        addPressFeedback(master)

        bindOption(R.id.row_gs_bypass, R.id.toggle_gs_bypass, GameSpaceSettings.KEY_AUTO_BYPASS)
        bindOption(R.id.row_gs_touch, R.id.toggle_gs_touch, GameSpaceSettings.KEY_AUTO_TOUCH)
        bindOption(R.id.row_gs_heads_up, R.id.toggle_gs_heads_up, GameSpaceSettings.KEY_NO_HEADS_UP)
        bindOption(R.id.row_gs_brightness, R.id.toggle_gs_brightness,
            GameSpaceSettings.KEY_LOCK_BRIGHTNESS)
        if (!HighTouchPolling.isSupported()) {
            findViewById<View>(R.id.row_gs_touch).visibility = View.GONE
        }

        bindThermal(R.id.opt_th_keep, GameSpaceSettings.THERMAL_KEEP)
        bindThermal(R.id.opt_th_game, GameSpaceSettings.THERMAL_GAME)
        bindThermal(R.id.opt_th_perf, GameSpaceSettings.THERMAL_PERFORMANCE)

        // In-game panel
        bindOption(R.id.row_gs_panel, R.id.toggle_gs_panel, GameSpaceSettings.KEY_PANEL)
        bindOption(R.id.row_gs_mini, R.id.toggle_gs_mini, GameSpaceSettings.KEY_MINI)
        val slider = findViewById<SakuraSlider>(R.id.gs_opacity_slider)
        slider.min = GameSpaceSettings.OPACITY_MIN
        slider.max = GameSpaceSettings.OPACITY_MAX
        slider.onValueChanging = { showOpacity(it) }
        slider.onValueCommitted = {
            prefs.edit().putInt(GameSpaceSettings.KEY_OPACITY, it).apply()
        }
        bindChoice(R.id.opt_pos_left, GameSpaceSettings.KEY_MINI_POS, GameSpaceSettings.POS_LEFT)
        bindChoice(R.id.opt_pos_right, GameSpaceSettings.KEY_MINI_POS, GameSpaceSettings.POS_RIGHT)
        bindChoice(R.id.opt_size_s, GameSpaceSettings.KEY_MINI_SIZE, GameSpaceSettings.SIZE_SMALL)
        bindChoice(R.id.opt_size_m, GameSpaceSettings.KEY_MINI_SIZE, GameSpaceSettings.SIZE_MEDIUM)
        bindChoice(R.id.opt_size_l, GameSpaceSettings.KEY_MINI_SIZE, GameSpaceSettings.SIZE_LARGE)
    }

    private fun bindChoice(id: Int, key: String, value: Int) {
        findViewById<View>(id).setOnClickListener { prefs.edit().putInt(key, value).apply() }
    }

    /** Updates the number and the live preview while the slider moves. */
    private fun showOpacity(percent: Int) {
        findViewById<TextView>(R.id.gs_opacity_value).text =
            getString(R.string.hub_gs_opacity_value, percent)
        val alpha = percent * 255 / 100
        listOf(R.id.gs_preview_panel, R.id.gs_preview_mini).forEach { id ->
            val v = findViewById<View>(id)
            val bg: Drawable = v.background ?: return@forEach
            v.background = bg.mutate().also { it.alpha = alpha }
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        refresh()
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onPause()
    }

    private fun bindOption(rowId: Int, toggleId: Int, key: String) {
        val toggle = findViewById<SakuraToggle>(toggleId)
        toggle.onUserToggle = { on -> prefs.edit().putBoolean(key, on).apply() }
        optionToggles[key] = toggle
        val row = findViewById<View>(rowId)
        row.setOnClickListener { toggle.toggleByUser() }
        addPressFeedback(row)
    }

    private fun bindThermal(id: Int, value: Int) {
        findViewById<View>(id).setOnClickListener {
            prefs.edit().putInt(GameSpaceSettings.KEY_THERMAL, value).apply()
        }
    }

    private fun refresh() {
        val enabled = GameSpaceSettings.isEnabled(this)
        masterToggle.setChecked(enabled, animate = true)
        masterState.text = GameSpaceSettings.summary(this)

        optionToggles.forEach { (key, toggle) ->
            toggle.setChecked(GameSpaceSettings.option(this, key), animate = true)
        }
        val thermal = GameSpaceSettings.thermal(this)
        findViewById<View>(R.id.opt_th_keep).isSelected = thermal == GameSpaceSettings.THERMAL_KEEP
        findViewById<View>(R.id.opt_th_game).isSelected = thermal == GameSpaceSettings.THERMAL_GAME
        findViewById<View>(R.id.opt_th_perf).isSelected =
            thermal == GameSpaceSettings.THERMAL_PERFORMANCE
        findViewById<View>(R.id.gs_options).alpha = if (enabled) 1f else 0.55f

        val opacity = GameSpaceSettings.opacity(this)
        findViewById<SakuraSlider>(R.id.gs_opacity_slider).setValue(opacity)
        showOpacity(opacity)
        val pos = GameSpaceSettings.miniPosition(this)
        findViewById<View>(R.id.opt_pos_left).isSelected = pos == GameSpaceSettings.POS_LEFT
        findViewById<View>(R.id.opt_pos_right).isSelected = pos == GameSpaceSettings.POS_RIGHT
        val size = GameSpaceSettings.miniSize(this)
        findViewById<View>(R.id.opt_size_s).isSelected = size == GameSpaceSettings.SIZE_SMALL
        findViewById<View>(R.id.opt_size_m).isSelected = size == GameSpaceSettings.SIZE_MEDIUM
        findViewById<View>(R.id.opt_size_l).isSelected = size == GameSpaceSettings.SIZE_LARGE

        refreshGames()
        refreshLastSession()
    }

    private fun refreshGames() {
        gamesRow.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val pm = packageManager
        GameSpaceSettings.games(this)
            .mapNotNull { pkg ->
                try { pm.getApplicationInfo(pkg, 0) } catch (e: PackageManager.NameNotFoundException) { null }
            }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .forEach { info ->
                val tile = inflater.inflate(R.layout.hanita_game_tile, gamesRow, false)
                tile.findViewById<ImageView>(R.id.tile_icon).setImageDrawable(info.loadIcon(pm))
                tile.findViewById<TextView>(R.id.tile_label).text = info.loadLabel(pm)
                tile.setOnClickListener { openPicker() }
                gamesRow.addView(tile)
            }

        val add = inflater.inflate(R.layout.hanita_game_tile, gamesRow, false)
        add.findViewById<ImageView>(R.id.tile_icon).apply {
            setImageResource(R.drawable.ic_hub_add)
            setBackgroundResource(R.drawable.bg_hub_add_tile)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            imageTintList = ColorStateList.valueOf(getColor(R.color.hub_accent))
        }
        add.findViewById<TextView>(R.id.tile_label).setText(R.string.hub_gs_add)
        add.setOnClickListener { openPicker() }
        gamesRow.addView(add)
    }

    private fun refreshLastSession() {
        val section = findViewById<View>(R.id.gs_section_last)
        val card = findViewById<View>(R.id.card_gs_last)
        val pkg = prefs.getString(GameSpaceSettings.KEY_LAST_PKG, null)
        val info = pkg?.let {
            try { packageManager.getApplicationInfo(it, 0) } catch (e: PackageManager.NameNotFoundException) { null }
        }
        if (info == null) {
            section.visibility = View.GONE
            card.visibility = View.GONE
            return
        }
        section.visibility = View.VISIBLE
        card.visibility = View.VISIBLE

        val minutes = (prefs.getLong(GameSpaceSettings.KEY_LAST_DURATION, 0L) / 60_000L).toInt()
        val end = prefs.getLong(GameSpaceSettings.KEY_LAST_END, 0L)
        val battery = prefs.getInt(GameSpaceSettings.KEY_LAST_BATTERY, 0)
        val maxTemp = prefs.getInt(GameSpaceSettings.KEY_LAST_MAX_TEMP, Int.MIN_VALUE)
        val fps = prefs.getInt(GameSpaceSettings.KEY_LAST_FPS, -1)
        findViewById<TextView>(R.id.gs_last_fps).text =
            if (fps > 0) fps.toString() else getString(R.string.hub_health_unknown)

        findViewById<ImageView>(R.id.gs_last_icon).setImageDrawable(info.loadIcon(packageManager))
        findViewById<TextView>(R.id.gs_last_name).text = info.loadLabel(packageManager)
        findViewById<TextView>(R.id.gs_last_when).text = DateUtils.getRelativeTimeSpanString(
            end, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        findViewById<TextView>(R.id.gs_last_duration).text =
            getString(R.string.hub_gs_minutes, minutes)
        findViewById<TextView>(R.id.gs_last_battery).text =
            getString(R.string.hub_gs_battery_delta, battery)
        findViewById<TextView>(R.id.gs_last_temp).text =
            if (maxTemp == Int.MIN_VALUE) getString(R.string.hub_health_unknown)
            else getString(R.string.hub_temp_short, maxTemp / 10f)
    }

    private fun openPicker() {
        startActivity(Intent(this, GamePickerActivity::class.java))
    }
}
