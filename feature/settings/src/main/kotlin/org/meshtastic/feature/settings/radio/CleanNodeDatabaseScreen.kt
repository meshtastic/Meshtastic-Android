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
package org.meshtastic.feature.settings.radio

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.clean_node_database_description
import org.meshtastic.core.strings.clean_node_database_title
import org.meshtastic.core.strings.clean_nodes_older_than
import org.meshtastic.core.strings.clean_now
import org.meshtastic.core.strings.clean_unknown_nodes
import org.meshtastic.core.strings.nodes_queued_for_deletion
import org.meshtastic.core.ui.component.NodeChip

/**
 * Composable screen for cleaning the node database. Allows users to specify criteria for deleting nodes. The list of
 * nodes to be deleted updates automatically as filter criteria change.
 */
@Composable
fun CleanNodeDatabaseScreen(viewModel: CleanNodeDatabaseViewModel = hiltViewModel()) {
    val olderThanDays by viewModel.olderThanDays.collectAsState()
    val onlyUnknownNodes by viewModel.onlyUnknownNodes.collectAsState()
    val nodesToDelete by viewModel.nodesToDelete.collectAsState()

    LaunchedEffect(olderThanDays, onlyUnknownNodes) { viewModel.getNodesToDelete() }

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(Res.string.clean_node_database_title))
        Text(stringResource(Res.string.clean_node_database_description), style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))

        DaysThresholdFilter(
            olderThanDays = olderThanDays,
            onlyUnknownNodes = onlyUnknownNodes,
            onDaysChanged = viewModel::onOlderThanDaysChanged,
        )

        Spacer(modifier = Modifier.height(8.dp))

        UnknownNodesFilter(onlyUnknownNodes = onlyUnknownNodes, onCheckedChanged = viewModel::onOnlyUnknownNodesChanged)

        Spacer(modifier = Modifier.height(32.dp))

        NodesDeletionPreview(nodesToDelete = nodesToDelete)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { if (nodesToDelete.isNotEmpty()) viewModel.requestCleanNodes() },
            modifier = Modifier.fillMaxWidth(),
            enabled = nodesToDelete.isNotEmpty(),
        ) {
            Text(stringResource(Res.string.clean_now))
        }
    }
}

private const val MIN_UNKNOWN_DAYS_THRESHOLD = 0f
private const val MIN_KNOWN_DAYS_THRESHOLD = 7f
private const val MAX_DAYS_THRESHOLD = 365f

/**
 * Composable for the "older than X days" filter. This filter is always active.
 *
 * @param olderThanDays The number of days for the filter.
 * @param onlyUnknownNodes Whether the "only unknown nodes" filter is enabled.
 * @param onDaysChanged Callback for when the number of days changes.
 */
@Composable
private fun DaysThresholdFilter(olderThanDays: Float, onlyUnknownNodes: Boolean, onDaysChanged: (Float) -> Unit) {
    val valueRange =
        if (onlyUnknownNodes) {
            MIN_UNKNOWN_DAYS_THRESHOLD..MAX_DAYS_THRESHOLD
        } else {
            MIN_KNOWN_DAYS_THRESHOLD..MAX_DAYS_THRESHOLD
        }
    val steps = (valueRange.endInclusive - valueRange.start - 1).toInt().coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = stringResource(Res.string.clean_nodes_older_than, olderThanDays.toInt()),
        )
        Slider(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            value = olderThanDays,
            onValueChange = onDaysChanged,
            valueRange = valueRange,
            steps = steps,
        )
    }
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
        Text(stringResource(Res.string.clean_unknown_nodes))
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
        stringResource(Res.string.nodes_queued_for_deletion, nodesToDelete.size),
        modifier = Modifier.padding(bottom = 16.dp),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        nodesToDelete.forEach { node ->
            NodeChip(node = node.toModel(), modifier = Modifier.padding(end = 8.dp, bottom = 8.dp))
        }
    }
}
