/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touch

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import java.io.File

/**
 * LineageSettings.System.HIGH_TOUCH_POLLING_RATE_ENABLE, accessed straight
 * through the Lineage settings provider to avoid a dependency on the Lineage SDK.
 */
object HighTouchPolling {
    private const val TAG = "HighTouchPolling"

    const val KEY = "high_touch_polling_rate_enable"
    const val SYSFS_PATH = "/sys/devices/virtual/touch/touch_dev/bump_sample_rate"

    private val PROVIDER_URI = Uri.parse("content://lineagesettings")
    val SYSTEM_TABLE_URI = Uri.parse("content://lineagesettings/system")
    private const val CALL_PUT_SYSTEM = "PUT_system"
    private const val CALL_VALUE_KEY = "value"
    private const val CALL_USER_KEY = "_user"

    fun isSupported(): Boolean = File(SYSFS_PATH).exists()

    fun isEnabled(context: Context): Boolean = try {
        context.contentResolver.query(
            SYSTEM_TABLE_URI, arrayOf("value"), "name=?", arrayOf(KEY), null
        )?.use { c -> c.moveToFirst() && c.getString(0) == "1" } ?: false
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read $KEY", e)
        false
    }

    /** Same call LineageSettings.System.putInt() makes. Returns false on failure. */
    fun setEnabled(context: Context, enabled: Boolean): Boolean = try {
        val extras = Bundle().apply {
            putString(CALL_VALUE_KEY, if (enabled) "1" else "0")
            putInt(CALL_USER_KEY, UserHandle.myUserId())
        }
        context.contentResolver.call(PROVIDER_URI, CALL_PUT_SYSTEM, KEY, extras)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to write $KEY", e)
        false
    }

    fun applyToHardware(enabled: Boolean) {
        try {
            File(SYSFS_PATH).writeText(if (enabled) "1" else "0")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $SYSFS_PATH", e)
        }
    }
}
