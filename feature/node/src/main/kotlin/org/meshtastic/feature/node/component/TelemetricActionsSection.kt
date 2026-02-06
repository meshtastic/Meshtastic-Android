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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.logs
import org.meshtastic.core.strings.request_air_quality_metrics
import org.meshtastic.core.strings.request_telemetry
import org.meshtastic.core.strings.telemetry
import org.meshtastic.core.strings.userinfo
import org.meshtastic.core.ui.icon.AirQuality
import org.meshtastic.core.ui.icon.LineAxis
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Person
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Temperature
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
    isLocal: Boolean = false,
) {
    val features = rememberTelemetricFeatures(node, lastTracerouteTime, lastRequestNeighborsTime, metricsState, isLocal)

    SectionCard(title = Res.string.telemetry) {
        features
            .filter { feature ->
                feature.isVisible(node) || (feature.logsType != null && availableLogs.contains(feature.logsType))
            }
            .forEachIndexed { index, feature ->
                if (index > 0) {
                    SectionDivider()
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
    isLocal: Boolean,
): List<TelemetricFeature> = remember(node, lastTracerouteTime, lastRequestNeighborsTime, metricsState, isLocal) {
    listOf(
        TelemetricFeature(
            titleRes = Res.string.userinfo,
            icon = MeshtasticIcons.Person,
            requestAction = { NodeMenuAction.RequestUserInfo(it) },
            isVisible = { !isLocal },
        ),
        TelemetricFeature(
            titleRes = LogsType.TRACEROUTE.titleRes,
            icon = LogsType.TRACEROUTE.icon,
            requestAction = { NodeMenuAction.TraceRoute(it) },
            logsType = LogsType.TRACEROUTE,
            cooldownTimestamp = lastTracerouteTime,
            isVisible = { !isLocal },
        ),
        TelemetricFeature(
            titleRes = LogsType.NEIGHBOR_INFO.titleRes,
            icon = LogsType.NEIGHBOR_INFO.icon,
            requestAction = { NodeMenuAction.RequestNeighborInfo(it) },
            logsType = LogsType.NEIGHBOR_INFO,
            isVisible = { it.capabilities.canRequestNeighborInfo },
            cooldownTimestamp = lastRequestNeighborsTime,
            cooldownDuration = REQUEST_NEIGHBORS_COOL_DOWN_TIME_MS,
        ),
        TelemetricFeature(
            titleRes = LogsType.SIGNAL.titleRes,
            icon = LogsType.SIGNAL.icon,
            requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.LOCAL_STATS) },
            logsType = LogsType.SIGNAL,
            isVisible = { !isLocal },
        ),
        TelemetricFeature(
            titleRes = LogsType.DEVICE.titleRes,
            icon = LogsType.DEVICE.icon,
            requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.DEVICE) },
            logsType = LogsType.DEVICE,
        ),
        TelemetricFeature(
            titleRes = LogsType.ENVIRONMENT.titleRes,
            icon = MeshtasticIcons.Temperature,
            requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.ENVIRONMENT) },
            logsType = LogsType.ENVIRONMENT,
            content = { EnvironmentMetrics(it, metricsState.displayUnits, metricsState.isFahrenheit) },
            hasContent = { it.hasEnvironmentMetrics },
        ),
        TelemetricFeature(
            titleRes = Res.string.request_air_quality_metrics,
            icon = MeshtasticIcons.AirQuality,
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
private fun FeatureRow(node: Node, feature: TelemetricFeature, hasLogs: Boolean, onAction: (NodeDetailAction) -> Unit) {
    val showContent = feature.content != null && feature.hasContent(node)
    val description = stringResource(feature.titleRes)
    val logsDescription = description + " " + stringResource(Res.string.logs)
    val requestDescription = description + " " + stringResource(Res.string.request_telemetry)

    Column {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(imageVector = feature.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            headlineContent = {
                Text(
                    text = stringResource(feature.titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = hasLogs) {
                        TooltipBox(
                            positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
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
                                    MeshtasticIcons.LineAxis,
                                    contentDescription = logsDescription,
                                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    if (feature.requestAction != null) {
                        if (hasLogs) Spacer(modifier = Modifier.width(8.dp))
                        TooltipBox(
                            positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
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
                                    imageVector = MeshtasticIcons.Refresh,
                                    contentDescription = requestDescription,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            },
        )

        if (showContent) {
            Column(modifier = Modifier.padding(start = 56.dp, end = 20.dp, bottom = 12.dp)) {
                feature.content?.invoke(node)
            }
        }
    }
}
