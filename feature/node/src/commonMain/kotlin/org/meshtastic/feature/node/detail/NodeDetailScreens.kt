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
package org.meshtastic.feature.node.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.FirmwareRelease
import org.meshtastic.core.model.Node
import org.meshtastic.core.navigation.MapRoute
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.repository.LiveUpdateMode
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.details
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.ShareContactDialog
import org.meshtastic.core.ui.util.ActiveWhileStarted
import org.meshtastic.core.ui.util.rememberLiveUpdateSupport
import org.meshtastic.core.ui.util.rememberMeshHudSupported
import org.meshtastic.feature.node.compass.CompassUiState
import org.meshtastic.feature.node.compass.CompassViewModel
import org.meshtastic.feature.node.component.CompassSheetContent
import org.meshtastic.feature.node.component.DeviceModelDialog
import org.meshtastic.feature.node.component.FirmwareReleaseSheetContent
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.model.NodeDetailAction

private sealed interface NodeDetailOverlay {
    data object SharedContact : NodeDetailOverlay

    data class FirmwareReleaseInfo(val release: FirmwareRelease) : NodeDetailOverlay

    data class Compass(val nodeNum: Int, val displayUnits: MeasurementSystem) : NodeDetailOverlay
}

@Composable
fun NodeDetailScreen(
    nodeId: Int,
    modifier: Modifier = Modifier,
    viewModel: NodeDetailViewModel,
    navigateToMessages: (String) -> Unit = {},
    onNavigate: (Route) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    compassViewModel: CompassViewModel? = null,
    openCompass: Boolean = false,
    liveUpdateViewModel: NodeLiveUpdateViewModel? = null,
) {
    SideEffect(nodeId) { viewModel.start(nodeId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    LaunchedEffect(viewModel) { viewModel.navigationEvents.collect { currentOnNavigate(it) } }
    val liveUpdateAvailable = rememberLiveUpdateSupport().available && liveUpdateViewModel != null
    val liveUpdateMode by
        liveUpdateViewModel?.mode?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(LiveUpdateMode.Off) }
    val trackedInLiveUpdate =
        uiState.node?.takeIf { liveUpdateAvailable }?.let { liveUpdateMode == LiveUpdateMode.TrackNode(it.num) }
    val onTrackInLiveUpdate: (Int, Boolean) -> Unit = { nodeNum, track ->
        liveUpdateViewModel?.setTracking(nodeNum, track)
    }
    NodeDetailScaffold(
        modifier = modifier,
        uiState = uiState,
        viewModel = viewModel,
        navigateToMessages = navigateToMessages,
        onNavigate = onNavigate,
        onNavigateUp = onNavigateUp,
        compassViewModel = compassViewModel,
        openCompass = openCompass,
        trackedInLiveUpdate = trackedInLiveUpdate,
        onTrackInLiveUpdate = onTrackInLiveUpdate,
    )
}

/** Opens the compass sheet once per arrival: closing it, or coming back to this screen, must not reopen it. */
@Suppress("UnnecessaryLaunchedEffect") // Fires once per node, not once per recomposition.
@Composable
private fun OpenCompassOnArrival(uiState: NodeDetailUiState, open: (NodeDetailOverlay) -> Unit) {
    var opened by rememberSaveable { mutableStateOf(false) }
    val currentOpen by rememberUpdatedState(open)
    val node = uiState.node?.takeIf { !opened && it.validPosition != null }
    LaunchedEffect(node?.num) {
        node?.let {
            currentOpen(NodeDetailOverlay.Compass(it.num, uiState.metricsState.displayUnits))
            opened = true
        }
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod")
private fun NodeDetailScaffold(
    modifier: Modifier,
    uiState: NodeDetailUiState,
    viewModel: NodeDetailViewModel,
    navigateToMessages: (String) -> Unit,
    onNavigate: (Route) -> Unit,
    onNavigateUp: () -> Unit,
    compassViewModel: CompassViewModel? = null,
    openCompass: Boolean = false,
    trackedInLiveUpdate: Boolean? = null,
    onTrackInLiveUpdate: (nodeNum: Int, track: Boolean) -> Unit = { _, _ -> },
) {
    var activeOverlay by remember { mutableStateOf<NodeDetailOverlay?>(null) }
    // Saved, unlike the overlays: a rotation must not close the model, which the user may be turning the phone for.
    var deviceModelOpen by rememberSaveable { mutableStateOf(false) }
    if (openCompass) OpenCompassOnArrival(uiState) { activeOverlay = it }
    val actualCompassViewModel = compassViewModel
    val compassUiState by
        actualCompassViewModel?.uiState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(CompassUiState()) }

    val node = uiState.node
    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.details),
                subtitle = uiState.nodeName.asString(),
                ourNode = uiState.ourNode,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        NodeDetailContent(
            uiState = uiState,
            listState = listState,
            trackedInLiveUpdate = trackedInLiveUpdate,
            onAction = { action ->
                when (action) {
                    is NodeDetailAction.ShareContact -> activeOverlay = NodeDetailOverlay.SharedContact

                    is NodeDetailAction.TrackInLiveUpdate -> onTrackInLiveUpdate(action.nodeNum, action.track)

                    is NodeDetailAction.OpenCompass ->
                        activeOverlay = NodeDetailOverlay.Compass(action.node.num, action.displayUnits)

                    is NodeDetailAction.OpenDeviceModel -> deviceModelOpen = true

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
            onSaveNotes = { num, notes -> viewModel.setNodeNotes(num, notes) },
            onSaveDisplayName = { num, name -> viewModel.setNodeDisplayName(num, name) },
            modifier = Modifier.padding(paddingValues),
        )
    }

    val isLocalNode = node != null && node.num == uiState.ourNode?.num
    NodeDetailOverlays(
        activeOverlay,
        node,
        isLocalNode,
        compassUiState,
        actualCompassViewModel,
        { activeOverlay = null },
        onOpenMeshHud =
        if (rememberMeshHudSupported()) {
            { num ->
                activeOverlay = null
                onNavigate(MapRoute.MeshHud(num))
            }
        } else {
            null
        },
    ) {
        viewModel.handleNodeMenuAction(NodeMenuAction.RequestPosition(it))
    }
    uiState.metricsState.deviceHardware
        ?.takeIf { deviceModelOpen }
        ?.let { DeviceModelDialog(it, onDismiss = { deviceModelOpen = false }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDetailOverlays(
    overlay: NodeDetailOverlay?,
    node: Node?,
    isLocal: Boolean,
    compassUiState: CompassUiState,
    compassViewModel: CompassViewModel?,
    onDismiss: () -> Unit,
    onOpenMeshHud: ((Int) -> Unit)?,
    onRequestPosition: (Node) -> Unit,
) {
    val locationPermission = org.meshtastic.core.ui.util.rememberLocationPermissionState()
    val openLocationSettings = org.meshtastic.core.ui.util.rememberOpenLocationSettings()
    // Request a fresh position once the user grants from the compass warning, mirroring the prior onGranted callback.
    var positionPendingGrant by remember { mutableStateOf(false) }
    val currentNode by rememberUpdatedState(node)
    val currentOnRequestPosition by rememberUpdatedState(onRequestPosition)
    LaunchedEffect(locationPermission.status) {
        if (locationPermission.isGranted && positionPendingGrant) {
            currentNode?.let { currentOnRequestPosition(it) }
            positionPendingGrant = false
        }
    }
    val onRequestLocationPermission = {
        if (locationPermission.status == org.meshtastic.core.ui.util.PermissionStatus.PERMANENTLY_DENIED) {
            locationPermission.openAppSettings()
        } else {
            positionPendingGrant = true
            locationPermission.request()
        }
    }

    when (overlay) {
        is NodeDetailOverlay.SharedContact -> node?.let { ShareContactDialog(it, onDismiss, isOwnContact = isLocal) }

        is NodeDetailOverlay.FirmwareReleaseInfo ->
            NodeDetailBottomSheet(onDismiss) { FirmwareReleaseSheetContent(firmwareRelease = overlay.release) }

        is NodeDetailOverlay.Compass -> {
            val targetNode = node?.takeIf { it.num == overlay.nodeNum }
            if (targetNode != null) {
                compassViewModel?.let { viewModel ->
                    ActiveWhileStarted(targetNode, overlay.displayUnits, viewModel) {
                        viewModel.start(targetNode, overlay.displayUnits)
                        viewModel::stop
                    }
                }
            }
            NodeDetailBottomSheet(onDismiss = onDismiss) {
                CompassSheetContent(
                    uiState = compassUiState,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onOpenLocationSettings = { openLocationSettings() },
                    onRequestPosition = { node?.let { onRequestPosition(it) } },
                    modifier = Modifier.padding(bottom = 24.dp),
                    onOpenMeshHud = onOpenMeshHud?.let { open -> { open(overlay.nodeNum) } },
                )
            }
        }

        null -> {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDetailBottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
        )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) { content() }
}
