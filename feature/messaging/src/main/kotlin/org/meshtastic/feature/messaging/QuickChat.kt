/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.messaging

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.add
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.delete
import org.meshtastic.core.strings.message
import org.meshtastic.core.strings.name
import org.meshtastic.core.strings.quick_chat
import org.meshtastic.core.strings.quick_chat_append
import org.meshtastic.core.strings.quick_chat_edit
import org.meshtastic.core.strings.quick_chat_instant
import org.meshtastic.core.strings.quick_chat_new
import org.meshtastic.core.strings.save
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.component.dragContainer
import org.meshtastic.core.ui.component.dragDropItemsIndexed
import org.meshtastic.core.ui.component.rememberDragDropState
import org.meshtastic.core.ui.theme.AppTheme

@Composable
fun QuickChatScreen(
    modifier: Modifier = Modifier,
    viewModel: QuickChatViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
) {
    val actions by viewModel.quickChatActions.collectAsStateWithLifecycle()
    var showActionDialog by remember { mutableStateOf<QuickChatAction?>(null) }

    val listState = rememberLazyListState()
    val dragDropState =
        rememberDragDropState(listState) { fromIndex, toIndex ->
            val list = actions.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            viewModel.updateActionPositions(list)
        }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.quick_chat),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { innerPadding ->
        Box(modifier = modifier.fillMaxSize().padding(innerPadding)) {
            showActionDialog?.let {
                EditQuickChatDialog(
                    action = it,
                    onSave = viewModel::addQuickChatAction,
                    onDelete = viewModel::deleteQuickChatAction,
                ) {
                    showActionDialog = null
                }
            }

            LazyColumn(
                modifier = Modifier.dragContainer(dragDropState = dragDropState, haptics = LocalHapticFeedback.current),
                state = listState,
                contentPadding = PaddingValues(16.dp),
            ) {
                dragDropItemsIndexed(items = actions, dragDropState = dragDropState, key = { _, item -> item.uuid }) {
                        _,
                        action,
                        isDragging,
                    ->
                    QuickChatItem(action = action, onEdit = { showActionDialog = it })
                }
            }

            FloatingActionButton(
                onClick = { showActionDialog = QuickChatAction(position = actions.size) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = stringResource(Res.string.add))
            }
        }
    }
}

@Suppress("MagicNumber")
private fun getMessageName(message: String): String = if (message.length <= 3) {
    message.uppercase()
} else {
    buildString {
        append(message.first().uppercase())
        append(message[message.length / 2].uppercase())
        append(message.last().uppercase())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
private fun EditQuickChatDialog(
    action: QuickChatAction,
    onSave: (QuickChatAction) -> Unit,
    onDelete: (QuickChatAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var actionInput by remember { mutableStateOf(action) }
    val newQuickChat = remember { action.uuid == 0L }
    val isInstant = actionInput.mode == QuickChatAction.Mode.Instant
    val title = if (newQuickChat) Res.string.quick_chat_new else Res.string.quick_chat_edit

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (newQuickChat) {
            focusRequester.requestFocus()
        }
    }

    MeshtasticDialog(
        onDismiss = onDismiss,
        title = stringResource(title),
        confirmText = stringResource(Res.string.save),
        onConfirm = {
            onSave(actionInput)
            onDismiss()
        },
        dismissText = stringResource(Res.string.cancel),
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextFieldWithCounter(
                    label = stringResource(Res.string.name),
                    value = actionInput.name,
                    maxSize = 5,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    actionInput = actionInput.copy(name = it.uppercase())
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextFieldWithCounter(
                    label = stringResource(Res.string.message),
                    value = actionInput.message,
                    maxSize = 200,
                    getSize = { it.toByteArray().size + 1 },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                ) {
                    actionInput = actionInput.copy(message = it)
                    if (newQuickChat) {
                        actionInput = actionInput.copy(name = getMessageName(it))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val (text, icon) =
                    if (isInstant) {
                        Res.string.quick_chat_instant to Icons.Rounded.FastForward
                    } else {
                        Res.string.quick_chat_append to Icons.Rounded.Add
                    }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isInstant) {
                        Icon(imageVector = icon, contentDescription = stringResource(text))
                        Spacer(Modifier.width(12.dp))
                    }

                    Text(text = stringResource(text), modifier = Modifier.weight(1f))

                    Switch(
                        checked = isInstant,
                        onCheckedChange = { checked ->
                            actionInput =
                                actionInput.copy(
                                    mode =
                                    when (checked) {
                                        true -> QuickChatAction.Mode.Instant
                                        false -> QuickChatAction.Mode.Append
                                    },
                                )
                        },
                    )
                }

                if (!newQuickChat) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onDelete(actionInput)
                            onDismiss()
                        },
                    ) {
                        Text(text = stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    )
}

@Composable
private fun OutlinedTextFieldWithCounter(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    maxSize: Int,
    getSize: (String) -> Int = { it.length },
    onValueChange: (String) -> Unit = {},
) = Column(modifier) {
    var isFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            if (getSize(it) <= maxSize) {
                onValueChange(it)
            }
        },
        modifier = Modifier.onFocusEvent { isFocused = it.isFocused },
        label = { Text(text = label) },
        singleLine = singleLine,
    )
    if (isFocused) {
        Text(
            text = "${getSize(value)}/$maxSize",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 16.dp),
        )
    }
}

@Composable
private fun QuickChatItem(
    action: QuickChatAction,
    modifier: Modifier = Modifier,
    onEdit: (QuickChatAction) -> Unit = {},
) {
    Card(modifier = modifier.fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(12.dp)) {
        ListItem(
            leadingContent = {
                if (action.mode == QuickChatAction.Mode.Instant) {
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = stringResource(Res.string.quick_chat_instant),
                    )
                }
            },
            headlineContent = { Text(text = action.name) },
            supportingContent = { Text(text = action.message) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onEdit(action) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(Res.string.quick_chat_edit),
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(Res.string.quick_chat),
                    )
                }
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun QuickChatItemPreview() {
    AppTheme { QuickChatItem(action = QuickChatAction(name = "TST", message = "Test", position = 0)) }
}

@PreviewLightDark
@Composable
private fun EditQuickChatDialogPreview() {
    AppTheme {
        EditQuickChatDialog(
            action = QuickChatAction(name = "TST", message = "Test", position = 0),
            onSave = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}
