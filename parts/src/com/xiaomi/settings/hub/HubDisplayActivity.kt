/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.xiaomi.settings.R
import com.xiaomi.settings.touch.HighTouchPolling

/** Hanita Mods > Display & touch: screen resolution and high touch polling. */
class HubDisplayActivity : HubPageActivity() {

    private lateinit var resolutionState: TextView
    private lateinit var touchToggle: SakuraToggle
    private lateinit var touchState: TextView

    private val touchObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = refreshTouch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupPage(R.layout.hanita_hub_display)

        resolutionState = findViewById(R.id.resolution_state)
        touchToggle = findViewById(R.id.toggle_touch)
        touchState = findViewById(R.id.touch_state)

        val cardResolution = findViewById<View>(R.id.card_resolution)
        cardResolution.setOnClickListener {
            startActivity(Intent(this, ResolutionActivity::class.java))
        }
        addPressFeedback(cardResolution)

        val cardTouch = findViewById<View>(R.id.card_touch)
        if (HighTouchPolling.isSupported()) {
            touchToggle.onUserToggle = { enabled ->
                if (!HighTouchPolling.setEnabled(this, enabled)) {
                    touchToggle.setChecked(!enabled, animate = true)
                    Toast.makeText(this, R.string.hub_touch_error, Toast.LENGTH_SHORT).show()
                }
                refreshTouch()
            }
            cardTouch.setOnClickListener { touchToggle.toggleByUser() }
            addPressFeedback(cardTouch)
        } else {
            cardTouch.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(
            HighTouchPolling.SYSTEM_TABLE_URI, true, touchObserver)
        val res = DisplayResolution.current()
        resolutionState.text = getString(
            R.string.hub_res_state, getString(res.labelRes), res.width, res.height)
        refreshTouch()
    }

    override fun onPause() {
        contentResolver.unregisterContentObserver(touchObserver)
        super.onPause()
    }

    private fun refreshTouch() {
        val touch = HighTouchPolling.isSupported() && HighTouchPolling.isEnabled(this)
        touchToggle.setChecked(touch, animate = true)
        touchState.text = getString(if (touch) R.string.hub_touch_on else R.string.hub_touch_off)
    }
}
