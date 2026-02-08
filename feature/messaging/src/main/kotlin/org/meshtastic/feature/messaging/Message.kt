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
@file:Suppress("TooManyFunctions")

package org.meshtastic.feature.messaging

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.automirrored.rounded.SpeakerNotes
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SpeakerNotesOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.service.RetryEvent
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.alert_bell_text
import org.meshtastic.core.strings.cancel_reply
import org.meshtastic.core.strings.clear_selection
import org.meshtastic.core.strings.copy
import org.meshtastic.core.strings.delete
import org.meshtastic.core.strings.delete_messages
import org.meshtastic.core.strings.delete_messages_title
import org.meshtastic.core.strings.filter_disable_for_contact
import org.meshtastic.core.strings.filter_enable_for_contact
import org.meshtastic.core.strings.filter_hide_count
import org.meshtastic.core.strings.filter_show_count
import org.meshtastic.core.strings.message_input_label
import org.meshtastic.core.strings.navigate_back
import org.meshtastic.core.strings.overflow_menu
import org.meshtastic.core.strings.quick_chat
import org.meshtastic.core.strings.quick_chat_hide
import org.meshtastic.core.strings.quick_chat_show
import org.meshtastic.core.strings.reply
import org.meshtastic.core.strings.replying_to
import org.meshtastic.core.strings.scroll_to_bottom
import org.meshtastic.core.strings.select_all
import org.meshtastic.core.strings.send
import org.meshtastic.core.strings.type_a_message
import org.meshtastic.core.strings.unknown
import org.meshtastic.core.strings.unknown_channel
import org.meshtastic.core.ui.component.MeshtasticTextDialog
import org.meshtastic.core.ui.component.NodeKeyStatusIcon
import org.meshtastic.core.ui.component.SecurityIcon
import org.meshtastic.core.ui.component.SharedContactDialog
import org.meshtastic.core.ui.component.smartScrollToIndex
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.messaging.component.RetryConfirmationDialog
import org.meshtastic.proto.ChannelSet
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
 * @param navigateToNodeDetails Callback to navigate to a node's detail screen.
 * @param onNavigateBack Callback to navigate back from this screen.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // Due to multiple states and event handling
@Composable
fun MessageScreen(
    contactKey: String,
    message: String,
    viewModel: MessageViewModel = hiltViewModel(),
    navigateToNodeDetails: (Int) -> Unit,
    navigateToQuickChatOptions: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current
    val focusManager = LocalFocusManager.current

    val nodes by viewModel.nodeList.collectAsStateWithLifecycle()
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val quickChatActions by viewModel.quickChatActions.collectAsStateWithLifecycle(initialValue = emptyList())
    val pagedMessages = viewModel.getMessagesFromPaged(contactKey).collectAsLazyPagingItems()
    val contactSettings by viewModel.contactSettings.collectAsStateWithLifecycle(initialValue = emptyMap())
    val homoglyphEncodingEnabled by viewModel.homoglyphEncodingEnabled.collectAsStateWithLifecycle(initialValue = false)

    // UI State managed within this Composable
    var replyingToPacketId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var sharedContact by rememberSaveable { mutableStateOf<Node?>(null) }
    val selectedMessageIds = rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val messageInputState = rememberTextFieldState(message)
    val showQuickChat by viewModel.showQuickChat.collectAsStateWithLifecycle()
    val filteredCount by viewModel.getFilteredCount(contactKey).collectAsStateWithLifecycle(initialValue = 0)
    val showFiltered by viewModel.showFiltered.collectAsStateWithLifecycle()
    val filteringDisabled = contactSettings[contactKey]?.filteringDisabled ?: false

    // Retry dialog state
    var currentRetryEvent by remember { mutableStateOf<RetryEvent?>(null) }

    // Observe retry events from the service
    // Key on contactKey to restart collection when navigating between conversations
    LaunchedEffect(contactKey) {
        android.util.Log.d("MessageScreen", "Starting retry event collection for contact: $contactKey")
        viewModel.retryEvents.collect { event ->
            if (event != null) {
                android.util.Log.d("MessageScreen", "Received retry event: ${event.packetId}")
                currentRetryEvent = event
            } else {
                android.util.Log.d("MessageScreen", "Retry event cleared")
                currentRetryEvent = null
            }
        }
    }

    // Prevent the message TextField from stealing focus when the screen opens
    LaunchedEffect(contactKey) { focusManager.clearFocus() }

    // Derived state, memoized for performance
    val channelInfo =
        remember(contactKey, channels) {
            val index = contactKey.firstOrNull()?.digitToIntOrNull()
            val id = contactKey.substring(1)
            val name = index?.let { channels.getChannel(it)?.name } // channels can be null initially
            Triple(index, id, name)
        }
    val (channelIndex, nodeId, rawChannelName) = channelInfo
    val unknownChannelText = stringResource(Res.string.unknown_channel)
    val channelName = rawChannelName ?: unknownChannelText

    val title =
        remember(nodeId, channelName, viewModel) {
            when (nodeId) {
                DataPacket.ID_BROADCAST -> channelName
                else -> viewModel.getUser(nodeId).long_name
            }
        }

    val isMismatchKey =
        remember(channelIndex, nodeId, viewModel) {
            channelIndex == DataPacket.PKC_CHANNEL_INDEX && viewModel.getNode(nodeId).mismatchKey
        }

    val inSelectionMode by remember { derivedStateOf { selectedMessageIds.value.isNotEmpty() } }

    val listState = rememberLazyListState()

    // Track unread messages using lightweight metadata queries
    val hasUnreadMessages by viewModel.hasUnreadMessages(contactKey).collectAsStateWithLifecycle(initialValue = false)
    val firstUnreadMessageUuid by
        viewModel.getFirstUnreadMessageUuid(contactKey).collectAsStateWithLifecycle(initialValue = null)

    var hasPerformedInitialScroll by rememberSaveable(contactKey) { mutableStateOf(false) }

    // Find the index of the first unread message in the paged list
    val firstUnreadIndex by
        remember(pagedMessages.itemCount, firstUnreadMessageUuid) {
            derivedStateOf {
                firstUnreadMessageUuid?.let { uuid ->
                    (0 until pagedMessages.itemCount).firstOrNull { index -> pagedMessages[index]?.uuid == uuid }
                }
            }
        }

    // Scroll to first unread message on initial load
    LaunchedEffect(hasPerformedInitialScroll, firstUnreadIndex, pagedMessages.itemCount) {
        if (hasPerformedInitialScroll || pagedMessages.itemCount == 0) return@LaunchedEffect

        val shouldScrollToUnread = hasUnreadMessages && firstUnreadIndex != null
        if (shouldScrollToUnread) {
            val targetIndex = (firstUnreadIndex!! - (UnreadUiDefaults.VISIBLE_CONTEXT_COUNT - 1)).coerceAtLeast(0)
            listState.smartScrollToIndex(coroutineScope = coroutineScope, targetIndex = targetIndex)
            hasPerformedInitialScroll = true
        } else if (!hasUnreadMessages) {
            // If no unread messages, just scroll to bottom (most recent)
            listState.scrollToItem(0)
            hasPerformedInitialScroll = true
        }
    }

    val onEvent: (MessageScreenEvent) -> Unit =
        remember(viewModel, contactKey, messageInputState, ourNode) {
            fun handle(event: MessageScreenEvent) {
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
                        viewModel.clearUnreadCount(contactKey, event.messageUuid, event.lastReadTimestamp)

                    is MessageScreenEvent.NodeDetails -> navigateToNodeDetails(event.node.num)

                    is MessageScreenEvent.SetTitle -> viewModel.setTitle(event.title)
                    is MessageScreenEvent.NavigateToNodeDetails -> navigateToNodeDetails(event.nodeNum)
                    MessageScreenEvent.NavigateBack -> onNavigateBack()
                    is MessageScreenEvent.CopyToClipboard -> {
                        coroutineScope.launch {
                            clipboardManager.setClipEntry(
                                androidx.compose.ui.platform.ClipEntry(ClipData.newPlainText(event.text, event.text)),
                            )
                        }
                        selectedMessageIds.value = emptySet()
                    }
                }
            }

            ::handle
        }

    if (showDeleteDialog) {
        DeleteMessageDialog(
            count = selectedMessageIds.value.size,
            onConfirm = { onEvent(MessageScreenEvent.DeleteMessages(selectedMessageIds.value.toList())) },
            onDismiss = { showDeleteDialog = false },
        )
    }

    sharedContact?.let { contact -> SharedContactDialog(contact = contact, onDismiss = { sharedContact = null }) }

    // Show retry confirmation dialog
    currentRetryEvent?.let { event ->
        RetryConfirmationDialog(
            retryEvent = event,
            countdownSeconds = 5,
            onConfirm = {
                // User clicked "Retry Now" - proceed immediately
                viewModel.respondToRetry(event.packetId, shouldRetry = true)
                currentRetryEvent = null
            },
            onCancel = {
                // User clicked "Cancel Retry" - stop retrying
                viewModel.respondToRetry(event.packetId, shouldRetry = false)
                currentRetryEvent = null
            },
            onTimeout = {
                // Countdown reached 0 - auto-retry
                viewModel.respondToRetry(event.packetId, shouldRetry = true)
                currentRetryEvent = null
            },
        )
    }

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
                                    (0 until pagedMessages.itemCount)
                                        .mapNotNull { pagedMessages[it] }
                                        .filter { it.uuid in selectedMessageIds.value }
                                        .joinToString("\n") { it.text }
                                onEvent(MessageScreenEvent.CopyToClipboard(copiedText))
                            }

                            MessageMenuAction.Delete -> showDeleteDialog = true
                            MessageMenuAction.Dismiss -> selectedMessageIds.value = emptySet()
                            MessageMenuAction.SelectAll -> {
                                // Note: Select All is disabled with pagination since we don't have
                                // access to the full message list. This would need to be reworked
                                // to select all currently loaded items instead.
                                selectedMessageIds.value =
                                    if (selectedMessageIds.value.size == pagedMessages.itemCount) {
                                        emptySet()
                                    } else {
                                        (0 until pagedMessages.itemCount).mapNotNull { pagedMessages[it]?.uuid }.toSet()
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
                    filteringDisabled = filteringDisabled,
                    onToggleFilteringDisabled = {
                        viewModel.setContactFilteringDisabled(contactKey, !filteringDisabled)
                    },
                    filteredCount = filteredCount,
                    showFiltered = showFiltered,
                    onToggleShowFiltered = viewModel::toggleShowFiltered,
                )
            }
        },
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).focusable()) {
            Box(modifier = Modifier.weight(1f)) {
                MessageListPaged(
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    state =
                    MessageListPagedState(
                        nodes = nodes,
                        ourNode = ourNode,
                        messages = pagedMessages,
                        selectedIds = selectedMessageIds,
                        contactKey = contactKey,
                        firstUnreadMessageUuid = firstUnreadMessageUuid,
                        hasUnreadMessages = hasUnreadMessages,
                        filteredCount = filteredCount,
                        showFiltered = showFiltered,
                        filteringDisabled = filteringDisabled,
                    ),
                    handlers =
                    MessageListHandlers(
                        onUnreadChanged = { messageUuid, timestamp ->
                            onEvent(MessageScreenEvent.ClearUnreadCount(messageUuid, timestamp))
                        },
                        onSendReaction = { emoji, id -> onEvent(MessageScreenEvent.SendReaction(emoji, id)) },
                        onClickChip = { onEvent(MessageScreenEvent.NodeDetails(it)) },
                        onDeleteMessages = { viewModel.deleteMessages(it) },
                        onSendMessage = { text, key -> viewModel.sendMessage(text, key) },
                        onReply = { message -> replyingToPacketId = message?.packetId },
                    ),
                    quickEmojis = viewModel.frequentEmojis,
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
                    onClick = { action ->
                        handleQuickChatAction(
                            action = action,
                            messageInputState = messageInputState,
                            onSendMessage = { text -> onEvent(MessageScreenEvent.SendMessage(text)) },
                        )
                    },
                )
            }
            val originalMessage by
                remember(replyingToPacketId, pagedMessages.itemCount) {
                    derivedStateOf {
                        replyingToPacketId?.let { id ->
                            (0 until pagedMessages.itemCount).firstNotNullOfOrNull { index ->
                                pagedMessages[index]?.takeIf { it.packetId == id }
                            }
                        }
                    }
                }
            ReplySnippet(
                originalMessage = originalMessage,
                onClearReply = { replyingToPacketId = null },
                ourNode = ourNode,
            )
            MessageInput(
                isEnabled = connectionState.isConnected(),
                isHomoglyphEncodingEnabled = homoglyphEncodingEnabled,
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
            imageVector = Icons.Rounded.ArrowDownward,
            contentDescription = stringResource(Res.string.scroll_to_bottom),
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
            val isFromLocalUser = message.fromLocal
            val replyingToNodeUser = if (isFromLocalUser) ourNode?.user else message.node.user
            val unknownUserText = stringResource(Res.string.unknown)

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
                    contentDescription = stringResource(Res.string.reply), // Decorative
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.replying_to, replyingToNodeUser?.short_name ?: unknownUserText),
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
                        contentDescription = stringResource(Res.string.cancel_reply), // Specific action
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
 * @param onSendMessage Lambda to call when a message needs to be sent.
 */
private fun handleQuickChatAction(
    action: QuickChatAction,
    messageInputState: TextFieldState,
    onSendMessage: (String) -> Unit,
) {
    when (action.mode) {
        QuickChatAction.Mode.Append -> {
            val originalText = messageInputState.text.toString()
            // Avoid appending if the exact message is already present (simple check)
            if (!originalText.contains(action.message)) {
                val newText =
                    buildString {
                        append(originalText)
                        if (originalText.isNotEmpty() && !originalText.endsWith(' ')) {
                            append(' ')
                        }
                        append(action.message)
                    }
                        .limitBytes(MESSAGE_CHARACTER_LIMIT_BYTES)
                messageInputState.setTextAndPlaceCursorAtEnd(newText)
            }
        }

        QuickChatAction.Mode.Instant -> {
            // Byte limit for 'Send' mode messages is handled by the backend/transport layer.
            onSendMessage(action.message)
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
    val deleteMessagesString = pluralStringResource(Res.plurals.delete_messages, count, count)

    MeshtasticTextDialog(
        titleRes = Res.string.delete_messages_title,
        message = deleteMessagesString,
        confirmTextRes = Res.string.delete,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
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
                contentDescription = stringResource(Res.string.clear_selection),
            )
        }
    },
    actions = {
        IconButton(onClick = { onAction(MessageMenuAction.ClipboardCopy) }) {
            Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = stringResource(Res.string.copy))
        }
        IconButton(onClick = { onAction(MessageMenuAction.Delete) }) {
            Icon(imageVector = Icons.Rounded.Delete, contentDescription = stringResource(Res.string.delete))
        }
        IconButton(onClick = { onAction(MessageMenuAction.SelectAll) }) {
            Icon(imageVector = Icons.Rounded.SelectAll, contentDescription = stringResource(Res.string.select_all))
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
    channels: ChannelSet?,
    channelIndexParam: Int?,
    showQuickChat: Boolean,
    onToggleQuickChat: () -> Unit,
    onNavigateToQuickChatOptions: () -> Unit = {},
    filteringDisabled: Boolean = false,
    onToggleFilteringDisabled: () -> Unit = {},
    filteredCount: Int = 0,
    showFiltered: Boolean = false,
    onToggleShowFiltered: () -> Unit = {},
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
                contentDescription = stringResource(Res.string.navigate_back),
            )
        }
    },
    actions = {
        MessageTopBarActions(
            showQuickChat = showQuickChat,
            onToggleQuickChat = onToggleQuickChat,
            onNavigateToQuickChatOptions = onNavigateToQuickChatOptions,
            channelIndex = channelIndex,
            mismatchKey = mismatchKey,
            filteringDisabled = filteringDisabled,
            onToggleFilteringDisabled = onToggleFilteringDisabled,
            filteredCount = filteredCount,
            showFiltered = showFiltered,
            onToggleShowFiltered = onToggleShowFiltered,
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
    filteringDisabled: Boolean,
    onToggleFilteringDisabled: () -> Unit,
    filteredCount: Int,
    showFiltered: Boolean,
    onToggleShowFiltered: () -> Unit,
) {
    if (channelIndex == DataPacket.PKC_CHANNEL_INDEX) {
        NodeKeyStatusIcon(hasPKC = true, mismatchKey = mismatchKey)
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = true) {
            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.overflow_menu))
        }
        OverFlowMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            showQuickChat = showQuickChat,
            onToggleQuickChat = onToggleQuickChat,
            onNavigateToQuickChatOptions = onNavigateToQuickChatOptions,
            filteringDisabled = filteringDisabled,
            onToggleFilteringDisabled = onToggleFilteringDisabled,
            filteredCount = filteredCount,
            showFiltered = showFiltered,
            onToggleShowFiltered = onToggleShowFiltered,
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
    filteringDisabled: Boolean,
    onToggleFilteringDisabled: () -> Unit,
    filteredCount: Int,
    showFiltered: Boolean,
    onToggleShowFiltered: () -> Unit,
) {
    if (expanded) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            QuickChatToggleMenuItem(showQuickChat, onDismiss, onToggleQuickChat)
            QuickChatOptionsMenuItem(onDismiss, onNavigateToQuickChatOptions)
            if (filteredCount > 0 && !filteringDisabled) {
                FilteredMessagesMenuItem(showFiltered, filteredCount, onDismiss, onToggleShowFiltered)
            }
            FilterToggleMenuItem(filteringDisabled, onDismiss, onToggleFilteringDisabled)
        }
    }
}

@Composable
private fun QuickChatToggleMenuItem(showQuickChat: Boolean, onDismiss: () -> Unit, onToggle: () -> Unit) {
    val title = stringResource(if (showQuickChat) Res.string.quick_chat_hide else Res.string.quick_chat_show)
    DropdownMenuItem(
        text = { Text(title) },
        onClick = {
            onDismiss()
            onToggle()
        },
        leadingIcon = {
            Icon(
                imageVector =
                if (showQuickChat) Icons.Rounded.SpeakerNotesOff else Icons.AutoMirrored.Rounded.SpeakerNotes,
                contentDescription = title,
            )
        },
    )
}

@Composable
private fun QuickChatOptionsMenuItem(onDismiss: () -> Unit, onNavigate: () -> Unit) {
    val title = stringResource(Res.string.quick_chat)
    DropdownMenuItem(
        text = { Text(title) },
        onClick = {
            onDismiss()
            onNavigate()
        },
        leadingIcon = { Icon(imageVector = Icons.Rounded.ChatBubbleOutline, contentDescription = title) },
    )
}

@Composable
private fun FilteredMessagesMenuItem(showFiltered: Boolean, count: Int, onDismiss: () -> Unit, onToggle: () -> Unit) {
    val title = stringResource(if (showFiltered) Res.string.filter_hide_count else Res.string.filter_show_count, count)
    DropdownMenuItem(
        text = { Text(title) },
        onClick = {
            onDismiss()
            onToggle()
        },
        leadingIcon = {
            Icon(
                imageVector = if (showFiltered) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = title,
            )
        },
    )
}

@Composable
private fun FilterToggleMenuItem(filteringDisabled: Boolean, onDismiss: () -> Unit, onToggle: () -> Unit) {
    val title =
        stringResource(
            if (filteringDisabled) Res.string.filter_enable_for_contact else Res.string.filter_disable_for_contact,
        )
    DropdownMenuItem(
        text = { Text(title) },
        onClick = {
            onDismiss()
            onToggle()
        },
        leadingIcon = {
            Icon(
                imageVector = if (filteringDisabled) Icons.Rounded.FilterList else Icons.Rounded.FilterListOff,
                contentDescription = title,
            )
        },
    )
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
    onClick: (QuickChatAction) -> Unit,
) {
    val alertActionMessage = stringResource(Res.string.alert_bell_text)
    val alertAction =
        remember(alertActionMessage) {
            // Memoize if content is static
            QuickChatAction(
                name = "🔔",
                message = "🔔 $alertActionMessage  ", // Bell character added to message
                mode = QuickChatAction.Mode.Append,
                position = -1, // Assuming -1 means it's a special prepended action
            )
        }

    val allActions = remember(alertAction, actions) { listOf(alertAction) + actions }

    LazyRow(modifier = modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(allActions, key = { it.uuid }) { action ->
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
    isHomoglyphEncodingEnabled: Boolean,
    textFieldState: TextFieldState,
    modifier: Modifier = Modifier,
    maxByteSize: Int = MESSAGE_CHARACTER_LIMIT_BYTES,
    onSendMessage: () -> Unit,
) {
    val currentTextRaw = textFieldState.text.toString()

    val currentText =
        if (isHomoglyphEncodingEnabled) {
            HomoglyphCharacterStringTransformer.optimizeUtf8StringWithHomoglyphs(currentTextRaw)
        } else {
            currentTextRaw
        }

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
        label = { Text(stringResource(Res.string.message_input_label)) },
        enabled = isEnabled,
        shape = RoundedCornerShape(ROUNDED_CORNER_PERCENT.toFloat()),
        isError = isOverLimit,
        placeholder = { Text(stringResource(Res.string.type_a_message)) },
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
                    contentDescription = stringResource(Res.string.send),
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
                MessageInput(
                    isEnabled = true,
                    isHomoglyphEncodingEnabled = false,
                    textFieldState = rememberTextFieldState("Hello"),
                    onSendMessage = {},
                )
                Spacer(Modifier.size(16.dp))
                MessageInput(
                    isEnabled = false,
                    isHomoglyphEncodingEnabled = false,
                    textFieldState = rememberTextFieldState("Disabled"),
                    onSendMessage = {},
                )
                Spacer(Modifier.size(16.dp))
                MessageInput(
                    isEnabled = true,
                    isHomoglyphEncodingEnabled = false,
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
                    isHomoglyphEncodingEnabled = false,
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
