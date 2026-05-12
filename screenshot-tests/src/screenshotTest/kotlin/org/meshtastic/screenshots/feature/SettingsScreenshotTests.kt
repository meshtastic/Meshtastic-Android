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
import org.meshtastic.feature.settings.component.NotificationSectionPreview
import org.meshtastic.feature.settings.component.PersistenceSectionPreview
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
