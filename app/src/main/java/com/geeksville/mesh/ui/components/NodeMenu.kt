package com.geeksville.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
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
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.NodeEntity

@Suppress("LongMethod")
@Composable
fun NodeMenu(
    node: NodeEntity,
    ignoreIncomingList: List<Int>,
    isThisNode: Boolean = false,
    onMenuItemAction: (MenuItemAction) -> Unit,
    onDismissRequest: () -> Unit,
    expanded: Boolean = false,
    isConnected: Boolean = false,
) {
    val isIgnored = ignoreIncomingList.contains(node.num)
    var displayIgnoreDialog by remember { mutableStateOf(false) }
    var displayRemoveDialog by remember { mutableStateOf(false) }
    if (displayIgnoreDialog) {
        SimpleAlertDialog(
            title = R.string.ignore,
            text = stringResource(
                id = if (isIgnored) R.string.ignore_remove else R.string.ignore_add,
                node.user.longName
            ),
            onConfirm = {
                displayIgnoreDialog = false
                onMenuItemAction(MenuItemAction.Ignore)
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
                onMenuItemAction(MenuItemAction.Remove)
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

        if (!isThisNode && isConnected) {
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onMenuItemAction(MenuItemAction.DirectMessage)
                },
                content = { Text(stringResource(R.string.direct_message)) }
            )
            Divider()
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onMenuItemAction(MenuItemAction.RequestUserInfo)
                },
                content = { Text(stringResource(R.string.request_userinfo)) }
            )
            Divider()
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onMenuItemAction(MenuItemAction.RequestPosition)
                },
                content = { Text(stringResource(R.string.request_position)) }
            )
            Divider()
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onMenuItemAction(MenuItemAction.TraceRoute)
                },
                content = { Text(stringResource(R.string.traceroute)) }
            )
            Divider()
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    displayRemoveDialog = true
                },
                content = { Text(stringResource(R.string.remove)) },
            )
            Divider()
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    displayIgnoreDialog = true
                },
                content = {
                    Text(stringResource(R.string.ignore))
                    Checkbox(
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary),
                        checked = isIgnored,
                        onCheckedChange = {
                            onDismissRequest()
                            displayIgnoreDialog = true
                        },
                    )
                },
                enabled = ignoreIncomingList.size < 3 || isIgnored
            )
            Divider()
        }
        DropdownMenuItem(
            onClick = {
                onDismissRequest()
                onMenuItemAction(MenuItemAction.MoreDetails)
            },
            content = { Text(stringResource(R.string.more_details)) }
        )
    }
}

enum class MenuItemAction {
    Remove,
    Ignore,
    DirectMessage,
    RequestUserInfo,
    RequestPosition,
    TraceRoute,
    MoreDetails
}
