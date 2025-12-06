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

package org.meshtastic.feature.messaging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.Reaction
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.new_messages_below
import org.meshtastic.feature.messaging.component.MessageItem
import org.meshtastic.feature.messaging.component.ReactionDialog

internal data class MessageListHandlers(
    val onUnreadChanged: (Long, Long) -> Unit,
    val onSendReaction: (String, Int) -> Unit,
    val onClickChip: (Node) -> Unit,
    val onDeleteMessages: (List<Long>) -> Unit,
    val onSendMessage: (String, String) -> Unit,
    val onReply: (Message?) -> Unit,
)

internal data class MessageListPagedState(
    val nodes: List<Node>,
    val ourNode: Node?,
    val messages: LazyPagingItems<Message>,
    val selectedIds: MutableState<Set<Long>>,
    val contactKey: String,
    val firstUnreadMessageUuid: Long? = null,
    val hasUnreadMessages: Boolean = false,
)

private fun MutableState<Set<Long>>.toggle(uuid: Long) {
    value =
        if (value.contains(uuid)) {
            value - uuid
        } else {
            value + uuid
        }
}

@Composable
internal fun MessageListPaged(
    state: MessageListPagedState,
    handlers: MessageListHandlers,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val haptics = LocalHapticFeedback.current
    val inSelectionMode by remember { derivedStateOf { state.selectedIds.value.isNotEmpty() } }

    var showStatusDialog by remember { mutableStateOf<Message?>(null) }
    showStatusDialog?.let { message ->
        MessageStatusDialog(
            message = message,
            nodes = state.nodes,
            resendOption = message.status?.equals(MessageStatus.ERROR) ?: false,
            onResend = {
                handlers.onDeleteMessages(listOf(message.uuid))
                handlers.onSendMessage(message.text, state.contactKey)
                showStatusDialog = null
            },
            onDismiss = { showStatusDialog = null },
        )
    }

    var showReactionDialog by remember { mutableStateOf<List<Reaction>?>(null) }
    showReactionDialog?.let { reactions -> ReactionDialog(reactions) { showReactionDialog = null } }

    val coroutineScope = rememberCoroutineScope()

    // Disable auto-scroll when any dialog is open to prevent list jumping
    val hasDialogOpen = showStatusDialog != null || showReactionDialog != null

    // Track unread count based on scroll position
    UpdateUnreadCountPaged(listState = listState, messages = state.messages, onUnreadChange = handlers.onUnreadChanged)

    // Auto-scroll to bottom when new messages arrive
    AutoScrollToBottomPaged(
        listState = listState,
        messages = state.messages,
        hasUnreadMessages = state.hasUnreadMessages,
        hasDialogOpen = hasDialogOpen,
    )

    MessageListPagedContent(
        listState = listState,
        state = state,
        handlers = handlers,
        inSelectionMode = inSelectionMode,
        coroutineScope = coroutineScope,
        haptics = haptics,
        onShowStatusDialog = { showStatusDialog = it },
        onShowReactions = { showReactionDialog = it },
        modifier = modifier,
    )
}

@Composable
private fun MessageListPagedContent(
    listState: LazyListState,
    state: MessageListPagedState,
    handlers: MessageListHandlers,
    inSelectionMode: Boolean,
    coroutineScope: CoroutineScope,
    haptics: HapticFeedback,
    onShowStatusDialog: (Message) -> Unit,
    onShowReactions: (List<Reaction>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Calculate unread divider position
    val unreadDividerIndex by
        remember(state.messages.itemCount, state.firstUnreadMessageUuid) {
            derivedStateOf {
                state.firstUnreadMessageUuid?.let { uuid ->
                    (0 until state.messages.itemCount).firstOrNull { index -> state.messages[index]?.uuid == uuid }
                }
            }
        }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, reverseLayout = true) {
            items(count = state.messages.itemCount, key = state.messages.itemKey { it.uuid }) { index ->
                val message = state.messages[index]
                if (message != null) {
                    renderPagedChatMessageRow(
                        message = message,
                        state = state,
                        handlers = handlers,
                        inSelectionMode = inSelectionMode,
                        coroutineScope = coroutineScope,
                        haptics = haptics,
                        listState = listState,
                        onShowStatusDialog = onShowStatusDialog,
                        onShowReactions = onShowReactions,
                    )

                    // Show unread divider after the first unread message
                    if (state.hasUnreadMessages && unreadDividerIndex == index) {
                        UnreadMessagesDivider(modifier = Modifier.animateItem())
                    }
                }
            }

            // Loading indicator at the end (top when reversed) when loading more items
            state.messages.apply {
                when {
                    loadState.append is LoadState.Loading -> {
                        item(key = "append_loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.renderPagedChatMessageRow(
    message: Message,
    state: MessageListPagedState,
    handlers: MessageListHandlers,
    inSelectionMode: Boolean,
    coroutineScope: CoroutineScope,
    haptics: HapticFeedback,
    listState: LazyListState,
    onShowStatusDialog: (Message) -> Unit,
    onShowReactions: (List<Reaction>) -> Unit,
) {
    val ourNode = state.ourNode ?: return
    val selected by
        remember(message.uuid, state.selectedIds.value) {
            derivedStateOf { state.selectedIds.value.contains(message.uuid) }
        }
    val node by
        remember(message.node.num, state.nodes) {
            derivedStateOf { state.nodes.find { it.num == message.node.num } ?: message.node }
        }

    MessageItem(
        modifier = Modifier.animateItem(),
        node = node,
        ourNode = ourNode,
        message = message,
        selected = selected,
        onClick = { if (inSelectionMode) state.selectedIds.toggle(message.uuid) },
        onLongClick = {
            state.selectedIds.toggle(message.uuid)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onClickChip = handlers.onClickChip,
        onStatusClick = { onShowStatusDialog(message) },
        onReply = { handlers.onReply(message) },
        emojis = message.emojis,
        sendReaction = { handlers.onSendReaction(it, message.packetId) },
        onShowReactions = { onShowReactions(message.emojis) },
        onNavigateToOriginalMessage = {
            coroutineScope.launch {
                // Note: With pagination, we can't guarantee the original message is loaded
                // This is a limitation of pagination - we would need to implement
                // a search/jump feature to load and scroll to specific messages
                val targetIndex =
                    (0 until state.messages.itemCount).firstOrNull { index ->
                        state.messages[index]?.packetId == message.replyId
                    }
                if (targetIndex != null) {
                    listState.animateScrollToItem(index = targetIndex)
                }
            }
        },
    )
}

@Suppress("CyclomaticComplexMethod")
@Composable
private fun AutoScrollToBottomPaged(
    listState: LazyListState,
    messages: LazyPagingItems<Message>,
    hasUnreadMessages: Boolean,
    hasDialogOpen: Boolean = false,
    itemThreshold: Int = 3,
) = with(listState) {
    val shouldStickToBottom by
        remember(hasUnreadMessages, hasDialogOpen) {
            derivedStateOf {
                if (hasDialogOpen) {
                    false
                } else {
                    val isAtBottom =
                        firstVisibleItemIndex == 0 &&
                            firstVisibleItemScrollOffset <= UnreadUiDefaults.AUTO_SCROLL_BOTTOM_OFFSET_TOLERANCE
                    val isNearBottom = firstVisibleItemIndex <= itemThreshold
                    isAtBottom || (!hasUnreadMessages && isNearBottom)
                }
            }
        }

    val isRefreshing by remember { derivedStateOf { messages.loadState.refresh is LoadState.Loading } }
    var wasPreviouslyRefreshing by remember { mutableStateOf(false) }

    // Maintain scroll position during and after refresh
    LaunchedEffect(isRefreshing, shouldStickToBottom) {
        if (!shouldStickToBottom) return@LaunchedEffect

        if (isRefreshing) {
            wasPreviouslyRefreshing = true
            if (!isScrollInProgress && messages.itemCount > 0) {
                scrollToItem(0)
            }
        } else if (wasPreviouslyRefreshing) {
            wasPreviouslyRefreshing = false
            if (messages.itemCount > 0) {
                scrollToItem(0)
            }
        }
    }

    // Normal auto-scroll for new messages (when not refreshing)
    if (shouldStickToBottom && !isRefreshing) {
        LaunchedEffect(messages.itemCount) {
            if (!isScrollInProgress && messages.itemCount > 0) {
                scrollToItem(0)
            }
        }
    }
}

private fun findFirstVisibleUnreadMessage(messages: LazyPagingItems<Message>, visibleIndex: Int): Message? {
    val firstVisibleUnreadIndex =
        (visibleIndex until messages.itemCount).firstOrNull { i ->
            val msg = messages[i]
            msg != null && !msg.read && !msg.fromLocal
        }
    return firstVisibleUnreadIndex?.let { messages[it] }
}

private fun findLastUnreadMessageIndex(messages: LazyPagingItems<Message>): Int? =
    (0 until messages.itemCount).lastOrNull { i ->
        val msg = messages[i]
        msg != null && !msg.read && !msg.fromLocal
    }

@OptIn(FlowPreview::class)
@Composable
private fun UpdateUnreadCountPaged(
    listState: LazyListState,
    messages: LazyPagingItems<Message>,
    onUnreadChange: (Long, Long) -> Unit,
) {
    val currentOnUnreadChange by rememberUpdatedState(onUnreadChange)
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    // Track lifecycle state changes
    DisposableEffect(lifecycleOwner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> isResumed = true
                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> isResumed = false
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Track remote message count to restart effect when remote messages change
    // This fixes race condition when sending/receiving messages during debounce period
    val remoteMessageCount by
        remember(messages.itemCount) {
            derivedStateOf {
                (0 until messages.itemCount).count { i ->
                    val msg = messages[i]
                    msg != null && !msg.fromLocal
                }
            }
        }

    // Mark messages as read after debounce period
    // Handles both scrolling cases and when all unread messages are visible without scrolling
    // Effect restarts when isResumed changes, so returning from background will restart the debounce
    LaunchedEffect(remoteMessageCount, listState, isResumed) {
        snapshotFlow {
            // Emit when scroll stops OR when at initial position (covers no-scroll case)
            // Include isResumed in the snapshot so lifecycle changes trigger new emissions
            if (listState.isScrollInProgress || !isResumed) {
                null // Scrolling in progress or not resumed, don't emit
            } else {
                listState.firstVisibleItemIndex // Emit current position when not scrolling and resumed
            }
        }
            .debounce(timeoutMillis = UnreadUiDefaults.SCROLL_DEBOUNCE_MILLIS)
            .collectLatest { index ->
                // Only mark messages as read if we have a valid index (screen is visible and not scrolling)
                if (index != null) {
                    val lastUnreadIndex = findLastUnreadMessageIndex(messages)
                    // If we're at/past the oldest unread, mark the first visible unread message
                    // Since newer messages have HIGHER timestamps, marking a newer message's timestamp
                    // will batch-mark all older messages via SQL: WHERE received_time <= timestamp
                    if (lastUnreadIndex != null && index <= lastUnreadIndex) {
                        val firstVisibleUnread = findFirstVisibleUnreadMessage(messages, index)
                        firstVisibleUnread?.let { currentOnUnreadChange(it.uuid, it.receivedTime) }
                    }
                }
            }
    }
}

@Composable
internal fun UnreadMessagesDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(Res.string.new_messages_below),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun MessageStatusDialog(
    message: Message,
    nodes: List<Node>,
    resendOption: Boolean,
    onResend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, text) = message.getStatusStringRes()
    val relayNodeName by
        remember(message.relayNode, nodes) {
            derivedStateOf {
                message.relayNode?.let { relayNodeId -> Packet.getRelayNode(relayNodeId, nodes)?.user?.longName }
            }
        }
    DeliveryInfo(
        title = title,
        resendOption = resendOption,
        text = text,
        relayNodeName = relayNodeName,
        relays = message.relays,
        onConfirm = onResend,
        onDismiss = onDismiss,
    )
}
