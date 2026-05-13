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
package org.meshtastic.feature.connections.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.viewmodel.ConnectionStatus
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.ui.components.ConnectingDeviceInfo
import org.meshtastic.feature.connections.ui.components.DeviceListItem
import org.meshtastic.feature.connections.ui.components.DeviceSectionHeader
import org.meshtastic.feature.connections.ui.components.DisconnectButton
import org.meshtastic.feature.connections.ui.components.EmptyStateContent
import org.meshtastic.feature.connections.ui.components.TransportFilterChips

@PreviewLightDark
@Composable
fun DeviceListItemPreview() {
    val device = DeviceListEntry.Tcp(name = "Meshtastic_abcd", fullAddress = "s192.168.1.100")
    AppTheme { DeviceListItem(connectionState = ConnectionState.Disconnected, device = device, onSelect = {}) }
}

@PreviewLightDark
@Composable
fun DisconnectButtonPreview() {
    AppTheme { DisconnectButton(onClick = {}) }
}

@PreviewLightDark
@Composable
fun ConnectingDeviceInfoPreview() {
    AppTheme {
        ConnectingDeviceInfo(
            deviceName = "Meshtastic_abcd",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            connectionStatus = ConnectionStatus.CONNECTING,
            connectionProgress = "Discovering services...",
            onClickDisconnect = {},
        )
    }
}

@PreviewLightDark
@Composable
fun EmptyStateContentPreview() {
    AppTheme { EmptyStateContent(text = "No devices found", imageVector = MeshtasticIcons.Search) }
}

@PreviewLightDark
@Composable
fun DeviceSectionHeaderPreview() {
    AppTheme { DeviceSectionHeader(title = "Bluetooth Devices", showProgress = true) }
}

@PreviewLightDark
@Composable
fun TransportFilterChipsPreview() {
    AppTheme {
        TransportFilterChips(
            showBle = true,
            showNetwork = true,
            showUsb = false,
            onToggleBle = {},
            onToggleNetwork = {},
            onToggleUsb = {},
        )
    }
}
