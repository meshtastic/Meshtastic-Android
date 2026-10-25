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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.repository.LiveUpdateMode
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.favorite
import org.meshtastic.core.resources.live_update
import org.meshtastic.core.resources.live_update_mesh_status
import org.meshtastic.core.resources.live_update_mesh_status_summary
import org.meshtastic.core.resources.live_update_no_nodes
import org.meshtastic.core.resources.live_update_off_summary
import org.meshtastic.core.resources.live_update_promotion_off
import org.meshtastic.core.resources.live_update_search_nodes
import org.meshtastic.core.resources.live_update_start
import org.meshtastic.core.resources.live_update_stop
import org.meshtastic.core.resources.live_update_switch
import org.meshtastic.core.resources.live_update_track_node
import org.meshtastic.core.resources.live_update_track_node_summary
import org.meshtastic.core.resources.live_update_tracking
import org.meshtastic.core.resources.open_settings
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Notifications
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.core.ui.util.formatAgo
import org.meshtastic.core.ui.util.rememberLiveUpdateSupport
import org.meshtastic.feature.connections.LiveUpdateViewModel
import org.meshtastic.feature.connections.filterLiveUpdateCandidates

/**
 * The Live Update row and its chooser, on platforms that have Live Updates. Shown while connected, and while one runs
 * whatever the connection, so a running Live Update can always be stopped.
 */
@Composable
fun LiveUpdateSection(connected: Boolean, modifier: Modifier = Modifier) {
    val support = rememberLiveUpdateSupport()
    if (!support.available) return
    val viewModel: LiveUpdateViewModel = koinViewModel()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val trackedNode by viewModel.trackedNode.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    var showChooser by rememberSaveable { mutableStateOf(false) }

    if (!connected && mode == LiveUpdateMode.Off) return
    LiveUpdateCard(
        mode = mode,
        trackedNode = trackedNode,
        turnedOffInSettings = support.turnedOff,
        onClick = { showChooser = true },
        onOpenSettings = support.openSettings,
        modifier = modifier,
    )
    if (showChooser) {
        LiveUpdateChooserSheet(
            mode = mode,
            candidates = candidates,
            onStart = {
                viewModel.setMode(it)
                showChooser = false
            },
            onStop = {
                viewModel.setMode(LiveUpdateMode.Off)
                showChooser = false
            },
            onDismiss = { showChooser = false },
        )
    }
}

/** The Connections row that shows what the Live Update is doing and opens the chooser. */
@Composable
fun LiveUpdateCard(
    mode: LiveUpdateMode,
    trackedNode: Node?,
    turnedOffInSettings: Boolean,
    onClick: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary =
        when (mode) {
            LiveUpdateMode.Off -> stringResource(Res.string.live_update_off_summary)

            LiveUpdateMode.MeshStatus -> stringResource(Res.string.live_update_mesh_status)

            is LiveUpdateMode.TrackNode ->
                stringResource(
                    Res.string.live_update_tracking,
                    trackedNode?.listName() ?: NodeAddress.numToDefaultId(mode.nodeNum),
                )
        }
    Card(modifier = modifier.fillMaxWidth()) {
        ListItem(
            text = stringResource(Res.string.live_update),
            supportingText = summary,
            leadingIcon = MeshtasticIcons.Notifications,
            onClick = onClick,
        )
        if (turnedOffInSettings && mode != LiveUpdateMode.Off) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.live_update_promotion_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenSettings) { Text(stringResource(Res.string.open_settings)) }
            }
        }
    }
}

private enum class LiveUpdateChoice {
    MeshStatus,
    TrackNode,
}

/**
 * Picks what the Live Update shows: Mesh Status, or one node from a searchable list. [candidates] arrive already
 * ordered, favorites first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun LiveUpdateChooserSheet(
    mode: LiveUpdateMode,
    candidates: List<Node>,
    onStart: (LiveUpdateMode) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by rememberSaveable {
        mutableStateOf(
            if (mode is LiveUpdateMode.TrackNode) LiveUpdateChoice.TrackNode else LiveUpdateChoice.MeshStatus,
        )
    }
    var selectedNum by rememberSaveable { mutableStateOf((mode as? LiveUpdateMode.TrackNode)?.nodeNum) }
    var query by rememberSaveable { mutableStateOf("") }
    val selection =
        when (choice) {
            LiveUpdateChoice.MeshStatus -> LiveUpdateMode.MeshStatus
            LiveUpdateChoice.TrackNode -> selectedNum?.let { LiveUpdateMode.TrackNode(it) }
        }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = stringResource(Res.string.live_update), style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.selectableGroup()) {
                ChoiceRow(
                    selected = choice == LiveUpdateChoice.MeshStatus,
                    title = stringResource(Res.string.live_update_mesh_status),
                    summary = stringResource(Res.string.live_update_mesh_status_summary),
                    onSelect = { choice = LiveUpdateChoice.MeshStatus },
                )
                ChoiceRow(
                    selected = choice == LiveUpdateChoice.TrackNode,
                    title = stringResource(Res.string.live_update_track_node),
                    summary = stringResource(Res.string.live_update_track_node_summary),
                    onSelect = { choice = LiveUpdateChoice.TrackNode },
                )
            }
            if (choice == LiveUpdateChoice.TrackNode) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(MeshtasticIcons.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(Res.string.live_update_search_nodes)) },
                )
                val shown = filterLiveUpdateCandidates(candidates, query)
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.live_update_no_nodes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).selectableGroup()) {
                        items(shown, key = { it.num }) { node ->
                            NodeChoiceRow(
                                node = node,
                                selected = node.num == selectedNum,
                                onSelect = { selectedNum = node.num },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (mode != LiveUpdateMode.Off) {
                    TextButton(onClick = onStop) { Text(stringResource(Res.string.live_update_stop)) }
                }
                Button(
                    onClick = { selection?.let(onStart) },
                    enabled = selection != null && selection != mode,
                ) {
                    val label =
                        if (mode == LiveUpdateMode.Off) Res.string.live_update_start else Res.string.live_update_switch
                    Text(stringResource(label))
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, summary: String, onSelect: () -> Unit) {
    Row(
        modifier =
        Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NodeChoiceRow(node: Node, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier =
        Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(text = node.listName(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = formatAgo(node.lastHeard),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (node.isFavorite) {
            Icon(
                imageVector = MeshtasticIcons.Favorite,
                contentDescription = stringResource(Res.string.favorite),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun Node.listName(): String = effectiveLongName.ifBlank { user.short_name }.ifBlank { user.id }
