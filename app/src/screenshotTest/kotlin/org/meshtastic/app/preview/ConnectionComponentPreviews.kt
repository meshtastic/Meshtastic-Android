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
package org.meshtastic.app.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.core.ui.icon.Share
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.connections.ui.components.ConnectingDeviceInfo
import org.meshtastic.feature.connections.ui.components.ConnectionsSegmentedBar
import org.meshtastic.feature.connections.ui.components.EmptyStateContent

@MultiPreview
@Composable
fun EmptyStateContentPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EmptyStateContent(text = "No BLE devices found", imageVector = MeshtasticIcons.Search)
                EmptyStateContent(text = "No TCP devices found", imageVector = MeshtasticIcons.Share)
            }
        }
    }
}

@MultiPreview
@Composable
fun ConnectingDeviceInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ConnectingDeviceInfo(
                    connectionState = ConnectionState.Connecting,
                    deviceName = "Meshtastic_abcd",
                    deviceAddress = "AA:BB:CC:DD:EE:FF",
                    onClickDisconnect = {},
                )
                ConnectingDeviceInfo(
                    connectionState = ConnectionState.Connected,
                    deviceName = "Meshtastic_1234",
                    deviceAddress = "192.168.1.100",
                    onClickDisconnect = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun ConnectionsSegmentedBarPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConnectionsSegmentedBar(
                    selectedDeviceType = DeviceType.BLE,
                    supportedDeviceTypes = DeviceType.entries,
                    onClickDeviceType = {},
                )
            }
        }
    }
}
