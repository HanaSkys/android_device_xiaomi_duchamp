/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.utils

import android.content.Context
import android.util.AttributeSet
import android.widget.RadioButton
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.xiaomi.settings.R

class RadioButtonPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private var isChecked: Boolean = false

    init {
        widgetLayoutResource = R.layout.preference_widget_radiobutton
    }

    fun setChecked(checked: Boolean) {
        if (isChecked != checked) {
            isChecked = checked
            notifyChanged()
        }
    }

    fun isChecked(): Boolean = isChecked

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val radioButton = holder.findViewById(R.id.radio_button) as? RadioButton
        radioButton?.isChecked = isChecked
    }
}
