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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.exchange_userinfo
import org.meshtastic.core.strings.request
import org.meshtastic.core.strings.request_air_quality_metrics
import org.meshtastic.core.strings.request_device_metrics
import org.meshtastic.core.strings.request_environment_metrics
import org.meshtastic.core.strings.request_local_stats
import org.meshtastic.core.strings.request_power_metrics
import org.meshtastic.feature.node.model.NodeDetailAction

@Composable
@Suppress("LongMethod")
internal fun RemoteDeviceActions(
    node: Node,
    lastTracerouteTime: Long?,
    lastRequestNeighborsTime: Long?,
    onAction: (NodeDetailAction) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = stringResource(Res.string.request),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
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
                onClick = {
                    onAction(
                        NodeDetailAction.HandleNodeMenuAction(
                            NodeMenuAction.RequestTelemetry(node, TelemetryType.DEVICE),
                        ),
                    )
                },
                label = { Text(stringResource(Res.string.request_device_metrics)) },
                leadingIcon = { Icon(Icons.Default.AreaChart, contentDescription = null, Modifier.size(18.dp)) },
            )

            AssistChip(
                onClick = {
                    onAction(
                        NodeDetailAction.HandleNodeMenuAction(
                            NodeMenuAction.RequestTelemetry(node, TelemetryType.ENVIRONMENT),
                        ),
                    )
                },
                label = { Text(stringResource(Res.string.request_environment_metrics)) },
                leadingIcon = { Icon(Icons.Default.AreaChart, contentDescription = null, Modifier.size(18.dp)) },
            )

            AssistChip(
                onClick = {
                    onAction(
                        NodeDetailAction.HandleNodeMenuAction(
                            NodeMenuAction.RequestTelemetry(node, TelemetryType.AIR_QUALITY),
                        ),
                    )
                },
                label = { Text(stringResource(Res.string.request_air_quality_metrics)) },
                leadingIcon = { Icon(Icons.Default.AreaChart, contentDescription = null, Modifier.size(18.dp)) },
            )

            AssistChip(
                onClick = {
                    onAction(
                        NodeDetailAction.HandleNodeMenuAction(
                            NodeMenuAction.RequestTelemetry(node, TelemetryType.POWER),
                        ),
                    )
                },
                label = { Text(stringResource(Res.string.request_power_metrics)) },
                leadingIcon = { Icon(Icons.Default.AreaChart, contentDescription = null, Modifier.size(18.dp)) },
            )

            AssistChip(
                onClick = {
                    onAction(
                        NodeDetailAction.HandleNodeMenuAction(
                            NodeMenuAction.RequestTelemetry(node, TelemetryType.LOCAL_STATS),
                        ),
                    )
                },
                label = { Text(stringResource(Res.string.request_local_stats)) },
                leadingIcon = { Icon(Icons.Default.AreaChart, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
    }
}
