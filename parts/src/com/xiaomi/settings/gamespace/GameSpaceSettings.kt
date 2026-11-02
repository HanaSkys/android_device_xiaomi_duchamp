/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.gamespace

import android.content.Context
import android.content.SharedPreferences
import com.xiaomi.settings.R

/**
 * Game Space options. Kept in device-protected storage so the service can read
 * them right after LOCKED_BOOT_COMPLETED, before the user unlocks.
 */
object GameSpaceSettings {
    private const val PREFS = "hanita_game_space"

    const val KEY_ENABLED = "enabled"
    const val KEY_GAMES = "games"
    const val KEY_AUTO_BYPASS = "auto_bypass"
    const val KEY_AUTO_TOUCH = "auto_touch"
    const val KEY_NO_HEADS_UP = "no_heads_up"
    const val KEY_LOCK_BRIGHTNESS = "lock_brightness"
    const val KEY_THERMAL = "thermal_profile"

    /** sconfig values, same ones the thermal profiles of XiaomiParts use. */
    const val THERMAL_KEEP = 0
    const val THERMAL_GAME = 19
    const val THERMAL_PERFORMANCE = 18

    // What the running session changed, so it can always be undone (even after a crash).
    const val KEY_SESSION_PKG = "session_pkg"
    const val KEY_SET_BYPASS = "session_set_bypass"
    const val KEY_SET_TOUCH = "session_set_touch"
    const val KEY_PREV_HEADS_UP = "session_prev_heads_up"
    const val KEY_SET_BRIGHTNESS = "session_set_brightness"
    const val KEY_PREV_THERMAL = "session_prev_thermal"
    val SESSION_KEYS = listOf(
        KEY_SESSION_PKG, KEY_SET_BYPASS, KEY_SET_TOUCH,
        KEY_PREV_HEADS_UP, KEY_SET_BRIGHTNESS, KEY_PREV_THERMAL,
    )

    // Last finished session
    const val KEY_LAST_PKG = "last_pkg"
    const val KEY_LAST_END = "last_end"
    const val KEY_LAST_DURATION = "last_duration_ms"
    const val KEY_LAST_BATTERY = "last_battery_delta"
    const val KEY_LAST_MAX_TEMP = "last_max_temp_tenths"

    fun prefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    fun games(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_GAMES, null)?.toSet() ?: emptySet()

    fun setGames(context: Context, games: Set<String>) {
        prefs(context).edit().putStringSet(KEY_GAMES, HashSet(games)).apply()
    }

    fun option(context: Context, key: String) = prefs(context).getBoolean(key, defaultFor(key))

    fun defaultFor(key: String) = when (key) {
        KEY_AUTO_BYPASS, KEY_AUTO_TOUCH, KEY_NO_HEADS_UP -> true
        else -> false
    }

    fun thermal(context: Context) = prefs(context).getInt(KEY_THERMAL, THERMAL_GAME)

    /** "On · 3 games" / "Off", for the Hub. */
    fun summary(context: Context): String {
        if (!isEnabled(context)) return context.getString(R.string.hub_off)
        val count = games(context).size
        if (count == 0) return context.getString(R.string.hub_gs_no_games)
        return context.getString(R.string.hub_on) + " · " +
            context.resources.getQuantityString(R.plurals.hub_gs_games_count, count, count)
    }
}
