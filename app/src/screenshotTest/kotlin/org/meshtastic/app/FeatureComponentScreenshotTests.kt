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
import org.meshtastic.app.preview.ExpressiveSectionPreview
import org.meshtastic.app.preview.HomoglyphSettingPreview
import org.meshtastic.app.preview.LoadingOverlayPreview
import org.meshtastic.app.preview.LogLinePreview
import org.meshtastic.app.preview.MapReportingPreferencePreview
import org.meshtastic.app.preview.MultiPreview
import org.meshtastic.app.preview.NodeActionButtonPreview
import org.meshtastic.app.preview.NodeFilterTextFieldPreview
import org.meshtastic.app.preview.NotificationSectionPreview
import org.meshtastic.app.preview.ThemePickerDialogPreview
import org.meshtastic.app.preview.WarningDialogPreview

/** Screenshot tests for feature module components (node, messaging, settings). */
class FeatureComponentScreenshotTests {
    @PreviewTest
    @MultiPreview
    @Composable
    fun logLineScreenshot() {
        LogLinePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun nodeFilterTextFieldScreenshot() {
        NodeFilterTextFieldPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun expressiveSectionScreenshot() {
        ExpressiveSectionPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun themePickerDialogScreenshot() {
        ThemePickerDialogPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun homoglyphSettingScreenshot() {
        HomoglyphSettingPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun notificationSectionScreenshot() {
        NotificationSectionPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun warningDialogScreenshot() {
        WarningDialogPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun nodeActionButtonScreenshot() {
        NodeActionButtonPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun loadingOverlayScreenshot() {
        LoadingOverlayPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun mapReportingPreferenceScreenshot() {
        MapReportingPreferencePreview()
    }
}
