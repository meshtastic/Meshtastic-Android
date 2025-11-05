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

import android.content.res.Configuration
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.isUnmessageableRole
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.NodeKeyStatusIcon
import org.meshtastic.core.ui.component.SignalInfo
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.ConfigProtos.Config.DisplayConfig
import org.meshtastic.core.strings.R as Res

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NodeItem(
    thisNode: Node?,
    thatNode: Node,
    distanceUnits: Int,
    tempInFahrenheit: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    currentTimeMillis: Long,
    isConnected: Boolean = false,
) {
    val isFavorite = remember(thatNode) { thatNode.isFavorite }
    val isIgnored = thatNode.isIgnored
    val longName = thatNode.user.longName.ifEmpty { stringResource(Res.string.unknown_username) }
    val isThisNode = remember(thatNode) { thisNode?.num == thatNode.num }
    val system = remember(distanceUnits) { DisplayConfig.DisplayUnits.forNumber(distanceUnits) }
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
                val containerColor = Color(it).copy(alpha = 0.2f)
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
                thatNode.user.hasIsUnmessagable() -> thatNode.user.isUnmessagable
                else -> thatNode.user.role.isUnmessageableRole()
            }
        }

    Card(modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp), colors = cardColors) {
        Column(
            modifier =
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).fillMaxWidth().padding(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NodeChip(node = thatNode)

                NodeKeyStatusIcon(
                    hasPKC = thatNode.hasPKC,
                    mismatchKey = thatNode.mismatchKey,
                    publicKey = thatNode.user.publicKey,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = longName,
                    style = MaterialTheme.typography.titleMediumEmphasized.copy(fontStyle = style),
                    textDecoration = TextDecoration.LineThrough.takeIf { isIgnored },
                    softWrap = true,
                )
                LastHeardInfo(
                    lastHeard = thatNode.lastHeard,
                    currentTimeMillis = currentTimeMillis,
                    contentColor = contentColor,
                )
                NodeStatusIcons(
                    isThisNode = isThisNode,
                    isFavorite = isFavorite,
                    isUnmessageable = unmessageable,
                    isConnected = isConnected,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialBatteryInfo(
                    level = thatNode.batteryLevel,
                    voltage = thatNode.voltage,
                    contentColor = contentColor,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (distance != null) {
                        DistanceInfo(distance = distance, contentColor = contentColor)
                    }
                    thatNode.validPosition?.let { position ->
                        ElevationInfo(
                            altitude = position.altitude,
                            system = system,
                            suffix = stringResource(Res.string.elevation_suffix),
                            contentColor = contentColor,
                        )
                        val satCount = position.satsInView
                        if (satCount > 0) {
                            SatelliteCountInfo(satCount = satCount, contentColor = contentColor)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                SignalInfo(node = thatNode, isThisNode = isThisNode, contentColor = contentColor)
            }
            val telemetryStrings = thatNode.getTelemetryStrings(tempInFahrenheit)

            if (telemetryStrings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    telemetryStrings.forEach { telemetryString ->
                        Text(text = telemetryString, style = MaterialTheme.typography.bodySmall, color = contentColor)
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val labelStyle =
                    if (thatNode.isUnknownUser) {
                        MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)
                    } else {
                        MaterialTheme.typography.labelSmall
                    }
                Text(text = thatNode.user.hwModel.name, style = labelStyle)
                Text(text = thatNode.user.role.name, style = labelStyle)
                Text(text = thatNode.user.id.ifEmpty { "???" }, style = labelStyle)
            }
        }
    }
}

@Composable
@Preview(showBackground = false, uiMode = Configuration.UI_MODE_NIGHT_YES)
fun NodeInfoSimplePreview() {
    AppTheme {
        val thisNode = NodePreviewParameterProvider().values.first()
        val thatNode = NodePreviewParameterProvider().values.last()
        NodeItem(thisNode = thisNode, thatNode = thatNode, 0, true, currentTimeMillis = System.currentTimeMillis())
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
            currentTimeMillis = System.currentTimeMillis(),
        )
    }
}
