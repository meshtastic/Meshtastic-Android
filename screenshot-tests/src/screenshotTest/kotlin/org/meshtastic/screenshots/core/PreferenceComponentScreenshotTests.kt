/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.screenshots.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.ui.component.BitwisePreferencePreview
import org.meshtastic.core.ui.component.DropDownPreferencePreview
import org.meshtastic.core.ui.component.EditIPv4PreferencePreview
import org.meshtastic.core.ui.component.EditListPreferencePreview
import org.meshtastic.core.ui.component.EditPasswordPreferencePreview
import org.meshtastic.core.ui.component.EditTextPreferencePreview
import org.meshtastic.core.ui.component.ElevationInfoPreview
import org.meshtastic.core.ui.component.IAQScalePreview
import org.meshtastic.core.ui.component.IconInfoPreview
import org.meshtastic.core.ui.component.NodeChipPreview
import org.meshtastic.core.ui.component.PositionPrecisionPreferencePreview
import org.meshtastic.core.ui.component.PreferenceCategoryPreview
import org.meshtastic.core.ui.component.PreviewAllSecurityIconsWithDialog
import org.meshtastic.core.ui.component.PreviewImportFABChannel
import org.meshtastic.core.ui.component.PreviewImportFABContact
import org.meshtastic.core.ui.component.RegularPreferencePreview
import org.meshtastic.core.ui.component.SliderPreferenceDisabledPreview
import org.meshtastic.core.ui.component.SliderPreferencePreview
import org.meshtastic.core.ui.component.SwitchPreferencePreview
import org.meshtastic.core.ui.component.TextDividerPreferencePreview

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSliderPreference() {
    SliderPreferencePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSliderPreferenceDisabled() {
    SliderPreferenceDisabledPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotEditTextPreference() {
    EditTextPreferencePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotEditListPreference() {
    EditListPreferencePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSwitchPreference() {
    SwitchPreferencePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotBitwisePreference() {
    BitwisePreferencePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotPositionPrecisionPreference() {
    PositionPrecisionPreferencePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotRegularPreference() {
    RegularPreferencePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDropDownPreference() {
    DropDownPreferencePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotEditIPv4Preference() {
    EditIPv4PreferencePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotEditPasswordPreference() {
    EditPasswordPreferencePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotPreferenceCategory() {
    PreferenceCategoryPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTextDividerPreference() {
    TextDividerPreferencePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotElevationInfo() {
    ElevationInfoPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotAllSecurityIcons() {
    PreviewAllSecurityIconsWithDialog()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotIAQScale() {
    IAQScalePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeChip() {
    NodeChipPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotIconInfo() {
    IconInfoPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotImportFABContact() {
    PreviewImportFABContact()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotImportFABChannel() {
    PreviewImportFABChannel()
}
