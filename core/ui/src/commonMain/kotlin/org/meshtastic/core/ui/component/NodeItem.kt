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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import org.meshtastic.core.ui.util.LocalModemPreset
import org.meshtastic.proto.Config

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
        remember(thisNode, thatNode, system) {
            thisNode?.distance(thatNode)?.takeIf { it > 0 }?.toDistanceString(system)
        }
    val bearingDegrees = remember(thisNode, thatNode) { thisNode?.bearing(thatNode) }

    val contentColor = MaterialTheme.colorScheme.onSurface
    val nodeColor =
        (if (isThisNode) thisNode?.colors?.second else thatNode.colors.second)?.let { Color(it) } ?: Color.Transparent
    val cardColors = CardDefaults.cardColors(containerColor = nodeTintedContainer(nodeColor))
    val cardBorder = nodeBorderStroke(nodeColor, active = isActive)

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
    val modemPreset = LocalModemPreset.current
    val nodeDescription =
        remember(thatNode, distance, a11yStrings, modemPreset) {
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
                modemPreset = modemPreset,
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

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
private fun NodeSignalRow(thatNode: Node, isThisNode: Boolean, contentColor: Color) {
    // The signal pill bundles SNR + RSSI + quality into one scrim-backed chip (legibility, see StatusSurface). It's
    // wider than a 1/3 grid cell, so it renders on its own line at natural width; the short metrics flow in the grid.
    var signalChip: (@Composable () -> Unit)? = null
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
                    val showSnr = thatNode.snr < 100f
                    val showRssi = thatNode.rssi < 0
                    if (showSnr || showRssi) {
                        signalChip = {
                            // Full-width pill: SNR left, RSSI center, quality right — the pre-scrim spread, now
                            // scrimmed.
                            StatusSurface(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                if (showSnr) Snr(thatNode.snr)
                                if (showRssi) Rssi(thatNode.rssi)
                                if (showSnr && showRssi) {
                                    val quality = determineSignalQuality(thatNode.snr, LocalModemPreset.current)
                                    IconInfo(
                                        icon = vectorResource(quality.icon),
                                        contentDescription = stringResource(Res.string.signal_quality),
                                        contentColor = quality.color.invoke(),
                                        text = stringResource(quality.nameRes),
                                    )
                                }
                            }
                        }
                    }
                }
                if (thatNode.channel > 0) {
                    add { ChannelInfo(channel = thatNode.channel, contentColor = contentColor) }
                }
            }
        }

    if (signalChip != null || items.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            signalChip?.invoke()
            if (items.isNotEmpty()) MetricsGrid(items)
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun gatherSensors(node: Node, tempInFahrenheit: Boolean, contentColor: Color): List<@Composable () -> Unit> {
    val items = mutableListOf<@Composable () -> Unit>()
    val env = node.environmentMetrics
    val aq = node.airQualityMetrics
    val pax = node.paxcounter

    if (pax.ble != 0 || pax.wifi != 0) {
        items.add { PaxcountInfo(pax = "B:${pax.ble} W:${pax.wifi}", contentColor = contentColor) }
    }

    // Temperature carries presence, so `null` already means "no sensor" — testing against 0 hid an ordinary 0 °C
    // reading, which the temperature chart plots. Prefer the environment sensor, then the SCD4x CO₂ sensor's own.
    (env.temperature ?: aq.co2_temperature)?.let { temperature ->
        val temp = MetricFormatter.temperature(temperature, tempInFahrenheit)
        items.add { TemperatureInfo(temp = temp, contentColor = contentColor) }
    }

    // Humidity keeps its zero-guard: 0% RH is not physically reachable, and the humidity chart filters it too.
    if ((env.relative_humidity ?: 0f) != 0f) {
        items.add {
            HumidityInfo(humidity = MetricFormatter.humidity(env.relative_humidity ?: 0f), contentColor = contentColor)
        }
    } else if ((aq.co2_humidity ?: 0f) != 0f) {
        items.add {
            HumidityInfo(humidity = MetricFormatter.humidity(aq.co2_humidity ?: 0f), contentColor = contentColor)
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

/**
 * [LastHeardInfo] backed by a scrim chip and tinted StatusGreen when [online] — the legible "online" affordance —
 * rendered plain otherwise. Shared by the complete and compact node rows.
 */
@Composable
internal fun StatusAwareLastHeard(lastHeard: Int, online: Boolean, contentColor: Color, relative: Boolean = true) {
    if (online) {
        StatusSurface {
            LastHeardInfo(
                lastHeard = lastHeard,
                showLabel = false,
                relative = relative,
                contentColor = MaterialTheme.colorScheme.StatusGreen,
            )
        }
    } else {
        LastHeardInfo(lastHeard = lastHeard, showLabel = false, relative = relative, contentColor = contentColor)
    }
}

/** Key status (always status-colored) + the signed-node shield share one scrim chip so both stay legible. */
@Composable
fun NodeSecurityIcons(thatNode: Node, modifier: Modifier = Modifier, iconSize: Dp = 20.dp) {
    StatusSurface(modifier = modifier) {
        if (thatNode.signsPackets) {
            NodeSignedStatusIcon(modifier = Modifier.size(iconSize))
        }
        NodeKeyStatusIcon(
            hasPKC = thatNode.hasPKC,
            mismatchKey = thatNode.mismatchKey,
            publicKey = thatNode.user.public_key,
            modifier = Modifier.size(iconSize),
        )
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NodeChip(node = thatNode)
        NodeSecurityIcons(thatNode)

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
                StatusAwareLastHeard(
                    lastHeard = thatNode.lastHeard,
                    online = !isThisNode && thatNode.isOnline,
                    contentColor = contentColor,
                )
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
