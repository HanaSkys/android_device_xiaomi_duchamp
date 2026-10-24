/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.batteryhealth

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.xiaomi.settings.R

/** Hero card: state-of-health ring, live charging state and battery stats. */
class BatteryHealthHeaderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private var health: Int? = null
    private var cycles: Int? = null
    private var temperature: Float? = null
    private var status: String = ""
    private var animated = false

    init {
        layoutResource = R.layout.battery_health_header
        isSelectable = false
    }

    fun setHealth(health: Int?, cycles: Int?) {
        this.health = health
        this.cycles = cycles
        notifyChanged()
    }

    fun setLiveState(status: String, temperature: Float?) {
        if (this.status == status && this.temperature == temperature) return
        this.status = status
        this.temperature = temperature
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false

        val ctx = context
        val unknown = ctx.getString(R.string.battery_health_unknown_short)
        val h = health

        val (color, condition) = when {
            h == null -> ctx.getColor(R.color.bh_ring_track) to unknown
            h >= GOOD_THRESHOLD ->
                ctx.getColor(R.color.bh_ring_good) to ctx.getString(R.string.battery_health_good)
            h >= FAIR_THRESHOLD ->
                ctx.getColor(R.color.bh_ring_fair) to ctx.getString(R.string.battery_health_fair)
            else ->
                ctx.getColor(R.color.bh_ring_poor) to ctx.getString(R.string.battery_health_poor)
        }

        (holder.findViewById(R.id.health_ring) as BatteryHealthRingView)
            .setProgress(h ?: 0, color, animate = !animated && h != null)
        if (h != null) animated = true

        (holder.findViewById(R.id.health_percent) as TextView).text =
            h?.let { "$it%" } ?: unknown
        (holder.findViewById(R.id.health_status) as TextView).text = status
        (holder.findViewById(R.id.health_cycles) as TextView).text =
            cycles?.toString() ?: unknown
        (holder.findViewById(R.id.health_temperature) as TextView).text =
            temperature?.let { ctx.getString(R.string.battery_health_temperature_value, it) }
                ?: unknown
        (holder.findViewById(R.id.health_condition) as TextView).apply {
            text = condition
            setTextColor(color)
        }
    }

    companion object {
        private const val GOOD_THRESHOLD = 80
        private const val FAIR_THRESHOLD = 60
    }
}
