/*
 * Copyright (c) 2026 Meshtastic LLC
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_air
import org.meshtastic.core.resources.ic_person
import org.meshtastic.core.resources.ic_thermostat
import org.meshtastic.core.resources.logs
import org.meshtastic.core.resources.request_air_quality_metrics
import org.meshtastic.core.resources.request_telemetry
import org.meshtastic.core.resources.telemetry
import org.meshtastic.core.resources.userinfo
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.NodeDetailAction
import org.meshtastic.proto.Config

private data class TelemetricFeature(
    val titleRes: StringResource,
    val icon: DrawableResource,
    val requestAction: ((Node) -> NodeMenuAction)?,
    val logsType: LogsType? = null,
    val isVisible: (Node) -> Boolean = { true },
    val cooldownTimestamp: Long? = null,
    val cooldownDuration: Long = COOL_DOWN_TIME_MS,
    val content: @Composable ((Node, (NodeDetailAction) -> Unit) -> Unit)? = null,
    val hasContent: (Node) -> Boolean = { false },
)

@Composable
internal fun TelemetricActionsSection(
    node: Node,
    ourNode: Node?,
    availableLogs: Set<LogsType>,
    lastTracerouteTime: Long?,
    lastRequestNeighborsTime: Long?,
    displayUnits: Config.DisplayConfig.DisplayUnits,
    isFahrenheit: Boolean,
    onAction: (NodeDetailAction) -> Unit,
    isLocal: Boolean = false,
) {
    val features =
        rememberTelemetricFeatures(
            node,
            ourNode,
            lastTracerouteTime,
            lastRequestNeighborsTime,
            displayUnits,
            isFahrenheit,
            isLocal,
        )

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
    ourNode: Node?,
    lastTracerouteTime: Long?,
    lastRequestNeighborsTime: Long?,
    displayUnits: Config.DisplayConfig.DisplayUnits,
    isFahrenheit: Boolean,
    isLocal: Boolean,
): List<TelemetricFeature> =
    remember(node, ourNode, lastTracerouteTime, lastRequestNeighborsTime, displayUnits, isFahrenheit, isLocal) {
        listOf(
            TelemetricFeature(
                titleRes = Res.string.userinfo,
                icon = Res.drawable.ic_person,
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
                icon = Res.drawable.ic_thermostat,
                requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.ENVIRONMENT) },
                logsType = LogsType.ENVIRONMENT,
                content = { node, _ -> EnvironmentMetrics(node, displayUnits, isFahrenheit) },
                hasContent = { it.hasEnvironmentMetrics },
            ),
            TelemetricFeature(
                titleRes = Res.string.request_air_quality_metrics,
                icon = Res.drawable.ic_air,
                requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.AIR_QUALITY) },
            ),
            TelemetricFeature(
                titleRes = LogsType.POWER.titleRes,
                icon = LogsType.POWER.icon,
                requestAction = { NodeMenuAction.RequestTelemetry(it, TelemetryType.POWER) },
                logsType = LogsType.POWER,
                content = { node, _ -> PowerMetrics(node) },
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
            TelemetricFeature(
                titleRes = LogsType.POSITIONS.titleRes,
                icon = LogsType.POSITIONS.icon,
                requestAction = if (isLocal) null else { n -> NodeMenuAction.RequestPosition(n) },
                logsType = LogsType.POSITIONS,
                content = { node, action -> PositionInlineContent(node, ourNode, displayUnits, action) },
                hasContent = { it.latitude != 0.0 || it.longitude != 0.0 },
            ),
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
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
                Icon(
                    imageVector = vectorResource(feature.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
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
                                colors = IconButtonDefaults.filledTonalIconButtonColors(),
                                onClick = {
                                    feature.logsType?.let {
                                        onAction(NodeDetailAction.Navigate(it.routeFactory(node.num)))
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = vectorResource(feature.logsType?.icon ?: feature.icon),
                                    modifier = Modifier.size(24.dp),
                                    contentDescription = logsDescription,
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
                feature.content.invoke(node, onAction)
            }
        }
    }
}
