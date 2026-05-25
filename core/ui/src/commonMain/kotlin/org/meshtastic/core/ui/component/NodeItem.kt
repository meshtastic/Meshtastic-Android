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
@file:Suppress("MagicNumber")

package org.meshtastic.core.ui.component

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.isUnmessageableRole
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.air_utilization
import org.meshtastic.core.resources.channel_utilization
import org.meshtastic.core.resources.current
import org.meshtastic.core.resources.elevation_suffix
import org.meshtastic.core.resources.node_list_click_label
import org.meshtastic.core.resources.node_list_long_click_label
import org.meshtastic.core.resources.signal_quality
import org.meshtastic.core.resources.unknown_username
import org.meshtastic.core.resources.voltage
import org.meshtastic.core.ui.icon.AirUtilization
import org.meshtastic.core.ui.icon.ChannelUtilization
import org.meshtastic.core.ui.icon.MapCompass
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Notes
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.proto.Config

private const val ACTIVE_BORDER_ALPHA = 0.65f
private const val INACTIVE_BORDER_ALPHA = 0.3f
private const val GRID_COLUMNS = 3

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun NodeItem(
    thisNode: Node?,
    thatNode: Node,
    distanceUnits: Int,
    tempInFahrenheit: Boolean,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    deviceType: DeviceType? = null,
    isActive: Boolean = false,
    showTelemetry: Boolean = true,
    deviceImageUrl: String? = null,
) {
    val originalLongName = thatNode.user.long_name.ifEmpty { stringResource(Res.string.unknown_username) }
    val isMuted = remember(thatNode) { thatNode.isMuted }
    val isIgnored = thatNode.isIgnored
    val isFavorite = thatNode.isFavorite

    val isThisNode = remember(thatNode) { thisNode?.num == thatNode.num }
    val system =
        remember(distanceUnits) {
            Config.DisplayConfig.DisplayUnits.fromValue(distanceUnits) ?: Config.DisplayConfig.DisplayUnits.METRIC
        }
    val distance =
        remember(thisNode, thatNode) { thisNode?.distance(thatNode)?.takeIf { it > 0 }?.toDistanceString(system) }
    val bearingDegrees = remember(thisNode, thatNode) { thisNode?.bearing(thatNode) }

    val contentColor = MaterialTheme.colorScheme.onSurface
    val nodeColor =
        (if (isThisNode) thisNode?.colors?.second else thatNode.colors.second)?.let { Color(it) } ?: Color.Transparent
    val cardContainerColor = CardDefaults.cardColors().containerColor
    val tintedContainerColor =
        if (nodeColor == Color.Transparent) cardContainerColor else lerp(cardContainerColor, nodeColor, 0.08f)
    val cardColors = CardDefaults.cardColors(containerColor = tintedContainerColor)
    val borderColor =
        (if (isThisNode) thisNode?.colors?.second else thatNode.colors.second)?.let {
            Color(it).copy(alpha = if (isActive) ACTIVE_BORDER_ALPHA else INACTIVE_BORDER_ALPHA)
        }
    val cardBorder = borderColor?.let { BorderStroke(1.5.dp, it) }

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

    val a11yStrings = rememberNodeDescriptionStrings()
    val nodeDescription =
        remember(thatNode, a11yStrings) {
            buildNodeDescription(
                name = originalLongName,
                isOnline = thatNode.isOnline,
                isFavorite = isFavorite,
                lastHeard = thatNode.lastHeard,
                role = thatNode.user.role.name,
                hopsAway = thatNode.hopsAway,
                batteryLevel = thatNode.batteryLevel,
                distance = distance,
                snr = thatNode.snr,
                rssi = thatNode.rssi,
                viaMqtt = thatNode.viaMqtt,
                strings = a11yStrings,
            )
        }

    Card(
        modifier =
        modifier.nodeCardGlow(lastHeard = thatNode.lastHeard, nodeColor = nodeColor).fillMaxWidth().semantics(
            mergeDescendants = true,
        ) {
            contentDescription = nodeDescription
            role = Role.Button
        },
        colors = cardColors,
        border = cardBorder,
    ) {
        Column(
            modifier =
            Modifier.combinedClickable(
                onClickLabel = stringResource(Res.string.node_list_click_label),
                onClick = onClick,
                onLongClickLabel = stringResource(Res.string.node_list_long_click_label),
                onLongClick = onLongClick,
            )
                .fillMaxWidth()
                .padding(12.dp),
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
                deviceType = deviceType,
                contentColor = contentColor,
            )

            thatNode.nodeStatus?.let { status ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MeshtasticIcons.Notes,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline,
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
                bearingDegrees = bearingDegrees,
                system = system,
                contentColor = contentColor,
            )

            NodeSignalRow(thatNode = thatNode, isThisNode = isThisNode, contentColor = contentColor)

            if (showTelemetry) {
                val sensorItems = gatherSensors(thatNode, tempInFahrenheit, contentColor)
                if (sensorItems.isNotEmpty()) {
                    MetricsGrid(sensorItems)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            NodeItemFooter(thatNode = thatNode, contentColor = contentColor, deviceImageUrl = deviceImageUrl)
        }
    }
}

@Composable
private fun NodeBatteryPositionRow(
    thatNode: Node,
    distance: String?,
    bearingDegrees: Int?,
    system: Config.DisplayConfig.DisplayUnits,
    contentColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if ((thatNode.batteryLevel ?: 0) > 0) {
            MaterialBatteryInfo(
                level = thatNode.batteryLevel ?: 0,
                voltage = thatNode.voltage ?: 0f,
                contentColor = contentColor,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (distance != null) {
                DistanceInfo(distance = distance, contentColor = contentColor)
            }
            if (bearingDegrees != null) {
                Icon(
                    imageVector = MeshtasticIcons.MapCompass,
                    contentDescription = "$bearingDegrees°",
                    modifier = Modifier.size(16.dp).rotate(bearingDegrees.toFloat()),
                    tint = contentColor,
                )
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

@Suppress("CyclomaticComplexMethod")
@Composable
private fun NodeSignalRow(thatNode: Node, isThisNode: Boolean, contentColor: Color) {
    val items =
        buildList<@Composable () -> Unit> {
            if (isThisNode) {
                add {
                    IconInfo(
                        icon = MeshtasticIcons.ChannelUtilization,
                        contentDescription = stringResource(Res.string.channel_utilization),
                        label = stringResource(Res.string.channel_utilization),
                        text = MetricFormatter.percent(thatNode.deviceMetrics.channel_utilization ?: 0f),
                        contentColor = contentColor,
                    )
                }
                add {
                    IconInfo(
                        icon = MeshtasticIcons.AirUtilization,
                        contentDescription = stringResource(Res.string.air_utilization),
                        label = stringResource(Res.string.air_utilization),
                        text = MetricFormatter.percent(thatNode.deviceMetrics.air_util_tx ?: 0f),
                        contentColor = contentColor,
                    )
                }
            } else {
                if (thatNode.hopsAway > 0) {
                    add { HopsInfo(hops = thatNode.hopsAway, contentColor = contentColor) }
                } else if (thatNode.hopsAway == 0 && !thatNode.viaMqtt) {
                    if (thatNode.snr < 100f) add { Snr(thatNode.snr) }
                    if (thatNode.rssi < 0) add { Rssi(thatNode.rssi) }
                    if (thatNode.snr < 100f && thatNode.rssi < 0) {
                        val quality = determineSignalQuality(thatNode.snr, thatNode.rssi)
                        add {
                            IconInfo(
                                icon = vectorResource(quality.icon),
                                contentDescription = stringResource(Res.string.signal_quality),
                                contentColor = quality.color.invoke(),
                                text = stringResource(quality.nameRes),
                            )
                        }
                    }
                }
                if (thatNode.channel > 0) {
                    add { ChannelInfo(channel = thatNode.channel, contentColor = contentColor) }
                }
            }

            val satCount = thatNode.validPosition?.sats_in_view ?: 0
            if (satCount > 0) {
                add { SatelliteCountInfo(satCount = satCount, contentColor = contentColor) }
            }
        }

    if (items.isNotEmpty()) {
        MetricsGrid(items)
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun gatherSensors(node: Node, tempInFahrenheit: Boolean, contentColor: Color): List<@Composable () -> Unit> {
    val items = mutableListOf<@Composable () -> Unit>()
    val env = node.environmentMetrics
    val pax = node.paxcounter

    if (pax.ble != 0 || pax.wifi != 0) {
        items.add { PaxcountInfo(pax = "B:${pax.ble} W:${pax.wifi}", contentColor = contentColor) }
    }

    if ((env.temperature ?: 0f) != 0f) {
        val temp = MetricFormatter.temperature(env.temperature ?: 0f, tempInFahrenheit)
        items.add { TemperatureInfo(temp = temp, contentColor = contentColor) }
    }
    if ((env.relative_humidity ?: 0f) != 0f) {
        items.add {
            HumidityInfo(humidity = MetricFormatter.humidity(env.relative_humidity ?: 0f), contentColor = contentColor)
        }
    }
    if ((env.barometric_pressure ?: 0f) != 0f) {
        items.add {
            PressureInfo(
                pressure = MetricFormatter.pressure(env.barometric_pressure ?: 0f),
                contentColor = contentColor,
            )
        }
    }
    if ((env.soil_temperature ?: 0f) != 0f) {
        val temp = MetricFormatter.temperature(env.soil_temperature ?: 0f, tempInFahrenheit)
        items.add { SoilTemperatureInfo(temp = temp, contentColor = contentColor) }
    }
    if ((env.soil_moisture ?: 0) != 0 && (env.soil_temperature ?: 0f) != 0f) {
        items.add { SoilMoistureInfo(moisture = "${env.soil_moisture}%", contentColor = contentColor) }
    }
    if ((env.voltage ?: 0f) != 0f) {
        items.add {
            PowerInfo(
                value = MetricFormatter.voltage(env.voltage ?: 0f),
                label = stringResource(Res.string.voltage),
                contentColor = contentColor,
            )
        }
    }
    if ((env.current ?: 0f) != 0f) {
        items.add {
            PowerInfo(
                value = MetricFormatter.current(env.current ?: 0f),
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
        items.forEachIndexed { index, item ->
            val columnIndex = index % GRID_COLUMNS
            val alignment =
                when (columnIndex) {
                    GRID_COLUMNS - 1 -> Alignment.CenterEnd
                    0 -> Alignment.CenterStart
                    else -> Alignment.Center
                }
            Box(Modifier.weight(1f), contentAlignment = alignment) { item() }
        }
        if (remainder != 0) {
            repeat(GRID_COLUMNS - remainder) { Spacer(Modifier.weight(1f)) }
        }
    }
}

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
    deviceType: DeviceType?,
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

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = longName,
                    style = MaterialTheme.typography.titleMediumEmphasized.copy(fontStyle = style),
                    textDecoration = TextDecoration.LineThrough.takeIf { isIgnored },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TransportIcon(
                    transport = thatNode.lastTransport,
                    viaMqtt = thatNode.viaMqtt,
                    modifier = Modifier.size(16.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val statusColor =
                    if (!isThisNode && thatNode.isOnline) {
                        MaterialTheme.colorScheme.StatusGreen
                    } else {
                        contentColor
                    }
                LastHeardInfo(lastHeard = thatNode.lastHeard, showLabel = false, contentColor = statusColor)
            }
        }

        NodeStatusIcons(
            isThisNode = isThisNode,
            isFavorite = isFavorite,
            isMuted = isMuted,
            isUnmessageable = isUnmessageable,
            connectionState = connectionState,
            deviceType = deviceType,
            contentColor = contentColor,
        )
    }
}

@Composable
private fun NodeItemFooter(thatNode: Node, contentColor: Color, deviceImageUrl: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HardwareInfo(
            hwModel = thatNode.user.hw_model.name,
            deviceImageUrl = deviceImageUrl,
            contentColor = contentColor,
        )
        RoleInfo(role = thatNode.user.role, contentColor = contentColor)
        NodeIdInfo(id = thatNode.user.id.ifEmpty { "???" }, contentColor = contentColor)
    }
}
