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

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.loading
import org.meshtastic.feature.node.component.AdministrationSection
import org.meshtastic.feature.node.component.DeviceActions
import org.meshtastic.feature.node.component.DeviceDetailsSection
import org.meshtastic.feature.node.component.NodeDetailsSection
import org.meshtastic.feature.node.component.NotesSection
import org.meshtastic.feature.node.model.NodeDetailAction

/**
 * Shared content composable for node details, usable from both Android and Desktop.
 *
 * Renders a [Crossfade] between a loading spinner and the full [NodeDetailList] when the node is present. This
 * composable contains no Android-specific APIs — overlays (compass, bottom sheets, permission launchers) are handled by
 * the platform-specific screen wrapper.
 */
@Composable
fun NodeDetailContent(
    uiState: NodeDetailUiState,
    onAction: (NodeDetailAction) -> Unit,
    onFirmwareSelect: (FirmwareRelease) -> Unit,
    onSaveNotes: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Crossfade(targetState = uiState.node != null, label = "NodeDetailContent", modifier = modifier) { isNodePresent ->
        if (isNodePresent && uiState.node != null) {
            NodeDetailList(
                node = uiState.node,
                ourNode = uiState.ourNode,
                uiState = uiState,
                listState = listState,
                onAction = onAction,
                onFirmwareSelect = onFirmwareSelect,
                onSaveNotes = onSaveNotes,
            )
        } else {
            val loadingDescription = stringResource(Res.string.loading)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDescription })
            }
        }
    }
}

/**
 * Scrollable list of node detail sections: identity, device actions (including telemetry and position), hardware
 * details, notes, and administration.
 */
@Composable
fun NodeDetailList(
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
                ourNode = ourNode,
                lastTracerouteTime = uiState.lastTracerouteTime,
                lastRequestNeighborsTime = uiState.lastRequestNeighborsTime,
                availableLogs = uiState.availableLogs,
                onAction = onAction,
                displayUnits = uiState.metricsState.displayUnits,
                isFahrenheit = uiState.metricsState.isFahrenheit,
                isLocal = uiState.metricsState.isLocal,
            )
        }
        if (uiState.metricsState.deviceHardware != null) {
            item { DeviceDetailsSection(uiState.metricsState) }
        }
        item { NotesSection(node = node, onSaveNotes = onSaveNotes) }
        if (!uiState.metricsState.isManaged) {
            item {
                AdministrationSection(
                    node = node,
                    metricsState = uiState.metricsState,
                    onAction = onAction,
                    onFirmwareSelect = onFirmwareSelect,
                    sessionStatus = uiState.sessionStatus,
                    isEnsuringSession = uiState.isEnsuringSession,
                )
            }
        }
    }
}
