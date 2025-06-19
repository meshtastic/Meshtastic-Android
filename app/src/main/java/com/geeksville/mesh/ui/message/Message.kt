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

package com.geeksville.mesh.ui.message

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.model.Message
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.getChannel
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.node.components.NodeKeyStatusIcon
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import com.geeksville.mesh.ui.sharing.SharedContactDialog
import kotlinx.coroutines.launch

private const val MESSAGE_CHARACTER_LIMIT = 200
private const val SNIPPET_CHARACTER_LIMIT = 50

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun MessageScreen(
    contactKey: String,
    message: String,
    viewModel: UIViewModel = hiltViewModel(),
    navigateToMessages: (String) -> Unit,
    navigateToNodeDetails: (Int) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current

    val channelIndex = contactKey[0].digitToIntOrNull()
    val nodeId = contactKey.substring(1)
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val channelName by remember(channelIndex) {
        derivedStateOf {
            channelIndex?.let { channels.getChannel(it)?.name } ?: "Unknown Channel"
        }
    }

    val title = when (nodeId) {
        DataPacket.ID_BROADCAST -> channelName
        else -> viewModel.getUser(nodeId).longName
    }
    viewModel.setTitle(title)
    val mismatchKey =
        DataPacket.PKC_CHANNEL_INDEX == channelIndex && viewModel.getNode(nodeId).mismatchKey

//    if (channelIndex != DataPacket.PKC_CHANNEL_INDEX && nodeId != DataPacket.ID_BROADCAST) {
//        subtitle = "(ch: $channelIndex - $channelName)"
//    }

    val selectedIds = rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val inSelectionMode by remember { derivedStateOf { selectedIds.value.isNotEmpty() } }

    val connState by viewModel.connectionState.collectAsStateWithLifecycle()
    val quickChat by viewModel.quickChatActions.collectAsStateWithLifecycle()
    val messages by viewModel.getMessagesFrom(contactKey).collectAsStateWithLifecycle(listOf())

    val messageInput = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(message))
    }
    var replyingTo by remember { mutableStateOf<Message?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        DeleteMessageDialog(
            size = selectedIds.value.size,
            onConfirm = {
                viewModel.deleteMessages(selectedIds.value.toList())
                selectedIds.value = emptySet()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Scaffold(
        topBar = {
            if (inSelectionMode) {
                ActionModeTopBar(selectedIds.value) { action ->
                    when (action) {
                        MessageMenuAction.ClipboardCopy -> coroutineScope.launch {
                            val copiedText = messages
                                .filter { it.uuid in selectedIds.value }
                                .joinToString("\n") { it.text }

                            val clipData = ClipData.newPlainText("", AnnotatedString(copiedText))
                            clipboardManager.setClipEntry(ClipEntry(clipData))
                            selectedIds.value = emptySet()
                        }

                        MessageMenuAction.Delete -> {
                            showDeleteDialog = true
                        }

                        MessageMenuAction.Dismiss -> selectedIds.value = emptySet()
                        MessageMenuAction.SelectAll -> {
                            if (selectedIds.value.size == messages.size) {
                                selectedIds.value = emptySet()
                            } else {
                                selectedIds.value = messages.map { it.uuid }.toSet()
                            }
                        }
                    }
                }
            } else {
                MessageTopBar(title, channelIndex, mismatchKey, onNavigateBack)
            }
        },
        bottomBar = {
            val isConnected = connState.isConnected()
            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            ) {
                QuickChatRow(isConnected, quickChat) { action ->
                    if (action.mode == QuickChatAction.Mode.Append) {
                        val originalText = messageInput.value.text
                        if (!originalText.contains(action.message)) {
                            val needsSpace =
                                !originalText.endsWith(' ') && originalText.isNotEmpty()
                            val newText = buildString {
                                append(originalText)
                                if (needsSpace) append(' ')
                                append(action.message)
                            }.take(MESSAGE_CHARACTER_LIMIT)
                            messageInput.value = TextFieldValue(newText, TextRange(newText.length))
                        }
                    } else {
                        viewModel.sendMessage(action.message, contactKey)
                    }
                }

                AnimatedVisibility(visible = replyingTo != null) {
                    val fromLocal = replyingTo?.node?.user?.id == DataPacket.ID_LOCAL

                    val replyingToNode = if (fromLocal) {
                        viewModel.ourNodeInfo.value
                    } else {
                        replyingTo?.node
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.Reply,
                            contentDescription = stringResource(R.string.reply)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Replying to ${replyingToNode?.user?.shortName ?: stringResource(R.string.unknown)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                replyingTo?.text?.take(SNIPPET_CHARACTER_LIMIT)
                                    ?.let { if (it.length == SNIPPET_CHARACTER_LIMIT) "$it…" else it } ?: "", // Snippet
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            replyingTo = null
                        }) { // ViewModel function to set replyingToMessageState to null
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                }
                TextInput(isConnected, messageInput) { message ->
                    replyingTo?.let {
                        viewModel.sendMessage(message, contactKey, it.packetId)
                        replyingTo = null
                    } ?: viewModel.sendMessage(message, contactKey)
                    // Clear the text input after sending the message and updating all state
                    messageInput.value = TextFieldValue("")
                }
            }
        }
    ) { padding ->
        if (messages.isNotEmpty()) {
            var sharedContact: Node? by remember { mutableStateOf(null) }
            if (sharedContact != null) {
                SharedContactDialog(
                    contact = sharedContact,
                    onDismiss = { sharedContact = null }
                )
            }

            MessageList(
                modifier = Modifier.padding(padding),
                messages = messages,
                selectedIds = selectedIds,
                onUnreadChanged = { viewModel.clearUnreadCount(contactKey, it) },
                onSendReaction = { emoji, id -> viewModel.sendReaction(emoji, id, contactKey) },
                viewModel = viewModel,
                contactKey = contactKey,
                onReply = { replyingTo = it },
                onNodeMenuAction = { action ->
                    when (action) {
                        is NodeMenuAction.DirectMessage -> {
                            val hasPKC =
                                viewModel.ourNodeInfo.value?.hasPKC == true && action.node.hasPKC
                            val channel =
                                if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else action.node.channel
                            navigateToMessages("$channel${action.node.user.id}")
                        }

                        is NodeMenuAction.MoreDetails -> navigateToNodeDetails(action.node.num)
                        is NodeMenuAction.Share -> sharedContact = action.node
                        else -> viewModel.handleNodeMenuAction(action)
                    }
                },
            )
        }
    }
}

@Composable
private fun DeleteMessageDialog(
    size: Int,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val deleteMessagesString = pluralStringResource(R.plurals.delete_messages, size, size)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        text = {
            Text(
                text = deleteMessagesString,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

sealed class MessageMenuAction {
    data object ClipboardCopy : MessageMenuAction()
    data object Delete : MessageMenuAction()
    data object Dismiss : MessageMenuAction()
    data object SelectAll : MessageMenuAction()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionModeTopBar(
    selectedList: Set<Long>,
    onAction: (MessageMenuAction) -> Unit,
) = TopAppBar(
    title = { Text(text = selectedList.size.toString()) },
    navigationIcon = {
        IconButton(onClick = { onAction(MessageMenuAction.Dismiss) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.clear),
            )
        }
    },
    actions = {
        IconButton(onClick = { onAction(MessageMenuAction.ClipboardCopy) }) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(id = R.string.copy)
            )
        }
        IconButton(onClick = { onAction(MessageMenuAction.Delete) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(id = R.string.delete)
            )
        }
        IconButton(onClick = { onAction(MessageMenuAction.SelectAll) }) {
            Icon(
                imageVector = Icons.Default.SelectAll,
                contentDescription = stringResource(id = R.string.select_all)
            )
        }
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageTopBar(
    title: String,
    channelIndex: Int?,
    mismatchKey: Boolean = false,
    onNavigateBack: () -> Unit
) = TopAppBar(
    title = { Text(text = title) },
    navigationIcon = {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.navigate_back),
            )
        }
    },
    actions = {
        if (channelIndex == DataPacket.PKC_CHANNEL_INDEX) {
            NodeKeyStatusIcon(hasPKC = true, mismatchKey = mismatchKey)
        }
    }
)

@Composable
private fun QuickChatRow(
    enabled: Boolean,
    actions: List<QuickChatAction>,
    modifier: Modifier = Modifier,
    onClick: (QuickChatAction) -> Unit
) {
    val alertAction = QuickChatAction(
        name = "🔔",
        message = "🔔 ${stringResource(R.string.alert_bell_text)} \u0007",
        mode = QuickChatAction.Mode.Append,
        position = -1
    )

    LazyRow(
        modifier = modifier,
    ) {
        items(listOf(alertAction) + actions, key = { it.uuid }) { action ->
            Button(
                onClick = { onClick(action) },
                modifier = Modifier.padding(horizontal = 4.dp),
                enabled = enabled,
            ) {
                Text(
                    text = action.name,
                )
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun TextInput(
    enabled: Boolean,
    message: MutableState<TextFieldValue>,
    modifier: Modifier = Modifier,
    maxSize: Int = MESSAGE_CHARACTER_LIMIT,
    onClick: (String) -> Unit = {}
) = Column(modifier) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = message.value,
        onValueChange = {
            if (it.text.toByteArray().size <= maxSize) {
                message.value = it
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusEvent { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.key == Key.Enter &&
                    event.isAltPressed
                ) {
                    val str = message.value.text.trim()
                    if (str.isNotEmpty()) {
                        onClick(str)
                    }
                    true // Consume the event
                } else {
                    false // Do not consume other key events
                }
            },
        enabled = enabled,
        placeholder = { Text(stringResource(id = R.string.send_text)) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Send
        ),
        keyboardActions = KeyboardActions(
            onSend = {
                val str = message.value.text.trim()
                if (str.isNotEmpty()) {
                    onClick(str)
                }
            }
        ),
        maxLines = 3,
        shape = RoundedCornerShape(24.dp),
        trailingIcon = {
            IconButton(
                onClick = {
                    val str = message.value.text.trim()
                    if (str.isNotEmpty()) {
                        onClick(str)
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier.size(48.dp),
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.Send,
                    contentDescription = stringResource(id = R.string.send_text),
                )
            }
        }
    )
    if (isFocused) {
        Text(
            text = "${message.value.text.toByteArray().size}/$maxSize",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp, end = 72.dp)
        )
    }
}

@PreviewLightDark
@Composable
private fun TextInputPreview() {
    AppTheme {
        Surface {
            Column {
                TextInput(
                    enabled = true,
                    message = remember { mutableStateOf(TextFieldValue("")) },
                )
                Spacer(Modifier.size(16.dp))
                TextInput(
                    enabled = true,
                    message = remember { mutableStateOf(TextFieldValue("Hello")) },
                )
            }
        }
    }
}
