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
package org.meshtastic.feature.node.detail

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtastic.core.strings.getString
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SharedContactDialog
import org.meshtastic.feature.node.compass.CompassUiState
import org.meshtastic.feature.node.compass.CompassViewModel
import org.meshtastic.feature.node.component.AdministrationSection
import org.meshtastic.feature.node.component.CompassSheetContent
import org.meshtastic.feature.node.component.DeviceActions
import org.meshtastic.feature.node.component.DeviceDetailsSection
import org.meshtastic.feature.node.component.FirmwareReleaseSheetContent
import org.meshtastic.feature.node.component.NodeDetailsSection
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.component.NotesSection
import org.meshtastic.feature.node.component.PositionSection
import org.meshtastic.feature.node.metrics.MetricsViewModel
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.NodeDetailAction

private sealed interface NodeDetailOverlay {
    data object SharedContact : NodeDetailOverlay

    data class FirmwareReleaseInfo(val release: FirmwareRelease) : NodeDetailOverlay

    data object Compass : NodeDetailOverlay
}

@Composable
fun NodeDetailScreen(
    nodeId: Int,
    modifier: Modifier = Modifier,
    metricsViewModel: MetricsViewModel = hiltViewModel(),
    nodeDetailViewModel: NodeDetailViewModel = hiltViewModel(),
    navigateToMessages: (String) -> Unit = {},
    onNavigate: (Route) -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    LaunchedEffect(nodeId) {
        metricsViewModel.setNodeId(nodeId)
        nodeDetailViewModel.start(nodeId)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        nodeDetailViewModel.effects.collect { effect ->
            if (effect is NodeRequestEffect.ShowFeedback) {
                @Suppress("SpreadOperator")
                snackbarHostState.showSnackbar(getString(effect.resource, *effect.args.toTypedArray()))
            }
        }
    }

    val metricsState by metricsViewModel.state.collectAsStateWithLifecycle()
    val envState by metricsViewModel.environmentState.collectAsStateWithLifecycle()
    val ourNode by nodeDetailViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val availableLogs by
        remember(metricsState, envState) { derivedStateOf { getAvailableLogs(metricsState, envState) } }

    NodeDetailScaffold(
        modifier = modifier,
        metricsState = metricsState,
        ourNode = ourNode,
        availableLogs = availableLogs,
        snackbarHostState = snackbarHostState,
        metricsViewModel = metricsViewModel,
        nodeDetailViewModel = nodeDetailViewModel,
        navigateToMessages = navigateToMessages,
        onNavigate = onNavigate,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
@Suppress("LongParameterList")
private fun NodeDetailScaffold(
    modifier: Modifier,
    metricsState: MetricsState,
    ourNode: Node?,
    availableLogs: Set<LogsType>,
    snackbarHostState: SnackbarHostState,
    metricsViewModel: MetricsViewModel,
    nodeDetailViewModel: NodeDetailViewModel,
    navigateToMessages: (String) -> Unit,
    onNavigate: (Route) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var activeOverlay by remember { mutableStateOf<NodeDetailOverlay?>(null) }
    val inspectionMode = LocalInspectionMode.current
    val compassViewModel = if (inspectionMode) null else hiltViewModel<CompassViewModel>()
    val compassUiState by
        compassViewModel?.uiState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(CompassUiState()) }

    val node = metricsState.node
    Scaffold(
        modifier = modifier,
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        NodeDetailContent(
            node = node,
            metricsState = metricsState,
            ourNode = ourNode,
            availableLogs = availableLogs,
            nodeDetailViewModel = nodeDetailViewModel,
            onAction = { action ->
                when (action) {
                    is NodeDetailAction.ShareContact -> activeOverlay = NodeDetailOverlay.SharedContact
                    is NodeDetailAction.OpenCompass -> {
                        compassViewModel?.start(action.node, action.displayUnits)
                        activeOverlay = NodeDetailOverlay.Compass
                    }
                    else ->
                        handleNodeAction(
                            action = action,
                            ourNode = ourNode,
                            node = node!!,
                            navigateToMessages = navigateToMessages,
                            onNavigateUp = onNavigateUp,
                            onNavigate = onNavigate,
                            metricsViewModel = metricsViewModel,
                            nodeDetailViewModel = nodeDetailViewModel,
                        )
                }
            },
            onFirmwareSelect = { activeOverlay = NodeDetailOverlay.FirmwareReleaseInfo(it) },
            modifier = Modifier.padding(paddingValues),
        )
    }

    NodeDetailOverlays(activeOverlay, node, compassUiState, compassViewModel, { activeOverlay = null }) {
        nodeDetailViewModel.handleNodeMenuAction(NodeMenuAction.RequestPosition(it))
    }
}

@Composable
private fun NodeDetailContent(
    node: Node?,
    metricsState: MetricsState,
    ourNode: Node?,
    availableLogs: Set<LogsType>,
    nodeDetailViewModel: NodeDetailViewModel,
    onAction: (NodeDetailAction) -> Unit,
    onFirmwareSelect: (FirmwareRelease) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastTracerouteTime by nodeDetailViewModel.lastTraceRouteTime.collectAsStateWithLifecycle()
    val lastRequestNeighborsTime by nodeDetailViewModel.lastRequestNeighborsTime.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = node,
        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
        label = "NodeDetailContent",
        modifier = modifier,
    ) { targetNode ->
        if (targetNode != null) {
            NodeDetailList(
                node = targetNode,
                ourNode = ourNode,
                metricsState = metricsState,
                lastTracerouteTime = lastTracerouteTime,
                lastRequestNeighborsTime = lastRequestNeighborsTime,
                availableLogs = availableLogs,
                onAction = onAction,
                onFirmwareSelect = onFirmwareSelect,
                onSaveNotes = { num, notes -> nodeDetailViewModel.setNodeNotes(num, notes) },
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

private fun getAvailableLogs(
    metricsState: MetricsState,
    envState: org.meshtastic.feature.node.metrics.EnvironmentMetricsState,
): Set<LogsType> = buildSet {
    if (metricsState.hasDeviceMetrics()) add(LogsType.DEVICE)
    if (metricsState.hasPositionLogs()) {
        add(LogsType.NODE_MAP)
        add(LogsType.POSITIONS)
    }
    if (envState.hasEnvironmentMetrics()) add(LogsType.ENVIRONMENT)
    if (metricsState.hasSignalMetrics()) add(LogsType.SIGNAL)
    if (metricsState.hasPowerMetrics()) add(LogsType.POWER)
    if (metricsState.hasTracerouteLogs()) add(LogsType.TRACEROUTE)
    if (metricsState.hasHostMetrics()) add(LogsType.HOST)
    if (metricsState.hasPaxMetrics()) add(LogsType.PAX)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDetailOverlays(
    overlay: NodeDetailOverlay?,
    node: Node?,
    compassUiState: CompassUiState,
    compassViewModel: CompassViewModel?,
    onDismiss: () -> Unit,
    onRequestPosition: (Node) -> Unit,
) {
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }
    val locationSettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> }

    when (overlay) {
        is NodeDetailOverlay.SharedContact -> node?.let { SharedContactDialog(it, onDismiss) }
        is NodeDetailOverlay.FirmwareReleaseInfo ->
            NodeDetailBottomSheet(onDismiss) { FirmwareReleaseSheetContent(firmwareRelease = overlay.release) }
        is NodeDetailOverlay.Compass -> {
            DisposableEffect(Unit) { onDispose { compassViewModel?.stop() } }
            NodeDetailBottomSheet(
                onDismiss = {
                    compassViewModel?.stop()
                    onDismiss()
                },
            ) {
                CompassSheetContent(
                    uiState = compassUiState,
                    onRequestLocationPermission = {
                        val perms =
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        permissionLauncher.launch(perms)
                    },
                    onOpenLocationSettings = {
                        locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    onRequestPosition = { node?.let { onRequestPosition(it) } },
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
        null -> {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDetailBottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) { content() }
}

@Composable
private fun NodeDetailList(
    node: Node,
    ourNode: Node?,
    metricsState: MetricsState,
    lastTracerouteTime: Long?,
    lastRequestNeighborsTime: Long?,
    availableLogs: Set<LogsType>,
    onAction: (NodeDetailAction) -> Unit,
    onFirmwareSelect: (FirmwareRelease) -> Unit,
    onSaveNotes: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).focusable(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        NodeDetailsSection(node)
        DeviceActions(
            node = node,
            lastTracerouteTime = lastTracerouteTime,
            lastRequestNeighborsTime = lastRequestNeighborsTime,
            availableLogs = availableLogs,
            onAction = onAction,
            metricsState = metricsState,
            isLocal = metricsState.isLocal,
        )
        PositionSection(node, ourNode, metricsState, availableLogs, onAction)
        if (metricsState.deviceHardware != null) DeviceDetailsSection(metricsState)
        NotesSection(node = node, onSaveNotes = onSaveNotes)
        if (!metricsState.isManaged) {
            AdministrationSection(node, metricsState, onAction, onFirmwareSelect)
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
    metricsViewModel: MetricsViewModel,
    nodeDetailViewModel: NodeDetailViewModel,
) {
    when (action) {
        is NodeDetailAction.Navigate -> onNavigate(action.route)
        is NodeDetailAction.TriggerServiceAction -> metricsViewModel.onServiceAction(action.action)
        is NodeDetailAction.HandleNodeMenuAction -> {
            when (val menuAction = action.action) {
                is NodeMenuAction.DirectMessage -> {
                    val hasPKC = ourNode?.hasPKC == true
                    val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
                    navigateToMessages("${channel}${node.user.id}")
                }
                is NodeMenuAction.Remove -> {
                    nodeDetailViewModel.handleNodeMenuAction(menuAction)
                    onNavigateUp()
                }
                else -> nodeDetailViewModel.handleNodeMenuAction(menuAction)
            }
        }
        else -> {}
    }
}
