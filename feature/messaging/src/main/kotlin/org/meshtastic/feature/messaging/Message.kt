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

@file:Suppress("TooManyFunctions")

package org.meshtastic.feature.messaging

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SpeakerNotes
import androidx.compose.material.icons.filled.SpeakerNotesOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.NodeKeyStatusIcon
import org.meshtastic.core.ui.component.SecurityIcon
import org.meshtastic.core.ui.component.SharedContactDialog
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.AppOnlyProtos
import java.nio.charset.StandardCharsets

private const val MESSAGE_CHARACTER_LIMIT_BYTES = 200
private const val SNIPPET_CHARACTER_LIMIT = 50
private const val ROUNDED_CORNER_PERCENT = 100

/**
 * The main screen for displaying and sending messages to a contact or channel.
 *
 * @param contactKey A unique key identifying the contact or channel.
 * @param message An optional message to pre-fill in the input field.
 * @param viewModel The [MessageViewModel] instance for handling business logic and state.
 * @param navigateToMessages Callback to navigate to a different message thread.
 * @param navigateToNodeDetails Callback to navigate to a node's detail screen.
 * @param onNavigateBack Callback to navigate back from this screen.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // Due to multiple states and event handling
@Composable
fun MessageScreen(
    contactKey: String,
    message: String,
    viewModel: MessageViewModel = hiltViewModel(),
    navigateToMessages: (String) -> Unit,
    navigateToNodeDetails: (Int) -> Unit,
    navigateToQuickChatOptions: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current

    val nodes by viewModel.nodeList.collectAsStateWithLifecycle()
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val quickChatActions by viewModel.quickChatActions.collectAsStateWithLifecycle(initialValue = emptyList())
    val messages by viewModel.getMessagesFrom(contactKey).collectAsStateWithLifecycle(initialValue = emptyList())

    // UI State managed within this Composable
    var replyingToPacketId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var sharedContact by rememberSaveable { mutableStateOf<Node?>(null) }
    val selectedMessageIds = rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val messageInputState = rememberTextFieldState(message)
    val showQuickChat by viewModel.showQuickChat.collectAsStateWithLifecycle()

    // Derived state, memoized for performance
    val channelInfo =
        remember(contactKey, channels) {
            val index = contactKey.firstOrNull()?.digitToIntOrNull()
            val id = contactKey.substring(1)
            val name = index?.let { channels.getChannel(it)?.name } // channels can be null initially
            Triple(index, id, name)
        }
    val (channelIndex, nodeId, rawChannelName) = channelInfo
    val unknownChannelText = stringResource(id = R.string.unknown_channel)
    val channelName = rawChannelName ?: unknownChannelText

    val title =
        remember(nodeId, channelName, viewModel) {
            when (nodeId) {
                DataPacket.ID_BROADCAST -> channelName
                else -> viewModel.getUser(nodeId).longName
            }
        }

    val isMismatchKey =
        remember(channelIndex, nodeId, viewModel) {
            channelIndex == DataPacket.PKC_CHANNEL_INDEX && viewModel.getNode(nodeId).mismatchKey
        }

    val inSelectionMode by remember { derivedStateOf { selectedMessageIds.value.isNotEmpty() } }

    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = remember(messages) { messages.indexOfLast { !it.read }.coerceAtLeast(0) },
        )

    val onEvent: (MessageScreenEvent) -> Unit =
        remember(viewModel, contactKey, messageInputState, ourNode) {
            { event ->
                when (event) {
                    is MessageScreenEvent.SendMessage -> {
                        viewModel.sendMessage(event.text, contactKey, event.replyingToPacketId)
                        if (event.replyingToPacketId != null) replyingToPacketId = null
                        messageInputState.clearText()
                    }

                    is MessageScreenEvent.SendReaction ->
                        viewModel.sendReaction(event.emoji, event.messageId, contactKey)

                    is MessageScreenEvent.DeleteMessages -> {
                        viewModel.deleteMessages(event.ids)
                        selectedMessageIds.value = emptySet()
                        showDeleteDialog = false
                    }

                    is MessageScreenEvent.ClearUnreadCount ->
                        viewModel.clearUnreadCount(contactKey, event.lastReadMessageId)

                    is MessageScreenEvent.NodeDetails -> navigateToNodeDetails(event.node.num)

                    is MessageScreenEvent.SetTitle -> viewModel.setTitle(event.title)
                    is MessageScreenEvent.NavigateToMessages -> navigateToMessages(event.contactKey)
                    is MessageScreenEvent.NavigateToNodeDetails -> navigateToNodeDetails(event.nodeNum)
                    MessageScreenEvent.NavigateBack -> onNavigateBack()
                    is MessageScreenEvent.CopyToClipboard -> {
                        clipboardManager.nativeClipboard.setPrimaryClip(ClipData.newPlainText(event.text, event.text))
                        selectedMessageIds.value = emptySet()
                    }
                }
            }
        }

    if (showDeleteDialog) {
        DeleteMessageDialog(
            count = selectedMessageIds.value.size,
            onConfirm = { onEvent(MessageScreenEvent.DeleteMessages(selectedMessageIds.value.toList())) },
            onDismiss = { showDeleteDialog = false },
        )
    }

    sharedContact?.let { contact -> SharedContactDialog(contact = contact, onDismiss = { sharedContact = null }) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (inSelectionMode) {
                ActionModeTopBar(
                    selectedCount = selectedMessageIds.value.size,
                    onAction = { action ->
                        when (action) {
                            MessageMenuAction.ClipboardCopy -> {
                                val copiedText =
                                    messages
                                        .filter { it.uuid in selectedMessageIds.value }
                                        .joinToString("\n") { it.text }
                                onEvent(MessageScreenEvent.CopyToClipboard(copiedText))
                            }

                            MessageMenuAction.Delete -> showDeleteDialog = true
                            MessageMenuAction.Dismiss -> selectedMessageIds.value = emptySet()
                            MessageMenuAction.SelectAll -> {
                                selectedMessageIds.value =
                                    if (selectedMessageIds.value.size == messages.size) {
                                        emptySet()
                                    } else {
                                        messages.map { it.uuid }.toSet()
                                    }
                            }
                        }
                    },
                )
            } else {
                MessageTopBar(
                    title = title,
                    channelIndex = channelIndex,
                    mismatchKey = isMismatchKey,
                    onNavigateBack = { onEvent(MessageScreenEvent.NavigateBack) },
                    channels = channels,
                    channelIndexParam = channelIndex,
                    showQuickChat = showQuickChat,
                    onToggleQuickChat = viewModel::toggleShowQuickChat,
                    onNavigateToQuickChatOptions = navigateToQuickChatOptions,
                )
            }
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            Box(modifier = Modifier.weight(1f)) {
                MessageList(
                    nodes = nodes,
                    ourNode = ourNode,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    messages = messages,
                    selectedIds = selectedMessageIds,
                    onUnreadChanged = { messageId -> onEvent(MessageScreenEvent.ClearUnreadCount(messageId)) },
                    onSendReaction = { emoji, id -> onEvent(MessageScreenEvent.SendReaction(emoji, id)) },
                    onDeleteMessages = { viewModel.deleteMessages(it) },
                    onSendMessage = { text, contactKey -> viewModel.sendMessage(text, contactKey) },
                    contactKey = contactKey,
                    onReply = { message -> replyingToPacketId = message?.packetId },
                    onClickChip = { onEvent(MessageScreenEvent.NodeDetails(it)) },
                )
                // Show FAB if we can scroll towards the newest messages (index 0).
                if (listState.canScrollBackward) {
                    ScrollToBottomFab(coroutineScope, listState)
                }
            }
            AnimatedVisibility(visible = showQuickChat) {
                QuickChatRow(
                    enabled = connectionState.isConnected(),
                    actions = quickChatActions,
                    userLatitude = ourNode?.takeIf { it.validPosition != null }?.latitude,
                    userLongitude = ourNode?.takeIf { it.validPosition != null }?.longitude,
                    onClick = { action ->
                        handleQuickChatAction(
                            action = action,
                            messageInputState = messageInputState,
                            userLatitude = ourNode?.takeIf { it.validPosition != null }?.latitude,
                            userLongitude = ourNode?.takeIf { it.validPosition != null }?.longitude,
                            onSendMessage = { text -> onEvent(MessageScreenEvent.SendMessage(text)) },
                        )
                    },
                )
            }
            val originalMessage by
                remember(replyingToPacketId, messages) {
                    derivedStateOf {
                        replyingToPacketId?.let { messages.firstOrNull { it.packetId == replyingToPacketId } }
                    }
                }
            ReplySnippet(
                originalMessage = originalMessage,
                onClearReply = { replyingToPacketId = null },
                ourNode = ourNode,
            )
            MessageInput(
                isEnabled = connectionState.isConnected(),
                textFieldState = messageInputState,
                onSendMessage = {
                    val messageText = messageInputState.text.toString().trim()
                    if (messageText.isNotEmpty()) {
                        onEvent(MessageScreenEvent.SendMessage(messageText, replyingToPacketId))
                    }
                },
            )
        }
    }
}

/**
 * A FloatingActionButton that scrolls the message list to the bottom (most recent messages).
 *
 * @param coroutineScope The coroutine scope for launching the scroll animation.
 * @param listState The [LazyListState] of the message list.
 */
@Composable
private fun BoxScope.ScrollToBottomFab(coroutineScope: CoroutineScope, listState: LazyListState) {
    FloatingActionButton(
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        onClick = {
            coroutineScope.launch {
                // Assuming messages are ordered with the newest at index 0
                listState.animateScrollToItem(0)
            }
        },
    ) {
        Icon(
            imageVector = Icons.Default.ArrowDownward,
            contentDescription = stringResource(id = R.string.scroll_to_bottom),
        )
    }
}

/**
 * Displays a snippet of the message being replied to.
 *
 * @param originalMessage The message being replied to, or null if not replying.
 * @param onClearReply Callback to clear the reply state.
 * @param ourNode The current user's node information, to display "You" if replying to self.
 */
@Composable
private fun ReplySnippet(originalMessage: Message?, onClearReply: () -> Unit, ourNode: Node?) {
    AnimatedVisibility(visible = originalMessage != null) {
        originalMessage?.let { message ->
            val isFromLocalUser = message.node.user.id == DataPacket.ID_LOCAL
            val replyingToNodeUser = if (isFromLocalUser) ourNode?.user else message.node.user
            val unknownUserText = stringResource(R.string.unknown)

            Row(
                modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.Reply,
                    contentDescription = stringResource(R.string.reply), // Decorative
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.replying_to, replyingToNodeUser?.shortName ?: unknownUserText),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = message.text.ellipsize(SNIPPET_CHARACTER_LIMIT),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onClearReply) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cancel_reply), // Specific action
                    )
                }
            }
        }
    }
}

/**
 * Ellipsizes a string if its length exceeds [maxLength].
 *
 * @param maxLength The maximum number of characters to display before adding "…".
 * @return The ellipsized string.
 * @receiver The string to ellipsize.
 */
private fun String.ellipsize(maxLength: Int): String = if (length > maxLength) "${take(maxLength)}…" else this

/**
 * Handles a quick chat action, either appending its message to the input field or sending it directly.
 *
 * @param action The [QuickChatAction] to handle.
 * @param messageInputState The [TextFieldState] of the message input field.
 * @param userLatitude Current user latitude, if available.
 * @param userLongitude Current user longitude, if available.
 * @param onSendMessage Lambda to call when a message needs to be sent.
 */
private fun handleQuickChatAction(
    action: QuickChatAction,
    messageInputState: TextFieldState,
    userLatitude: Double?,
    userLongitude: Double?,
    onSendMessage: (String) -> Unit,
) {
    val processedMessage = if (userLatitude != null && userLongitude != null) {
        val gpsString = "%.7f,%.7f".format(userLatitude, userLongitude)
        action.message.replace("%GPS", gpsString, ignoreCase = true)
    } else {
        action.message
    }

    when (action.mode) {
        QuickChatAction.Mode.Append -> {
            val originalText = messageInputState.text.toString()
            // Avoid appending if the exact message is already present (simple check)
            if (!originalText.contains(processedMessage)) {
                val newText =
                    buildString {
                        append(originalText)
                        if (originalText.isNotEmpty() && !originalText.endsWith(' ')) {
                            append(' ')
                        }
                        append(processedMessage)
                    }
                        .limitBytes(MESSAGE_CHARACTER_LIMIT_BYTES)
                messageInputState.setTextAndPlaceCursorAtEnd(newText)
            }
        }

        QuickChatAction.Mode.Instant -> {
            // Byte limit for 'Send' mode messages is handled by the backend/transport layer.
            onSendMessage(processedMessage)
        }
    }
}

/**
 * Truncates a string to ensure its UTF-8 byte representation does not exceed [maxBytes].
 *
 * This implementation iterates by characters and checks byte length to avoid splitting multi-byte characters.
 *
 * @param maxBytes The maximum allowed byte length.
 * @return The truncated string, or the original string if it's within the byte limit.
 * @receiver The string to limit.
 */
private fun String.limitBytes(maxBytes: Int): String {
    val bytes = this.toByteArray(StandardCharsets.UTF_8)
    if (bytes.size <= maxBytes) {
        return this
    }

    var currentBytesSum = 0
    var validCharCount = 0
    for (charIndex in this.indices) {
        val charToTest = this[charIndex]
        val charBytes = charToTest.toString().toByteArray(StandardCharsets.UTF_8).size
        if (currentBytesSum + charBytes > maxBytes) {
            break
        }
        currentBytesSum += charBytes
        validCharCount++
    }
    return this.substring(0, validCharCount)
}

/**
 * A dialog confirming the deletion of messages.
 *
 * @param count The number of messages to be deleted.
 * @param onConfirm Callback invoked when the user confirms the deletion.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
private fun DeleteMessageDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val deleteMessagesString = pluralStringResource(R.plurals.delete_messages, count, count)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text(stringResource(R.string.delete_messages_title)) },
        text = { Text(text = deleteMessagesString) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Actions available in the message selection mode's top bar. */
internal sealed class MessageMenuAction {
    data object ClipboardCopy : MessageMenuAction()

    data object Delete : MessageMenuAction()

    data object Dismiss : MessageMenuAction()

    data object SelectAll : MessageMenuAction()
}

/**
 * The top app bar displayed when in message selection mode.
 *
 * @param selectedCount The number of currently selected messages.
 * @param onAction Callback for when a menu action is triggered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionModeTopBar(selectedCount: Int, onAction: (MessageMenuAction) -> Unit) = TopAppBar(
    title = { Text(text = selectedCount.toString()) },
    navigationIcon = {
        IconButton(onClick = { onAction(MessageMenuAction.Dismiss) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.clear_selection),
            )
        }
    },
    actions = {
        IconButton(onClick = { onAction(MessageMenuAction.ClipboardCopy) }) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = stringResource(id = R.string.copy))
        }
        IconButton(onClick = { onAction(MessageMenuAction.Delete) }) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(id = R.string.delete))
        }
        IconButton(onClick = { onAction(MessageMenuAction.SelectAll) }) {
            Icon(
                imageVector = Icons.Default.SelectAll,
                contentDescription = stringResource(id = R.string.select_all),
            )
        }
    },
)

/**
 * The default top app bar for the message screen.
 *
 * @param title The title to display (contact or channel name).
 * @param channelIndex The index of the current channel, if applicable.
 * @param mismatchKey True if there's a key mismatch for the current PKC.
 * @param onNavigateBack Callback for the navigation icon.
 * @param channels The set of all channels, used for the [SecurityIcon].
 * @param channelIndexParam The specific channel index for the [SecurityIcon].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageTopBar(
    title: String,
    channelIndex: Int?,
    mismatchKey: Boolean,
    onNavigateBack: () -> Unit,
    channels: AppOnlyProtos.ChannelSet?,
    channelIndexParam: Int?,
    showQuickChat: Boolean,
    onToggleQuickChat: () -> Unit,
    onNavigateToQuickChatOptions: () -> Unit = {},
) = TopAppBar(
    title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.width(10.dp))

            if (channels != null && channelIndexParam != null) {
                SecurityIcon(channels, channelIndexParam)
            }
        }
    },
    navigationIcon = {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.navigate_back),
            )
        }
    },
    actions = {
        MessageTopBarActions(
            showQuickChat,
            onToggleQuickChat,
            onNavigateToQuickChatOptions,
            channelIndex,
            mismatchKey,
        )
    },
)

@Composable
private fun MessageTopBarActions(
    showQuickChat: Boolean,
    onToggleQuickChat: () -> Unit,
    onNavigateToQuickChatOptions: () -> Unit,
    channelIndex: Int?,
    mismatchKey: Boolean,
) {
    if (channelIndex == DataPacket.PKC_CHANNEL_INDEX) {
        NodeKeyStatusIcon(hasPKC = true, mismatchKey = mismatchKey)
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = true) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(id = R.string.overflow_menu))
        }
        OverFlowMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            showQuickChat = showQuickChat,
            onToggleQuickChat = onToggleQuickChat,
            onNavigateToQuickChatOptions = onNavigateToQuickChatOptions,
        )
    }
}

@Composable
private fun OverFlowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    showQuickChat: Boolean,
    onToggleQuickChat: () -> Unit,
    onNavigateToQuickChatOptions: () -> Unit,
) {
    if (expanded) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            val quickChatToggleTitle =
                if (showQuickChat) {
                    stringResource(R.string.quick_chat_hide)
                } else {
                    stringResource(R.string.quick_chat_show)
                }
            DropdownMenuItem(
                text = { Text(quickChatToggleTitle) },
                onClick = {
                    onDismiss()
                    onToggleQuickChat()
                },
                leadingIcon = {
                    Icon(
                        imageVector =
                        if (showQuickChat) {
                            Icons.Default.SpeakerNotesOff
                        } else {
                            Icons.Default.SpeakerNotes
                        },
                        contentDescription = quickChatToggleTitle,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.quick_chat)) },
                onClick = {
                    onDismiss()
                    onNavigateToQuickChatOptions()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = stringResource(id = R.string.quick_chat),
                    )
                },
            )
        }
    }
}

/**
 * A row of quick chat action buttons.
 *
 * @param enabled Whether the buttons should be enabled.
 * @param actions The list of [QuickChatAction]s to display.
 * @param onClick Callback when a quick chat button is clicked.
 */
@Composable
private fun QuickChatRow(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    actions: List<QuickChatAction>,
    userLatitude: Double? = null,
    userLongitude: Double? = null,
    onClick: (QuickChatAction) -> Unit,
) {
    val alertActionMessage = stringResource(R.string.alert_bell_text)
    val alertAction =
        remember(alertActionMessage) {
            // Memoize if content is static
            QuickChatAction(
                name = "🔔",
                message = "🔔 $alertActionMessage ", // Bell character added to message
                mode = QuickChatAction.Mode.Append,
                position = -1, // Assuming -1 means it's a special prepended action
            )
        }

    val allActions = remember(alertAction, actions) { listOf(alertAction) + actions }

    LazyRow(modifier = modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(allActions, key = { it.position }) { action ->
            Button(onClick = { onClick(action) }, enabled = enabled) { Text(text = action.name) }
        }
    }
}

private const val MAX_LINES = 3

/**
 * The text input field for composing messages.
 *
 * @param isEnabled Whether the input field should be enabled.
 * @param textFieldState The [TextFieldState] managing the input's text.
 * @param modifier The modifier for this composable.
 * @param maxByteSize The maximum allowed size of the message in bytes.
 * @param onSendMessage Callback invoked when the send button is pressed or send IME action is triggered.
 */
@Suppress("LongMethod") // Due to multiple parts of the OutlinedTextField
@Composable
private fun MessageInput(
    isEnabled: Boolean,
    textFieldState: TextFieldState,
    modifier: Modifier = Modifier,
    maxByteSize: Int = MESSAGE_CHARACTER_LIMIT_BYTES,
    onSendMessage: () -> Unit,
) {
    val currentText = textFieldState.text.toString()
    val currentByteLength =
        remember(currentText) {
            // Recalculate only when text changes
            currentText.toByteArray(StandardCharsets.UTF_8).size
        }

    val isOverLimit = currentByteLength > maxByteSize
    val canSend = !isOverLimit && currentText.isNotEmpty() && isEnabled

    OutlinedTextField(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        state = textFieldState,
        lineLimits = TextFieldLineLimits.MultiLine(1, MAX_LINES),
        label = { Text(stringResource(R.string.message_input_label)) },
        enabled = isEnabled,
        shape = RoundedCornerShape(ROUNDED_CORNER_PERCENT.toFloat()),
        isError = isOverLimit,
        placeholder = { Text(stringResource(R.string.type_a_message)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        supportingText = {
            if (isEnabled) { // Only show supporting text if input is enabled
                Text(
                    text = "$currentByteLength/$maxByteSize",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                    if (isOverLimit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }
        },
        // Direct byte limiting via inputTransformation in TextFieldState is complex.
        // The current approach (show error, disable send) is generally preferred for UX.
        // If strict real-time byte trimming is required, it needs careful handling of
        // cursor position and multi-byte characters, likely outside simple inputTransformation.
        trailingIcon = {
            IconButton(onClick = { if (canSend) onSendMessage() }, enabled = canSend) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.Send,
                    contentDescription = stringResource(id = R.string.send),
                )
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun MessageInputPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(8.dp)) {
                MessageInput(isEnabled = true, textFieldState = rememberTextFieldState("Hello"), onSendMessage = {})
                Spacer(Modifier.size(16.dp))
                MessageInput(isEnabled = false, textFieldState = rememberTextFieldState("Disabled"), onSendMessage = {})
                Spacer(Modifier.size(16.dp))
                MessageInput(
                    isEnabled = true,
                    textFieldState =
                    rememberTextFieldState(
                        "A very long message that might exceed the byte limit " +
                            "and cause an error state display for the user to see clearly.",
                    ),
                    onSendMessage = {},
                    maxByteSize = 50, // Test with a smaller limit
                )
                Spacer(Modifier.size(16.dp))
                // Test Japanese characters (multi-byte)
                MessageInput(
                    isEnabled = true,
                    textFieldState = rememberTextFieldState("こんにちは世界"), // Hello World in Japanese
                    onSendMessage = {},
                    maxByteSize = 10,
                    // Each char is 3 bytes, so "こん" (6 bytes) is ok, "こんに" (9 bytes) is ok, "こんにち"
                    // (12 bytes) is over
                )
            }
        }
    }
}
