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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SocialDistance
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.formatAgo
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.SettingsItem
import org.meshtastic.core.ui.component.SettingsItemDetail
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.NodeDetailAction
import org.meshtastic.feature.node.model.isEffectivelyUnmessageable

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
) {
    val distance = ourNode?.distance(node)?.takeIf { it > 0 }?.toDistanceString(metricsState.displayUnits)
    val hasValidPosition = node.latitude != 0.0 || node.longitude != 0.0
    TitledCard(title = stringResource(R.string.position)) {
        // Current position coordinates (linked)
        if (hasValidPosition) {
            InlineMap(node = node, Modifier.fillMaxWidth().height(200.dp))
            SettingsItemDetail(
                text = stringResource(R.string.last_position_update),
                icon = Icons.Default.LocationOn,
                supportingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatAgo(node.position.time), style = MaterialTheme.typography.titleLarge)
                        LinkedCoordinates(
                            latitude = node.latitude,
                            longitude = node.longitude,
                            nodeName = node.user.longName,
                        )
                    }
                },
            )
        }

        // Distance (if available)
        if (distance != null && distance.isNotEmpty()) {
            SettingsItemDetail(
                text = stringResource(R.string.node_sort_distance),
                icon = Icons.Default.SocialDistance,
                supportingText = distance,
            )
        }

        // Exchange position action
        if (!node.isEffectivelyUnmessageable) {
            SettingsItem(
                text = stringResource(id = R.string.exchange_position),
                leadingIcon = Icons.Default.LocationOn,
                trailingContent = {},
                onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.RequestPosition(node))) },
            )
        }

        // Node Map log
        if (availableLogs.contains(LogsType.NODE_MAP)) {
            SettingsItem(text = stringResource(LogsType.NODE_MAP.titleRes), leadingIcon = LogsType.NODE_MAP.icon) {
                onAction(NodeDetailAction.Navigate(LogsType.NODE_MAP.route))
            }
        }

        // Positions Log
        if (availableLogs.contains(LogsType.POSITIONS)) {
            SettingsItem(text = stringResource(LogsType.POSITIONS.titleRes), leadingIcon = LogsType.POSITIONS.icon) {
                onAction(NodeDetailAction.Navigate(LogsType.POSITIONS.route))
            }
        }
    }
}
