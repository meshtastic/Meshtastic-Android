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
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.app.preview.ExpressiveSectionPreview
import org.meshtastic.app.preview.HomoglyphSettingPreview
import org.meshtastic.app.preview.LoadingOverlayPreview
import org.meshtastic.app.preview.LogLinePreview
import org.meshtastic.app.preview.MapReportingPreferencePreview
import org.meshtastic.app.preview.NodeActionButtonPreview
import org.meshtastic.app.preview.NodeFilterTextFieldPreview
import org.meshtastic.app.preview.NotificationSectionPreview
import org.meshtastic.app.preview.ThemePickerDialogPreview
import org.meshtastic.app.preview.WarningDialogPreview

/** Screenshot tests for feature module components (node, messaging, settings). */
class FeatureComponentScreenshotTests {
    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun logLineScreenshot() {
        LogLinePreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun nodeFilterTextFieldScreenshot() {
        NodeFilterTextFieldPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun expressiveSectionScreenshot() {
        ExpressiveSectionPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun themePickerDialogScreenshot() {
        ThemePickerDialogPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun homoglyphSettingScreenshot() {
        HomoglyphSettingPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun notificationSectionScreenshot() {
        NotificationSectionPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun warningDialogScreenshot() {
        WarningDialogPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun nodeActionButtonScreenshot() {
        NodeActionButtonPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun loadingOverlayScreenshot() {
        LoadingOverlayPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun mapReportingPreferenceScreenshot() {
        MapReportingPreferencePreview()
    }
}
