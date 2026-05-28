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
package org.meshtastic.screenshots.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.feature.settings.component.AppInfoSectionPreview
import org.meshtastic.feature.settings.component.AppearanceSectionPreview
import org.meshtastic.feature.settings.component.NodeLayoutSettingsCompactMinimalPreview
import org.meshtastic.feature.settings.component.NodeLayoutSettingsCompactPreview
import org.meshtastic.feature.settings.component.NodeLayoutSettingsCompletePreview
import org.meshtastic.feature.settings.component.NotificationSectionPreview
import org.meshtastic.feature.settings.component.PersistenceSectionPreview
import org.meshtastic.feature.settings.component.SampleNodeCompactAllFieldsPreview
import org.meshtastic.feature.settings.component.SampleNodeCompactNameOnlyPreview
import org.meshtastic.feature.settings.component.SampleNodeCompactSignalOnlyPreview
import org.meshtastic.feature.settings.component.SampleNodeCompactToggleMatrixPreview
import org.meshtastic.feature.settings.component.SampleNodeCompleteFahrenheitPreview
import org.meshtastic.feature.settings.component.SampleNodeCompleteImperialPreview
import org.meshtastic.feature.settings.component.SampleNodeCompletePreview
import org.meshtastic.feature.settings.component.SampleNodeCompleteToggleMatrixPreview
import org.meshtastic.feature.settings.radio.component.TakConfigCardPreview
import org.meshtastic.feature.settings.radio.component.TakServerSectionDisabledPreview
import org.meshtastic.feature.settings.radio.component.TakServerSectionEnabledPreview
import org.meshtastic.feature.settings.radio.component.TakTestCardIdlePreview
import org.meshtastic.feature.settings.radio.component.TakTestCardResultsPreview
import org.meshtastic.feature.settings.radio.component.TakTestCardRunningPreview

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotAppearanceSection() {
    AppearanceSectionPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotPersistenceSection() {
    PersistenceSectionPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotAppInfoSection() {
    AppInfoSectionPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNotificationSection() {
    NotificationSectionPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTakConfigCard() {
    TakConfigCardPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTakServerSectionDisabled() {
    TakServerSectionDisabledPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTakServerSectionEnabled() {
    TakServerSectionEnabledPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTakTestCardIdle() {
    TakTestCardIdlePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTakTestCardRunning() {
    TakTestCardRunningPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTakTestCardResults() {
    TakTestCardResultsPreview()
}

// ---------------------------------------------------------------------------
// Node layout settings screenshots
// ---------------------------------------------------------------------------

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeLayoutSettingsCompact() {
    NodeLayoutSettingsCompactPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeLayoutSettingsComplete() {
    NodeLayoutSettingsCompletePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeLayoutSettingsCompactMinimal() {
    NodeLayoutSettingsCompactMinimalPreview()
}

// ---------------------------------------------------------------------------
// Isolated sample node screenshots
// ---------------------------------------------------------------------------

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSampleNodeComplete() {
    SampleNodeCompletePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSampleNodeCompactAllFields() {
    SampleNodeCompactAllFieldsPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSampleNodeCompactSignalOnly() {
    SampleNodeCompactSignalOnlyPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSampleNodeCompactNameOnly() {
    SampleNodeCompactNameOnlyPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSampleNodeCompleteFahrenheit() {
    SampleNodeCompleteFahrenheitPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSampleNodeCompleteImperial() {
    SampleNodeCompleteImperialPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSampleNodeCompactToggleMatrix() {
    SampleNodeCompactToggleMatrixPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSampleNodeCompleteToggleMatrix() {
    SampleNodeCompleteToggleMatrixPreview()
}
