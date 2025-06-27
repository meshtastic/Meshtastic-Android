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

package com.geeksville.mesh.ui.node.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ConfigProtos.Config.DeviceConfig
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.isUnmessageableRole
import com.geeksville.mesh.ui.common.components.BatteryInfo
import com.geeksville.mesh.ui.common.components.SignalInfo
import com.geeksville.mesh.ui.common.preview.NodePreviewParameterProvider
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.util.toDistanceString

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NodeItem(
    thisNode: Node?,
    thatNode: Node,
    gpsFormat: Int,
    distanceUnits: Int,
    tempInFahrenheit: Boolean,
    modifier: Modifier = Modifier,
    onAction: (NodeMenuAction) -> Unit = {},
    expanded: Boolean = false,
    currentTimeMillis: Long,
    isConnected: Boolean = false,
) {
    val isFavorite = remember(thatNode) { thatNode.isFavorite }
    val isIgnored = thatNode.isIgnored
    val longName = thatNode.user.longName.ifEmpty { stringResource(id = R.string.unknown_username) }
    val isThisNode = remember(thatNode) { thisNode?.num == thatNode.num }
    val system = remember(distanceUnits) { DisplayConfig.DisplayUnits.forNumber(distanceUnits) }
    val distance = remember(thisNode, thatNode) {
        thisNode?.distance(thatNode)?.takeIf { it > 0 }?.toDistanceString(system)
    }

    val hwInfoString = when (val hwModel = thatNode.user.hwModel) {
        MeshProtos.HardwareModel.UNSET -> MeshProtos.HardwareModel.UNSET.name
        else -> hwModel.name.replace('_', '-').replace('p', '.').lowercase()
    }
    val roleName = if (thatNode.isUnknownUser) {
        DeviceConfig.Role.UNRECOGNIZED.name
    } else {
        thatNode.user.role.name
    }

    val style = if (thatNode.isUnknownUser) {
        LocalTextStyle.current.copy(fontStyle = FontStyle.Italic)
    } else {
        LocalTextStyle.current
    }

    val cardColors = if (isThisNode) {
        thisNode?.colors?.second
    } else {
        thatNode.colors.second
    }?.let {
        val containerColor = Color(it).copy(alpha = 0.2f)
        CardDefaults.cardColors().copy(
            containerColor = containerColor,
            contentColor = contentColorFor(containerColor)
        )
    } ?: (CardDefaults.cardColors())

    val (detailsShown, showDetails) = remember { mutableStateOf(expanded) }
    val unmessageable = remember(thatNode) {
        when {
            thatNode.user.hasIsUnmessagable() -> thatNode.user.isUnmessagable
            else -> thatNode.user.role.isUnmessageableRole()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .defaultMinSize(minHeight = 80.dp),
        onClick = { showDetails(!detailsShown) },
        colors = cardColors
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NodeChip(
                    node = thatNode,
                    isThisNode = isThisNode,
                    isConnected = isConnected,
                    onAction = onAction,
                )

                NodeKeyStatusIcon(
                    hasPKC = thatNode.hasPKC,
                    mismatchKey = thatNode.mismatchKey,
                    publicKey = thatNode.user.publicKey,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = longName,
                    style = style,
                    textDecoration = TextDecoration.LineThrough.takeIf { isIgnored },
                    softWrap = true,
                )
                LastHeardInfo(
                    lastHeard = thatNode.lastHeard,
                    currentTimeMillis = currentTimeMillis
                )
                NodeStatusIcons(
                    isThisNode = isThisNode,
                    isFavorite = isFavorite,
                    isUnmessageable = unmessageable,
                    isConnected = isConnected
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (distance != null) {
                    Text(
                        text = distance,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    )
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                BatteryInfo(
                    batteryLevel = thatNode.batteryLevel,
                    voltage = thatNode.voltage
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalInfo(
                    node = thatNode,
                    isThisNode = isThisNode
                )
                thatNode.validPosition?.let { position ->
                    val satCount = position.satsInView
                    if (satCount > 0) {
                        SatelliteCountInfo(satCount = satCount)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val telemetryString = thatNode.getTelemetryString(tempInFahrenheit)
                if (telemetryString.isNotEmpty()) {
                    Text(
                        text = telemetryString,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    )
                }
            }

            if (detailsShown || expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    thatNode.validPosition?.let {
                        LinkedCoordinates(
                            latitude = thatNode.latitude,
                            longitude = thatNode.longitude,
                            format = gpsFormat,
                            nodeName = longName
                        )
                    }
                    thatNode.validPosition?.let { position ->
                        ElevationInfo(
                            altitude = position.altitude,
                            system = system,
                            suffix = stringResource(id = R.string.elevation_suffix)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = hwInfoString,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        style = style,
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text = roleName,
                        textAlign = TextAlign.Center,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        style = style,
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text = thatNode.user.id.ifEmpty { "???" },
                        textAlign = TextAlign.End,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        style = style,
                    )
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = false)
fun NodeInfoSimplePreview() {
    AppTheme {
        val thisNode = NodePreviewParameterProvider().values.first()
        val thatNode = NodePreviewParameterProvider().values.last()
        NodeItem(
            thisNode = thisNode,
            thatNode = thatNode,
            1,
            0,
            true,
            currentTimeMillis = System.currentTimeMillis(),
        )
    }
}

@Composable
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
fun NodeInfoPreview(
    @PreviewParameter(NodePreviewParameterProvider::class)
    thatNode: Node
) {
    AppTheme {
        val thisNode = NodePreviewParameterProvider().values.first()
        Column {
            Text(
                text = "Details Collapsed",
                color = MaterialTheme.colorScheme.onBackground
            )
            NodeItem(
                thisNode = thisNode,
                thatNode = thatNode,
                gpsFormat = 0,
                distanceUnits = 1,
                tempInFahrenheit = true,
                expanded = false,
                currentTimeMillis = System.currentTimeMillis(),
            )
            Text(
                text = "Details Shown",
                color = MaterialTheme.colorScheme.onBackground
            )
            NodeItem(
                thisNode = thisNode,
                thatNode = thatNode,
                gpsFormat = 0,
                distanceUnits = 1,
                tempInFahrenheit = true,
                expanded = true,
                currentTimeMillis = System.currentTimeMillis(),
            )
        }
    }
}
