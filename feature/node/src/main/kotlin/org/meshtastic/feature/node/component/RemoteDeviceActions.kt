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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.exchange_userinfo
import org.meshtastic.core.strings.request_telemetry
import org.meshtastic.feature.node.model.NodeDetailAction

@Composable
internal fun RemoteDeviceActions(
    node: Node,
    lastTracerouteTime: Long?,
    lastRequestNeighborsTime: Long?,
    onAction: (NodeDetailAction) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.RequestUserInfo(node))) },
            label = { Text(stringResource(Res.string.exchange_userinfo)) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, Modifier.size(18.dp)) },
        )

        TracerouteChip(
            lastTracerouteTime = lastTracerouteTime,
            onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.TraceRoute(node))) },
        )

        RequestNeighborsChip(
            lastRequestNeighborsTime = lastRequestNeighborsTime,
            onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.RequestNeighborInfo(node))) },
        )

        AssistChip(
            onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.RequestTelemetry(node))) },
            label = { Text(stringResource(Res.string.request_telemetry)) },
            leadingIcon = { Icon(Icons.Default.AreaChart, contentDescription = null, Modifier.size(18.dp)) },
        )
    }
}
