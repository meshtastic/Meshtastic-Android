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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.humidity
import org.meshtastic.core.resources.message_delivery_status
import org.meshtastic.core.resources.message_status_delivered
import org.meshtastic.core.resources.pressure
import org.meshtastic.core.resources.temperature
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.messaging.component.MessageActionsContent
import org.meshtastic.feature.messaging.component.MessageTopBar
import org.meshtastic.feature.node.metrics.InfoDialogData
import org.meshtastic.feature.node.metrics.Legend
import org.meshtastic.feature.node.metrics.LegendData
import org.meshtastic.feature.node.metrics.LegendInfoDialog
import org.meshtastic.feature.settings.radio.channel.component.EditChannelDialog
import org.meshtastic.feature.settings.radio.component.EditDeviceProfileDialog
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.DeviceProfile

@MultiPreview
@Composable
fun MessageActionsContentPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            MessageActionsContent(
                quickEmojis = listOf("\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDE02", "\uD83D\uDD25", "\u2764\uFE0F"),
                onReply = {},
                onReact = {},
                onMoreReactions = {},
                onCopy = {},
                onSelect = {},
                onDelete = {},
                statusString = Pair(Res.string.message_delivery_status, Res.string.message_status_delivered),
                status = MessageStatus.DELIVERED,
                onStatus = {},
            )
        }
    }
}

@MultiPreview
@Composable
fun MessageTopBarPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            MessageTopBar(
                title = "General Chat",
                channelIndex = 0,
                mismatchKey = false,
                onNavigateBack = {},
                channels = null,
                channelIndexParam = 0,
                showQuickChat = true,
                onToggleQuickChat = {},
            )
        }
    }
}

@MultiPreview
@Composable
fun LegendPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Legend(
                    legendData =
                    listOf(
                        LegendData(nameRes = Res.string.temperature, color = Color(0xFFFF5722)),
                        LegendData(nameRes = Res.string.humidity, color = Color(0xFF2196F3)),
                        LegendData(nameRes = Res.string.pressure, color = Color(0xFF4CAF50), isLine = true),
                    ),
                    onToggle = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun LegendInfoDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            LegendInfoDialog(
                infoData =
                listOf(
                    InfoDialogData(
                        titleRes = Res.string.temperature,
                        definitionRes = Res.string.temperature,
                        color = Color(0xFFFF5722),
                    ),
                    InfoDialogData(
                        titleRes = Res.string.humidity,
                        definitionRes = Res.string.humidity,
                        color = Color(0xFF2196F3),
                    ),
                ),
                onDismiss = {},
            )
        }
    }
}

@MultiPreview
@Composable
fun EditDeviceProfileDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            EditDeviceProfileDialog(
                title = "Import Device Profile",
                deviceProfile = DeviceProfile(long_name = "Meshtastic Node", short_name = "MN"),
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

@MultiPreview
@Composable
fun EditChannelDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            EditChannelDialog(
                channelSettings = ChannelSettings(name = "LongFast"),
                onAddClick = {},
                onDismissRequest = {},
                modemPresetName = "Long Range / Fast",
            )
        }
    }
}
