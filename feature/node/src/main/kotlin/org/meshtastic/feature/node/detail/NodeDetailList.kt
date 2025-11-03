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

package org.meshtastic.feature.node.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.ui.component.SharedContactDialog
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.node.component.AdministrationSection
import org.meshtastic.feature.node.component.DeviceActions
import org.meshtastic.feature.node.component.DeviceDetailsSection
import org.meshtastic.feature.node.component.FirmwareReleaseSheetContent
import org.meshtastic.feature.node.component.MetricsSection
import org.meshtastic.feature.node.component.NodeDetailsSection
import org.meshtastic.feature.node.component.NotesSection
import org.meshtastic.feature.node.component.PositionSection
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.NodeDetailAction

@Composable
fun NodeDetailContent(
    node: Node,
    ourNode: Node?,
    metricsState: MetricsState,
    lastTracerouteTime: Long?,
    availableLogs: Set<LogsType>,
    onAction: (NodeDetailAction) -> Unit,
    onSaveNotes: (nodeNum: Int, notes: String) -> Unit,
    modifier: Modifier = Modifier,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailList(
    node: Node,
    lastTracerouteTime: Long?,
    ourNode: Node?,
    metricsState: MetricsState,
    onAction: (NodeDetailAction) -> Unit,
    availableLogs: Set<LogsType>,
    onSaveNotes: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
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
            DeviceDetailsSection(metricsState)
        }

        NodeDetailsSection(node)

        NotesSection(node = node, onSaveNotes = onSaveNotes)

        DeviceActions(
            isLocal = metricsState.isLocal,
            lastTracerouteTime = lastTracerouteTime,
            node = node,
            onAction = onAction,
        )

        PositionSection(
            node = node,
            ourNode = ourNode,
            metricsState = metricsState,
            availableLogs = availableLogs,
            onAction = onAction,
        )

        MetricsSection(node, metricsState, availableLogs, onAction)

        if (!metricsState.isManaged) {
            AdministrationSection(
                node = node,
                metricsState = metricsState,
                onAction = onAction,
                onFirmwareSelect = { firmware ->
                    selectedFirmware = firmware
                    showFirmwareSheet = true
                },
            )
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
            metricsState = MetricsState.Companion.Empty,
            availableLogs = emptySet(),
            onAction = {},
            onSaveNotes = { _, _ -> },
        )
    }
}
