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
package org.meshtastic.feature.map.mapcompose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.ui.component.BasicListItem

/**
 * Lists the nodes of a cluster whose members all share one location (so zooming in can never separate them); tapping a
 * node opens its details. The shared twin of the google flavor's same-location cluster dialog.
 */
@Composable
internal fun ClusterItemsListDialog(nodes: List<Node>, onDismiss: () -> Unit, onNodeClick: (Node) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.nodes)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                nodes.forEach { node ->
                    BasicListItem(
                        text = node.user.long_name.ifBlank { node.user.short_name },
                        supportingText = node.user.short_name,
                        onClick = { onNodeClick(node) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}
