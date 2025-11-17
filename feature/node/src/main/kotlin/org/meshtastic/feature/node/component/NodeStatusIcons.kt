/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.node.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NoCell
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.CloudDone
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.CloudSync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.connected
import org.meshtastic.core.strings.connecting
import org.meshtastic.core.strings.device_sleeping
import org.meshtastic.core.strings.disconnected
import org.meshtastic.core.strings.favorite
import org.meshtastic.core.strings.unmessageable
import org.meshtastic.core.strings.unmonitored_or_infrastructure
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeStatusIcons(
    isThisNode: Boolean,
    isUnmessageable: Boolean,
    isFavorite: Boolean,
    connectionState: ConnectionState,
) {
    Row(modifier = Modifier.padding(4.dp)) {
        if (isThisNode) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = {
                    PlainTooltip {
                        Text(
                            stringResource(
                                when (connectionState) {
                                    ConnectionState.Connected -> Res.string.connected
                                    ConnectionState.Connecting -> Res.string.connecting
                                    ConnectionState.Disconnected -> Res.string.disconnected
                                    ConnectionState.DeviceSleep -> Res.string.device_sleeping
                                },
                            ),
                        )
                    }
                },
                state = rememberTooltipState(),
            ) {
                when (connectionState) {
                    ConnectionState.Connected -> {
                        Icon(
                            imageVector = Icons.TwoTone.CloudDone,
                            contentDescription = stringResource(Res.string.connected),
                            modifier = Modifier.size(24.dp), // Smaller size for badge
                            tint = MaterialTheme.colorScheme.StatusGreen,
                        )
                    }
                    ConnectionState.Connecting -> {
                        Icon(
                            imageVector = Icons.TwoTone.CloudSync,
                            contentDescription = stringResource(Res.string.connecting),
                            modifier = Modifier.size(24.dp), // Smaller size for badge
                            tint = MaterialTheme.colorScheme.StatusOrange,
                        )
                    }
                    ConnectionState.Disconnected -> {
                        Icon(
                            imageVector = Icons.TwoTone.CloudOff,
                            contentDescription = stringResource(Res.string.connecting),
                            modifier = Modifier.size(24.dp), // Smaller size for badge
                            tint = MaterialTheme.colorScheme.StatusRed,
                        )
                    }
                    ConnectionState.DeviceSleep -> {
                        Icon(
                            imageVector = Icons.TwoTone.Cloud,
                            contentDescription = stringResource(Res.string.device_sleeping),
                            modifier = Modifier.size(24.dp), // Smaller size for badge
                            tint = MaterialTheme.colorScheme.StatusYellow,
                        )
                    }
                }
            }
        }

        if (isUnmessageable) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(stringResource(Res.string.unmonitored_or_infrastructure)) } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.NoCell,
                        contentDescription = stringResource(Res.string.unmessageable),
                        modifier = Modifier.size(24.dp), // Smaller size for badge
                    )
                }
            }
        }
        if (isFavorite && !isThisNode) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(stringResource(Res.string.favorite)) } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = stringResource(Res.string.favorite),
                        modifier = Modifier.size(24.dp), // Smaller size for badge
                        tint = MaterialTheme.colorScheme.StatusYellow,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun StatusIconsPreview() {
    NodeStatusIcons(
        isThisNode = true,
        isUnmessageable = true,
        isFavorite = true,
        connectionState = ConnectionState.Connected,
    )
}
