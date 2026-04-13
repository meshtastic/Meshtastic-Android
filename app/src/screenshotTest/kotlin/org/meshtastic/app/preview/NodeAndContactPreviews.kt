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
import org.meshtastic.core.model.Contact
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.component.SignalInfo
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.connections.ui.components.CurrentlyConnectedInfo
import org.meshtastic.feature.messaging.ui.contact.ContactItem
import org.meshtastic.feature.node.component.NodeDetailsSection
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Position
import org.meshtastic.proto.User

@MultiPreview
@Composable
fun SignalInfoDetailPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Good signal:", style = MaterialTheme.typography.labelSmall)
                SignalInfo(node = Node(num = 1, snr = 12.5f, rssi = -42, hopsAway = 0))
                Text("Weak signal:", style = MaterialTheme.typography.labelSmall)
                SignalInfo(node = Node(num = 2, snr = -5.0f, rssi = -110, hopsAway = 3))
            }
        }
    }
}

@MultiPreview
@Composable
fun NodeDetailsSectionPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            NodeDetailsSection(
                node =
                Node(
                    num = 1001,
                    user =
                    User(
                        id = "!a1b2c3d4",
                        long_name = "Mickey Mouse",
                        short_name = "MM",
                        hw_model = HardwareModel.TBEAM,
                    ),
                    position = Position(latitude_i = 378_804_010, longitude_i = -1_224_760_530, altitude = 100),
                    snr = 10.5f,
                    rssi = -55,
                    lastHeard = 1700000000,
                    deviceMetrics =
                    DeviceMetrics(
                        battery_level = 85,
                        voltage = 3.95f,
                        channel_utilization = 12.5f,
                        air_util_tx = 3.2f,
                        uptime_seconds = 86400,
                    ),
                    channel = 0,
                    hopsAway = 1,
                    isFavorite = true,
                ),
            )
        }
    }
}

@MultiPreview
@Composable
fun ContactItemPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Active contact with unread:", style = MaterialTheme.typography.labelSmall)
                ContactItem(
                    contact =
                    Contact(
                        contactKey = "c!a1b2c3d4",
                        shortName = "MM",
                        longName = "Mickey Mouse",
                        lastMessageTime = 1700000000L,
                        lastMessageText = "Hey, are you on the trail?",
                        unreadCount = 3,
                        messageCount = 15,
                        isMuted = false,
                        isUnmessageable = false,
                    ),
                    selected = false,
                    isActive = true,
                )
                Text("Muted contact:", style = MaterialTheme.typography.labelSmall)
                ContactItem(
                    contact =
                    Contact(
                        contactKey = "c!e5f6g7h8",
                        shortName = "DD",
                        longName = "Donald Duck",
                        lastMessageTime = 1699990000L,
                        lastMessageText = "Quack quack!",
                        unreadCount = 0,
                        messageCount = 42,
                        isMuted = true,
                        isUnmessageable = false,
                    ),
                    selected = false,
                    isActive = false,
                )
                Text("Selected contact:", style = MaterialTheme.typography.labelSmall)
                ContactItem(
                    contact =
                    Contact(
                        contactKey = "c0",
                        shortName = "CH",
                        longName = "Channel 0",
                        lastMessageTime = 1700001000L,
                        lastMessageText = "Welcome to the mesh!",
                        unreadCount = 1,
                        messageCount = 100,
                        isMuted = false,
                        isUnmessageable = false,
                    ),
                    selected = true,
                    isActive = true,
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun CurrentlyConnectedInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            CurrentlyConnectedInfo(
                node =
                Node(
                    num = 1001,
                    user =
                    User(
                        id = "!a1b2c3d4",
                        long_name = "My Meshtastic Node",
                        short_name = "MN",
                        hw_model = HardwareModel.TBEAM,
                    ),
                    deviceMetrics = DeviceMetrics(battery_level = 92, voltage = 4.1f),
                ),
                onNavigateToNodeDetails = {},
                onClickDisconnect = {},
            )
        }
    }
}
