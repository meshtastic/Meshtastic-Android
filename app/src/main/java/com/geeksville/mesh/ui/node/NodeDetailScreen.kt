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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material.icons.twotone.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.model.MetricsState
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.ui.sharing.SharedContactDialog
import com.geeksville.mesh.util.thenIf
import com.mikepenz.markdown.m3.Markdown
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.isUnmessageableRole
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.model.util.UnitConversions
import org.meshtastic.core.model.util.UnitConversions.toTempString
import org.meshtastic.core.model.util.formatAgo
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.model.util.toSmallDistanceString
import org.meshtastic.core.model.util.toSpeedString
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SettingsItem
import org.meshtastic.core.ui.component.SettingsItemDetail
import org.meshtastic.core.ui.component.SettingsItemSwitch
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.feature.node.component.NodeActionDialogs
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.component.TracerouteButton
import org.meshtastic.feature.node.detail.NodeDetailViewModel
import timber.log.Timber

private data class VectorMetricInfo(
    @StringRes val label: Int,
    val value: String,
    val icon: ImageVector,
    val rotateIcon: Float = 0f,
)

private data class DrawableMetricInfo(
    @StringRes val label: Int,
    val value: String,
    @DrawableRes val icon: Int,
    val rotateIcon: Float = 0f,
)

@Suppress("LongMethod")
@Composable
fun NodeDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: MetricsViewModel = hiltViewModel(),
    nodeDetailViewModel: NodeDetailViewModel = hiltViewModel(),
    navigateToMessages: (String) -> Unit = {},
    onNavigate: (Route) -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val environmentState by viewModel.environmentState.collectAsStateWithLifecycle()
    val lastTracerouteTime by nodeDetailViewModel.lastTraceRouteTime.collectAsStateWithLifecycle()
    val ourNode by nodeDetailViewModel.ourNodeInfo.collectAsStateWithLifecycle()

    val availableLogs by
        remember(state, environmentState) {
            derivedStateOf {
                buildSet {
                    if (state.hasDeviceMetrics()) add(LogsType.DEVICE)
                    if (state.hasPositionLogs()) {
                        add(LogsType.NODE_MAP)
                        add(LogsType.POSITIONS)
                    }
                    if (environmentState.hasEnvironmentMetrics()) add(LogsType.ENVIRONMENT)
                    if (state.hasSignalMetrics()) add(LogsType.SIGNAL)
                    if (state.hasPowerMetrics()) add(LogsType.POWER)
                    if (state.hasTracerouteLogs()) add(LogsType.TRACEROUTE)
                    if (state.hasHostMetrics()) add(LogsType.HOST)
                    if (state.hasPaxMetrics()) add(LogsType.PAX)
                }
            }
        }

    val node = state.node

    @Suppress("ModifierNotUsedAtRoot")
    Scaffold(
        topBar = {
            MainAppBar(
                title = node?.user?.longName ?: "",
                ourNode = ourNode,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        if (node != null) {
            @Suppress("ViewModelForwarding")
            NodeDetailContent(
                node = node,
                ourNode = ourNode,
                metricsState = state,
                lastTracerouteTime = lastTracerouteTime,
                availableLogs = availableLogs,
                onAction = { action ->
                    handleNodeAction(
                        action = action,
                        ourNode = ourNode,
                        node = node,
                        navigateToMessages = navigateToMessages,
                        onNavigateUp = onNavigateUp,
                        onNavigate = onNavigate,
                        viewModel = viewModel,
                        handleNodeMenuAction = { nodeDetailViewModel.handleNodeMenuAction(it) },
                    )
                },
                modifier = modifier.padding(paddingValues),
                onSaveNotes = { num, notes -> nodeDetailViewModel.setNodeNotes(num, notes) },
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

private fun handleNodeAction(
    action: NodeDetailAction,
    ourNode: Node?,
    node: Node,
    navigateToMessages: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigate: (Route) -> Unit,
    viewModel: MetricsViewModel,
    handleNodeMenuAction: (NodeMenuAction) -> Unit,
) {
    when (action) {
        is NodeDetailAction.Navigate -> onNavigate(action.route)
        is NodeDetailAction.TriggerServiceAction -> viewModel.onServiceAction(action.action)
        is NodeDetailAction.HandleNodeMenuAction -> {
            when (val menuAction = action.action) {
                is NodeMenuAction.DirectMessage -> {
                    val hasPKC = ourNode?.hasPKC == true
                    val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
                    navigateToMessages("$channel${node.user.id}")
                }

                is NodeMenuAction.Remove -> {
                    handleNodeMenuAction(menuAction)
                    onNavigateUp()
                }

                else -> handleNodeMenuAction(menuAction)
            }
        }

        is NodeDetailAction.ShareContact -> {
            /* Handled in NodeDetailContent */
        }
    }
}

sealed interface NodeDetailAction {
    data class Navigate(val route: Route) : NodeDetailAction

    data class TriggerServiceAction(val action: ServiceAction) : NodeDetailAction

    data class HandleNodeMenuAction(val action: NodeMenuAction) : NodeDetailAction

    data object ShareContact : NodeDetailAction
}

val Node.isEffectivelyUnmessageable: Boolean
    get() =
        if (user.hasIsUnmessagable()) {
            user.isUnmessagable
        } else {
            user.role?.isUnmessageableRole() == true
        }

private enum class LogsType(@StringRes val titleRes: Int, val icon: ImageVector, val route: Route) {
    DEVICE(R.string.device_metrics_log, Icons.Default.ChargingStation, NodeDetailRoutes.DeviceMetrics),
    NODE_MAP(R.string.node_map, Icons.Default.Map, NodeDetailRoutes.NodeMap),
    POSITIONS(R.string.position_log, Icons.Default.LocationOn, NodeDetailRoutes.PositionLog),
    ENVIRONMENT(R.string.env_metrics_log, Icons.Default.Thermostat, NodeDetailRoutes.EnvironmentMetrics),
    SIGNAL(R.string.sig_metrics_log, Icons.Default.SignalCellularAlt, NodeDetailRoutes.SignalMetrics),
    POWER(R.string.power_metrics_log, Icons.Default.Power, NodeDetailRoutes.PowerMetrics),
    TRACEROUTE(R.string.traceroute_log, Icons.Default.Route, NodeDetailRoutes.TracerouteLog),
    HOST(R.string.host_metrics_log, Icons.Default.Memory, NodeDetailRoutes.HostMetricsLog),
    PAX(R.string.pax_metrics_log, Icons.Default.People, NodeDetailRoutes.PaxMetrics),
}

@Composable
private fun NodeDetailContent(
    node: Node,
    ourNode: Node?,
    metricsState: MetricsState,
    lastTracerouteTime: Long?,
    availableLogs: Set<LogsType>,
    onAction: (NodeDetailAction) -> Unit,
    modifier: Modifier = Modifier,
    onSaveNotes: (nodeNum: Int, notes: String) -> Unit,
) {
    var showShareDialog by remember { mutableStateOf(false) }
    if (showShareDialog) {
        SharedContactDialog(node) { showShareDialog = false }
    }

    NodeDetailList(
        node = node,
        lastTracerouteTime = lastTracerouteTime,
        ourNode = ourNode,
        metricsState = metricsState,
        onAction = { action ->
            if (action is NodeDetailAction.ShareContact) {
                showShareDialog = true
            } else {
                onAction(action)
            }
        },
        modifier = modifier,
        availableLogs = availableLogs,
        onSaveNotes = onSaveNotes,
    )
}

@Composable
private fun notesSection(node: Node, onSaveNotes: (Int, String) -> Unit) {
    if (node.isFavorite) {
        TitledCard(title = stringResource(R.string.notes)) {
            val originalNotes = node.notes
            var notes by remember(node.notes) { mutableStateOf(node.notes) }
            val edited = notes.trim() != originalNotes.trim()
            val keyboardController = LocalSoftwareKeyboardController.current

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text(stringResource(id = R.string.add_a_note)) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            onSaveNotes(node.num, notes.trim())
                            keyboardController?.hide()
                        },
                        enabled = edited,
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = stringResource(id = R.string.save))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                KeyboardActions(
                    onDone = {
                        onSaveNotes(node.num, notes.trim())
                        keyboardController?.hide()
                    },
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDetailList(
    modifier: Modifier = Modifier,
    node: Node,
    lastTracerouteTime: Long?,
    ourNode: Node?,
    metricsState: MetricsState,
    onAction: (NodeDetailAction) -> Unit,
    availableLogs: Set<LogsType>,
    onSaveNotes: (Int, String) -> Unit,
) {
    var showFirmwareSheet by remember { mutableStateOf(false) }
    var selectedFirmware by remember { mutableStateOf<FirmwareRelease?>(null) }

    if (showFirmwareSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(onDismissRequest = { showFirmwareSheet = false }, sheetState = sheetState) {
            selectedFirmware?.let { FirmwareReleaseSheetContent(firmwareRelease = it) }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (metricsState.deviceHardware != null) {
            TitledCard(title = stringResource(R.string.device)) {
                Spacer(modifier = Modifier.height(16.dp))
                DeviceDetailsContent(metricsState)
            }
        }

        TitledCard(title = stringResource(R.string.details)) {
            NodeDetailsContent(node, ourNode, metricsState.displayUnits)
        }
        notesSection(node = node, onSaveNotes = onSaveNotes)

        DeviceActions(
            isLocal = metricsState.isLocal,
            lastTracerouteTime = lastTracerouteTime,
            node = node,
            onAction = onAction,
        )
        MetricsSection(node, metricsState, availableLogs, onAction)

        if (!metricsState.isManaged) {
            AdministrationSection(
                node = node,
                metricsState = metricsState,
                onAction = onAction,
                onFirmwareSelected = { firmware ->
                    selectedFirmware = firmware
                    showFirmwareSheet = true
                },
            )
        }
    }
}

@Composable
private fun MetricsSection(
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

    if (availableLogs.isNotEmpty()) {
        TitledCard(title = stringResource(id = R.string.logs)) {
            LogsType.entries.forEach { type ->
                if (availableLogs.contains(type)) {
                    SettingsItem(text = stringResource(type.titleRes), leadingIcon = type.icon) {
                        onAction(NodeDetailAction.Navigate(type.route))
                    }
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun AdministrationSection(
    node: Node,
    metricsState: MetricsState,
    onAction: (NodeDetailAction) -> Unit,
    onFirmwareSelected: (FirmwareRelease) -> Unit,
) {
    TitledCard(stringResource(id = R.string.administration)) {
        SettingsItem(
            text = stringResource(id = R.string.request_metadata),
            leadingIcon = Icons.Default.Memory,
            trailingContent = {},
            onClick = { onAction(NodeDetailAction.TriggerServiceAction(ServiceAction.GetDeviceMetadata(node.num))) },
        )
        SettingsItem(
            text = stringResource(id = R.string.remote_admin),
            leadingIcon = Icons.Default.Settings,
            enabled = metricsState.isLocal || node.metadata != null,
        ) {
            onAction(NodeDetailAction.Navigate(SettingsRoutes.Settings(node.num)))
        }
    }

    TitledCard(stringResource(R.string.firmware)) {
        if (metricsState.isLocal) {
            val firmwareEdition = metricsState.firmwareEdition
            firmwareEdition?.let {
                val icon =
                    when (it) {
                        MeshProtos.FirmwareEdition.VANILLA -> Icons.Default.Icecream
                        else -> Icons.Default.ForkLeft
                    }

                SettingsItemDetail(
                    text = stringResource(R.string.firmware_edition),
                    icon = icon,
                    supportingText = it.name,
                )
            }
        }
        node.metadata?.firmwareVersion?.let { firmwareVersion ->
            val latestStable = metricsState.latestStableFirmware
            val latestAlpha = metricsState.latestAlphaFirmware

            val deviceVersion = DeviceVersion(firmwareVersion.substringBeforeLast("."))
            val statusColor = deviceVersion.determineFirmwareStatusColor(latestStable, latestAlpha)

            SettingsItemDetail(
                text = stringResource(R.string.installed_firmware_version),
                icon = Icons.Default.Memory,
                supportingText = firmwareVersion.substringBeforeLast("."),
                iconTint = statusColor,
            )
            HorizontalDivider()
            SettingsItemDetail(
                text = stringResource(R.string.latest_stable_firmware),
                icon = Icons.Default.Memory,
                supportingText = latestStable.id.substringBeforeLast(".").replace("v", ""),
                iconTint = colorScheme.StatusGreen,
                onClick = { onFirmwareSelected(latestStable) },
            )
            SettingsItemDetail(
                text = stringResource(R.string.latest_alpha_firmware),
                icon = Icons.Default.Memory,
                supportingText = latestAlpha.id.substringBeforeLast(".").replace("v", ""),
                iconTint = colorScheme.StatusYellow,
                onClick = { onFirmwareSelected(latestAlpha) },
            )
        }
    }
}

@Composable
private fun DeviceVersion.determineFirmwareStatusColor(
    latestStable: FirmwareRelease,
    latestAlpha: FirmwareRelease,
): Color {
    val stableVersion = latestStable.asDeviceVersion()
    val alphaVersion = latestAlpha.asDeviceVersion()
    return when {
        this < stableVersion -> colorScheme.StatusRed
        this == stableVersion -> colorScheme.StatusGreen
        this in stableVersion..alphaVersion -> colorScheme.StatusYellow
        this > alphaVersion -> colorScheme.StatusOrange
        else -> colorScheme.onSurface
    }
}

@Composable
private fun FirmwareReleaseSheetContent(firmwareRelease: FirmwareRelease) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = firmwareRelease.title, style = MaterialTheme.typography.titleLarge)
        Text(text = "Version: ${firmwareRelease.id}", style = MaterialTheme.typography.bodyMedium)
        Markdown(modifier = Modifier.padding(8.dp), content = firmwareRelease.releaseNotes)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, firmwareRelease.pageUrl.toUri())
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.error_no_app_to_handle_link, Toast.LENGTH_LONG).show()
                        Timber.e(e)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Default.Link, contentDescription = stringResource(id = R.string.view_release))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.view_release))
            }
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, firmwareRelease.zipUrl.toUri())
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.error_no_app_to_handle_link, Toast.LENGTH_LONG).show()
                        Timber.e(e)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = stringResource(id = R.string.download))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.download))
            }
        }
    }
}

@Composable
private fun DeviceActions(
    isLocal: Boolean = false,
    node: Node,
    lastTracerouteTime: Long?,
    onAction: (NodeDetailAction) -> Unit,
) {
    var displayFavoriteDialog by remember { mutableStateOf(false) }
    var displayIgnoreDialog by remember { mutableStateOf(false) }
    var displayRemoveDialog by remember { mutableStateOf(false) }

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
        onConfirmFavorite = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Favorite(it))) },
        onConfirmIgnore = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Ignore(it))) },
        onConfirmRemove = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Remove(it))) },
    )
    TitledCard(title = stringResource(R.string.actions)) {
        SettingsItem(
            text = stringResource(id = R.string.share_contact),
            leadingIcon = Icons.Rounded.QrCode2,
            trailingContent = {},
            onClick = { onAction(NodeDetailAction.ShareContact) },
        )
        if (!isLocal) {
            RemoteDeviceActions(node = node, lastTracerouteTime = lastTracerouteTime, onAction = onAction)
        }
        SettingsItemSwitch(
            text = stringResource(R.string.favorite),
            leadingIcon = if (node.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            leadingIconTint = if (node.isFavorite) Color.Yellow else LocalContentColor.current,
            checked = node.isFavorite,
            onClick = { displayFavoriteDialog = true },
        )
        SettingsItemSwitch(
            text = stringResource(R.string.ignore),
            leadingIcon =
            if (node.isIgnored) Icons.AutoMirrored.Outlined.VolumeMute else Icons.AutoMirrored.Default.VolumeUp,
            checked = node.isIgnored,
            onClick = { displayIgnoreDialog = true },
        )
        SettingsItem(
            text = stringResource(id = R.string.remove),
            leadingIcon = Icons.Rounded.Delete,
            trailingContent = {},
            onClick = { displayRemoveDialog = true },
        )
    }
}

@Composable
private fun RemoteDeviceActions(node: Node, lastTracerouteTime: Long?, onAction: (NodeDetailAction) -> Unit) {
    if (!node.isEffectivelyUnmessageable) {
        SettingsItem(
            text = stringResource(id = R.string.direct_message),
            leadingIcon = Icons.AutoMirrored.TwoTone.Message,
            trailingContent = {},
            onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.DirectMessage(node))) },
        )
    }
    SettingsItem(
        text = stringResource(id = R.string.exchange_position),
        leadingIcon = Icons.Default.LocationOn,
        trailingContent = {},
        onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.RequestPosition(node))) },
    )
    SettingsItem(
        text = stringResource(id = R.string.exchange_userinfo),
        leadingIcon = Icons.Default.Person,
        trailingContent = {},
        onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.RequestUserInfo(node))) },
    )
    TracerouteButton(
        lastTracerouteTime = lastTracerouteTime,
        onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.TraceRoute(node))) },
    )
}

@Composable
private fun ColumnScope.DeviceDetailsContent(state: MetricsState) {
    val node = state.node ?: return
    val deviceHardware = state.deviceHardware ?: return
    val hwModelName = deviceHardware.displayName
    val isSupported = deviceHardware.activelySupported
    Box(
        modifier =
        Modifier.align(Alignment.CenterHorizontally)
            .size(100.dp)
            .clip(CircleShape)
            .background(color = Color(node.colors.second).copy(alpha = .5f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DeviceHardwareImage(deviceHardware, Modifier.fillMaxSize())
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsItemDetail(
        text = stringResource(R.string.hardware),
        icon = Icons.Default.Router,
        supportingText = hwModelName,
    )
    SettingsItemDetail(
        text =
        if (isSupported) {
            stringResource(R.string.supported)
        } else {
            stringResource(R.string.supported_by_community)
        },
        icon =
        if (isSupported) {
            Icons.TwoTone.Verified
        } else {
            ImageVector.vectorResource(com.geeksville.mesh.R.drawable.unverified)
        },
        supportingText = null,
        iconTint = if (isSupported) colorScheme.StatusGreen else colorScheme.StatusRed,
    )
}

@Composable
fun DeviceHardwareImage(deviceHardware: DeviceHardware, modifier: Modifier = Modifier) {
    val hwImg = deviceHardware.images?.getOrNull(1) ?: deviceHardware.images?.getOrNull(0) ?: "unknown.svg"
    val imageUrl = "https://flasher.meshtastic.org/img/devices/$hwImg"
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(imageUrl).build(),
        contentScale = ContentScale.Inside,
        contentDescription = deviceHardware.displayName,
        placeholder = painterResource(com.geeksville.mesh.R.drawable.hw_unknown),
        error = painterResource(com.geeksville.mesh.R.drawable.hw_unknown),
        fallback = painterResource(com.geeksville.mesh.R.drawable.hw_unknown),
        modifier = modifier.padding(16.dp),
    )
}

@Composable
private fun NodeDetailsContent(
    node: Node,
    ourNode: Node?,
    displayUnits: ConfigProtos.Config.DisplayConfig.DisplayUnits,
) {
    if (node.mismatchKey) {
        EncryptionErrorContent()
    }
    MainNodeDetails(node, ourNode, displayUnits)
}

@Composable
private fun EncryptionErrorContent() {
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

@Composable
private fun MainNodeDetails(node: Node, ourNode: Node?, displayUnits: ConfigProtos.Config.DisplayConfig.DisplayUnits) {
    SettingsItemDetail(
        text = stringResource(R.string.long_name),
        icon = Icons.TwoTone.Person,
        supportingText = node.user.longName.ifEmpty { "???" },
    )
    SettingsItemDetail(
        text = stringResource(R.string.short_name),
        icon = Icons.Outlined.Person,
        supportingText = node.user.shortName.ifEmpty { "???" },
    )
    SettingsItemDetail(
        text = stringResource(R.string.node_number),
        icon = Icons.Default.Numbers,
        supportingText = node.num.toUInt().toString(),
    )
    SettingsItemDetail(
        text = stringResource(R.string.user_id),
        icon = Icons.Default.Person,
        supportingText = node.user.id,
    )
    SettingsItemDetail(
        text = stringResource(R.string.role),
        icon = Icons.Default.Work,
        supportingText = node.user.role.name,
    )
    if (node.isEffectivelyUnmessageable) {
        SettingsItemDetail(text = stringResource(R.string.unmonitored_or_infrastructure), icon = Icons.Outlined.NoCell, supportingText = null)
    }
    if (node.deviceMetrics.uptimeSeconds > 0) {
        SettingsItemDetail(
            text = stringResource(R.string.uptime),
            icon = Icons.Default.CheckCircle,
            supportingText = formatUptime(node.deviceMetrics.uptimeSeconds),
        )
    }
    SettingsItemDetail(
        text = stringResource(R.string.node_sort_last_heard),
        icon = Icons.Default.History,
        supportingText = formatAgo(node.lastHeard),
    )
    val distance = ourNode?.distance(node)?.takeIf { it > 0 }?.toDistanceString(displayUnits)
    if (distance != null && distance.isNotEmpty()) {
        SettingsItemDetail(
            text = stringResource(R.string.node_sort_distance),
            icon = Icons.Default.SocialDistance,
            supportingText = distance,
        )
    }

    SettingsItemDetail(
        text = stringResource(R.string.last_position_update),
        icon = Icons.Default.LocationOn,
        supportingText = formatAgo(node.position.time),
    )
}

@Composable
private fun InfoCard(icon: ImageVector, text: String, value: String, rotateIcon: Float = 0f) {
    Card(modifier = Modifier.padding(4.dp).width(100.dp).height(100.dp)) {
        Box(modifier = Modifier.padding(4.dp).width(100.dp).height(100.dp), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    modifier = Modifier.size(24.dp).thenIf(rotateIcon != 0f) { rotate(rotateIcon) },
                )
                Text(
                    textAlign = TextAlign.Center,
                    text = text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
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

@Composable
private fun DrawableInfoCard(@DrawableRes iconRes: Int, text: String, value: String, rotateIcon: Float = 0f) {
    Card(modifier = Modifier.padding(4.dp).width(100.dp).height(100.dp)) {
        Box(modifier = Modifier.padding(4.dp).width(100.dp).height(100.dp), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = text,
                    modifier = Modifier.size(24.dp).thenIf(rotateIcon != 0f) { rotate(rotateIcon) },
                )
                Text(
                    textAlign = TextAlign.Center,
                    text = text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
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

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
private fun EnvironmentMetrics(
    node: Node,
    isFahrenheit: Boolean = false,
    displayUnits: ConfigProtos.Config.DisplayConfig.DisplayUnits,
) {
    val vectorMetrics =
        remember(node.environmentMetrics, isFahrenheit, displayUnits) {
            buildList {
                with(node.environmentMetrics) {
                    if (hasTemperature()) {
                        add(
                            VectorMetricInfo(
                                R.string.temperature,
                                temperature.toTempString(isFahrenheit),
                                Icons.Default.Thermostat,
                            ),
                        )
                    }
                    if (hasRelativeHumidity()) {
                        add(
                            VectorMetricInfo(
                                R.string.humidity,
                                "%.0f%%".format(relativeHumidity),
                                Icons.Default.WaterDrop,
                            ),
                        )
                    }
                    if (hasBarometricPressure()) {
                        add(
                            VectorMetricInfo(
                                R.string.pressure,
                                "%.0f hPa".format(barometricPressure),
                                Icons.Default.Speed,
                            ),
                        )
                    }
                    if (hasGasResistance()) {
                        add(
                            VectorMetricInfo(
                                R.string.gas_resistance,
                                "%.0f MΩ".format(gasResistance),
                                Icons.Default.BlurOn,
                            ),
                        )
                    }
                    if (hasVoltage()) {
                        add(VectorMetricInfo(R.string.voltage, "%.2fV".format(voltage), Icons.Default.Bolt))
                    }
                    if (hasCurrent()) {
                        add(VectorMetricInfo(R.string.current, "%.1fmA".format(current), Icons.Default.Power))
                    }
                    if (hasIaq()) add(VectorMetricInfo(R.string.iaq, iaq.toString(), Icons.Default.Air))
                    if (hasDistance()) {
                        add(
                            VectorMetricInfo(
                                R.string.distance,
                                distance.toSmallDistanceString(displayUnits),
                                Icons.Default.Height,
                            ),
                        )
                    }
                    if (hasLux()) add(VectorMetricInfo(R.string.lux, "%.0f lx".format(lux), Icons.Default.LightMode))
                    if (hasUvLux()) {
                        add(VectorMetricInfo(R.string.uv_lux, "%.0f lx".format(uvLux), Icons.Default.LightMode))
                    }
                    if (hasWindSpeed()) {
                        @Suppress("MagicNumber")
                        val normalizedBearing = (windDirection + 180) % 360
                        add(
                            VectorMetricInfo(
                                R.string.wind,
                                windSpeed.toSpeedString(displayUnits),
                                Icons.Outlined.Navigation,
                                normalizedBearing.toFloat(),
                            ),
                        )
                    }
                    if (hasWeight()) {
                        add(VectorMetricInfo(R.string.weight, "%.2f kg".format(weight), Icons.Default.Scale))
                    }
                }
            }
        }
    val drawableMetrics =
        remember(node.environmentMetrics, isFahrenheit) {
            buildList {
                with(node.environmentMetrics) {
                    if (hasTemperature() && hasRelativeHumidity()) {
                        val dewPoint = UnitConversions.calculateDewPoint(temperature, relativeHumidity)
                        add(
                            DrawableMetricInfo(
                                R.string.dew_point,
                                dewPoint.toTempString(isFahrenheit),
                                com.geeksville.mesh.R.drawable.ic_outlined_dew_point_24,
                            ),
                        )
                    }
                    if (hasSoilTemperature()) {
                        add(
                            DrawableMetricInfo(
                                R.string.soil_temperature,
                                soilTemperature.toTempString(isFahrenheit),
                                com.geeksville.mesh.R.drawable.soil_temperature,
                            ),
                        )
                    }
                    if (hasSoilMoisture()) {
                        add(
                            DrawableMetricInfo(
                                R.string.soil_moisture,
                                "%d%%".format(soilMoisture),
                                com.geeksville.mesh.R.drawable.soil_moisture,
                            ),
                        )
                    }
                    if (hasRadiation()) {
                        add(
                            DrawableMetricInfo(
                                R.string.radiation,
                                "%.1f µR/h".format(radiation),
                                com.geeksville.mesh.R.drawable.ic_filled_radioactive_24,
                            ),
                        )
                    }
                }
            }
        }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        vectorMetrics.forEach { metric ->
            InfoCard(
                icon = metric.icon,
                text = stringResource(metric.label),
                value = metric.value,
                rotateIcon = metric.rotateIcon,
            )
        }
        drawableMetrics.forEach { metric ->
            DrawableInfoCard(
                iconRes = metric.icon,
                text = stringResource(metric.label),
                value = metric.value,
                rotateIcon = metric.rotateIcon,
            )
        }
    }
}

/**
 * Displays environmental metrics for a node, including temperature, humidity, pressure, and other sensor data.
 *
 * WARNING: All metrics must be added in pairs (e.g., voltage and current for each channel) due to the display logic,
 * which arranges metrics in columns of two. If an odd number of metrics is provided, the UI may not display as
 * intended.
 */
@Composable
private fun PowerMetrics(node: Node) {
    val metrics =
        remember(node.powerMetrics) {
            buildList {
                with(node.powerMetrics) {
                    if (ch1Voltage != 0f) {
                        add(VectorMetricInfo(R.string.channel_1, "%.2fV".format(ch1Voltage), Icons.Default.Bolt))
                        add(VectorMetricInfo(R.string.channel_1, "%.1fmA".format(ch1Current), Icons.Default.Power))
                    }
                    if (ch2Voltage != 0f) {
                        add(VectorMetricInfo(R.string.channel_2, "%.2fV".format(ch2Voltage), Icons.Default.Bolt))
                        add(VectorMetricInfo(R.string.channel_2, "%.1fmA".format(ch2Current), Icons.Default.Power))
                    }
                    if (ch3Voltage != 0f) {
                        add(VectorMetricInfo(R.string.channel_3, "%.2fV".format(ch3Voltage), Icons.Default.Bolt))
                        add(VectorMetricInfo(R.string.channel_3, "%.1fmA".format(ch3Current), Icons.Default.Power))
                    }
                }
            }
        }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        metrics.chunked(2).forEach { rowMetrics ->
            Column {
                rowMetrics.forEach { metric ->
                    InfoCard(icon = metric.icon, text = stringResource(metric.label), value = metric.value)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NodeDetailsPreview(@PreviewParameter(NodePreviewParameterProvider::class) node: Node) {
    AppTheme {
        NodeDetailList(
            node = node,
            ourNode = node,
            lastTracerouteTime = null,
            metricsState = MetricsState.Empty,
            availableLogs = emptySet(),
            onAction = {},
            onSaveNotes = { _, _ -> },
        )
    }
}

@Preview(name = "Wind Dir -359°")
@Suppress("detekt:MagicNumber")
@Composable
private fun PreviewWindDirectionn359() {
    PreviewWindDirectionItem(-359f)
}

@Preview(name = "Wind Dir 0°")
@Suppress("detekt:MagicNumber")
@Composable
private fun PreviewWindDirection0() {
    PreviewWindDirectionItem(0f)
}

@Preview(name = "Wind Dir 45°")
@Suppress("detekt:MagicNumber")
@Composable
private fun PreviewWindDirection45() {
    PreviewWindDirectionItem(45f)
}

@Preview(name = "Wind Dir 90°")
@Suppress("detekt:MagicNumber")
@Composable
private fun PreviewWindDirection90() {
    PreviewWindDirectionItem(90f)
}

@Preview(name = "Wind Dir 180°")
@Suppress("detekt:MagicNumber")
@Composable
private fun PreviewWindDirection180() {
    PreviewWindDirectionItem(180f)
}

@Preview(name = "Wind Dir 225°")
@Suppress("detekt:MagicNumber")
@Composable
private fun PreviewWindDirection225() {
    PreviewWindDirectionItem(225f)
}

@Preview(name = "Wind Dir 270°")
@Suppress("detekt:MagicNumber")
@Composable
private fun PreviewWindDirection270() {
    PreviewWindDirectionItem(270f)
}

@Preview(name = "Wind Dir 315°")
@Suppress("detekt:MagicNumber")
@Composable
private fun PreviewWindDirection315() {
    PreviewWindDirectionItem(315f)
}

@Preview(name = "Wind Dir -45")
@Suppress("detekt:MagicNumber")
@Composable
private fun PreviewWindDirectionN45() {
    PreviewWindDirectionItem(-45f)
}

@Suppress("detekt:MagicNumber")
@Composable
private fun PreviewWindDirectionItem(windDirection: Float, windSpeed: String = "5 m/s") {
    val normalizedBearing = (windDirection + 180) % 360
    InfoCard(icon = Icons.Outlined.Navigation, text = "Wind", value = windSpeed, rotateIcon = normalizedBearing)
}
