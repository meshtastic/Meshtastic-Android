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
import org.meshtastic.app.preview.AutoLinkTextPreview
import org.meshtastic.app.preview.ConnectionsNavIconPreview
import org.meshtastic.app.preview.CopyIconButtonPreview
import org.meshtastic.app.preview.IAQScalePreview
import org.meshtastic.app.preview.InsetDividerPreview
import org.meshtastic.app.preview.MaterialBluetoothSignalInfoPreview
import org.meshtastic.app.preview.NodeKeyStatusIconPreview
import org.meshtastic.app.preview.PlaceholderScreenPreview
import org.meshtastic.app.preview.SlidingSelectorPreview
import org.meshtastic.app.preview.TransportIconPreview

/** Screenshot tests for utility components (transport, copy, bluetooth, key status, links, selectors, etc.). */
class UtilityComponentScreenshotTests {
    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun transportIconScreenshot() {
        TransportIconPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun copyIconButtonScreenshot() {
        CopyIconButtonPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun materialBluetoothSignalInfoScreenshot() {
        MaterialBluetoothSignalInfoPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun nodeKeyStatusIconScreenshot() {
        NodeKeyStatusIconPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun autoLinkTextScreenshot() {
        AutoLinkTextPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun slidingSelectorScreenshot() {
        SlidingSelectorPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun insetDividerScreenshot() {
        InsetDividerPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun placeholderScreenScreenshot() {
        PlaceholderScreenPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun iaqScaleScreenshot() {
        IAQScalePreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun connectionsNavIconScreenshot() {
        ConnectionsNavIconPreview()
    }
}
