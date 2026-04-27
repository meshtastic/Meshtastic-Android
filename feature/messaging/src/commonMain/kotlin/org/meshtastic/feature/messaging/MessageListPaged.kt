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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Reaction
import org.meshtastic.feature.messaging.component.MessageItem
import org.meshtastic.feature.messaging.component.MessageStatusDialog
import org.meshtastic.feature.messaging.component.ReactionDialog
import org.meshtastic.feature.messaging.component.UnreadMessagesDivider

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
    val filteredCount: Int = 0,
    val showFiltered: Boolean = false,
    val filteringDisabled: Boolean = false,
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
    quickEmojis: List<String> = emptyList(),
) {
    val haptics = LocalHapticFeedback.current
    val inSelectionMode by remember { derivedStateOf { state.selectedIds.value.isNotEmpty() } }

    // Optimization: Pre-calculate map for O(1) lookup in list items to avoid O(N) linear search during scrolling.
    val nodeMap = remember(state.nodes) { state.nodes.associateBy { it.num } }

    var showStatusDialog by remember { mutableStateOf<Message?>(null) }
    showStatusDialog?.let { message ->
        MessageStatusDialog(
            message = message,
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
    showReactionDialog?.let { reactions ->
        ReactionDialog(
            reactions = reactions,
            myId = state.ourNode?.user?.id,
            onDismiss = { showReactionDialog = null },
            onResend = { reaction ->
                handlers.onSendReaction(reaction.emoji, reaction.replyId)
                showReactionDialog = null
            },
        )
    }

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
        nodeMap = nodeMap,
        handlers = handlers,
        inSelectionMode = inSelectionMode,
        coroutineScope = coroutineScope,
        haptics = haptics,
        onShowStatusDialog = { showStatusDialog = it },
        onShowReactions = { showReactionDialog = it },
        modifier = modifier,
        quickEmojis = quickEmojis,
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun MessageListPagedContent(
    listState: LazyListState,
    state: MessageListPagedState,
    nodeMap: Map<Int, Node>,
    handlers: MessageListHandlers,
    inSelectionMode: Boolean,
    coroutineScope: CoroutineScope,
    haptics: HapticFeedback,
    onShowStatusDialog: (Message) -> Unit,
    onShowReactions: (List<Reaction>) -> Unit,
    modifier: Modifier = Modifier,
    quickEmojis: List<String>,
) {
    // Calculate unread divider position using snapshot to avoid side-effects and improve performance
    // Optimized: Use full snapshot index to correctly match LazyColumn index range
    val unreadDividerIndex by
        remember(state.messages.itemCount, state.firstUnreadMessageUuid) {
            derivedStateOf {
                val uuid = state.firstUnreadMessageUuid ?: return@derivedStateOf null
                state.messages.itemSnapshotList.indexOfFirst { it?.uuid == uuid }.takeIf { it != -1 }
            }
        }

    // Disable animations during scroll to prevent jank/stutter
    val enableAnimations by remember { derivedStateOf { !listState.isScrollInProgress } }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(
                count = state.messages.itemCount,
                key = state.messages.itemKey { it.uuid },
                contentType = state.messages.itemContentType { "message" },
            ) { index ->
                val message = state.messages[index]
                val visuallyPrevMessage = if (index < state.messages.itemCount - 1) state.messages[index + 1] else null
                val visuallyNextMessage = if (index > 0) state.messages[index - 1] else null

                val hasSamePrev =
                    if (message != null && visuallyPrevMessage != null) {
                        visuallyPrevMessage.fromLocal == message.fromLocal &&
                            (message.fromLocal || visuallyPrevMessage.node.num == message.node.num)
                    } else {
                        false
                    }

                val hasSameNext =
                    if (message != null && visuallyNextMessage != null) {
                        visuallyNextMessage.fromLocal == message.fromLocal &&
                            (message.fromLocal || visuallyNextMessage.node.num == message.node.num)
                    } else {
                        false
                    }

                if (message != null) {
                    val isFirstUnread = state.hasUnreadMessages && unreadDividerIndex == index
                    val itemModifier = if (enableAnimations) Modifier.animateItem() else Modifier

                    if (isFirstUnread) {
                        // Wrap in Column to prevent overlapping of divider and message item
                        // Apply animation to the container Column once
                        Column(modifier = itemModifier) {
                            UnreadMessagesDivider()
                            RenderPagedChatMessageRow(
                                message = message,
                                state = state,
                                nodeMap = nodeMap,
                                handlers = handlers,
                                inSelectionMode = inSelectionMode,
                                coroutineScope = coroutineScope,
                                haptics = haptics,
                                listState = listState,
                                onShowStatusDialog = onShowStatusDialog,
                                onShowReactions = onShowReactions,
                                showUserName = !hasSamePrev,
                                hasSamePrev = hasSamePrev,
                                hasSameNext = hasSameNext,
                                quickEmojis = quickEmojis,
                            )
                        }
                    } else {
                        RenderPagedChatMessageRow(
                            message = message,
                            state = state,
                            nodeMap = nodeMap,
                            handlers = handlers,
                            inSelectionMode = inSelectionMode,
                            coroutineScope = coroutineScope,
                            haptics = haptics,
                            listState = listState,
                            onShowStatusDialog = onShowStatusDialog,
                            onShowReactions = onShowReactions,
                            modifier = itemModifier,
                            showUserName = !hasSamePrev,
                            hasSamePrev = hasSamePrev,
                            hasSameNext = hasSameNext,
                            quickEmojis = quickEmojis,
                        )
                    }
                }
            }

            // Loading indicator at the end (top when reversed) when loading more items
            state.messages.apply {
                when {
                    loadState.append is LoadState.Loading -> {
                        item(key = "append_loading", contentType = "loading") {
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

@Suppress("LongParameterList")
@Composable
private fun RenderPagedChatMessageRow(
    message: Message,
    state: MessageListPagedState,
    nodeMap: Map<Int, Node>,
    handlers: MessageListHandlers,
    inSelectionMode: Boolean,
    coroutineScope: CoroutineScope,
    haptics: HapticFeedback,
    listState: LazyListState,
    onShowStatusDialog: (Message) -> Unit,
    onShowReactions: (List<Reaction>) -> Unit,
    modifier: Modifier = Modifier,
    showUserName: Boolean,
    hasSamePrev: Boolean,
    hasSameNext: Boolean,
    quickEmojis: List<String>,
) {
    val ourNode = state.ourNode ?: return
    val selected by
        remember(message.uuid, state.selectedIds.value) {
            derivedStateOf { state.selectedIds.value.contains(message.uuid) }
        }
    val node = nodeMap[message.node.num] ?: message.node

    MessageItem(
        modifier = modifier,
        node = node,
        ourNode = ourNode,
        message = message,
        selected = selected,
        inSelectionMode = inSelectionMode,
        onClick = { if (inSelectionMode) state.selectedIds.toggle(message.uuid) },
        onLongClick = {
            if (inSelectionMode) {
                state.selectedIds.toggle(message.uuid)
            }
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onSelect = { state.selectedIds.toggle(message.uuid) },
        onDelete = { handlers.onDeleteMessages(listOf(message.uuid)) },
        onClickChip = handlers.onClickChip,
        onStatusClick = { onShowStatusDialog(message) },
        onReply = { handlers.onReply(message) },
        emojis = message.emojis,
        showUserName = showUserName,
        sendReaction = { emoji ->
            val hasReacted =
                message.emojis.any { reaction ->
                    (
                        reaction.user.id == ourNode.user.id ||
                            reaction.user.id == org.meshtastic.core.model.DataPacket.ID_LOCAL
                        ) && reaction.emoji == emoji
                }
            if (!hasReacted) {
                handlers.onSendReaction(emoji, message.packetId)
            }
        },
        onShowReactions = { onShowReactions(message.emojis) },
        onNavigateToOriginalMessage = {
            coroutineScope.launch {
                // Note: With pagination, we can't guarantee the original message is loaded
                // Optimized: Use snapshot to find index to avoid side-effects during search
                val targetIndex =
                    state.messages.itemSnapshotList.indexOfFirst { it?.packetId == message.replyId }.takeIf { it != -1 }

                if (targetIndex != null) {
                    listState.animateScrollToItem(index = targetIndex)
                }
            }
        },
        hasSamePrev = hasSamePrev,
        hasSameNext = hasSameNext,
        quickEmojis = quickEmojis,
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
    // Cache whether we were at the bottom - only update when not actively scrolling
    // This prevents stuttering while still tracking position for auto-scroll
    var cachedAtBottom by remember { mutableStateOf(true) }

    val isCurrentlyAtBottom by
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

    // Update cached position only when scroll is idle to prevent stuttering
    LaunchedEffect(isScrollInProgress) {
        if (!isScrollInProgress) {
            cachedAtBottom = isCurrentlyAtBottom
        }
    }

    // Consolidated scroll logic to prevent race conditions
    // Fixes issue where multiple scroll operations could trigger simultaneously
    // by unifying all scroll triggers into a single LaunchedEffect
    LaunchedEffect(messages.itemCount) {
        // Use cached position (captured when scroll was idle) to decide if we should auto-scroll
        // This prevents race conditions where new message renders before we check position
        if (cachedAtBottom && messages.itemCount > 0) {
            scrollToItem(0)
            // Update cache immediately after scrolling
            cachedAtBottom = true
        }
    }
}

private fun findFirstVisibleUnreadMessage(messages: LazyPagingItems<Message>, visibleIndex: Int): Message? {
    val snapshot = messages.itemSnapshotList
    if (visibleIndex >= snapshot.size) return null
    val firstVisibleUnreadIndex =
        (visibleIndex until snapshot.size).firstOrNull { i ->
            val msg = snapshot[i]
            msg != null && !msg.read && !msg.fromLocal
        }
    return firstVisibleUnreadIndex?.let { snapshot[it] }
}

private fun findLastUnreadMessageIndex(messages: LazyPagingItems<Message>): Int? {
    val snapshot = messages.itemSnapshotList
    return (0 until snapshot.size).lastOrNull { i ->
        val msg = snapshot[i]
        msg != null && !msg.read && !msg.fromLocal
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun UpdateUnreadCountPaged(
    listState: LazyListState,
    messages: LazyPagingItems<Message>,
    onUnreadChange: (Long, Long) -> Unit,
) {
    val currentOnUnreadChange by rememberUpdatedState(onUnreadChange)
    var isResumed by remember { mutableStateOf(false) }

    // Track lifecycle state changes
    LifecycleResumeEffect(Unit) {
        isResumed = true
        onPauseOrDispose { isResumed = false }
    }

    // Track remote message count to restart effect when remote messages change
    // This fixes race condition when sending/receiving messages during debounce period
    // Optimized: Use itemSnapshotList instead of iterating through indices
    val remoteMessageCount by
        remember(messages.itemCount) { derivedStateOf { messages.itemSnapshotList.items.count { !it.fromLocal } } }

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
