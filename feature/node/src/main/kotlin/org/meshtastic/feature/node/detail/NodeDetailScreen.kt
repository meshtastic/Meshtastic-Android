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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtastic.core.strings.getString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.details
import org.meshtastic.core.strings.loading
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SharedContactDialog
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
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
    viewModel: NodeDetailViewModel = hiltViewModel(),
    navigateToMessages: (String) -> Unit = {},
    onNavigate: (Route) -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    viewModel.start(nodeId)

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            if (effect is NodeRequestEffect.ShowFeedback) {
                @Suppress("SpreadOperator")
                snackbarHostState.showSnackbar(getString(effect.resource, *effect.args.toTypedArray()))
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NodeDetailScaffold(
        modifier = modifier,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
        navigateToMessages = navigateToMessages,
        onNavigate = onNavigate,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
@Suppress("LongParameterList")
private fun NodeDetailScaffold(
    modifier: Modifier,
    uiState: NodeDetailUiState,
    snackbarHostState: SnackbarHostState,
    viewModel: NodeDetailViewModel,
    navigateToMessages: (String) -> Unit,
    onNavigate: (Route) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var activeOverlay by remember { mutableStateOf<NodeDetailOverlay?>(null) }
    val inspectionMode = LocalInspectionMode.current
    val compassViewModel = if (inspectionMode) null else hiltViewModel<CompassViewModel>()
    val compassUiState by
        compassViewModel?.uiState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(CompassUiState()) }

    val node = uiState.node
    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = getString(Res.string.details),
                subtitle = node?.user?.long_name ?: "",
                ourNode = uiState.ourNode,
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
            uiState = uiState,
            viewModel = viewModel,
            listState = listState,
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
                            uiState = uiState,
                            navigateToMessages = navigateToMessages,
                            onNavigateUp = onNavigateUp,
                            onNavigate = onNavigate,
                            viewModel = viewModel,
                        )
                }
            },
            onFirmwareSelect = { activeOverlay = NodeDetailOverlay.FirmwareReleaseInfo(it) },
            modifier = Modifier.padding(paddingValues),
        )
    }

    NodeDetailOverlays(activeOverlay, node, compassUiState, compassViewModel, { activeOverlay = null }) {
        viewModel.handleNodeMenuAction(NodeMenuAction.RequestPosition(it))
    }
}

@Composable
private fun NodeDetailContent(
    uiState: NodeDetailUiState,
    viewModel: NodeDetailViewModel,
    listState: LazyListState,
    onAction: (NodeDetailAction) -> Unit,
    onFirmwareSelect: (FirmwareRelease) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = uiState.node != null,
        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
        label = "NodeDetailContent",
        modifier = modifier,
    ) { isNodePresent ->
        if (isNodePresent && uiState.node != null) {
            NodeDetailList(
                node = uiState.node,
                ourNode = uiState.ourNode,
                uiState = uiState,
                listState = listState,
                onAction = onAction,
                onFirmwareSelect = onFirmwareSelect,
                onSaveNotes = { num, notes -> viewModel.setNodeNotes(num, notes) },
            )
        } else {
            val loadingDescription = stringResource(Res.string.loading)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDescription })
            }
        }
    }
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
    uiState: NodeDetailUiState,
    listState: LazyListState,
    onAction: (NodeDetailAction) -> Unit,
    onFirmwareSelect: (FirmwareRelease) -> Unit,
    onSaveNotes: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { NodeDetailsSection(node) }
        item {
            DeviceActions(
                node = node,
                lastTracerouteTime = uiState.lastTracerouteTime,
                lastRequestNeighborsTime = uiState.lastRequestNeighborsTime,
                availableLogs = uiState.availableLogs,
                onAction = onAction,
                metricsState = uiState.metricsState,
                isLocal = uiState.metricsState.isLocal,
            )
        }
        item { PositionSection(node, ourNode, uiState.metricsState, uiState.availableLogs, onAction) }
        if (uiState.metricsState.deviceHardware != null) {
            item { DeviceDetailsSection(uiState.metricsState) }
        }
        item { NotesSection(node = node, onSaveNotes = onSaveNotes) }
        if (!uiState.metricsState.isManaged) {
            item { AdministrationSection(node, uiState.metricsState, onAction, onFirmwareSelect) }
        }
    }
}

private fun handleNodeAction(
    action: NodeDetailAction,
    uiState: NodeDetailUiState,
    navigateToMessages: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigate: (Route) -> Unit,
    viewModel: NodeDetailViewModel,
) {
    when (action) {
        is NodeDetailAction.Navigate -> onNavigate(action.route)
        is NodeDetailAction.TriggerServiceAction -> viewModel.onServiceAction(action.action)
        is NodeDetailAction.HandleNodeMenuAction -> {
            when (val menuAction = action.action) {
                is NodeMenuAction.DirectMessage -> {
                    val route = viewModel.getDirectMessageRoute(menuAction.node, uiState.ourNode)
                    navigateToMessages(route)
                }
                is NodeMenuAction.Remove -> {
                    viewModel.handleNodeMenuAction(menuAction)
                    onNavigateUp()
                }
                else -> viewModel.handleNodeMenuAction(menuAction)
            }
        }
        else -> {}
    }
}

@Preview(showBackground = true)
@Composable
private fun NodeDetailListPreview(@PreviewParameter(NodePreviewParameterProvider::class) node: Node) {
    AppTheme {
        val uiState =
            NodeDetailUiState(
                node = node,
                ourNode = node,
                metricsState = MetricsState(node = node, isLocal = true, isManaged = false),
                availableLogs = emptySet(),
            )
        NodeDetailList(
            node = node,
            ourNode = node,
            uiState = uiState,
            listState = rememberLazyListState(),
            onAction = {},
            onFirmwareSelect = {},
            onSaveNotes = { _, _ -> },
        )
    }
}
