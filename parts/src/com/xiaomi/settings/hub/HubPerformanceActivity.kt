/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.xiaomi.settings.R

/** Hanita Mods > Performance: TCP congestion control and ZRAM compression. */
class HubPerformanceActivity : HubPageActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupPage(R.layout.hanita_hub_performance)

        bindOption(R.id.opt_tcp_cubic) { PerformanceTuning.setTcp(PerformanceTuning.TCP_CUBIC) }
        bindOption(R.id.opt_tcp_bbr) { PerformanceTuning.setTcp(PerformanceTuning.TCP_BBR) }
        bindOption(R.id.opt_zram_lz4) { PerformanceTuning.setZram(PerformanceTuning.ZRAM_LZ4) }
        bindOption(R.id.opt_zram_zstd) { PerformanceTuning.setZram(PerformanceTuning.ZRAM_ZSTD) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun bindOption(id: Int, apply: () -> Boolean) {
        findViewById<View>(id).setOnClickListener {
            if (it.isSelected) return@setOnClickListener
            if (!apply()) {
                Toast.makeText(this, R.string.hub_option_error, Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    private fun refresh() {
        val bbr = PerformanceTuning.tcp() == PerformanceTuning.TCP_BBR
        findViewById<View>(R.id.opt_tcp_cubic).isSelected = !bbr
        findViewById<View>(R.id.opt_tcp_bbr).isSelected = bbr
        findViewById<TextView>(R.id.tcp_state).text = getString(
            if (bbr) R.string.hub_tcp_bbr_summary else R.string.hub_tcp_cubic_summary)

        val zram = PerformanceTuning.zram()
        val zstd = zram == PerformanceTuning.ZRAM_ZSTD
        findViewById<View>(R.id.opt_zram_lz4).isSelected = !zstd
        findViewById<View>(R.id.opt_zram_zstd).isSelected = zstd
        findViewById<TextView>(R.id.zram_state).text = getString(
            if (zstd) R.string.hub_zram_zstd_summary else R.string.hub_zram_lz4_summary)
        val note = findViewById<TextView>(R.id.zram_note)
        val active = PerformanceTuning.activeZram()
        if (active == null) {
            note.visibility = View.GONE
        } else {
            note.visibility = View.VISIBLE
            note.text = getString(
                if (active == zram) R.string.hub_zram_note_same else R.string.hub_zram_note_reboot,
                active.uppercase())
        }
    }
}
