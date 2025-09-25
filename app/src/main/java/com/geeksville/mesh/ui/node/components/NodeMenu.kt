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

package com.geeksville.mesh.ui.node.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.twotone.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.component.SimpleAlertDialog
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.isUnmessageableRole
import org.meshtastic.core.strings.R

@Suppress("LongMethod")
@Composable
fun NodeMenu(
    expanded: Boolean,
    node: Node,
    showFullMenu: Boolean = false,
    onDismissMenuRequest: () -> Unit,
    onAction: (NodeMenuAction) -> Unit,
) {
    val isUnmessageable =
        if (node.user.hasIsUnmessagable()) {
            node.user.isUnmessagable
        } else {
            // for older firmwares
            node.user.role?.isUnmessageableRole() == true
        }

    var displayFavoriteDialog by remember { mutableStateOf(false) }
    var displayIgnoreDialog by remember { mutableStateOf(false) }
    var displayRemoveDialog by remember { mutableStateOf(false) }
    val dialogDismissRequest = {
        displayFavoriteDialog = false
        displayIgnoreDialog = false
        displayRemoveDialog = false
        onDismissMenuRequest()
    }
    val onMenuAction: (NodeMenuAction) -> Unit = {
        dialogDismissRequest()
        onDismissMenuRequest()
        onAction(it)
    }
    NodeActionDialogs(
        node = node,
        displayFavoriteDialog = displayFavoriteDialog,
        displayIgnoreDialog = displayIgnoreDialog,
        displayRemoveDialog = displayRemoveDialog,
        onDismissMenuRequest = dialogDismissRequest,
        onAction = onMenuAction,
    )
    DropdownMenu(
        modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 1f)),
        expanded = expanded,
        onDismissRequest = onDismissMenuRequest,
    ) {
        if (showFullMenu) {
            if (!isUnmessageable) {
                DropdownMenuItem(
                    onClick = {
                        dialogDismissRequest()
                        onMenuAction(NodeMenuAction.DirectMessage(node))
                    },
                    text = { Text(stringResource(R.string.direct_message)) },
                )
            }
            DropdownMenuItem(
                onClick = {
                    dialogDismissRequest()
                    onMenuAction(NodeMenuAction.RequestUserInfo(node))
                },
                text = { Text(stringResource(R.string.exchange_userinfo)) },
            )
            DropdownMenuItem(
                onClick = {
                    dialogDismissRequest()
                    onMenuAction(NodeMenuAction.RequestPosition(node))
                },
                text = { Text(stringResource(R.string.exchange_position)) },
            )
            DropdownMenuItem(
                onClick = {
                    dialogDismissRequest()
                    onMenuAction(NodeMenuAction.TraceRoute(node))
                },
                text = { Text(stringResource(R.string.traceroute)) },
            )
            DropdownMenuItem(
                onClick = {
                    dialogDismissRequest()
                    displayFavoriteDialog = true
                },
                enabled = !node.isIgnored,
                text = { Text(stringResource(R.string.favorite)) },
                trailingIcon = {
                    Icon(
                        imageVector = if (node.isFavorite) Icons.Filled.Star else Icons.TwoTone.StarBorder,
                        contentDescription = stringResource(R.string.favorite),
                    )
                },
            )
            DropdownMenuItem(
                onClick = {
                    dialogDismissRequest()
                    displayIgnoreDialog = true
                },
                text = { Text(stringResource(R.string.ignore)) },
                trailingIcon = {
                    Checkbox(
                        checked = node.isIgnored,
                        onCheckedChange = {
                            dialogDismissRequest()
                            displayIgnoreDialog = true
                        },
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            DropdownMenuItem(
                onClick = {
                    dialogDismissRequest()
                    displayRemoveDialog = true
                },
                enabled = !node.isIgnored,
                text = { Text(stringResource(R.string.remove)) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
        DropdownMenuItem(
            onClick = {
                dialogDismissRequest()
                onMenuAction(NodeMenuAction.Share(node))
            },
            text = { Text(stringResource(R.string.share_contact)) },
        )

        DropdownMenuItem(
            onClick = {
                dialogDismissRequest()
                onMenuAction(NodeMenuAction.MoreDetails(node))
            },
            text = { Text(stringResource(R.string.more_details)) },
        )
    }
}

@Composable
fun NodeActionDialogs(
    node: Node,
    displayFavoriteDialog: Boolean,
    displayIgnoreDialog: Boolean,
    displayRemoveDialog: Boolean,
    onDismissMenuRequest: () -> Unit,
    onAction: (NodeMenuAction) -> Unit,
) {
    if (displayFavoriteDialog) {
        SimpleAlertDialog(
            title = R.string.favorite,
            text =
            stringResource(
                id = if (node.isFavorite) R.string.favorite_remove else R.string.favorite_add,
                node.user.longName,
            ),
            onConfirm = {
                onDismissMenuRequest()
                onAction(NodeMenuAction.Favorite(node))
            },
            onDismiss = onDismissMenuRequest,
        )
    }
    if (displayIgnoreDialog) {
        SimpleAlertDialog(
            title = R.string.ignore,
            text =
            stringResource(
                id = if (node.isIgnored) R.string.ignore_remove else R.string.ignore_add,
                node.user.longName,
            ),
            onConfirm = {
                onDismissMenuRequest()
                onAction(NodeMenuAction.Ignore(node))
            },
            onDismiss = onDismissMenuRequest,
        )
    }
    if (displayRemoveDialog) {
        SimpleAlertDialog(
            title = R.string.remove,
            text = R.string.remove_node_text,
            onConfirm = {
                onDismissMenuRequest()
                onAction(NodeMenuAction.Remove(node))
            },
            onDismiss = onDismissMenuRequest,
        )
    }
}

sealed class NodeMenuAction {
    data class Remove(val node: Node) : NodeMenuAction()

    data class Ignore(val node: Node) : NodeMenuAction()

    data class Favorite(val node: Node) : NodeMenuAction()

    data class DirectMessage(val node: Node) : NodeMenuAction()

    data class RequestUserInfo(val node: Node) : NodeMenuAction()

    data class RequestPosition(val node: Node) : NodeMenuAction()

    data class TraceRoute(val node: Node) : NodeMenuAction()

    data class MoreDetails(val node: Node) : NodeMenuAction()

    data class Share(val node: Node) : NodeMenuAction()
}
