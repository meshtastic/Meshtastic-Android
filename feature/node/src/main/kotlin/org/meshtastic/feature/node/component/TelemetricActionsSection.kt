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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StackedLineChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshtastic.core.strings.getString
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.logs
import org.meshtastic.core.strings.neighbor_info
import org.meshtastic.core.strings.request_air_quality_metrics
import org.meshtastic.core.strings.request_local_stats
import org.meshtastic.core.strings.request_telemetry
import org.meshtastic.core.strings.telemetry
import org.meshtastic.core.strings.userinfo
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.NodeDetailAction

private data class TelemetricFeature(
    val titleRes: StringResource,
    val icon: ImageVector,
    val requestAction: ((Node) -> NodeMenuAction)?,
    val logsType: LogsType? = null,
    val isVisible: (Node) -> Boolean = { true },
    val cooldownTimestamp: Long? = null,
    val cooldownDuration: Long = COOL_DOWN_TIME_MS,
    val content: @Composable ((Node) -> Unit)? = null,
    val hasContent: (Node) -> Boolean = { false },
)

@Composable
internal fun TelemetricActionsSection(
    node: Node,
    availableLogs: Set<LogsType>,
    lastTracerouteTime: Long?,
    lastRequestNeighborsTime: Long?,
    metricsState: MetricsState,
    onAction: (NodeDetailAction) -> Unit,
) {
    val features =
        rememberTelemetricFeatures(node, lastTracerouteTime, lastRequestNeighborsTime, metricsState)

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = stringResource(Res.string.telemetry),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        features
            .filter { it.isVisible(node) }
            .forEachIndexed { index, feature ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                FeatureRow(
                    node = node,
                    feature = feature,
                    hasLogs = feature.logsType != null && availableLogs.contains(feature.logsType),
                    onAction = onAction,
                )
            }
    }
}

@Suppress("LongMethod")
@Composable
private fun rememberTelemetricFeatures(
    node: Node,
    lastTracerouteTime: Long?,
    lastRequestNeighborsTime: Long?,
    metricsState: MetricsState,
): List<TelemetricFeature> =
    remember(node, lastTracerouteTime, lastRequestNeighborsTime, metricsState) {
        listOf(
            TelemetricFeature(
                titleRes = Res.string.userinfo,
                icon = Icons.Default.Person,
                requestAction = { NodeMenuAction.RequestUserInfo(it) },
            ),
            TelemetricFeature(
                titleRes = LogsType.TRACEROUTE.titleRes,
                icon = LogsType.TRACEROUTE.icon,
                requestAction = { NodeMenuAction.TraceRoute(it) },
                logsType = LogsType.TRACEROUTE,
                cooldownTimestamp = lastTracerouteTime,
            ),
            TelemetricFeature(
                titleRes = Res.string.neighbor_info,
                icon = Icons.Default.Groups,
                requestAction = { NodeMenuAction.RequestNeighborInfo(it) },
                isVisible = { it.capabilities.canRequestNeighborInfo },
                cooldownTimestamp = lastRequestNeighborsTime,
                cooldownDuration = REQUEST_NEIGHBORS_COOL_DOWN_TIME_MS,
            ),
            TelemetricFeature(
                titleRes = LogsType.DEVICE.titleRes,
                icon = LogsType.DEVICE.icon,
                requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.DEVICE) },
                logsType = LogsType.DEVICE,
            ),
            TelemetricFeature(
                titleRes = LogsType.ENVIRONMENT.titleRes,
                icon = LogsType.ENVIRONMENT.icon,
                requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.ENVIRONMENT) },
                logsType = LogsType.ENVIRONMENT,
                content = {
                    EnvironmentMetrics(it, metricsState.displayUnits, metricsState.isFahrenheit)
                },
                hasContent = { it.hasEnvironmentMetrics },
            ),
            TelemetricFeature(
                titleRes = Res.string.request_air_quality_metrics,
                icon = Icons.Default.Air,
                requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.AIR_QUALITY) },
            ),
            TelemetricFeature(
                titleRes = LogsType.POWER.titleRes,
                icon = LogsType.POWER.icon,
                requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.POWER) },
                logsType = LogsType.POWER,
                content = { PowerMetrics(it) },
                hasContent = { it.hasPowerMetrics },
            ),
            TelemetricFeature(
                titleRes = Res.string.request_local_stats,
                icon = Icons.Default.Speed,
                requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.LOCAL_STATS) },
            ),
            TelemetricFeature(
                titleRes = LogsType.HOST.titleRes,
                icon = LogsType.HOST.icon,
                requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.HOST) },
                logsType = LogsType.HOST,
            ),
            TelemetricFeature(
                titleRes = LogsType.PAX.titleRes,
                icon = LogsType.PAX.icon,
                requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.PAX) },
                logsType = LogsType.PAX,
            ),
        )
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod")
@Composable
private fun FeatureRow(
    node: Node,
    feature: TelemetricFeature,
    hasLogs: Boolean,
    onAction: (NodeDetailAction) -> Unit,
) {
    val showContent = feature.content != null && feature.hasContent(node)

    Column {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )

            Text(
                text = stringResource(feature.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            val description = getString(feature.titleRes)
            val logsDescription = description + " " + getString(Res.string.logs)
            val requestDescription = description + " " + getString(Res.string.request_telemetry)

            AnimatedVisibility(visible = hasLogs) {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above,
                        ),
                    tooltip = { PlainTooltip { Text(logsDescription) } },
                    state = rememberTooltipState(),
                ) {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                        onClick = {
                            feature.logsType?.let {
                                onAction(NodeDetailAction.Navigate(it.routeFactory(node.num)))
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.StackedLineChart,
                            contentDescription = logsDescription,
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (feature.requestAction != null) {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above,
                        ),
                    tooltip = { PlainTooltip { Text(requestDescription) } },
                    state = rememberTooltipState(),
                ) {
                    CooldownOutlinedIconButton(
                        onClick = {
                            val menuAction = feature.requestAction.invoke(node)
                            onAction(NodeDetailAction.HandleNodeMenuAction(menuAction))
                        },
                        cooldownTimestamp = feature.cooldownTimestamp,
                        cooldownDuration = feature.cooldownDuration,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = requestDescription,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (showContent) {
            Column(modifier = Modifier.padding(start = 56.dp, end = 20.dp, bottom = 8.dp)) {
                feature.content?.invoke(node)
            }
        }
    }
}
