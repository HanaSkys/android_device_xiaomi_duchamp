/*
 * SPDX-FileCopyrightText: 2026 HanaSkys
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.bypass

import android.os.Bundle
import android.provider.Settings
import androidx.preference.PreferenceFragmentCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.xiaomi.settings.R

class BypassChargingFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.bypass_charging, rootKey)
        val resolver = requireContext().contentResolver

        findPreference<MainSwitchPreference>(KEY_SWITCH)?.apply {
            isChecked = Settings.Secure.getInt(
                resolver, BypassChargingService.KEY_MANUAL, 0) == 1
            setOnPreferenceChangeListener { _, newValue ->
                Settings.Secure.putInt(
                    resolver, BypassChargingService.KEY_MANUAL,
                    if (newValue as Boolean) 1 else 0)
                true
            }
        }
    }

    companion object {
        private const val KEY_SWITCH = "bypass_charging_enable"
    }
}
