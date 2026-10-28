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
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * The touch IC drops back to its default report rate when the panel resumes,
 * so the high touch polling rate selected in Settings is lost on every
 * screen off/on. Re-apply it after the touch controller has resumed, and
 * apply it right away whenever the setting changes (e.g. from Hanita Mods).
 */
class HighTouchPollingService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val reapply = Runnable { HighTouchPolling.applyToHardware(true) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!HighTouchPolling.isEnabled(this@HighTouchPollingService)) return
            handler.removeCallbacks(reapply)
            // The touch controller resumes asynchronously after SCREEN_ON.
            REAPPLY_DELAYS_MS.forEach { handler.postDelayed(reapply, it) }
        }
    }

    private val settingObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            // Registered on the whole table: only react to our key.
            if (uri != null && uri.lastPathSegment != HighTouchPolling.KEY) return
            handler.removeCallbacks(reapply)
            HighTouchPolling.applyToHardware(
                HighTouchPolling.isEnabled(this@HighTouchPollingService))
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON),
            Context.RECEIVER_NOT_EXPORTED)
        contentResolver.registerContentObserver(
            HighTouchPolling.SYSTEM_TABLE_URI, true, settingObserver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(settingObserver)
        unregisterReceiver(screenReceiver)
        handler.removeCallbacks(reapply)
        super.onDestroy()
    }

    companion object {
        private val REAPPLY_DELAYS_MS = longArrayOf(300L, 1000L)
    }
}
