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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.SettingsItem
import org.meshtastic.core.ui.component.TitledCard
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
        TitledCard(stringResource(R.string.environment)) {}
        EnvironmentMetrics(node, metricsState.isFahrenheit, metricsState.displayUnits)
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (node.hasPowerMetrics) {
        TitledCard(stringResource(R.string.power)) {}
        PowerMetrics(node)
        Spacer(modifier = Modifier.height(8.dp))
    }

    val nonPositionLogs = availableLogs.filter { it != LogsType.NODE_MAP && it != LogsType.POSITIONS }

    if (nonPositionLogs.isNotEmpty()) {
        TitledCard(title = stringResource(id = R.string.logs)) {
            nonPositionLogs.forEach { type ->
                SettingsItem(text = stringResource(type.titleRes), leadingIcon = type.icon) {
                    onAction(NodeDetailAction.Navigate(type.route))
                }
            }
        }
    }
}
