/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.TopIntroPreference
import com.xiaomi.settings.R

class M3CardItemDecoration(private val context: Context) : RecyclerView.ItemDecoration() {

    private val horizontalMargin = dpToPx(16)
    private val itemGap = dpToPx(2)
    private val groupBottomMargin = dpToPx(12)

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val pref = adapter.getItem(position) ?: return
        if (!isCardItem(pref)) return

        val prevPref = if (position > 0) adapter.getItem(position - 1) else null
        val nextPref = if (position < adapter.itemCount - 1) adapter.getItem(position + 1) else null

        val hasPrev = prevPref != null && isCardItem(prevPref) && prevPref.parent == pref.parent
        val hasNext = nextPref != null && isCardItem(nextPref) && nextPref.parent == pref.parent

        outRect.left = horizontalMargin
        outRect.right = horizontalMargin
        outRect.top = if (hasPrev) itemGap else 0
        outRect.bottom = if (!hasNext) groupBottomMargin else 0
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return

        for (i in 0 until parent.childCount) {
            val view = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) continue

            val pref = adapter.getItem(position) ?: continue
            if (!isCardItem(pref)) continue

            val prevPref = if (position > 0) adapter.getItem(position - 1) else null
            val nextPref = if (position < adapter.itemCount - 1) adapter.getItem(position + 1) else null

            val hasPrev = prevPref != null && isCardItem(prevPref) && prevPref.parent == pref.parent
            val hasNext = nextPref != null && isCardItem(nextPref) && nextPref.parent == pref.parent

            val bgRes = when {
                !hasPrev && !hasNext -> R.drawable.bg_m3_card_single
                !hasPrev && hasNext  -> R.drawable.bg_m3_card_top
                hasPrev && hasNext   -> R.drawable.bg_m3_card_middle
                else                 -> R.drawable.bg_m3_card_bottom
            }

            if (view.tag != bgRes) {
                view.setBackgroundResource(bgRes)
                view.tag = bgRes
            }
        }
    }

    private fun isCardItem(pref: androidx.preference.Preference): Boolean {
        return pref !is PreferenceCategory &&
               pref !is TopIntroPreference &&
               pref !is FooterPreference &&
               pref !is MainSwitchPreference
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
