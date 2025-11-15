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

package org.meshtastic.feature.map.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.nodes_at_this_location
import org.meshtastic.core.strings.okay
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.feature.map.model.NodeClusterItem

@Composable
fun ClusterItemsListDialog(
    items: List<NodeClusterItem>,
    onDismiss: () -> Unit,
    onItemClick: (NodeClusterItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.nodes_at_this_location)) },
        text = {
            // Use a LazyColumn for potentially long lists of items
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(items, key = { it.node.num }) { item ->
                    ClusterDialogListItem(item = item, onClick = { onItemClick(item) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.okay)) } },
    )
}

@Composable
private fun ClusterDialogListItem(item: NodeClusterItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        leadingContent = { NodeChip(node = item.node) },
        headlineContent = { Text(item.nodeTitle) },
        supportingContent = {
            if (item.nodeSnippet.isNotBlank()) {
                Text(item.nodeSnippet)
            }
        },
        modifier =
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp), // Add some padding around list items
    )
}
