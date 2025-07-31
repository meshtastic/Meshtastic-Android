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

package com.geeksville.mesh.ui.radioconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.ui.node.components.NodeChip

/**
 * Composable screen for cleaning the node database. Allows users to specify criteria for deleting nodes. The list of
 * nodes to be deleted updates automatically as filter criteria change.
 */
@Composable
fun CleanNodeDatabaseScreen(viewModel: CleanNodeDatabaseViewModel = hiltViewModel()) {
    val olderThanDaysEnabled by viewModel.olderThanDaysEnabled.collectAsState()
    val olderThanDays by viewModel.olderThanDays.collectAsState()
    val onlyUnknownNodes by viewModel.onlyUnknownNodes.collectAsState()
    val nodesToDelete by viewModel.nodesToDelete.collectAsState()
    var showConfirmationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(olderThanDaysEnabled, olderThanDays, onlyUnknownNodes) { viewModel.getNodesToDelete() }

    if (showConfirmationDialog) {
        ConfirmationDialog(
            nodesToDeleteCount = nodesToDelete.size,
            onConfirm = {
                viewModel.cleanNodes()
                showConfirmationDialog = false
            },
            onDismiss = { showConfirmationDialog = false },
        )
    }

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.clean_node_database_title))
        Text(stringResource(R.string.clean_node_database_description), style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))

        DaysThresholdFilter(
            olderThanDaysEnabled = olderThanDaysEnabled,
            olderThanDays = olderThanDays,
            onEnabledChanged = viewModel::onOlderThanDaysEnabledChanged,
            onDaysChanged = viewModel::onOlderThanDaysChanged,
        )

        Spacer(modifier = Modifier.height(8.dp))

        UnknownNodesFilter(onlyUnknownNodes = onlyUnknownNodes, onCheckedChanged = viewModel::onOnlyUnknownNodesChanged)

        Spacer(modifier = Modifier.height(32.dp))

        NodesDeletionPreview(nodesToDelete = nodesToDelete)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { if (nodesToDelete.isNotEmpty()) showConfirmationDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = nodesToDelete.isNotEmpty(),
        ) {
            Text(stringResource(R.string.clean_now))
        }
    }
}

/**
 * Composable for the "older than X days" filter.
 *
 * @param olderThanDaysEnabled Whether the filter is enabled.
 * @param olderThanDays The number of days for the filter.
 * @param onEnabledChanged Callback for when the enabled state changes.
 * @param onDaysChanged Callback for when the number of days changes.
 */
@Composable
private fun DaysThresholdFilter(
    olderThanDaysEnabled: Boolean,
    olderThanDays: Float,
    onEnabledChanged: (Boolean) -> Unit,
    onDaysChanged: (Float) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            text = stringResource(R.string.clean_nodes_older_than, olderThanDays.toInt()),
        )
        Switch(checked = olderThanDaysEnabled, onCheckedChange = onEnabledChanged)
    }
    Slider(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        value = olderThanDays,
        onValueChange = onDaysChanged,
        valueRange = 1f..365f,
        steps = 364,
        enabled = olderThanDaysEnabled,
    )
}

/**
 * Composable for the "only unknown nodes" filter.
 *
 * @param onlyUnknownNodes Whether the filter is enabled.
 * @param onCheckedChanged Callback for when the checked state changes.
 */
@Composable
private fun UnknownNodesFilter(onlyUnknownNodes: Boolean, onCheckedChanged: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.clean_unknown_nodes))
        Spacer(Modifier.weight(1f))
        Switch(checked = onlyUnknownNodes, onCheckedChange = onCheckedChanged)
    }
}

/**
 * Composable for displaying the list of nodes queued for deletion.
 *
 * @param nodesToDelete The list of nodes to be deleted.
 */
@Composable
private fun NodesDeletionPreview(nodesToDelete: List<NodeEntity>) {
    Text(
        stringResource(R.string.nodes_queued_for_deletion, nodesToDelete.size),
        modifier = Modifier.padding(bottom = 16.dp),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        nodesToDelete.forEach { node ->
            NodeChip(
                node = node.toModel(),
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                isThisNode = false,
                isConnected = false,
            ) {}
        }
    }
}

/**
 * Composable for the confirmation dialog before deleting nodes.
 *
 * @param nodesToDeleteCount The number of nodes to be deleted.
 * @param onConfirm Callback for when the user confirms the deletion.
 * @param onDismiss Callback for when the user dismisses the dialog.
 */
@Composable
private fun ConfirmationDialog(nodesToDeleteCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.are_you_sure)) },
        text = { Text(stringResource(R.string.clean_node_database_confirmation, nodesToDeleteCount)) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.clean_now)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
