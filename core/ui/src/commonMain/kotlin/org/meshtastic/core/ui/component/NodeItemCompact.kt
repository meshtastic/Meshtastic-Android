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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.isUnmessageableRole
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.distance
import org.meshtastic.core.resources.node_list_click_label
import org.meshtastic.core.resources.node_list_long_click_label
import org.meshtastic.core.resources.unknown_username
import org.meshtastic.core.ui.icon.DeviceSleep
import org.meshtastic.core.ui.icon.Distance
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MqttConnected
import org.meshtastic.core.ui.icon.Success
import org.meshtastic.core.ui.icon.Unmessageable
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.proto.Config

private const val ACTIVE_ALPHA = 0.5f
private const val INACTIVE_ALPHA = 0.2f
private const val COMPACT_ICON_SIZE_DP = 16

@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod", "UnusedParameter")
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
        remember(thisNode, thatNode) { thisNode?.distance(thatNode)?.takeIf { it > 0 }?.toDistanceString(system) }
    val unmessageable =
        remember(thatNode) {
            when {
                thatNode.user.is_unmessagable != null -> thatNode.user.is_unmessagable!!
                else -> thatNode.user.role.isUnmessageableRole()
            }
        }

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
            } ?: CardDefaults.cardColors()

    val style = if (thatNode.isUnknownUser) FontStyle.Italic else FontStyle.Normal

    val a11yStrings = rememberNodeDescriptionStrings()
    val nodeDescription =
        remember(thatNode, lastHeardIsRelative, a11yStrings) {
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
            )
        }

    Card(
        modifier =
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = nodeDescription
            role = Role.Button
        },
        colors = cardColors,
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Leading: NodeChip avatar
            NodeChip(node = thatNode)

            // Content rows
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

                // Row 2: Glanceable health — online + last heard + battery + distance + signal
                CompactHealthRow(
                    thatNode = thatNode,
                    isThisNode = isThisNode,
                    distance = distance,
                    showPower = showPower,
                    showLastHeard = showLastHeard,
                    lastHeardIsRelative = lastHeardIsRelative,
                    showLocation = showLocation,
                    showSignal = showSignal,
                    contentColor = contentColor,
                )

                // Row 3: Tertiary metadata — hardware · role · hops · channel · telemetry
                CompactFooterRow(
                    thatNode = thatNode,
                    isThisNode = isThisNode,
                    showHops = showHops,
                    showChannel = showChannel,
                    showRole = showRole,
                    contentColor = contentColor,
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NodeKeyStatusIcon(
            hasPKC = thatNode.hasPKC,
            mismatchKey = thatNode.mismatchKey,
            publicKey = thatNode.user.public_key,
            modifier = Modifier.size(18.dp),
        )
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
    showPower: Boolean,
    showLastHeard: Boolean,
    lastHeardIsRelative: Boolean,
    showLocation: Boolean,
    showSignal: Boolean,
    contentColor: Color,
) {
    val segments = buildList {
        // Online indicator + Last heard
        if (showLastHeard && thatNode.lastHeard > 0 && !isFutureDate(thatNode.lastHeard)) {
            add(
                @Composable {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector =
                            if (thatNode.isOnline) MeshtasticIcons.Success else MeshtasticIcons.DeviceSleep,
                            contentDescription = null,
                            modifier = Modifier.size(COMPACT_ICON_SIZE_DP.dp),
                            tint =
                            if (thatNode.isOnline) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                        LastHeardInfo(
                            lastHeard = thatNode.lastHeard,
                            showLabel = false,
                            relative = lastHeardIsRelative,
                            contentColor = contentColor,
                        )
                    }
                },
            )
        }

        // Battery
        if (showPower && thatNode.batteryLevel != null) {
            add(
                @Composable {
                    MaterialBatteryInfo(
                        level = thatNode.batteryLevel ?: 0,
                        voltage = thatNode.voltage ?: 0f,
                        contentColor = contentColor,
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

        // Signal quality
        val hasDirectSignal = thatNode.hopsAway == 0 && thatNode.snr < 100f && !thatNode.viaMqtt && thatNode.rssi < 0
        if (showSignal && hasDirectSignal) {
            val quality = determineSignalQuality(thatNode.snr, thatNode.rssi)
            add(
                @Composable {
                    IconInfo(
                        icon = vectorResource(quality.icon),
                        contentDescription = stringResource(quality.nameRes),
                        contentColor = quality.color.invoke(),
                        text = stringResource(quality.nameRes),
                    )
                },
            )
        }
    }

    if (segments.isNotEmpty()) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            segments.forEachIndexed { index, content ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) {
                        Text(
                            text = "· ",
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.5f),
                        )
                    }
                    content()
                }
            }
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
    contentColor: Color,
) {
    val tertiaryColor = contentColor.copy(alpha = 0.7f)
    val segments = buildList {
        // Hardware model
        if (showRole) {
            add(thatNode.user.hw_model.name)
        }

        // Role
        if (showRole) {
            add(thatNode.user.role.name)
        }

        // Hops
        if (showHops && thatNode.hopsAway > 0 && !isThisNode) {
            add("${thatNode.hopsAway} hop${if (thatNode.hopsAway > 1) "s" else ""}")
        }

        // Channel
        if (showChannel && thatNode.channel > 0) {
            add("Ch ${thatNode.channel}")
        }
    }

    if (segments.isNotEmpty() || (showRole && thatNode.viaMqtt)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (segments.isNotEmpty()) {
                Text(
                    text = segments.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = tertiaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // MQTT status icon
            if (showRole && thatNode.viaMqtt) {
                Icon(
                    imageVector = MeshtasticIcons.MqttConnected,
                    contentDescription = null,
                    modifier = Modifier.size(COMPACT_ICON_SIZE_DP.dp),
                    tint = tertiaryColor,
                )
            }
        }
    }
}

private fun isFutureDate(lastHeard: Int): Boolean {
    val nowSeconds = org.meshtastic.core.common.util.nowSeconds.toInt()
    val oneYearSeconds = 365 * 24 * 60 * 60
    return lastHeard > nowSeconds + oneYearSeconds
}
