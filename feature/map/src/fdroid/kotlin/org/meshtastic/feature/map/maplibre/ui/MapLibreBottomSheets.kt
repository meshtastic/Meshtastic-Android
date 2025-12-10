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

package org.meshtastic.feature.map.maplibre.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.ui.component.NodeChip

/** Bottom sheet showing details and actions for a selected node */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailsBottomSheet(
    node: Node,
    lastHeardAgo: String,
    coords: String,
    distanceKm: String?,
    onViewFullNode: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            NodeChip(node = node)
            val longName = node.user.longName
            if (!longName.isNullOrBlank()) {
                Text(
                    text = longName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(text = "Last heard: $lastHeardAgo", modifier = Modifier.padding(top = 8.dp))
            Text(text = "Coordinates: $coords")
            if (distanceKm != null) {
                Text(text = "Distance: $distanceKm km")
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Button(onClick = onViewFullNode) { Text("View full node") }
            }
        }
    }
}

/** Bottom sheet showing a list of nodes in a large cluster */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterListBottomSheet(
    members: List<Node>,
    onNodeClicked: (Node) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "Cluster items (${members.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(members) { node ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onNodeClicked(node) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NodeChip(node = node, onClick = { onNodeClicked(node) })
                        Spacer(modifier = Modifier.width(12.dp))
                        val longName = node.user.longName
                        if (!longName.isNullOrBlank()) {
                            Text(text = longName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
