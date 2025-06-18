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

package com.geeksville.mesh.ui.node

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.automirrored.twotone.Message
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChargingStation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SocialDistance
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.NoCell
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material.icons.twotone.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.DisplayUnits
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.database.entity.asDeviceVersion
import com.geeksville.mesh.model.DeviceHardware
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.MetricsState
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.isUnmessageableRole
import com.geeksville.mesh.navigation.NodeDetailRoutes
import com.geeksville.mesh.navigation.RadioConfigRoutes
import com.geeksville.mesh.navigation.Route
import com.geeksville.mesh.service.ServiceAction
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.preview.NodePreviewParameterProvider
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.common.theme.Green
import com.geeksville.mesh.ui.common.theme.Orange
import com.geeksville.mesh.ui.common.theme.Yellow
import com.geeksville.mesh.ui.node.components.NodeActionDialogs
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import com.geeksville.mesh.ui.radioconfig.NavCard
import com.geeksville.mesh.ui.sharing.SharedContactDialog
import com.geeksville.mesh.util.UnitConversions.calculateDewPoint
import com.geeksville.mesh.util.UnitConversions.toTempString
import com.geeksville.mesh.util.formatAgo
import com.geeksville.mesh.util.formatUptime
import com.geeksville.mesh.util.thenIf
import com.geeksville.mesh.util.toDistanceString
import com.geeksville.mesh.util.toSpeedString
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private enum class LogsType(
    val titleRes: Int,
    val icon: ImageVector,
    val route: Route
) {
    DEVICE(
        R.string.device_metrics_log,
        Icons.Default.ChargingStation,
        NodeDetailRoutes.DeviceMetrics
    ),
    NODE_MAP(R.string.node_map, Icons.Default.Map, NodeDetailRoutes.NodeMap),
    POSITIONS(R.string.position_log, Icons.Default.LocationOn, NodeDetailRoutes.PositionLog),
    ENVIRONMENT(
        R.string.env_metrics_log,
        Icons.Default.Thermostat,
        NodeDetailRoutes.EnvironmentMetrics
    ),
    SIGNAL(
        R.string.sig_metrics_log,
        Icons.Default.SignalCellularAlt,
        NodeDetailRoutes.SignalMetrics
    ),
    POWER(R.string.power_metrics_log, Icons.Default.Power, NodeDetailRoutes.PowerMetrics),
    TRACEROUTE(R.string.traceroute_log, Icons.Default.Route, NodeDetailRoutes.TracerouteLog),
    HOST(R.string.host_metrics_log, Icons.Default.Memory, NodeDetailRoutes.HostMetricsLog),
}

@Suppress("LongMethod")
@Composable
fun NodeDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: MetricsViewModel = hiltViewModel(),
    uiViewModel: UIViewModel = hiltViewModel(),
    navigateToMessages: (String) -> Unit,
    onNavigate: (Route) -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val environmentState by viewModel.environmentState.collectAsStateWithLifecycle()
    val lastTracerouteTime by uiViewModel.lastTraceRouteTime.collectAsStateWithLifecycle()

    /* The order is with respect to the enum above: LogsType */
    val availabilities = remember(key1 = state, key2 = environmentState) {
        booleanArrayOf(
            state.hasDeviceMetrics(),
            state.hasPositionLogs(),
            state.hasPositionLogs(),
            environmentState.hasEnvironmentMetrics(),
            state.hasSignalMetrics(),
            state.hasPowerMetrics(),
            state.hasTracerouteLogs(),
            state.hasHostMetrics(),
        )
    }
    val ourNode by uiViewModel.ourNodeInfo.collectAsStateWithLifecycle()

    if (state.node != null) {
        val node = state.node ?: return
        uiViewModel.setTitle(node.user.longName)
        var share by remember { mutableStateOf<Boolean>(false) }
        if (share) {
            SharedContactDialog(node) {
                share = false
            }
        }
        NodeDetailList(
            node = node,
            lastTracerouteTime = lastTracerouteTime,
            ourNode = ourNode,
            metricsState = state,
            onAction = { action ->
                when (action) {
                    is Route -> onNavigate(action)
                    is ServiceAction -> viewModel.onServiceAction(action)

                    is NodeMenuAction -> {
                        if (action is NodeMenuAction.DirectMessage) {
                            val hasPKC = uiViewModel.ourNodeInfo.value?.hasPKC == true
                            val channel =
                                if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
                            navigateToMessages("$channel${node.user.id}")
                        } else if (action is NodeMenuAction.Remove) {
                            uiViewModel.handleNodeMenuAction(action)
                            onNavigateUp()
                        } else {
                            uiViewModel.handleNodeMenuAction(action)
                        }
                    }

                    else -> debug("Unhandled action: $action")
                }
            },
            modifier = modifier,
            metricsAvailability = availabilities,
            onShared = {
                share = true
            }
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun NodeDetailList(
    modifier: Modifier = Modifier,
    node: Node,
    lastTracerouteTime: Long? = null,
    ourNode: Node?,
    metricsState: MetricsState,
    onAction: (Any) -> Unit = {},
    metricsAvailability: BooleanArray,
    onShared: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (metricsState.deviceHardware != null) {
            PreferenceCategory(stringResource(R.string.device)) {
                DeviceDetailsContent(metricsState)
            }
        }
        PreferenceCategory(stringResource(R.string.details)) {
            NodeDetailsContent(node, ourNode, metricsState.displayUnits)
        }

        DeviceActions(
            isLocal = metricsState.isLocal,
            lastTracerouteTime = lastTracerouteTime,
            node = node,
            onShared = onShared,
            onAction = onAction
        )

        if (node.hasEnvironmentMetrics) {
            PreferenceCategory(stringResource(R.string.environment))
            EnvironmentMetrics(node, metricsState.isFahrenheit)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (node.hasPowerMetrics) {
            PreferenceCategory(stringResource(R.string.power))
            PowerMetrics(node)
            Spacer(modifier = Modifier.height(8.dp))
        }

        /* Metric Logs Navigation */
        PreferenceCategory(stringResource(id = R.string.logs)) {
            for (type in LogsType.entries) {
                NavCard(
                    title = stringResource(type.titleRes),
                    icon = type.icon,
                    enabled = metricsAvailability[type.ordinal]
                ) {
                    onAction(type.route)
                }
            }
        }

        if (!metricsState.isManaged) {
            PreferenceCategory(stringResource(id = R.string.administration)) {
                NodeActionButton(
                    title = stringResource(id = R.string.request_metadata),
                    icon = Icons.Default.Memory,
                    enabled = true,
                    onClick = { onAction(ServiceAction.GetDeviceMetadata(node.num)) }
                )
                NavCard(
                    title = stringResource(id = R.string.remote_admin),
                    icon = Icons.Default.Settings,
                    enabled = metricsState.isLocal || node.metadata != null
                ) {
                    onAction(RadioConfigRoutes.RadioConfig(node.num))
                }
            }

            node.metadata?.firmwareVersion?.let { firmwareVersion ->
                val deviceVersion = DeviceVersion(firmwareVersion.substringBeforeLast("."))
                PreferenceCategory(stringResource(R.string.firmware)) {
                    val latestStableFirmware = metricsState.latestStableFirmware
                    val latestAlphaFirmware = metricsState.latestAlphaFirmware
                    NodeDetailRow(
                        label = "Installed",
                        icon = Icons.Default.Memory,
                        value = firmwareVersion.substringBeforeLast("."),
                        iconTint = if (deviceVersion < latestStableFirmware.asDeviceVersion()) {
                            MaterialTheme.colorScheme.error
                        } else if (deviceVersion == latestStableFirmware.asDeviceVersion()) {
                            Green
                        } else if (deviceVersion in
                            latestStableFirmware.asDeviceVersion()..latestAlphaFirmware.asDeviceVersion()
                        ) {
                            Yellow
                        } else if (deviceVersion >= latestAlphaFirmware.asDeviceVersion()) {
                            Orange
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    HorizontalDivider()

                    NodeDetailRow(
                        label = "Latest stable",
                        icon = Icons.Default.Memory,
                        value = latestStableFirmware.id.substringBeforeLast(".").replace("v", ""),
                        iconTint = Green
                    )

                    NodeDetailRow(
                        label = "Latest alpha",
                        icon = Icons.Default.Memory,
                        value = latestAlphaFirmware.id.substringBeforeLast(".").replace("v", ""),
                        iconTint = Yellow
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeDetailRow(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    value: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = iconTint
        )
        Text(label)
        Spacer(modifier = Modifier.weight(1f))
        Text(textAlign = TextAlign.End, text = value)
    }
}

@Suppress("LongMethod")
@Composable
private fun DeviceActions(
    isLocal: Boolean = false,
    node: Node,
    lastTracerouteTime: Long? = null,
    onShared: () -> Unit,
    onAction: (Any) -> Unit,
) {
    var displayFavoriteDialog by remember { mutableStateOf(false) }
    var displayIgnoreDialog by remember { mutableStateOf(false) }
    var displayRemoveDialog by remember { mutableStateOf(false) }
    val isUnmessageable = if (node.user.hasIsUnmessagable()) {
        node.user.isUnmessagable
    } else {
        // for older firmwares
        node.user.role?.isUnmessageableRole() == true
    }
    NodeActionDialogs(
        node = node,
        displayFavoriteDialog = displayFavoriteDialog,
        displayIgnoreDialog = displayIgnoreDialog,
        displayRemoveDialog = displayRemoveDialog,
        onDismissMenuRequest = {
            displayFavoriteDialog = false
            displayIgnoreDialog = false
            displayRemoveDialog = false
        },
        onAction = onAction,
    )
    PreferenceCategory(text = stringResource(R.string.actions)) {
        NodeActionButton(
            title = stringResource(id = R.string.share_contact),
            icon = Icons.Default.Share,
            enabled = true,
            onClick = onShared
        )

        if (!isLocal) {
            if (!isUnmessageable) {
                NodeActionButton(
                    title = stringResource(id = R.string.direct_message),
                    icon = Icons.AutoMirrored.TwoTone.Message,
                    enabled = true,
                    onClick = {
                        onAction(NodeMenuAction.DirectMessage(node))
                    }
                )
            }
            NodeActionButton(
                title = stringResource(id = R.string.exchange_position),
                icon = Icons.Default.LocationOn,
                enabled = true,
                onClick = { onAction(NodeMenuAction.RequestPosition(node)) }
            )
            NodeActionButton(
                title = stringResource(id = R.string.exchange_userinfo),
                icon = Icons.Default.Person,
                enabled = true,
                onClick = { onAction(NodeMenuAction.RequestUserInfo(node)) }
            )
            TracerouteActionButton(
                title = stringResource(id = R.string.traceroute),
                lastTracerouteTime = lastTracerouteTime,
                onClick = {
                    onAction(NodeMenuAction.TraceRoute(node))
                }
            )
            NodeActionSwitch(
                title = stringResource(R.string.favorite),
                icon = if (node.isFavorite) {
                    Icons.Default.Star
                } else {
                    Icons.Default.StarBorder
                },
                iconTint = if (node.isFavorite) {
                    Color.Yellow
                } else {
                    LocalContentColor.current
                },
                enabled = true,
                checked = node.isFavorite,
                onClick = { displayFavoriteDialog = true }
            )
            NodeActionSwitch(
                title = stringResource(R.string.ignore),
                icon = if (node.isIgnored) {
                    Icons.AutoMirrored.Outlined.VolumeMute
                } else {
                    Icons.AutoMirrored.Default.VolumeUp
                },
                enabled = true,
                checked = node.isIgnored,
                onClick = { displayIgnoreDialog = true }
            )
            NodeActionButton(
                title = stringResource(id = R.string.remove),
                icon = Icons.Default.Delete,
                enabled = true,
                onClick = { displayRemoveDialog = true }
            )
        }
    }
}

@Composable
private fun DeviceDetailsContent(
    state: MetricsState
) {
    val node = state.node ?: return
    val deviceHardware = state.deviceHardware ?: return
    val hwModelName = deviceHardware.displayName
    val isSupported = deviceHardware.activelySupported
    Box(
        modifier = Modifier
            .size(100.dp)
            .padding(4.dp)
            .clip(CircleShape)
            .background(
                color = Color(node.colors.second).copy(alpha = .5f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        DeviceHardwareImage(deviceHardware, Modifier.fillMaxSize())
    }
    NodeDetailRow(
        label = stringResource(R.string.hardware),
        icon = Icons.Default.Router,
        value = hwModelName
    )
    NodeDetailRow(
        label = if (isSupported) stringResource(R.string.supported) else "Supported by Community",
        icon = if (isSupported) Icons.TwoTone.Verified else ImageVector.vectorResource(R.drawable.unverified),
        value = "",
        iconTint = if (isSupported) Color.Green else Color.Red
    )
}

@Composable
fun DeviceHardwareImage(
    deviceHardware: DeviceHardware,
    modifier: Modifier = Modifier,
) {
    val hwImg =
        deviceHardware.images?.getOrNull(1) ?: deviceHardware.images?.getOrNull(0)
        ?: "unknown.svg"
    val imageUrl = "https://flasher.meshtastic.org/img/devices/$hwImg"
    val listener = object : ImageRequest.Listener {
        override fun onStart(request: ImageRequest) {
            super.onStart(request)
            debug("Image request started")
        }

        override fun onError(request: ImageRequest, result: ErrorResult) {
            super.onError(request, result)
            debug("Image request failed: ${result.throwable.message}")
        }

        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
            super.onSuccess(request, result)
            debug("Image request succeeded: ${result.dataSource.name}")
        }
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .listener(listener)
            .data(imageUrl)
            .build(),
        contentScale = ContentScale.Inside,
        contentDescription = deviceHardware.displayName,
        placeholder = painterResource(R.drawable.hw_unknown),
        error = painterResource(R.drawable.hw_unknown),
        fallback = painterResource(R.drawable.hw_unknown),
        modifier = modifier
            .padding(16.dp)
    )
}

@Suppress("LongMethod")
@Composable
private fun NodeDetailsContent(
    node: Node,
    ourNode: Node?,
    displayUnits: DisplayUnits,
) {
    if (node.mismatchKey) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.KeyOff,
                contentDescription = stringResource(id = R.string.encryption_error),
                tint = Color.Red,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(id = R.string.encryption_error),
                style = MaterialTheme.typography.titleLarge.copy(color = Color.Red),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.encryption_error_text),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
    NodeDetailRow(
        label = stringResource(R.string.long_name),
        icon = Icons.TwoTone.Person,
        value = node.user.longName.ifEmpty { "???" }
    )
    NodeDetailRow(
        label = stringResource(R.string.short_name),
        icon = Icons.Outlined.Person,
        value = node.user.shortName.ifEmpty { "???" }
    )
    NodeDetailRow(
        label = stringResource(R.string.node_number),
        icon = Icons.Default.Numbers,
        value = node.num.toUInt().toString()
    )
    NodeDetailRow(
        label = stringResource(R.string.user_id),
        icon = Icons.Default.Person,
        value = node.user.id
    )
    NodeDetailRow(
        label = stringResource(R.string.role),
        icon = Icons.Default.Work,
        value = node.user.role.name
    )
    val unmessageable = if (node.user.hasIsUnmessagable()) {
        node.user.isUnmessagable
    } else {
        node.user.role?.isUnmessageableRole() == true
    }
    if (unmessageable) {
        NodeDetailRow(
            label = stringResource(R.string.unmonitored_or_infrastructure),
            icon = Icons.Outlined.NoCell,
            value = ""
        )
    }
    if (node.deviceMetrics.uptimeSeconds > 0) {
        NodeDetailRow(
            label = stringResource(R.string.uptime),
            icon = Icons.Default.CheckCircle,
            value = formatUptime(node.deviceMetrics.uptimeSeconds)
        )
    }
    NodeDetailRow(
        label = stringResource(R.string.node_sort_last_heard),
        icon = Icons.Default.History,
        value = formatAgo(node.lastHeard)
    )
    val distance = ourNode?.distance(node)?.toDistanceString(displayUnits)
    if (node != ourNode && distance != null) {
        NodeDetailRow(
            label = stringResource(R.string.node_sort_distance),
            icon = Icons.Default.SocialDistance,
            value = distance
        )
        NodeDetailRow(
            label = stringResource(R.string.last_position_update),
            icon = Icons.Default.LocationOn,
            value = formatAgo(node.position.time)
        )
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    text: String,
    value: String,
    rotateIcon: Float = 0f,
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .width(100.dp)
            .height(100.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .width(100.dp)
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    modifier = Modifier
                        .size(24.dp)
                        .thenIf(rotateIcon != 0f) { rotate(rotateIcon) },
                )
                Text(
                    textAlign = TextAlign.Center,
                    text = text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun EnvironmentMetrics(
    node: Node,
    isFahrenheit: Boolean = false,
) = with(node.environmentMetrics) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (hasTemperature()) {
            InfoCard(
                icon = Icons.Default.Thermostat,
                text = stringResource(R.string.temperature),
                value = temperature.toTempString(isFahrenheit)
            )
        }
        if (hasRelativeHumidity()) {
            InfoCard(
                icon = Icons.Default.WaterDrop,
                text = stringResource(R.string.humidity),
                value = "%.0f%%".format(relativeHumidity)
            )
        }
        if (hasTemperature() && hasRelativeHumidity()) {
            val dewPoint = calculateDewPoint(temperature, relativeHumidity)
            InfoCard(
                icon = ImageVector.vectorResource(R.drawable.ic_outlined_dew_point_24),
                text = stringResource(R.string.dew_point),
                value = dewPoint.toTempString(isFahrenheit)
            )
        }
        if (hasBarometricPressure()) {
            InfoCard(
                icon = Icons.Default.Speed,
                text = stringResource(R.string.pressure),
                value = "%.0f hPa".format(barometricPressure)
            )
        }
        if (hasGasResistance()) {
            InfoCard(
                icon = Icons.Default.BlurOn,
                text = stringResource(R.string.gas_resistance),
                value = "%.0f MΩ".format(gasResistance)
            )
        }
        if (hasVoltage()) {
            InfoCard(
                icon = Icons.Default.Bolt,
                text = stringResource(R.string.voltage),
                value = "%.2fV".format(voltage)
            )
        }
        if (hasCurrent()) {
            InfoCard(
                icon = Icons.Default.Power,
                text = stringResource(R.string.current),
                value = "%.1fmA".format(current)
            )
        }
        if (hasIaq()) {
            InfoCard(
                icon = Icons.Default.Air,
                text = stringResource(R.string.iaq),
                value = iaq.toString()
            )
        }
        if (hasDistance()) {
            InfoCard(
                icon = Icons.Default.Height,
                text = stringResource(R.string.distance),
                value = "%.0f mm".format(distance)
            )
        }
        if (hasLux()) {
            InfoCard(
                icon = Icons.Default.LightMode,
                text = stringResource(R.string.lux),
                value = "%.0f lx".format(lux)
            )
        }
        if (hasWindSpeed()) {
            @Suppress("MagicNumber")
            val normalizedBearing = (windDirection % 360 + 360) % 360
            InfoCard(
                icon = Icons.Outlined.Navigation,
                text = stringResource(R.string.wind),
                value = windSpeed.toSpeedString(),
                rotateIcon = normalizedBearing.toFloat(),
            )
        }
        if (hasWeight()) {
            InfoCard(
                icon = Icons.Default.Scale,
                text = stringResource(R.string.weight),
                value = "%.2f kg".format(weight)
            )
        }
        if (hasRadiation()) {
            InfoCard(
                icon = ImageVector.vectorResource(R.drawable.ic_filled_radioactive_24),
                text = stringResource(R.string.radiation),
                value = "%.1f µR/h".format(radiation)
            )
        }
    }
}

@Composable
private fun PowerMetrics(node: Node) = with(node.powerMetrics) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (ch1Voltage != 0f) {
            Column {
                InfoCard(
                    icon = Icons.Default.Bolt,
                    text = stringResource(R.string.channel_1),
                    value = "%.2fV".format(ch1Voltage)
                )
                InfoCard(
                    icon = Icons.Default.Power,
                    text = stringResource(R.string.channel_1),
                    value = "%.1fmA".format(ch1Current)
                )
            }
        }
        if (ch2Voltage != 0f) {
            Column {
                InfoCard(
                    icon = Icons.Default.Bolt,
                    text = stringResource(R.string.channel_2),
                    value = "%.2fV".format(ch2Voltage)
                )
                InfoCard(
                    icon = Icons.Default.Power,
                    text = stringResource(R.string.channel_2),
                    value = "%.1fmA".format(ch2Current)
                )
            }
        }
        if (ch3Voltage != 0f) {
            Column {
                InfoCard(
                    icon = Icons.Default.Bolt,
                    text = stringResource(R.string.channel_3),
                    value = "%.2fV".format(ch3Voltage)
                )
                InfoCard(
                    icon = Icons.Default.Power,
                    text = stringResource(R.string.channel_3),
                    value = "%.1fmA".format(ch3Current)
                )
            }
        }
    }
}

private const val CoolDownTime = 30000f

@Composable
fun TracerouteActionButton(
    title: String,
    lastTracerouteTime: Long?,
    onClick: () -> Unit
) {
    var coolDownProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lastTracerouteTime) {
        while (true) {
            val timeSinceLast = (
                    System.currentTimeMillis() -
                            (lastTracerouteTime ?: 0)
                    )
            val progress = 1f - (timeSinceLast / CoolDownTime)
            coolDownProgress = progress.coerceIn(0f, 1f)
            if (progress <= 0f) {
                break
            }
            delay(10.milliseconds)
        }
    }
    Button(
        onClick = {
            onClick()
        },
        enabled = coolDownProgress <= 0f,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (coolDownProgress > 0f) {
                CircularProgressIndicator(
                    progress = { coolDownProgress },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    trackColor = ProgressIndicatorDefaults.circularDeterminateTrackColor,
                    strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = stringResource(R.string.traceroute),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
fun NodeActionButton(
    modifier: Modifier = Modifier,
    title: String,
    enabled: Boolean,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    onClick: () -> Unit
) {

    Button(
        onClick = {
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint ?: LocalContentColor.current,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun NodeActionSwitch(
    title: String,
    enabled: Boolean,
    checked: Boolean,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onClick() }
            ),
        shape = MaterialTheme.shapes.large,
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint ?: LocalContentColor.current,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NodeDetailsPreview(
    @PreviewParameter(NodePreviewParameterProvider::class)
    node: Node,
) {
    AppTheme {
        NodeDetailList(
            node = node,
            ourNode = node,
            lastTracerouteTime = null,
            metricsState = MetricsState.Empty,
            metricsAvailability = BooleanArray(LogsType.entries.size) { false },
        )
    }
}
