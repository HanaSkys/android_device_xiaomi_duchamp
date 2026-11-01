/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.hub

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.TextView
import android.widget.Toast
import com.xiaomi.settings.R

/** Hanita Mods > Display > Screen resolution. */
class ResolutionActivity : Activity() {

    private class Column(
        val option: DisplayResolution.Option,
        val root: View,
        val preview: ResolutionPreviewView,
        val label: TextView,
    )

    private lateinit var columns: List<Column>
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.hanita_resolution)

        val content = findViewById<ViewGroup>(R.id.res_content)
        val pad = content.paddingStart
        val padBottom = content.paddingBottom
        findViewById<View>(R.id.res_scroll).setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            content.setPadding(pad + bars.left, bars.top, pad + bars.right, padBottom + bars.bottom)
            WindowInsets.CONSUMED
        }
        findViewById<View>(R.id.res_back).setOnClickListener { finish() }
        info = findViewById(R.id.res_info)

        columns = listOf(
            column(DisplayResolution.Option.WQHD, R.id.col_wqhd, R.id.preview_wqhd,
                R.id.label_wqhd, R.id.size_wqhd),
            column(DisplayResolution.Option.FHD, R.id.col_fhd, R.id.preview_fhd,
                R.id.label_fhd, R.id.size_fhd),
            column(DisplayResolution.Option.HD, R.id.col_hd, R.id.preview_hd,
                R.id.label_hd, R.id.size_hd),
        )
        columns.forEach { col -> col.root.setOnClickListener { onPick(col.option) } }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun column(
        option: DisplayResolution.Option, rootId: Int, previewId: Int, labelId: Int, sizeId: Int,
    ): Column {
        findViewById<TextView>(sizeId).text =
            getString(R.string.hub_res_size, option.width, option.height)
        val preview = findViewById<ResolutionPreviewView>(previewId)
        preview.sharpness = option.previewSharpness
        return Column(option, findViewById(rootId), preview, findViewById(labelId))
    }

    private fun refresh() {
        val current = DisplayResolution.current()
        columns.forEach { col ->
            val selected = col.option == current
            col.preview.selectedOption = selected
            col.label.setTextColor(getColor(if (selected) R.color.hub_accent else R.color.hub_text))
            col.root.isSelected = selected
        }
        info.text = getString(R.string.hub_res_info, getString(current.labelRes),
            current.width, current.height)
    }

    private fun onPick(option: DisplayResolution.Option) {
        if (option == DisplayResolution.current()) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.hub_res_confirm_title, getString(option.labelRes)))
            .setMessage(R.string.hub_res_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.hub_res_confirm_apply) { _, _ ->
                if (!DisplayResolution.apply(option)) {
                    Toast.makeText(this, R.string.hub_res_error, Toast.LENGTH_SHORT).show()
                }
                // The density change recreates this activity; refresh in case it does not.
                refresh()
            }
            .show()
    }
}
