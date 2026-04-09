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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.exchange_position
import org.meshtastic.core.resources.open_compass
import org.meshtastic.core.resources.position
import org.meshtastic.core.ui.icon.Compass
import org.meshtastic.core.ui.icon.Distance
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.LocalInlineMapProvider
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.NodeDetailAction
import org.meshtastic.proto.Config

private const val EXCHANGE_BUTTON_WEIGHT = 1.1f
private const val COMPASS_BUTTON_WEIGHT = 0.9f
private const val MAP_HEIGHT_DP = 200

/**
 * Displays node position details, last update time, distance, and related actions like requesting position and
 * accessing map/position logs.
 */
@Composable
fun PositionSection(
    node: Node,
    ourNode: Node?,
    metricsState: MetricsState,
    availableLogs: Set<LogsType>,
    onAction: (NodeDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val distance = ourNode?.distance(node)?.takeIf { it > 0 }?.toDistanceString(metricsState.displayUnits)
    val hasValidPosition = node.latitude != 0.0 || node.longitude != 0.0
    val isLocal = metricsState.isLocal

    SectionCard(title = Res.string.position, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            if (hasValidPosition) {
                PositionMap(node, distance)
                LinkedCoordinatesItem(node, metricsState.displayUnits)
                Spacer(Modifier.height(8.dp))
            }

            PositionActionButtons(
                node = node,
                isLocal = isLocal,
                hasValidPosition = hasValidPosition,
                displayUnits = metricsState.displayUnits,
                onAction = onAction,
            )

            if (availableLogs.contains(LogsType.NODE_MAP) || availableLogs.contains(LogsType.POSITIONS)) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (availableLogs.contains(LogsType.NODE_MAP)) {
                        AssistChip(
                            onClick = { onAction(NodeDetailAction.Navigate(LogsType.NODE_MAP.routeFactory(node.num))) },
                            label = { Text(stringResource(LogsType.NODE_MAP.titleRes)) },
                            leadingIcon = { Icon(LogsType.NODE_MAP.icon(), null, Modifier.size(18.dp)) },
                        )
                    }

                    if (availableLogs.contains(LogsType.POSITIONS)) {
                        AssistChip(
                            onClick = {
                                onAction(NodeDetailAction.Navigate(LogsType.POSITIONS.routeFactory(node.num)))
                            },
                            label = { Text(stringResource(LogsType.POSITIONS.titleRes)) },
                            leadingIcon = { Icon(LogsType.POSITIONS.icon(), null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionMap(node: Node, distance: String?) {
    Box(modifier = Modifier.padding(vertical = 4.dp)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().height(MAP_HEIGHT_DP.dp)) {
            LocalInlineMapProvider.current(node, Modifier.fillMaxSize())
        }
        if (distance != null && distance.isNotEmpty()) {
            Surface(
                modifier = Modifier.padding(12.dp).align(Alignment.TopEnd),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(MeshtasticIcons.Distance, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(distance, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun PositionActionButtons(
    node: Node,
    isLocal: Boolean,
    hasValidPosition: Boolean,
    displayUnits: Config.DisplayConfig.DisplayUnits,
    onAction: (NodeDetailAction) -> Unit,
) {
    if (isLocal && !hasValidPosition) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isLocal) {
            Button(
                onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.RequestPosition(node))) },
                modifier = Modifier.weight(EXCHANGE_BUTTON_WEIGHT),
                shape = MaterialTheme.shapes.large,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(MeshtasticIcons.LocationOn, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.exchange_position),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                )
            }
        }

        if (hasValidPosition) {
            FilledTonalButton(
                onClick = { onAction(NodeDetailAction.OpenCompass(node, displayUnits)) },
                modifier = if (isLocal) Modifier.fillMaxWidth() else Modifier.weight(COMPASS_BUTTON_WEIGHT),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(MeshtasticIcons.Compass, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.open_compass),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
