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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.ui.components.DeviceListItem
import org.meshtastic.feature.messaging.component.QuickChatRow
import org.meshtastic.feature.node.component.FirmwareReleaseSheetContent
import org.meshtastic.feature.settings.debugging.DebugCustomFilterInput
import org.meshtastic.feature.settings.radio.component.RouterRoleConfirmationDialog

@MultiPreview
@Composable
fun DebugCustomFilterInputPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                DebugCustomFilterInput(
                    customFilterText = "GPS",
                    onCustomFilterTextChange = {},
                    filterTexts = listOf("BLE", "MQTT", "GPS"),
                    onFilterTextsChange = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun RouterRoleConfirmationDialogPreview() {
    AppTheme(isSystemInDarkTheme()) { Surface { RouterRoleConfirmationDialog(onDismiss = {}, onConfirm = {}) } }
}

@MultiPreview
@Composable
fun FirmwareReleaseSheetContentPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            FirmwareReleaseSheetContent(
                firmwareRelease =
                FirmwareRelease(
                    id = "v2.6.0.abcdef1",
                    title = "Meshtastic Firmware v2.6.0",
                    releaseNotes =
                    "## What's New\n- Improved BLE stability\n- Added new telemetry metrics\n- Fixed MQTT reconnection issues",
                    pageUrl = "https://github.com/meshtastic/firmware/releases/tag/v2.6.0",
                    zipUrl = "https://github.com/meshtastic/firmware/releases/download/v2.6.0/firmware.zip",
                    releaseType = FirmwareReleaseType.STABLE,
                ),
            )
        }
    }
}

@MultiPreview
@Composable
fun QuickChatRowPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Enabled:", style = MaterialTheme.typography.labelSmall)
                QuickChatRow(
                    enabled = true,
                    actions =
                    listOf(
                        QuickChatAction(
                            uuid = 1,
                            name = "Hi",
                            message = "Hello!",
                            mode = QuickChatAction.Mode.Instant,
                            position = 0,
                        ),
                        QuickChatAction(
                            uuid = 2,
                            name = "OMW",
                            message = "On my way!",
                            mode = QuickChatAction.Mode.Instant,
                            position = 1,
                        ),
                        QuickChatAction(
                            uuid = 3,
                            name = "GPS",
                            message = "/gps",
                            mode = QuickChatAction.Mode.Append,
                            position = 2,
                        ),
                    ),
                    onClick = {},
                )
                Text("Disabled:", style = MaterialTheme.typography.labelSmall)
                QuickChatRow(
                    enabled = false,
                    actions =
                    listOf(
                        QuickChatAction(
                            uuid = 1,
                            name = "Hi",
                            message = "Hello!",
                            mode = QuickChatAction.Mode.Instant,
                            position = 0,
                        ),
                    ),
                    onClick = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun DeviceListItemPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("TCP device (connected):", style = MaterialTheme.typography.labelSmall)
                DeviceListItem(
                    connectionState = ConnectionState.Connected,
                    device = DeviceListEntry.Tcp(name = "meshtastic.local", fullAddress = "x192.168.1.100"),
                    onSelect = {},
                    rssi = null,
                )
                Text("Mock device (disconnected):", style = MaterialTheme.typography.labelSmall)
                DeviceListItem(
                    connectionState = ConnectionState.Disconnected,
                    device = DeviceListEntry.Mock(name = "Simulator Node"),
                    onSelect = {},
                    onDelete = {},
                )
                Text("TCP device (connecting):", style = MaterialTheme.typography.labelSmall)
                DeviceListItem(
                    connectionState = ConnectionState.Connecting,
                    device = DeviceListEntry.Tcp(name = "10.0.0.42", fullAddress = "x10.0.0.42"),
                    onSelect = {},
                    rssi = -65,
                )
            }
        }
    }
}
