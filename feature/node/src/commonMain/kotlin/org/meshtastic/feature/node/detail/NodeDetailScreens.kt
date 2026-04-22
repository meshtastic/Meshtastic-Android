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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.Node
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.details
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SharedContactDialog
import org.meshtastic.feature.node.compass.CompassUiState
import org.meshtastic.feature.node.compass.CompassViewModel
import org.meshtastic.feature.node.component.CompassSheetContent
import org.meshtastic.feature.node.component.FirmwareReleaseSheetContent
import org.meshtastic.feature.node.component.NodeMenuAction
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
    viewModel: NodeDetailViewModel,
    navigateToMessages: (String) -> Unit = {},
    onNavigate: (Route) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    compassViewModel: CompassViewModel? = null,
) {
    LaunchedEffect(nodeId) { viewModel.start(nodeId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.navigationEvents.collect { onNavigate(it) } }
    NodeDetailScaffold(
        modifier = modifier,
        uiState = uiState,
        viewModel = viewModel,
        navigateToMessages = navigateToMessages,
        onNavigate = onNavigate,
        onNavigateUp = onNavigateUp,
        compassViewModel = compassViewModel,
    )
}

@Composable
@Suppress("LongParameterList")
private fun NodeDetailScaffold(
    modifier: Modifier,
    uiState: NodeDetailUiState,
    viewModel: NodeDetailViewModel,
    navigateToMessages: (String) -> Unit,
    onNavigate: (Route) -> Unit,
    onNavigateUp: () -> Unit,
    compassViewModel: CompassViewModel? = null,
) {
    var activeOverlay by remember { mutableStateOf<NodeDetailOverlay?>(null) }
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
            onAction = { action ->
                when (action) {
                    is NodeDetailAction.ShareContact -> activeOverlay = NodeDetailOverlay.SharedContact
                    is NodeDetailAction.OpenCompass -> {
                        actualCompassViewModel?.start(action.node, action.displayUnits)
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
            onSaveNotes = { num, notes -> viewModel.setNodeNotes(num, notes) },
            modifier = Modifier.padding(paddingValues),
        )
    }

    NodeDetailOverlays(activeOverlay, node, compassUiState, actualCompassViewModel, { activeOverlay = null }) {
        viewModel.handleNodeMenuAction(NodeMenuAction.RequestPosition(it))
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
    val requestLocationPermission =
        org.meshtastic.core.ui.util.rememberRequestLocationPermission(
            onGranted = { node?.let { onRequestPosition(it) } },
            onDenied = {},
        )
    val openLocationSettings = org.meshtastic.core.ui.util.rememberOpenLocationSettings()

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
                    onRequestLocationPermission = { requestLocationPermission() },
                    onOpenLocationSettings = { openLocationSettings() },
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
