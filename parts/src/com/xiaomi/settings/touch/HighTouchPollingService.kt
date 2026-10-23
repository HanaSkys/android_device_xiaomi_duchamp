/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touch

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * The touch IC drops back to its default report rate when the panel resumes,
 * so the high touch polling rate selected in Settings is lost on every
 * screen off/on. Re-apply it after the touch controller has resumed.
 */
class HighTouchPollingService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val reapply = Runnable {
        try {
            File(HTPR_PATH).writeText("1")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $HTPR_PATH", e)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isEnabled()) return
            handler.removeCallbacks(reapply)
            // The touch controller resumes asynchronously after SCREEN_ON.
            REAPPLY_DELAYS_MS.forEach { handler.postDelayed(reapply, it) }
        }
    }

    // LineageSettings.System.HIGH_TOUCH_POLLING_RATE_ENABLE, read straight from
    // the Lineage settings provider to avoid a dependency on the Lineage SDK.
    private fun isEnabled(): Boolean = try {
        contentResolver.query(
            LINEAGE_SYSTEM_URI, arrayOf("value"), "name=?", arrayOf(KEY_HTPR), null
        )?.use { c -> c.moveToFirst() && c.getString(0) == "1" } ?: false
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read $KEY_HTPR", e)
        false
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON),
            Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        handler.removeCallbacks(reapply)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HighTouchPolling"
        private const val KEY_HTPR = "high_touch_polling_rate_enable"
        private val LINEAGE_SYSTEM_URI = Uri.parse("content://lineagesettings/system")
        private const val HTPR_PATH = "/sys/devices/virtual/touch/touch_dev/bump_sample_rate"
        private val REAPPLY_DELAYS_MS = longArrayOf(300L, 1000L)
    }
}
