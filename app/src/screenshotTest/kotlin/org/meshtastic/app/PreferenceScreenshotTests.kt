/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.app

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.app.preview.DropDownPreferencePreview
import org.meshtastic.app.preview.EditTextPreferencePreview
import org.meshtastic.app.preview.MultiPreview
import org.meshtastic.app.preview.PreferenceCategoryPreview
import org.meshtastic.app.preview.PreferenceFooterPreview
import org.meshtastic.app.preview.RegularPreferencePreview
import org.meshtastic.app.preview.SliderPreferencePreview
import org.meshtastic.app.preview.SwitchPreferencePreview
import org.meshtastic.app.preview.TextDividerPreferencePreview

/** Screenshot tests for Meshtastic preference/settings components. */
class PreferenceScreenshotTests {
    @PreviewTest
    @MultiPreview
    @Composable
    fun switchPreferenceScreenshot() {
        SwitchPreferencePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun sliderPreferenceScreenshot() {
        SliderPreferencePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun regularPreferenceScreenshot() {
        RegularPreferencePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun editTextPreferenceScreenshot() {
        EditTextPreferencePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun dropDownPreferenceScreenshot() {
        DropDownPreferencePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun preferenceCategoryScreenshot() {
        PreferenceCategoryPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun textDividerPreferenceScreenshot() {
        TextDividerPreferencePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun preferenceFooterScreenshot() {
        PreferenceFooterPreview()
    }
}
