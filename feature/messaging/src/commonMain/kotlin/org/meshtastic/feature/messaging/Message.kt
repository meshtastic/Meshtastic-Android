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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.HomoglyphCharacterStringTransformer
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.message_input_label
import org.meshtastic.core.resources.send
import org.meshtastic.core.resources.type_a_message
import org.meshtastic.core.resources.unknown_channel
import org.meshtastic.core.ui.component.SharedContactDialog
import org.meshtastic.core.ui.component.smartScrollToIndex
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Send
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.createClipEntry
import org.meshtastic.feature.messaging.component.ActionModeTopBar
import org.meshtastic.feature.messaging.component.DeleteMessageDialog
import org.meshtastic.feature.messaging.component.MESSAGE_CHARACTER_LIMIT_BYTES
import org.meshtastic.feature.messaging.component.MessageMenuAction
import org.meshtastic.feature.messaging.component.MessageTopBar
import org.meshtastic.feature.messaging.component.QuickChatRow
import org.meshtastic.feature.messaging.component.ReplySnippet
import org.meshtastic.feature.messaging.component.ScrollToBottomFab

private const val ROUNDED_CORNER_PERCENT = 100
private const val MAX_LINES = 3

/**
 * The main screen for displaying and sending messages to a contact or channel.
 *
 * @param contactKey A unique key identifying the contact or channel.
 * @param message An optional message to pre-fill in the input field.
 * @param viewModel The [MessageViewModel] instance for handling business logic and state.
 * @param navigateToNodeDetails Callback to navigate to a node's detail screen.
 * @param navigateToQuickChatOptions Callback to navigate to the quick chat options screen.
 * @param navigateToFilterSettings Callback to navigate to the message filter settings screen.
 * @param onNavigateBack Callback to navigate back from this screen.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun MessageScreen(
    contactKey: String,
    message: String,
    viewModel: MessageViewModel,
    navigateToNodeDetails: (Int) -> Unit,
    navigateToQuickChatOptions: () -> Unit,
    navigateToFilterSettings: () -> Unit,
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
    val filteredCount by viewModel.filteredCount.collectAsStateWithLifecycle()
    val showFiltered by viewModel.showFiltered.collectAsStateWithLifecycle()
    val filteringDisabled = contactSettings[contactKey]?.filteringDisabled ?: false

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
    val hasUnreadMessages by viewModel.hasUnreadMessages.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val firstUnreadMessageUuid by viewModel.firstUnreadMessageUuid.collectAsStateWithLifecycle()

    var hasPerformedInitialScroll by rememberSaveable(contactKey) { mutableStateOf(false) }

    // Find the index of the first unread message in the paged list
    val firstUnreadIndex by
        remember(pagedMessages.itemCount, firstUnreadMessageUuid) {
            derivedStateOf {
                firstUnreadMessageUuid?.let { uuid ->
                    pagedMessages.itemSnapshotList.indexOfFirst { it?.uuid == uuid }.takeIf { it != -1 }
                }
            }
        }

    // Scroll to first unread message on initial load
    LaunchedEffect(
        hasPerformedInitialScroll,
        firstUnreadIndex,
        pagedMessages.itemCount,
        hasUnreadMessages,
        firstUnreadMessageUuid,
    ) {
        if (hasPerformedInitialScroll || pagedMessages.itemCount == 0) return@LaunchedEffect
        if (hasUnreadMessages == null) return@LaunchedEffect // Wait for DB state to initialize

        if (hasUnreadMessages == true) {
            if (firstUnreadMessageUuid == null) return@LaunchedEffect // Wait for UUID query

            val index = firstUnreadIndex
            if (index != null) {
                val targetIndex = (index - (UnreadUiDefaults.VISIBLE_CONTEXT_COUNT - 1)).coerceAtLeast(0)
                listState.smartScrollToIndex(coroutineScope = coroutineScope, targetIndex = targetIndex)
                hasPerformedInitialScroll = true
            } else {
                // The first unread message is deeper than the currently loaded pages.
                // Scroll to the end of the loaded items to trigger the next page load.
                // This will re-trigger this LaunchedEffect until we find the message.
                listState.scrollToItem(pagedMessages.itemCount - 1)
            }
        } else {
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
                        coroutineScope.launch { clipboardManager.setClipEntry(createClipEntry(event.text, event.text)) }
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

    val originalMessage by
        remember(replyingToPacketId, pagedMessages.itemCount) {
            derivedStateOf {
                replyingToPacketId?.let { id -> pagedMessages.itemSnapshotList.firstOrNull { it?.packetId == id } }
            }
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
                    onNavigateToFilterSettings = navigateToFilterSettings,
                )
            }
        },
        bottomBar = {
            Column {
                AnimatedVisibility(visible = showQuickChat) {
                    QuickChatRow(
                        enabled = connectionState is ConnectionState.Connected,
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
                ReplySnippet(
                    originalMessage = originalMessage,
                    onClearReply = { replyingToPacketId = null },
                    ourNode = ourNode,
                )
                MessageInput(
                    isEnabled = connectionState is ConnectionState.Connected,
                    isHomoglyphEncodingEnabled = homoglyphEncodingEnabled,
                    textFieldState = messageInputState,
                    onSendMessage = {
                        val messageText = messageInputState.text.toString().trim { it.isWhitespace() }
                        if (messageText.isNotEmpty()) {
                            onEvent(MessageScreenEvent.SendMessage(messageText, replyingToPacketId))
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues).focusable()) {
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
                    hasUnreadMessages = hasUnreadMessages == true,
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
                ScrollToBottomFab(coroutineScope, listState, unreadCount)
            }
        }
    }
}

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
    org.meshtastic.feature.messaging.component.handleQuickChatAction(
        action = action,
        currentText = messageInputState.text.toString(),
        onUpdateText = { newText -> messageInputState.setTextAndPlaceCursorAtEnd(newText) },
        onSendMessage = onSendMessage,
    )
}

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
            currentText.encodeToByteArray().size
        }

    val isOverLimit = currentByteLength > maxByteSize
    val canSend = !isOverLimit && currentText.isNotEmpty() && isEnabled

    OutlinedTextField(
        modifier =
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).onKeyEvent { keyEvent ->
            val isEnterNoShift = keyEvent.key == Key.Enter && !keyEvent.isShiftPressed
            if (isEnterNoShift) {
                if (keyEvent.type == KeyEventType.KeyUp && canSend) {
                    onSendMessage()
                }
                true // consume both KeyDown and KeyUp to prevent newline insertion
            } else {
                false
            }
        },
        state = textFieldState,
        lineLimits = TextFieldLineLimits.MultiLine(1, MAX_LINES),
        label = { Text(stringResource(Res.string.message_input_label)) },
        enabled = isEnabled,
        shape = RoundedCornerShape(ROUNDED_CORNER_PERCENT.toFloat()),
        isError = isOverLimit,
        placeholder = { Text(stringResource(Res.string.type_a_message)) },
        keyboardOptions =
        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
        onKeyboardAction = { if (canSend) onSendMessage() },
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
                Icon(imageVector = MeshtasticIcons.Send, contentDescription = stringResource(Res.string.send))
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
