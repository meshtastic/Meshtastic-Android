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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.feature.map.maplibre.utils.distanceKmBetween
import org.meshtastic.feature.map.maplibre.utils.formatSecondsAgo

/** Bottom sheet showing selected node details */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailsBottomSheet(
    selectedNode: Node,
    ourNode: Node?,
    onNavigateToNodeDetails: (Int) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            NodeChip(node = selectedNode)
            val longName = selectedNode.user.longName
            if (!longName.isNullOrBlank()) {
                Text(
                    text = longName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val lastHeardAgo = formatSecondsAgo(selectedNode.lastHeard)
            val coords = selectedNode.gpsString()
            Text(text = "Last heard: $lastHeardAgo", modifier = Modifier.padding(top = 8.dp))
            Text(text = "Coordinates: $coords")
            val km = ourNode?.let { me -> distanceKmBetween(me, selectedNode) }
            if (km != null) Text(text = "Distance: ${"%.1f".format(km)} km")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Button(
                    onClick = {
                        onNavigateToNodeDetails(selectedNode.num)
                        onDismiss()
                    },
                ) {
                    Text("View full node")
                }
            }
        }
    }
}
