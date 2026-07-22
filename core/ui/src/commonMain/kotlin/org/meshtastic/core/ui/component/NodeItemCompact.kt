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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.isUnmessageableRole
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.distance
import org.meshtastic.core.resources.ic_memory
import org.meshtastic.core.resources.node_list_click_label
import org.meshtastic.core.resources.node_list_long_click_label
import org.meshtastic.core.resources.unknown_username
import org.meshtastic.core.ui.icon.Channel
import org.meshtastic.core.ui.icon.Counter0
import org.meshtastic.core.ui.icon.Counter1
import org.meshtastic.core.ui.icon.Counter2
import org.meshtastic.core.ui.icon.Counter3
import org.meshtastic.core.ui.icon.Counter4
import org.meshtastic.core.ui.icon.Counter5
import org.meshtastic.core.ui.icon.Counter6
import org.meshtastic.core.ui.icon.Counter7
import org.meshtastic.core.ui.icon.Counter8
import org.meshtastic.core.ui.icon.Distance
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.HardwareModel
import org.meshtastic.core.ui.icon.HopCount
import org.meshtastic.core.ui.icon.Humidity
import org.meshtastic.core.ui.icon.MapCompass
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MqttConnected
import org.meshtastic.core.ui.icon.Pressure
import org.meshtastic.core.ui.icon.Temperature
import org.meshtastic.core.ui.icon.Unmessageable
import org.meshtastic.core.ui.icon.role
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.core.ui.util.LocalModemPreset
import org.meshtastic.proto.Config

private const val COMPACT_ICON_SIZE_DP = 16

@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun NodeItemCompact(
    thisNode: Node?,
    thatNode: Node,
    distanceUnits: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    isActive: Boolean = false,
    showPower: Boolean = true,
    showLastHeard: Boolean = true,
    lastHeardIsRelative: Boolean = true,
    showLocation: Boolean = true,
    showHops: Boolean = true,
    showSignal: Boolean = true,
    showChannel: Boolean = true,
    showRole: Boolean = true,
    showTelemetry: Boolean = true,
    tempInFahrenheit: Boolean = false,
    deviceImageUrl: String? = null,
) {
    val longName = thatNode.user.long_name.ifEmpty { stringResource(Res.string.unknown_username) }
    val isFavorite = thatNode.isFavorite
    val isIgnored = thatNode.isIgnored
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
    val unmessageable =
        remember(thatNode) {
            when {
                thatNode.user.is_unmessagable != null -> thatNode.user.is_unmessagable!!
                else -> thatNode.user.role.isUnmessageableRole()
            }
        }

    val contentColor = MaterialTheme.colorScheme.onSurface
    val nodeColor =
        (if (isThisNode) thisNode?.colors?.second else thatNode.colors.second)?.let { Color(it) } ?: Color.Transparent
    val cardColors = CardDefaults.cardColors(containerColor = nodeTintedContainer(nodeColor))
    val cardBorder = nodeBorderStroke(nodeColor, active = isActive)

    val style = if (thatNode.isUnknownUser) FontStyle.Italic else FontStyle.Normal

    val a11yStrings = rememberNodeDescriptionStrings()
    val modemPreset = LocalModemPreset.current
    val nodeDescription =
        remember(thatNode, distance, lastHeardIsRelative, a11yStrings, modemPreset) {
            buildNodeDescription(
                name = longName,
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
                lastHeardIsRelative = lastHeardIsRelative,
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
        Row(
            modifier =
            Modifier.combinedClickable(
                onClickLabel = stringResource(Res.string.node_list_click_label),
                onClick = onClick,
                onLongClickLabel = stringResource(Res.string.node_list_long_click_label),
                onLongClick = onLongClick,
            )
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Column 1: Chip + Battery (fixed width)
            Column(
                modifier = Modifier.widthIn(min = 90.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NodeChip(node = thatNode)
                if (showPower && (thatNode.batteryLevel ?: 0) > 0) {
                    MaterialBatteryInfo(
                        level = thatNode.batteryLevel ?: 0,
                        voltage = thatNode.voltage ?: 0f,
                        contentColor = contentColor,
                    )
                }
            }

            // Column 2: Content rows (fills remaining width)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Row 1: Identity — name + PKC + favorite
                CompactNameRow(
                    thatNode = thatNode,
                    longName = longName,
                    style = style,
                    isIgnored = isIgnored,
                    isFavorite = isFavorite,
                    unmessageable = unmessageable,
                )

                // Row 2: Glanceable health — online + last heard + distance + signal
                CompactHealthRow(
                    thatNode = thatNode,
                    isThisNode = isThisNode,
                    distance = distance,
                    bearingDegrees = bearingDegrees,
                    showLastHeard = showLastHeard,
                    lastHeardIsRelative = lastHeardIsRelative,
                    showLocation = showLocation,
                    showSignal = showSignal,
                    contentColor = contentColor,
                )

                // Row 3: Environment metrics — temp · humidity · pressure (icon + value only)
                if (showTelemetry) {
                    CompactMetricsRow(
                        thatNode = thatNode,
                        tempInFahrenheit = tempInFahrenheit,
                        contentColor = contentColor,
                    )
                }

                // Row 4: Tertiary metadata — hardware · role · hops · channel
                CompactFooterRow(
                    thatNode = thatNode,
                    isThisNode = isThisNode,
                    showHops = showHops,
                    showChannel = showChannel,
                    showRole = showRole,
                    deviceImageUrl = deviceImageUrl,
                )
            }
        }
    }
}

@Composable
private fun CompactNameRow(
    thatNode: Node,
    longName: String,
    style: FontStyle,
    isIgnored: Boolean,
    isFavorite: Boolean,
    unmessageable: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NodeSecurityIcons(thatNode, iconSize = 18.dp)
        Text(
            text = longName,
            style = MaterialTheme.typography.titleMediumEmphasized.copy(fontStyle = style),
            textDecoration = TextDecoration.LineThrough.takeIf { isIgnored },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (unmessageable) {
            Icon(
                imageVector = MeshtasticIcons.Unmessageable,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        if (isFavorite) {
            Icon(
                imageVector = MeshtasticIcons.Favorite,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.StatusYellow,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod")
private fun CompactHealthRow(
    thatNode: Node,
    isThisNode: Boolean,
    distance: String?,
    bearingDegrees: Int?,
    showLastHeard: Boolean,
    lastHeardIsRelative: Boolean,
    showLocation: Boolean,
    showSignal: Boolean,
    contentColor: Color,
) {
    val segments = buildList {
        // Last heard (tinted by online status)
        if (showLastHeard && thatNode.lastHeard > 0 && !isFutureDate(thatNode.lastHeard)) {
            add(
                @Composable {
                    StatusAwareLastHeard(
                        lastHeard = thatNode.lastHeard,
                        online = thatNode.isOnline,
                        contentColor = contentColor,
                        relative = lastHeardIsRelative,
                    )
                },
            )
        }

        // Distance
        if (showLocation && distance != null && !isThisNode) {
            add(
                @Composable {
                    IconInfo(
                        icon = MeshtasticIcons.Distance,
                        contentDescription = stringResource(Res.string.distance),
                        contentColor = contentColor,
                        text = distance,
                    )
                },
            )
        }

        // Bearing (rotated compass arrow)
        if (showLocation && bearingDegrees != null && !isThisNode) {
            add(
                @Composable {
                    Icon(
                        imageVector = MeshtasticIcons.MapCompass,
                        contentDescription = "$bearingDegrees°",
                        modifier = Modifier.size(COMPACT_ICON_SIZE_DP.dp).rotate(bearingDegrees.toFloat()),
                        tint = contentColor,
                    )
                },
            )
        }

        // Signal quality
        val hasDirectSignal = thatNode.hopsAway == 0 && thatNode.snr < 100f && !thatNode.viaMqtt && thatNode.rssi < 0
        if (showSignal && hasDirectSignal) {
            val quality = determineSignalQuality(thatNode.snr, LocalModemPreset.current)
            add(
                @Composable {
                    StatusSurface {
                        IconInfo(
                            icon = vectorResource(quality.icon),
                            contentDescription = stringResource(quality.nameRes),
                            contentColor = quality.color.invoke(),
                            text = stringResource(quality.nameRes),
                        )
                    }
                },
            )
        }
    }

    if (segments.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            segments.forEach { content -> content() }
        }
    }
}

@Composable
@Suppress("LongParameterList", "CyclomaticComplexMethod")
private fun CompactFooterRow(
    thatNode: Node,
    isThisNode: Boolean,
    showHops: Boolean,
    showChannel: Boolean,
    showRole: Boolean,
    deviceImageUrl: String?,
) {
    val tertiaryColor = MaterialTheme.colorScheme.outline
    val segments =
        buildList<@Composable () -> Unit> {
            if (showRole) {
                add {
                    HardwareModelInfo(
                        hwModelName = thatNode.user.hw_model.name,
                        deviceImageUrl = deviceImageUrl,
                        contentColor = tertiaryColor,
                    )
                }
                add {
                    Icon(
                        imageVector = MeshtasticIcons.role(thatNode.user.role),
                        contentDescription = thatNode.user.role.name,
                        modifier = Modifier.size(COMPACT_ICON_SIZE_DP.dp),
                        tint = tertiaryColor,
                    )
                }
            }
            if (showHops && thatNode.hopsAway > 0 && !isThisNode) {
                add {
                    IconInfo(
                        icon = MeshtasticIcons.HopCount,
                        contentDescription = "${thatNode.hopsAway} hops",
                        contentColor = tertiaryColor,
                        text = thatNode.hopsAway.toString(),
                    )
                }
            }
            if (showChannel && thatNode.channel > 0) {
                add {
                    Icon(
                        imageVector = channelIcon(thatNode.channel),
                        contentDescription = "Channel ${thatNode.channel}",
                        modifier = Modifier.size(COMPACT_ICON_SIZE_DP.dp),
                        tint = tertiaryColor,
                    )
                }
            }
            if (showRole && thatNode.viaMqtt) {
                add {
                    Icon(
                        imageVector = MeshtasticIcons.MqttConnected,
                        contentDescription = null,
                        modifier = Modifier.size(COMPACT_ICON_SIZE_DP.dp),
                        tint = tertiaryColor,
                    )
                }
            }
        }

    SegmentedRow(segments)
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun CompactMetricsRow(thatNode: Node, tempInFahrenheit: Boolean, contentColor: Color) {
    val env = thatNode.environmentMetrics
    val segments =
        buildList<@Composable () -> Unit> {
            if ((env.temperature ?: 0f) != 0f) {
                val temp = MetricFormatter.temperature(env.temperature ?: 0f, tempInFahrenheit)
                add {
                    IconInfo(
                        icon = MeshtasticIcons.Temperature,
                        contentDescription = "Temperature",
                        contentColor = contentColor,
                        text = temp,
                    )
                }
            }
            if ((env.relative_humidity ?: 0f) != 0f) {
                add {
                    IconInfo(
                        icon = MeshtasticIcons.Humidity,
                        contentDescription = "Humidity",
                        contentColor = contentColor,
                        text = MetricFormatter.humidity(env.relative_humidity ?: 0f),
                    )
                }
            }
            if ((env.barometric_pressure ?: 0f) != 0f) {
                add {
                    IconInfo(
                        icon = MeshtasticIcons.Pressure,
                        contentDescription = "Pressure",
                        contentColor = contentColor,
                        text = MetricFormatter.pressure(env.barometric_pressure ?: 0f),
                    )
                }
            }
        }

    if (segments.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            segments.forEach { content -> content() }
        }
    }
}

@Composable
private fun SegmentedRow(segments: List<@Composable () -> Unit>) {
    if (segments.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        segments.forEach { content -> content() }
    }
}

@Composable
private fun channelIcon(channel: Int): ImageVector = when (channel) {
    0 -> MeshtasticIcons.Counter0
    1 -> MeshtasticIcons.Counter1
    2 -> MeshtasticIcons.Counter2
    3 -> MeshtasticIcons.Counter3
    4 -> MeshtasticIcons.Counter4
    5 -> MeshtasticIcons.Counter5
    6 -> MeshtasticIcons.Counter6
    7 -> MeshtasticIcons.Counter7
    8 -> MeshtasticIcons.Counter8
    else -> MeshtasticIcons.Channel
}

private fun isFutureDate(lastHeard: Int): Boolean {
    val nowSeconds = org.meshtastic.core.common.util.nowSeconds.toInt()
    val oneYearSeconds = 365 * 24 * 60 * 60
    return lastHeard > nowSeconds + oneYearSeconds
}

@Composable
private fun HardwareModelInfo(hwModelName: String, deviceImageUrl: String?, contentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (deviceImageUrl != null) {
            AsyncImage(
                model = deviceImageUrl,
                contentDescription = hwModelName,
                modifier = Modifier.size(COMPACT_ICON_SIZE_DP.dp),
                contentScale = ContentScale.Fit,
                fallback = painterResource(Res.drawable.ic_memory),
                error = painterResource(Res.drawable.ic_memory),
            )
        } else {
            Icon(
                imageVector = MeshtasticIcons.HardwareModel,
                contentDescription = hwModelName,
                modifier = Modifier.size(COMPACT_ICON_SIZE_DP.dp),
                tint = contentColor.copy(alpha = 0.65f),
            )
        }
        Text(
            text = hwModelName,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
            color = contentColor.copy(alpha = 0.95f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
