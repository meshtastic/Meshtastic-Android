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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.environment
import org.meshtastic.core.strings.logs
import org.meshtastic.core.strings.power
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.NodeDetailAction

@Composable
fun MetricsSection(
    node: Node,
    metricsState: MetricsState,
    availableLogs: Set<LogsType>,
    onAction: (NodeDetailAction) -> Unit,
) {
    if (node.hasEnvironmentMetrics) {
        EnvironmentCard(node, metricsState)
    }

    if (node.hasPowerMetrics) {
        PowerCard(node)
    }

    val nonPositionLogs = availableLogs.filter { it != LogsType.NODE_MAP && it != LogsType.POSITIONS }
    if (nonPositionLogs.isNotEmpty()) {
        LogsCard(node, nonPositionLogs, onAction)
    }
}

@Composable
private fun EnvironmentCard(node: Node, metricsState: MetricsState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle(stringResource(Res.string.environment))
            Spacer(modifier = Modifier.height(12.dp))
            EnvironmentMetrics(node, metricsState.displayUnits, metricsState.isFahrenheit)
        }
    }
}

@Composable
private fun PowerCard(node: Node) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle(stringResource(Res.string.power))
            Spacer(modifier = Modifier.height(12.dp))
            PowerMetrics(node)
        }
    }
}

@Composable
private fun LogsCard(node: Node, logs: List<LogsType>, onAction: (NodeDetailAction) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            SectionTitle(stringResource(Res.string.logs), Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            logs.forEachIndexed { index, type ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                ListItem(text = stringResource(type.titleRes), leadingIcon = type.icon) {
                    onAction(NodeDetailAction.Navigate(type.routeFactory(node.num)))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}
