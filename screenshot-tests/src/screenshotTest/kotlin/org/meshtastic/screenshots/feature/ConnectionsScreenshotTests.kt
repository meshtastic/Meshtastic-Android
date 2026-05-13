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
import org.meshtastic.feature.connections.component.ConnectingDeviceInfoPreview
import org.meshtastic.feature.connections.component.DeviceListItemPreview
import org.meshtastic.feature.connections.component.DeviceSectionHeaderPreview
import org.meshtastic.feature.connections.component.DisconnectButtonPreview
import org.meshtastic.feature.connections.component.EmptyStateContentPreview
import org.meshtastic.feature.connections.component.TransportFilterChipsPreview

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDeviceListItem() {
    DeviceListItemPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDisconnectButton() {
    DisconnectButtonPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotConnectingDeviceInfo() {
    ConnectingDeviceInfoPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotEmptyStateContent() {
    EmptyStateContentPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDeviceSectionHeader() {
    DeviceSectionHeaderPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTransportFilterChips() {
    TransportFilterChipsPreview()
}
