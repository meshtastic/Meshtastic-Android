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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SocialDistance
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.ui.component.InsetDivider
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.NodeDetailAction
import org.meshtastic.core.strings.R as Res

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
    TitledCard(title = stringResource(Res.string.position), modifier = modifier) {
        // Current position coordinates (linked)
        if (hasValidPosition) {
            InlineMap(node = node, Modifier.fillMaxWidth().height(200.dp))

            LinkedCoordinatesItem(node)
        }

        // Distance (if available)
        if (distance != null && distance.isNotEmpty()) {
            InsetDivider()

            ListItem(
                text = stringResource(Res.string.node_sort_distance),
                leadingIcon = Icons.Default.SocialDistance,
                supportingText = distance,
                copyable = true,
                trailingIcon = null,
            )
        }

        InsetDivider()

        // Exchange position action
        ListItem(
            text = stringResource(Res.string.exchange_position),
            leadingIcon = Icons.Default.LocationOn,
            trailingIcon = null,
            onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.RequestPosition(node))) },
        )

        // Node Map log
        if (availableLogs.contains(LogsType.NODE_MAP)) {
            InsetDivider()

            ListItem(text = stringResource(LogsType.NODE_MAP.titleRes), leadingIcon = LogsType.NODE_MAP.icon) {
                onAction(NodeDetailAction.Navigate(LogsType.NODE_MAP.route))
            }
        }

        // Positions Log
        if (availableLogs.contains(LogsType.POSITIONS)) {
            InsetDivider()

            ListItem(text = stringResource(LogsType.POSITIONS.titleRes), leadingIcon = LogsType.POSITIONS.icon) {
                onAction(NodeDetailAction.Navigate(LogsType.POSITIONS.route))
            }
        }
    }
}
