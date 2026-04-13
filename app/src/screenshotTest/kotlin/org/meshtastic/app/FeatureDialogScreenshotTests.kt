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
import org.meshtastic.app.preview.EditChannelDialogPreview
import org.meshtastic.app.preview.EditDeviceProfileDialogPreview
import org.meshtastic.app.preview.LegendInfoDialogPreview
import org.meshtastic.app.preview.LegendPreview
import org.meshtastic.app.preview.MessageActionsContentPreview
import org.meshtastic.app.preview.MessageTopBarPreview
import org.meshtastic.app.preview.MultiPreview

/**
 * Screenshot tests for feature dialog components (MessageActionsContent, MessageTopBar, Legend, LegendInfoDialog,
 * EditDeviceProfileDialog, EditChannelDialog).
 */
class FeatureDialogScreenshotTests {
    @PreviewTest
    @MultiPreview
    @Composable
    fun messageActionsContentScreenshot() {
        MessageActionsContentPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun messageTopBarScreenshot() {
        MessageTopBarPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun legendScreenshot() {
        LegendPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun legendInfoDialogScreenshot() {
        LegendInfoDialogPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun editDeviceProfileDialogScreenshot() {
        EditDeviceProfileDialogPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun editChannelDialogScreenshot() {
        EditChannelDialogPreview()
    }
}
