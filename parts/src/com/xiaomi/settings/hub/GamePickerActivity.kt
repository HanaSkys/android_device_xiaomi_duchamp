/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.xiaomi.settings.R
import com.xiaomi.settings.gamespace.GameSpaceSettings

/** Lets the user mark which installed apps are games. Apps Android tags as games go first. */
class GamePickerActivity : HubPageActivity() {

    private class App(val pkg: String, val label: String, val icon: Drawable, val isGame: Boolean)

    private lateinit var list: LinearLayout
    private lateinit var loading: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupPage(R.layout.hanita_game_picker)
        list = findViewById(R.id.picker_list)
        loading = findViewById(R.id.picker_loading)

        Thread({
            val apps = loadApps()
            runOnUiThread { if (!isDestroyed) show(apps) }
        }, "GamePickerLoad").start()
    }

    private fun loadApps(): List<App> {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .map {
                App(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm),
                    it.category == ApplicationInfo.CATEGORY_GAME)
            }
    }

    private fun show(apps: List<App>) {
        loading.visibility = View.GONE
        list.visibility = View.VISIBLE
        val selected = GameSpaceSettings.games(this).toMutableSet()
        val inflater = LayoutInflater.from(this)

        apps.sortedWith(compareBy<App>({ it.pkg !in selected }, { !it.isGame }, { it.label.lowercase() }))
            .forEach { app ->
                val row = inflater.inflate(R.layout.hanita_game_row, list, false)
                row.findViewById<ImageView>(R.id.row_icon).setImageDrawable(app.icon)
                row.findViewById<TextView>(R.id.row_label).text = app.label
                row.findViewById<View>(R.id.row_tag).visibility =
                    if (app.isGame) View.VISIBLE else View.GONE
                val toggle = row.findViewById<SakuraToggle>(R.id.row_toggle)
                toggle.contentDescription = app.label
                toggle.setChecked(app.pkg in selected, animate = false)
                toggle.onUserToggle = { on ->
                    if (on) selected.add(app.pkg) else selected.remove(app.pkg)
                    GameSpaceSettings.setGames(this, selected)
                }
                row.setOnClickListener { toggle.toggleByUser() }
                list.addView(row)
            }
    }
}
