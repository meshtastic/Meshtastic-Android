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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.SimpleAlertDialog

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
