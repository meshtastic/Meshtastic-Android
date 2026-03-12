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
package org.meshtastic.desktop.ui.messaging

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.no_messages_yet
import org.meshtastic.core.resources.unknown_channel
import org.meshtastic.core.ui.component.EmptyDetailPlaceholder
import org.meshtastic.core.ui.icon.Conversations
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.createClipEntry
import org.meshtastic.feature.messaging.MessageViewModel
import org.meshtastic.feature.messaging.component.ActionModeTopBar
import org.meshtastic.feature.messaging.component.DeleteMessageDialog
import org.meshtastic.feature.messaging.component.MessageInput
import org.meshtastic.feature.messaging.component.MessageItem
import org.meshtastic.feature.messaging.component.MessageMenuAction
import org.meshtastic.feature.messaging.component.MessageStatusDialog
import org.meshtastic.feature.messaging.component.MessageTopBar
import org.meshtastic.feature.messaging.component.QuickChatRow
import org.meshtastic.feature.messaging.component.ReplySnippet
import org.meshtastic.feature.messaging.component.ScrollToBottomFab
import org.meshtastic.feature.messaging.component.UnreadMessagesDivider
import org.meshtastic.feature.messaging.component.handleQuickChatAction

/**
 * Desktop message content view for the contacts detail pane.
 *
 * Uses a non-paged [LazyColumn] to display messages for a selected conversation. Now shares the full message screen
 * component set with Android, including: proper reply-to-message with replyId, message selection mode, quick chat row,
 * message filtering, delivery info dialog, overflow menu, byte counter input, and unread dividers.
 *
 * The only difference from Android is the non-paged data source (Flow<List<Message>> vs LazyPagingItems) and the
 * absence of PredictiveBackHandler.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun DesktopMessageContent(
    contactKey: String,
    viewModel: MessageViewModel,
    modifier: Modifier = Modifier,
    initialMessage: String = "",
    onNavigateUp: (() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current

    val nodes by viewModel.nodeList.collectAsStateWithLifecycle()
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val quickChatActions by viewModel.quickChatActions.collectAsStateWithLifecycle(initialValue = emptyList())
    val contactSettings by viewModel.contactSettings.collectAsStateWithLifecycle(initialValue = emptyMap())
    val homoglyphEncodingEnabled by viewModel.homoglyphEncodingEnabled.collectAsStateWithLifecycle(initialValue = false)

    val messages by viewModel.getMessagesFlow(contactKey).collectAsStateWithLifecycle(initialValue = emptyList())

    // UI State
    var replyingToPacketId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val selectedMessageIds = rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var messageText by rememberSaveable(contactKey) { mutableStateOf(initialMessage) }
    val showQuickChat by viewModel.showQuickChat.collectAsStateWithLifecycle()
    val filteredCount by viewModel.filteredCount.collectAsStateWithLifecycle()
    val showFiltered by viewModel.showFiltered.collectAsStateWithLifecycle()
    val filteringDisabled = contactSettings[contactKey]?.filteringDisabled ?: false

    var showStatusDialog by remember { mutableStateOf<org.meshtastic.core.model.Message?>(null) }
    val inSelectionMode by remember { derivedStateOf { selectedMessageIds.value.isNotEmpty() } }

    val listState = rememberLazyListState()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()

    // Derive title
    val channelInfo =
        remember(contactKey, channels) {
            val index = contactKey.firstOrNull()?.digitToIntOrNull()
            val id = contactKey.substring(1)
            val name = index?.let { channels.getChannel(it)?.name }
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

    // Find the original message for reply snippet
    val originalMessage by
        remember(replyingToPacketId, messages.size) {
            derivedStateOf { replyingToPacketId?.let { id -> messages.firstOrNull { it.packetId == id } } }
        }

    // Scroll to bottom when new messages arrive and we're already at the bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !listState.canScrollBackward) {
            listState.animateScrollToItem(0)
        }
    }

    // Seed route-provided draft text
    LaunchedEffect(contactKey, initialMessage) {
        if (initialMessage.isNotBlank() && messageText.isBlank()) {
            messageText = initialMessage
        }
    }

    // Mark messages as read when they become visible
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    LaunchedEffect(messages.size) {
        snapshotFlow { if (listState.isScrollInProgress) null else listState.layoutInfo }
            .debounce(SCROLL_SETTLE_MILLIS)
            .collectLatest { layoutInfo ->
                if (layoutInfo == null || messages.isEmpty()) return@collectLatest

                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@collectLatest

                val topVisibleIndex = visibleItems.first().index
                val bottomVisibleIndex = visibleItems.last().index

                val firstVisibleUnread =
                    (bottomVisibleIndex..topVisibleIndex)
                        .mapNotNull { if (it in messages.indices) messages[it] else null }
                        .firstOrNull { !it.fromLocal && !it.read }

                firstVisibleUnread?.let { message ->
                    viewModel.clearUnreadCount(contactKey, message.uuid, message.receivedTime)
                }
            }
    }

    // Dialogs
    if (showDeleteDialog) {
        DeleteMessageDialog(
            count = selectedMessageIds.value.size,
            onConfirm = {
                viewModel.deleteMessages(selectedMessageIds.value.toList())
                selectedMessageIds.value = emptySet()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    showStatusDialog?.let { message ->
        MessageStatusDialog(
            message = message,
            nodes = nodes,
            ourNode = ourNode,
            resendOption = message.status?.equals(MessageStatus.ERROR) ?: false,
            onResend = {
                viewModel.deleteMessages(listOf(message.uuid))
                viewModel.sendMessage(message.text, contactKey)
                showStatusDialog = null
            },
            onDismiss = { showStatusDialog = null },
        )
    }

    Scaffold(
        modifier = modifier,
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
                                coroutineScope.launch {
                                    clipboardManager.setClipEntry(createClipEntry(copiedText, "messages"))
                                }
                                selectedMessageIds.value = emptySet()
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
                    onNavigateBack = { onNavigateUp?.invoke() },
                    channels = channels,
                    channelIndexParam = channelIndex,
                    showQuickChat = showQuickChat,
                    onToggleQuickChat = viewModel::toggleShowQuickChat,
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
        bottomBar = {
            Column {
                AnimatedVisibility(visible = showQuickChat) {
                    QuickChatRow(
                        enabled = connectionState.isConnected(),
                        actions = quickChatActions,
                        onClick = { action ->
                            handleQuickChatAction(
                                action = action,
                                currentText = messageText,
                                onUpdateText = { messageText = it },
                                onSendMessage = { text -> viewModel.sendMessage(text, contactKey) },
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
                    messageText = messageText,
                    onMessageChange = { messageText = it },
                    onSendMessage = {
                        val trimmed = messageText.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.sendMessage(trimmed, contactKey, replyingToPacketId)
                            if (replyingToPacketId != null) replyingToPacketId = null
                            messageText = ""
                        }
                    },
                    isEnabled = connectionState.isConnected(),
                    isHomoglyphEncodingEnabled = homoglyphEncodingEnabled,
                )
            }
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding).focusable()) {
            if (messages.isEmpty()) {
                EmptyDetailPlaceholder(
                    icon = MeshtasticIcons.Conversations,
                    title = stringResource(Res.string.no_messages_yet),
                )
            } else {
                // Pre-calculate node map for O(1) lookup
                val nodeMap = remember(nodes) { nodes.associateBy { it.num } }

                // Find first unread index
                val firstUnreadIndex by
                    remember(messages.size) {
                        derivedStateOf { messages.indexOfFirst { !it.fromLocal && !it.read }.takeIf { it != -1 } }
                    }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(bottom = 24.dp, top = 24.dp),
                ) {
                    items(messages.size, key = { messages[it].uuid }) { index ->
                        val message = messages[index]
                        val isSender = message.fromLocal

                        // Because reverseLayout = true, visually previous (above) is index + 1
                        val visuallyPrevMessage = if (index < messages.size - 1) messages[index + 1] else null
                        val visuallyNextMessage = if (index > 0) messages[index - 1] else null

                        val hasSamePrev =
                            if (visuallyPrevMessage != null) {
                                visuallyPrevMessage.fromLocal == message.fromLocal &&
                                    (message.fromLocal || visuallyPrevMessage.node.num == message.node.num)
                            } else {
                                false
                            }

                        val hasSameNext =
                            if (visuallyNextMessage != null) {
                                visuallyNextMessage.fromLocal == message.fromLocal &&
                                    (message.fromLocal || visuallyNextMessage.node.num == message.node.num)
                            } else {
                                false
                            }

                        val isFirstUnread = firstUnreadIndex == index
                        val selected by
                            remember(message.uuid, selectedMessageIds.value) {
                                derivedStateOf { selectedMessageIds.value.contains(message.uuid) }
                            }
                        val node = nodeMap[message.node.num] ?: message.node

                        if (isFirstUnread) {
                            Column {
                                UnreadMessagesDivider()
                                DesktopMessageItemRow(
                                    message = message,
                                    node = node,
                                    ourNode = ourNode ?: Node(num = 0),
                                    selected = selected,
                                    inSelectionMode = inSelectionMode,
                                    selectedMessageIds = selectedMessageIds,
                                    contactKey = contactKey,
                                    viewModel = viewModel,
                                    listState = listState,
                                    messages = messages,
                                    onShowStatusDialog = { showStatusDialog = it },
                                    onReply = { replyingToPacketId = it?.packetId },
                                    hasSamePrev = hasSamePrev,
                                    hasSameNext = hasSameNext,
                                    showUserName = !isSender && !hasSamePrev,
                                    quickEmojis = viewModel.frequentEmojis,
                                )
                            }
                        } else {
                            DesktopMessageItemRow(
                                message = message,
                                node = node,
                                ourNode = ourNode ?: Node(num = 0),
                                selected = selected,
                                inSelectionMode = inSelectionMode,
                                selectedMessageIds = selectedMessageIds,
                                contactKey = contactKey,
                                viewModel = viewModel,
                                listState = listState,
                                messages = messages,
                                onShowStatusDialog = { showStatusDialog = it },
                                onReply = { replyingToPacketId = it?.packetId },
                                hasSamePrev = hasSamePrev,
                                hasSameNext = hasSameNext,
                                showUserName = !isSender && !hasSamePrev,
                                quickEmojis = viewModel.frequentEmojis,
                            )
                        }
                    }
                }
            }

            // Show FAB if we can scroll towards the newest messages (index 0).
            if (listState.canScrollBackward) {
                ScrollToBottomFab(coroutineScope = coroutineScope, listState = listState, unreadCount = unreadCount)
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun DesktopMessageItemRow(
    message: org.meshtastic.core.model.Message,
    node: Node,
    ourNode: Node,
    selected: Boolean,
    inSelectionMode: Boolean,
    selectedMessageIds: androidx.compose.runtime.MutableState<Set<Long>>,
    contactKey: String,
    viewModel: MessageViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    messages: List<org.meshtastic.core.model.Message>,
    onShowStatusDialog: (org.meshtastic.core.model.Message) -> Unit,
    onReply: (org.meshtastic.core.model.Message?) -> Unit,
    hasSamePrev: Boolean,
    hasSameNext: Boolean,
    showUserName: Boolean,
    quickEmojis: List<String>,
) {
    val coroutineScope = rememberCoroutineScope()

    MessageItem(
        message = message,
        node = node,
        ourNode = ourNode,
        selected = selected,
        inSelectionMode = inSelectionMode,
        onClick = { if (inSelectionMode) selectedMessageIds.value = selectedMessageIds.value.toggle(message.uuid) },
        onLongClick = {
            if (inSelectionMode) {
                selectedMessageIds.value = selectedMessageIds.value.toggle(message.uuid)
            }
        },
        onSelect = { selectedMessageIds.value = selectedMessageIds.value.toggle(message.uuid) },
        onDelete = { viewModel.deleteMessages(listOf(message.uuid)) },
        onReply = { onReply(message) },
        sendReaction = { emoji ->
            val hasReacted =
                message.emojis.any { reaction ->
                    (reaction.user.id == ourNode.user.id || reaction.user.id == DataPacket.ID_LOCAL) &&
                        reaction.emoji == emoji
                }
            if (!hasReacted) {
                viewModel.sendReaction(emoji, message.packetId, contactKey)
            }
        },
        onStatusClick = { onShowStatusDialog(message) },
        onNavigateToOriginalMessage = { replyId ->
            coroutineScope.launch {
                val targetIndex = messages.indexOfFirst { it.packetId == replyId }.takeIf { it != -1 }
                if (targetIndex != null) {
                    listState.animateScrollToItem(targetIndex)
                }
            }
        },
        emojis = message.emojis,
        showUserName = showUserName,
        hasSamePrev = hasSamePrev,
        hasSameNext = hasSameNext,
        quickEmojis = quickEmojis,
    )
}

private fun Set<Long>.toggle(uuid: Long): Set<Long> = if (contains(uuid)) this - uuid else this + uuid

/** Debounce delay before marking messages as read after scroll settles. */
private const val SCROLL_SETTLE_MILLIS = 300L
