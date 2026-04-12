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
import org.meshtastic.app.preview.AutoLinkTextPreview
import org.meshtastic.app.preview.ConnectionsNavIconPreview
import org.meshtastic.app.preview.CopyIconButtonPreview
import org.meshtastic.app.preview.IAQScalePreview
import org.meshtastic.app.preview.InsetDividerPreview
import org.meshtastic.app.preview.MaterialBluetoothSignalInfoPreview
import org.meshtastic.app.preview.MultiPreview
import org.meshtastic.app.preview.NodeKeyStatusIconPreview
import org.meshtastic.app.preview.PlaceholderScreenPreview
import org.meshtastic.app.preview.SlidingSelectorPreview
import org.meshtastic.app.preview.TransportIconPreview

/** Screenshot tests for utility components (transport, copy, bluetooth, key status, links, selectors, etc.). */
class UtilityComponentScreenshotTests {
    @PreviewTest
    @MultiPreview
    @Composable
    fun transportIconScreenshot() {
        TransportIconPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun copyIconButtonScreenshot() {
        CopyIconButtonPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun materialBluetoothSignalInfoScreenshot() {
        MaterialBluetoothSignalInfoPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun nodeKeyStatusIconScreenshot() {
        NodeKeyStatusIconPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun autoLinkTextScreenshot() {
        AutoLinkTextPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun slidingSelectorScreenshot() {
        SlidingSelectorPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun insetDividerScreenshot() {
        InsetDividerPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun placeholderScreenScreenshot() {
        PlaceholderScreenPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun iaqScaleScreenshot() {
        IAQScalePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun connectionsNavIconScreenshot() {
        ConnectionsNavIconPreview()
    }
}
