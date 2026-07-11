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
package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.node_filter_placeholder
import org.meshtastic.core.resources.waypoint_send_to
import org.meshtastic.proto.ChannelSet

/**
 * Direct-message contact key for sending to [node]. Uses the PKC channel index only when both ends support it — mirrors
 * [org.meshtastic.feature.node.list.NodeListViewModel.getDirectMessageRoute], the app's other DM-key builder —
 * otherwise falls back to the node's own channel, matching how a DM would actually route on the mesh.
 */
internal fun dmContactKey(node: Node, ourNode: Node?): String {
    val hasPKC = ourNode?.hasPKC == true && node.hasPKC
    val channel = if (hasPKC) NodeAddress.PKC_CHANNEL_INDEX else node.channel
    return "$channel${node.user.id}"
}

/**
 * Nodes whose long or short name contains [filterText] (case-insensitive); all of [nodes] when [filterText] is blank.
 */
private fun filterNodesByName(nodes: List<Node>, filterText: String): List<Node> = if (filterText.isBlank()) {
    nodes
} else {
    nodes.filter {
        it.user.long_name.contains(filterText, ignoreCase = true) ||
            it.user.short_name.contains(filterText, ignoreCase = true)
    }
}

/** Recipient-picker row label for [node]: long name, short name, or a derived id — never blank. */
private fun nodeRowLabel(node: Node): String = node.user.long_name.takeIf { it.isNotBlank() }
    ?: node.user.short_name.takeIf { it.isNotBlank() }
    ?: NodeAddress.numToDefaultId(node.num)

/**
 * Lists the primary channel (labelled with its configured name, e.g. "LongFast") plus any secondary channels, then
 * every known [nodes] entry — letting the user pick a waypoint destination the same way the Python Meshtastic CLI/API
 * can target a channel or a specific node.
 */
@Composable
internal fun WaypointRecipientPickerDialog(
    nodes: List<Node>,
    ourNode: Node?,
    channelSet: ChannelSet?,
    selectedContactKey: String,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val distinctNodes = nodes.distinctBy { it.num }
    var filterText by rememberSaveable { mutableStateOf("") }
    val filteredNodes = remember(distinctNodes, filterText) { filterNodesByName(distinctNodes, filterText) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.waypoint_send_to)) },
        text = {
            Column {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    label = { Text(stringResource(Res.string.node_filter_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.size(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(count = channelCount(channelSet), key = { "channel_$it" }) { channelIndex ->
                        val contactKey = ContactKey.broadcast(channelIndex).value
                        RecipientRow(
                            label = channelLabel(channelSet, channelIndex),
                            selected = selectedContactKey == contactKey,
                            onClick = { onSelect(contactKey) },
                        )
                    }
                    if (filteredNodes.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.size(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                    }
                    items(filteredNodes, key = { it.num }) { node ->
                        val contactKey = dmContactKey(node, ourNode)
                        RecipientRow(
                            label = nodeRowLabel(node),
                            selected = selectedContactKey == contactKey,
                            onClick = { onSelect(contactKey) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.cancel)) } },
        dismissButton = null,
    )
}

@Composable
private fun RecipientRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
        Modifier.fillMaxWidth()
            .toggleable(value = selected, role = Role.RadioButton, onValueChange = { onClick() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}
