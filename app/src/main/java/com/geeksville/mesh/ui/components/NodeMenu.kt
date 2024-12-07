/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.NodeEntity

@Suppress("LongMethod")
@Composable
fun NodeMenu(
    node: NodeEntity,
    showFullMenu: Boolean = false,
    onDismissRequest: () -> Unit,
    expanded: Boolean = false,
    onAction: (NodeMenuAction) -> Unit
) {
    var displayIgnoreDialog by remember { mutableStateOf(false) }
    var displayRemoveDialog by remember { mutableStateOf(false) }
    if (displayIgnoreDialog) {
        SimpleAlertDialog(
            title = R.string.ignore,
            text = stringResource(
                id = if (node.isIgnored) R.string.ignore_remove else R.string.ignore_add,
                node.user.longName
            ),
            onConfirm = {
                displayIgnoreDialog = false
                onAction(NodeMenuAction.Ignore(node))
            },
            onDismiss = {
                displayIgnoreDialog = false
            }
        )
    }
    if (displayRemoveDialog) {
        SimpleAlertDialog(
            title = R.string.remove,
            text = R.string.remove_node_text,
            onConfirm = {
                displayRemoveDialog = false
                onAction(NodeMenuAction.Remove(node))
            },
            onDismiss = {
                displayRemoveDialog = false
            }
        )
    }
    DropdownMenu(
        modifier = Modifier.background(MaterialTheme.colors.background.copy(alpha = 1f)),
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {

        if (showFullMenu) {
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.DirectMessage(node))
                },
                content = { Text(stringResource(R.string.direct_message)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.RequestUserInfo(node))
                },
                content = { Text(stringResource(R.string.request_userinfo)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.RequestPosition(node))
                },
                content = { Text(stringResource(R.string.request_position)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.TraceRoute(node))
                },
                content = { Text(stringResource(R.string.traceroute)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    displayIgnoreDialog = true
                },
            ) {
                Text(stringResource(R.string.ignore))
                Spacer(Modifier.weight(1f))
                Checkbox(
                    checked = node.isIgnored,
                    onCheckedChange = {
                        onDismissRequest()
                        displayIgnoreDialog = true
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    displayRemoveDialog = true
                },
                enabled = !node.isIgnored,
            ) { Text(stringResource(R.string.remove)) }
            Divider(Modifier.padding(vertical = 8.dp))
        }
        DropdownMenuItem(
            onClick = {
                onDismissRequest()
                onAction(NodeMenuAction.MoreDetails(node))
            },
            content = { Text(stringResource(R.string.more_details)) }
        )
    }
}

sealed class NodeMenuAction {
    data class Remove(val node: NodeEntity) : NodeMenuAction()
    data class Ignore(val node: NodeEntity) : NodeMenuAction()
    data class DirectMessage(val node: NodeEntity) : NodeMenuAction()
    data class RequestUserInfo(val node: NodeEntity) : NodeMenuAction()
    data class RequestPosition(val node: NodeEntity) : NodeMenuAction()
    data class TraceRoute(val node: NodeEntity) : NodeMenuAction()
    data class MoreDetails(val node: NodeEntity) : NodeMenuAction()
}
