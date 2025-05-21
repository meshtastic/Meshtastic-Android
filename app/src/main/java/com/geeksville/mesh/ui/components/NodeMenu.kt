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

package com.geeksville.mesh.ui.components

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
import com.geeksville.mesh.R
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.isUnmessageableRole
import com.geeksville.mesh.ui.supportsQrCodeSharing

@Suppress("LongMethod")
@Composable
fun NodeMenu(
    node: Node,
    showFullMenu: Boolean = false,
    onDismissRequest: () -> Unit,
    expanded: Boolean = false,
    onAction: (NodeMenuAction) -> Unit,
    firmwareVersion: String? = null,
) {
    val isUnmessageable = if (node.user.hasIsUnmessagable()) {
        node.user.isUnmessagable
    } else {
        // for older firmwares
        node.user.role?.isUnmessageableRole() == true
    }
    var displayFavoriteDialog by remember { mutableStateOf(false) }
    var displayIgnoreDialog by remember { mutableStateOf(false) }
    var displayRemoveDialog by remember { mutableStateOf(false) }
    if (displayFavoriteDialog) {
        SimpleAlertDialog(
            title = R.string.favorite,
            text = stringResource(
                id = if (node.isFavorite) R.string.favorite_remove else R.string.favorite_add,
                node.user.longName
            ),
            onConfirm = {
                displayFavoriteDialog = false
                onAction(NodeMenuAction.Favorite(node))
            },
            onDismiss = {
                displayFavoriteDialog = false
            }
        )
    }
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
        modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 1f)),
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {

        if (showFullMenu) {
            if (!isUnmessageable) {
                DropdownMenuItem(
                    onClick = {
                        onDismissRequest()
                        onAction(NodeMenuAction.DirectMessage(node))
                    },
                    text = { Text(stringResource(R.string.direct_message)) }
                )
            }
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.RequestUserInfo(node))
                },
                text = { Text(stringResource(R.string.exchange_userinfo)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.RequestPosition(node))
                },
                text = { Text(stringResource(R.string.exchange_position)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.TraceRoute(node))
                },
                text = { Text(stringResource(R.string.traceroute)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    displayFavoriteDialog = true
                },
                enabled = !node.isIgnored,
                text = {
                    Text(stringResource(R.string.favorite))
                },
                trailingIcon = {
                    Icon(
                        imageVector = if (node.isFavorite) Icons.Filled.Star else Icons.TwoTone.StarBorder,
                        contentDescription = "Favorite",
                    )
                }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    displayIgnoreDialog = true
                },
                text = {
                    Text(stringResource(R.string.ignore))
                },
                trailingIcon = {
                    Checkbox(
                        checked = node.isIgnored,
                        onCheckedChange = {
                            onDismissRequest()
                            displayIgnoreDialog = true
                        },
                        modifier = Modifier.size(24.dp),
                    )
                }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    displayRemoveDialog = true
                },
                enabled = !node.isIgnored,
                text = { Text(stringResource(R.string.remove)) }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
        val firmware = DeviceVersion(firmwareVersion ?: "0.0.0")
        if (firmware.supportsQrCodeSharing()) {
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.Share(node))
                },
                text = { Text(stringResource(R.string.share_contact)) }
            )
        }
        DropdownMenuItem(
            onClick = {
                onDismissRequest()
                onAction(NodeMenuAction.MoreDetails(node))
            },
            text = { Text(stringResource(R.string.more_details)) }
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
