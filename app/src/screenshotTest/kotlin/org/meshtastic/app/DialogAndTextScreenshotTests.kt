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
import org.meshtastic.app.preview.ClickableTextFieldPreview
import org.meshtastic.app.preview.DeliveryInfoErrorPreview
import org.meshtastic.app.preview.DeliveryInfoPreview
import org.meshtastic.app.preview.MeshtasticResourceDialogPreview
import org.meshtastic.app.preview.MeshtasticTextDialogPreview
import org.meshtastic.app.preview.MultiPreview
import org.meshtastic.app.preview.ShutdownConfirmationDialogPreview

/**
 * Screenshot tests for dialog and text field components (ClickableTextField, MeshtasticResourceDialog,
 * MeshtasticTextDialog, DeliveryInfo, ShutdownConfirmationDialog).
 */
class DialogAndTextScreenshotTests {
    @PreviewTest
    @MultiPreview
    @Composable
    fun clickableTextFieldScreenshot() {
        ClickableTextFieldPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun meshtasticResourceDialogScreenshot() {
        MeshtasticResourceDialogPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun meshtasticTextDialogScreenshot() {
        MeshtasticTextDialogPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun deliveryInfoScreenshot() {
        DeliveryInfoPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun deliveryInfoErrorScreenshot() {
        DeliveryInfoErrorPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun shutdownConfirmationDialogScreenshot() {
        ShutdownConfirmationDialogPreview()
    }
}
