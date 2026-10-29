/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.os.SystemProperties
import android.util.Log
import java.io.File

/**
 * Performance options of Hanita Mods. The app only stores the choice in a
 * persist.sys property; init.hanita.rc applies it (at boot and on change).
 */
object PerformanceTuning {
    private const val TAG = "HanitaPerf"
    const val PROP_TCP = "persist.sys.hanita.tcp_cc"
    const val PROP_ZRAM = "persist.sys.hanita.zram_algo"

    const val TCP_CUBIC = "cubic"
    const val TCP_BBR = "bbr"
    const val ZRAM_LZ4 = "lz4"
    const val ZRAM_ZSTD = "zstd"

    private const val TCP_PATH = "/proc/sys/net/ipv4/tcp_congestion_control"
    private const val ZRAM_ALGO_PATH = "/sys/block/zram0/comp_algorithm"

    /** Selected TCP algorithm; falls back to what the kernel is using. */
    fun tcp(): String = SystemProperties.get(PROP_TCP, "").ifEmpty {
        readFile(TCP_PATH)?.trim()?.takeIf { it == TCP_BBR } ?: TCP_CUBIC
    }

    fun setTcp(value: String) = setProp(PROP_TCP, value)

    /** Selected ZRAM algorithm for the next boot (lz4 is the device default). */
    fun zram(): String = SystemProperties.get(PROP_ZRAM, "").ifEmpty { ZRAM_LZ4 }

    fun setZram(value: String) = setProp(PROP_ZRAM, value)

    /** Returns false if the property could not be written (e.g. denied by SELinux). */
    private fun setProp(key: String, value: String): Boolean = try {
        SystemProperties.set(key, value)
        true
    } catch (e: RuntimeException) {
        Log.e(TAG, "Failed to set $key", e)
        false
    }

    /** Algorithm zram is using right now, e.g. "lz4" from "lzo [lz4] zstd". */
    fun activeZram(): String? = readFile(ZRAM_ALGO_PATH)
        ?.let { Regex("\\[([^\\]]+)\\]").find(it)?.groupValues?.get(1) }

    private fun readFile(path: String): String? =
        try { File(path).readText() } catch (e: Exception) { null }
}
