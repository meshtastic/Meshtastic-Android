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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.node.component

import android.content.res.Configuration
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.isUnmessageableRole
import org.meshtastic.core.model.util.UnitConversions.celsiusToFahrenheit
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.air_utilization
import org.meshtastic.core.strings.channel_utilization
import org.meshtastic.core.strings.current
import org.meshtastic.core.strings.elevation_suffix
import org.meshtastic.core.strings.signal_quality
import org.meshtastic.core.strings.unknown_username
import org.meshtastic.core.strings.voltage
import org.meshtastic.core.ui.component.AirQualityInfo
import org.meshtastic.core.ui.component.ChannelInfo
import org.meshtastic.core.ui.component.DistanceInfo
import org.meshtastic.core.ui.component.ElevationInfo
import org.meshtastic.core.ui.component.HardwareInfo
import org.meshtastic.core.ui.component.HopsInfo
import org.meshtastic.core.ui.component.HumidityInfo
import org.meshtastic.core.ui.component.IconInfo
import org.meshtastic.core.ui.component.LastHeardInfo
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.NodeIdInfo
import org.meshtastic.core.ui.component.NodeKeyStatusIcon
import org.meshtastic.core.ui.component.PaxcountInfo
import org.meshtastic.core.ui.component.PowerInfo
import org.meshtastic.core.ui.component.PressureInfo
import org.meshtastic.core.ui.component.RoleInfo
import org.meshtastic.core.ui.component.Rssi
import org.meshtastic.core.ui.component.SatelliteCountInfo
import org.meshtastic.core.ui.component.Snr
import org.meshtastic.core.ui.component.SoilMoistureInfo
import org.meshtastic.core.ui.component.SoilTemperatureInfo
import org.meshtastic.core.ui.component.TemperatureInfo
import org.meshtastic.core.ui.component.TransportIcon
import org.meshtastic.core.ui.component.determineSignalQuality
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.icon.AirUtilization
import org.meshtastic.core.ui.icon.ChannelUtilization
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Config

private const val ACTIVE_ALPHA = 0.5f
private const val INACTIVE_ALPHA = 0.2f
private const val GRID_COLUMNS = 3

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("LongMethod")
fun NodeItem(
    thisNode: Node?,
    thatNode: Node,
    distanceUnits: Int,
    tempInFahrenheit: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    connectionState: ConnectionState,
    isActive: Boolean = false,
) {
    val isFavorite = remember(thatNode) { thatNode.isFavorite }
    val isMuted = remember(thatNode) { thatNode.isMuted }
    val isIgnored = thatNode.isIgnored
    val originalLongName = (thatNode.user.long_name ?: "").ifEmpty { stringResource(Res.string.unknown_username) }

    val isThisNode = remember(thatNode) { thisNode?.num == thatNode.num }
    val system =
        remember(distanceUnits) {
            Config.DisplayConfig.DisplayUnits.fromValue(distanceUnits) ?: Config.DisplayConfig.DisplayUnits.METRIC
        }
    val distance =
        remember(thisNode, thatNode) { thisNode?.distance(thatNode)?.takeIf { it > 0 }?.toDistanceString(system) }

    var contentColor = MaterialTheme.colorScheme.onSurface
    val cardColors =
        if (isThisNode) {
            thisNode?.colors?.second
        } else {
            thatNode.colors.second
        }
            ?.let {
                val alpha = if (isActive) ACTIVE_ALPHA else INACTIVE_ALPHA
                val containerColor = Color(it).copy(alpha = alpha)
                contentColor = contentColorFor(containerColor)
                CardDefaults.cardColors().copy(containerColor = containerColor, contentColor = contentColor)
            } ?: (CardDefaults.cardColors())

    val style =
        if (thatNode.isUnknownUser) {
            FontStyle.Italic
        } else {
            FontStyle.Normal
        }

    val unmessageable =
        remember(thatNode) {
            when {
                thatNode.user.is_unmessagable != null -> thatNode.user.is_unmessagable!!
                else -> thatNode.user.role.isUnmessageableRole()
            }
        }

    Card(modifier = modifier.fillMaxWidth(), colors = cardColors) {
        Column(
            modifier =
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NodeItemHeader(
                thatNode = thatNode,
                isThisNode = isThisNode,
                longName = originalLongName,
                style = style,
                isIgnored = isIgnored,
                isFavorite = isFavorite,
                isMuted = isMuted,
                isUnmessageable = unmessageable,
                connectionState = connectionState,
                contentColor = contentColor,
            )

            thatNode.nodeStatus?.let { status ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Notes,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor.copy(alpha = 0.7f),
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            NodeBatteryPositionRow(
                thatNode = thatNode,
                distance = distance,
                system = system,
                contentColor = contentColor,
            )

            NodeSignalRow(thatNode = thatNode, isThisNode = isThisNode, contentColor = contentColor)

            val sensorItems = gatherSensors(thatNode, tempInFahrenheit, contentColor)
            if (sensorItems.isNotEmpty()) {
                MetricsGrid(sensorItems)
            }

            NodeItemFooter(thatNode = thatNode, contentColor = contentColor)
        }
    }
}

@Composable
private fun NodeBatteryPositionRow(
    thatNode: Node,
    distance: String?,
    system: Config.DisplayConfig.DisplayUnits,
    contentColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialBatteryInfo(
            level = thatNode.batteryLevel ?: 0,
            voltage = thatNode.voltage ?: 0f,
            contentColor = contentColor,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (distance != null) {
                DistanceInfo(distance = distance, contentColor = contentColor)
            }
            thatNode.validPosition?.let { position ->
                ElevationInfo(
                    altitude = position.altitude ?: 0,
                    system = system,
                    suffix = stringResource(Res.string.elevation_suffix),
                    contentColor = contentColor,
                )
            }
        }
    }
}

@Composable
private fun NodeSignalRow(thatNode: Node, isThisNode: Boolean, contentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isThisNode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconInfo(
                    icon = MeshtasticIcons.ChannelUtilization,
                    contentDescription = stringResource(Res.string.channel_utilization),
                    label = stringResource(Res.string.channel_utilization),
                    text = "%.1f%%".format(thatNode.deviceMetrics.channel_utilization),
                    contentColor = contentColor,
                )
                IconInfo(
                    icon = MeshtasticIcons.AirUtilization,
                    contentDescription = stringResource(Res.string.air_utilization),
                    label = stringResource(Res.string.air_utilization),
                    text = "%.1f%%".format(thatNode.deviceMetrics.air_util_tx),
                    contentColor = contentColor,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (thatNode.hopsAway > 0) {
                    HopsInfo(hops = thatNode.hopsAway, contentColor = contentColor)
                } else if (thatNode.hopsAway == 0 && !thatNode.viaMqtt) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (thatNode.snr < 100f) Snr(thatNode.snr)
                        if (thatNode.rssi < 0) Rssi(thatNode.rssi)
                        if (thatNode.snr < 100f && thatNode.rssi < 0) {
                            val quality = determineSignalQuality(thatNode.snr, thatNode.rssi)
                            IconInfo(
                                icon = quality.imageVector,
                                contentDescription = stringResource(Res.string.signal_quality),
                                contentColor = quality.color.invoke(),
                                text = stringResource(quality.nameRes),
                            )
                        }
                    }
                }
                if (thatNode.channel > 0) {
                    ChannelInfo(channel = thatNode.channel, contentColor = contentColor)
                }
            }
        }

        val satCount = thatNode.validPosition?.sats_in_view ?: 0
        if (satCount > 0) {
            SatelliteCountInfo(satCount = satCount, contentColor = contentColor)
        } else {
            Spacer(Modifier)
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun gatherSensors(node: Node, tempInFahrenheit: Boolean, contentColor: Color): List<@Composable () -> Unit> {
    val items = mutableListOf<@Composable () -> Unit>()
    val env = node.environmentMetrics
    val pax = node.paxcounter

    if ((pax.ble ?: 0) != 0 || (pax.wifi ?: 0) != 0) {
        items.add { PaxcountInfo(pax = "B:${pax.ble ?: 0} W:${pax.wifi ?: 0}", contentColor = contentColor) }
    }
    if ((env.temperature ?: 0f) != 0f) {
        val temp =
            if (tempInFahrenheit) {
                "%.1f°F".format(celsiusToFahrenheit(env.temperature ?: 0f))
            } else {
                "%.1f°C".format(env.temperature ?: 0f)
            }
        items.add { TemperatureInfo(temp = temp, contentColor = contentColor) }
    }
    if ((env.relative_humidity ?: 0f) != 0f) {
        items.add { HumidityInfo(humidity = "%.0f%%".format(env.relative_humidity ?: 0f), contentColor = contentColor) }
    }
    if ((env.barometric_pressure ?: 0f) != 0f) {
        items.add {
            PressureInfo(pressure = "%.1fhPa".format(env.barometric_pressure ?: 0f), contentColor = contentColor)
        }
    }
    if ((env.soil_temperature ?: 0f) != 0f) {
        val temp =
            if (tempInFahrenheit) {
                "%.1f°F".format(celsiusToFahrenheit(env.soil_temperature ?: 0f))
            } else {
                "%.1f°C".format(env.soil_temperature ?: 0f)
            }
        items.add { SoilTemperatureInfo(temp = temp, contentColor = contentColor) }
    }
    if ((env.soil_moisture ?: 0) != 0 && (env.soil_temperature ?: 0f) != 0f) {
        items.add { SoilMoistureInfo(moisture = "${env.soil_moisture}%", contentColor = contentColor) }
    }
    if ((env.voltage ?: 0f) != 0f) {
        items.add {
            PowerInfo(
                value = "%.2fV".format(env.voltage ?: 0f),
                label = stringResource(Res.string.voltage),
                contentColor = contentColor,
            )
        }
    }
    if ((env.current ?: 0f) != 0f) {
        items.add {
            PowerInfo(
                value = "%.1fmA".format(env.current ?: 0f),
                label = stringResource(Res.string.current),
                contentColor = contentColor,
            )
        }
    }
    if ((env.iaq ?: 0) != 0) {
        items.add { AirQualityInfo(iaq = "${env.iaq}", contentColor = contentColor) }
    }

    return items
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricsGrid(items: List<@Composable () -> Unit>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = GRID_COLUMNS,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val remainder = items.size % GRID_COLUMNS
        items.forEach { item -> Box(Modifier.weight(1f)) { item() } }
        if (remainder != 0) {
            repeat(GRID_COLUMNS - remainder) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NodeItemHeader(
    thatNode: Node,
    isThisNode: Boolean,
    longName: String,
    style: FontStyle,
    isIgnored: Boolean,
    isFavorite: Boolean,
    isMuted: Boolean,
    isUnmessageable: Boolean,
    connectionState: ConnectionState,
    contentColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NodeChip(node = thatNode)

        NodeKeyStatusIcon(
            hasPKC = thatNode.hasPKC,
            mismatchKey = thatNode.mismatchKey,
            publicKey = thatNode.user.public_key,
            modifier = Modifier.size(24.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = longName,
                    style = MaterialTheme.typography.titleMediumEmphasized.copy(fontStyle = style),
                    textDecoration = TextDecoration.LineThrough.takeIf { isIgnored },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TransportIcon(
                    transport = thatNode.lastTransport,
                    viaMqtt = thatNode.viaMqtt,
                    modifier = Modifier.size(16.dp),
                )
            }
            LastHeardInfo(lastHeard = thatNode.lastHeard, showLabel = false, contentColor = contentColor)
        }

        NodeStatusIcons(
            isThisNode = isThisNode,
            isFavorite = isFavorite,
            isMuted = isMuted,
            isUnmessageable = isUnmessageable,
            connectionState = connectionState,
            contentColor = contentColor,
        )
    }
}

@Composable
private fun NodeItemFooter(thatNode: Node, contentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HardwareInfo(hwModel = thatNode.user.hw_model.name, contentColor = contentColor)
        RoleInfo(role = thatNode.user.role.name, contentColor = contentColor)
        NodeIdInfo(id = thatNode.user.id.ifEmpty { "???" }, contentColor = contentColor)
    }
}

@Composable
@Preview(showBackground = false, uiMode = Configuration.UI_MODE_NIGHT_YES)
fun NodeInfoSimplePreview() {
    AppTheme {
        val thisNode = NodePreviewParameterProvider().values.first()
        val thatNode = NodePreviewParameterProvider().values.last().copy(lastHeard = 0)
        NodeItem(thisNode = thisNode, thatNode = thatNode, 0, true, connectionState = ConnectionState.Connected)
    }
}

@Composable
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
fun NodeInfoStatusPreview() {
    AppTheme {
        val thisNode = NodePreviewParameterProvider().values.first()
        val thatNode =
            NodePreviewParameterProvider().values.last().copy(nodeStatus = "Going to the farm.. to grow wheat.")
        NodeItem(thisNode = thisNode, thatNode = thatNode, 0, true, connectionState = ConnectionState.Connected)
    }
}

@Composable
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
fun NodeInfoSignalPreview() {
    AppTheme {
        val thisNode = NodePreviewParameterProvider().values.first()
        val thatNode = NodePreviewParameterProvider().values.last().copy(hopsAway = 0, snr = 5.5f, rssi = -100)
        NodeItem(thisNode = thisNode, thatNode = thatNode, 0, true, connectionState = ConnectionState.Connected)
    }
}

@Composable
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
fun NodeInfoPreview(@PreviewParameter(NodePreviewParameterProvider::class) thatNode: Node) {
    AppTheme {
        val thisNode = NodePreviewParameterProvider().values.first()
        NodeItem(
            thisNode = thisNode,
            thatNode = thatNode,
            distanceUnits = 1,
            tempInFahrenheit = true,
            connectionState = ConnectionState.Connected,
        )
    }
}
