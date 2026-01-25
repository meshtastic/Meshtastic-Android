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
package org.meshtastic.feature.node.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.connected
import org.meshtastic.core.strings.connecting
import org.meshtastic.core.strings.device_sleeping
import org.meshtastic.core.strings.disconnected
import org.meshtastic.core.strings.favorite
import org.meshtastic.core.strings.mute_always
import org.meshtastic.core.strings.unmessageable
import org.meshtastic.core.strings.unmonitored_or_infrastructure
import org.meshtastic.core.ui.icon.CloudDone
import org.meshtastic.core.ui.icon.CloudOffTwoTone
import org.meshtastic.core.ui.icon.CloudSync
import org.meshtastic.core.ui.icon.CloudTwoTone
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Unmessageable
import org.meshtastic.core.ui.icon.VolumeOff
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeStatusIcons(
    isThisNode: Boolean,
    isUnmessageable: Boolean,
    isFavorite: Boolean,
    isMuted: Boolean,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
    contentColor: Color = LocalContentColor.current,
) {
    Row(modifier = modifier.padding(4.dp)) {
        if (isThisNode) {
            ThisNodeStatusBadge(connectionState)
        }

        if (isUnmessageable) {
            StatusBadge(
                imageVector = MeshtasticIcons.Unmessageable,
                contentDescription = Res.string.unmessageable,
                tooltipText = Res.string.unmonitored_or_infrastructure,
                tint = contentColor,
            )
        }
        if (isMuted && !isThisNode) {
            StatusBadge(
                imageVector = MeshtasticIcons.VolumeOff,
                contentDescription = Res.string.mute_always,
                tooltipText = Res.string.mute_always,
                tint = contentColor,
            )
        }
        if (isFavorite && !isThisNode) {
            StatusBadge(
                imageVector = MeshtasticIcons.Favorite,
                contentDescription = Res.string.favorite,
                tooltipText = Res.string.favorite,
                tint = MaterialTheme.colorScheme.StatusYellow,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThisNodeStatusBadge(connectionState: ConnectionState) {
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
            ConnectionState.Connected -> ConnectedStatusIcon()
            ConnectionState.Connecting -> ConnectingStatusIcon()
            ConnectionState.Disconnected -> DisconnectedStatusIcon()
            ConnectionState.DeviceSleep -> DeviceSleepStatusIcon()
        }
    }
}

@Composable
private fun ConnectedStatusIcon() {
    Icon(
        imageVector = MeshtasticIcons.CloudDone,
        contentDescription = stringResource(Res.string.connected),
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.StatusGreen,
    )
}

@Composable
private fun ConnectingStatusIcon() {
    Icon(
        imageVector = MeshtasticIcons.CloudSync,
        contentDescription = stringResource(Res.string.connecting),
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.StatusOrange,
    )
}

@Composable
private fun DisconnectedStatusIcon() {
    Icon(
        imageVector = MeshtasticIcons.CloudOffTwoTone,
        contentDescription = stringResource(Res.string.disconnected),
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.StatusRed,
    )
}

@Composable
private fun DeviceSleepStatusIcon() {
    Icon(
        imageVector = MeshtasticIcons.CloudTwoTone,
        contentDescription = stringResource(Res.string.device_sleeping),
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.StatusYellow,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusBadge(
    imageVector: ImageVector,
    contentDescription: StringResource,
    tooltipText: StringResource,
    tint: Color = LocalContentColor.current,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(tooltipText)) } },
        state = rememberTooltipState(),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = stringResource(contentDescription),
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
    }
}

@Preview
@Composable
private fun StatusIconsPreview() {
    NodeStatusIcons(
        isThisNode = true,
        isUnmessageable = true,
        isFavorite = true,
        isMuted = true,
        connectionState = ConnectionState.Connected,
    )
}
