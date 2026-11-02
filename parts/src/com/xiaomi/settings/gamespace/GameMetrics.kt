/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.gamespace

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File
import kotlin.math.abs

/**
 * One reading of the numbers shown in the game panel. Every value is optional:
 * a node that cannot be read just shows "—" instead of breaking the panel.
 */
data class GameMetrics(
    val fps: Int?,
    val cpuGhz: Float?,
    val gpuMhz: Int?,
    val tempC: Float?,
    val watts: Float?,
) {
    companion object {
        /** First field of the GED KPI line is the frame rate GED is tracking. */
        private const val GED_KPI = "/sys/kernel/ged/hal/ged_kpi"
        /** "<opp index> <kHz>" */
        private const val GPU_FREQ = "/sys/kernel/ged/hal/current_freqency"
        /** Skin temperature in millidegrees, what the hand feels. */
        private const val BOARD_TEMP = "/sys/class/thermal/thermal_message/board_sensor_temp"
        private val CPU_FREQS = listOf(7, 4, 0).map {
            "/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq"
        }

        fun read(context: Context): GameMetrics {
            val fps = readText(GED_KPI)?.substringBefore(',')?.trim()?.toIntOrNull()
                ?.takeIf { it in 1..240 }
            val cpuKhz = CPU_FREQS.mapNotNull { readText(it)?.trim()?.toLongOrNull() }.maxOrNull()
            val gpuKhz = readText(GPU_FREQ)?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toIntOrNull()
            val temp = readText(BOARD_TEMP)?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            return GameMetrics(
                fps = fps,
                cpuGhz = cpuKhz?.let { it / 1_000_000f },
                gpuMhz = gpuKhz?.let { it / 1000 },
                tempC = temp?.let { it / 1000f },
                watts = readWatts(context),
            )
        }

        private fun readWatts(context: Context): Float? {
            val bm = context.getSystemService(BatteryManager::class.java) ?: return null
            val microAmps = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (microAmps == Int.MIN_VALUE || microAmps == 0) return null
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val milliVolts = status?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            if (milliVolts <= 0) return null
            return abs(microAmps / 1_000_000f) * (milliVolts / 1000f)
        }

        private fun readText(path: String): String? =
            try { File(path).readText() } catch (e: Exception) { null }
    }
}
