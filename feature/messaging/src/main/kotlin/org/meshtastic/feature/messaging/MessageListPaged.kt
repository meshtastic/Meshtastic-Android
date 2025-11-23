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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.meshtastic.core.database.entity.Reaction
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.feature.messaging.component.MessageItem
import org.meshtastic.feature.messaging.component.ReactionDialog

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
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    state: MessageListPagedState,
    handlers: MessageListHandlers,
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

    // Track unread count based on scroll position
    UpdateUnreadCountPaged(
        listState = listState,
        messages = state.messages,
        onUnreadChanged = handlers.onUnreadChanged,
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
    val unreadDividerIndex by remember(state.messages.itemCount, state.firstUnreadMessageUuid) {
        derivedStateOf {
            state.firstUnreadMessageUuid?.let { uuid ->
                (0 until state.messages.itemCount).firstOrNull { index ->
                    state.messages[index]?.uuid == uuid
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
        ) {
            items(
                count = state.messages.itemCount,
                key = state.messages.itemKey { it.uuid },
            ) { index ->
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
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
                val targetIndex = (0 until state.messages.itemCount).firstOrNull { index ->
                    state.messages[index]?.packetId == message.replyId
                }
                if (targetIndex != null) {
                    listState.animateScrollToItem(index = targetIndex)
                }
            }
        },
    )
}

@OptIn(FlowPreview::class)
@Composable
private fun UpdateUnreadCountPaged(
    listState: LazyListState,
    messages: LazyPagingItems<Message>,
    onUnreadChanged: (Long, Long) -> Unit,
) {
    LaunchedEffect(messages.itemCount) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .debounce(timeoutMillis = UnreadUiDefaults.SCROLL_DEBOUNCE_MILLIS)
            .collectLatest { index ->
                // Find the last unread message in the loaded items
                val lastUnreadIndex = (0 until messages.itemCount).lastOrNull { i ->
                    val msg = messages[i]
                    msg != null && !msg.read && !msg.fromLocal
                }

                if (lastUnreadIndex != null && index <= lastUnreadIndex && index < messages.itemCount) {
                    val visibleMessage = messages[index]
                    if (visibleMessage != null && !visibleMessage.read && !visibleMessage.fromLocal) {
                        onUnreadChanged(visibleMessage.uuid, visibleMessage.receivedTime)
                    }
                }
            }
    }
}
