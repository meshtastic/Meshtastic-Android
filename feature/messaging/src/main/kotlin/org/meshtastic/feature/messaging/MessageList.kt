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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlin.collections.buildList

internal data class MessageListState(
    val nodes: List<Node>,
    val ourNode: Node?,
    val messages: List<Message>,
    val selectedIds: MutableState<Set<Long>>,
    val hasUnreadMessages: Boolean,
    val initialUnreadMessageUuid: Long?,
    val fallbackUnreadIndex: Int?,
    val contactKey: String,
)

internal data class MessageListHandlers(
    val onUnreadChanged: (Long, Long) -> Unit,
    val onSendReaction: (String, Int) -> Unit,
    val onClickChip: (Node) -> Unit,
    val onDeleteMessages: (List<Long>) -> Unit,
    val onSendMessage: (String, String) -> Unit,
    val onReply: (Message?) -> Unit,
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
internal fun MessageList(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    state: MessageListState,
    handlers: MessageListHandlers,
) {
    val haptics = LocalHapticFeedback.current
    val inSelectionMode by remember { derivedStateOf { state.selectedIds.value.isNotEmpty() } }
    val unreadDividerIndex by
        remember(state.messages, state.initialUnreadMessageUuid, state.fallbackUnreadIndex) {
            derivedStateOf {
                state.initialUnreadMessageUuid?.let { uuid ->
                    state.messages.indexOfFirst { it.uuid == uuid }.takeIf { it >= 0 }
                } ?: state.fallbackUnreadIndex
            }
        }
    val showUnreadDivider = state.hasUnreadMessages && unreadDividerIndex != null
    AutoScrollToBottom(listState, state.messages, state.hasUnreadMessages)
    UpdateUnreadCount(listState, state.messages, handlers.onUnreadChanged)

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
    val messageRows =
        rememberMessageRows(
            messages = state.messages,
            showUnreadDivider = showUnreadDivider,
            unreadDividerIndex = unreadDividerIndex,
            initialUnreadMessageUuid = state.initialUnreadMessageUuid,
        )

    MessageListContent(
        listState = listState,
        messageRows = messageRows,
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

private sealed interface MessageListRow {
    data class ChatMessage(val index: Int, val message: Message) : MessageListRow

    data class UnreadDivider(val key: String) : MessageListRow
}

@Composable
private fun MessageListContent(
    listState: LazyListState,
    messageRows: List<MessageListRow>,
    state: MessageListState,
    handlers: MessageListHandlers,
    inSelectionMode: Boolean,
    coroutineScope: CoroutineScope,
    haptics: HapticFeedback,
    onShowStatusDialog: (Message) -> Unit,
    onShowReactions: (List<Reaction>) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState, reverseLayout = true) {
        items(
            items = messageRows,
            key = { row ->
                when (row) {
                    is MessageListRow.ChatMessage -> row.message.uuid
                    is MessageListRow.UnreadDivider -> row.key
                }
            },
        ) { row ->
            when (row) {
                is MessageListRow.UnreadDivider -> UnreadMessagesDivider(modifier = Modifier.animateItem())
                is MessageListRow.ChatMessage -> renderChatMessageRow(
                    row = row,
                    state = state,
                    handlers = handlers,
                    inSelectionMode = inSelectionMode,
                    coroutineScope = coroutineScope,
                    haptics = haptics,
                    listState = listState,
                    onShowStatusDialog = onShowStatusDialog,
                    onShowReactions = onShowReactions,
                )
            }
        }
    }
}

@Composable
private fun LazyItemScope.renderChatMessageRow(
    row: MessageListRow.ChatMessage,
    state: MessageListState,
    handlers: MessageListHandlers,
    inSelectionMode: Boolean,
    coroutineScope: CoroutineScope,
    haptics: HapticFeedback,
    listState: LazyListState,
    onShowStatusDialog: (Message) -> Unit,
    onShowReactions: (List<Reaction>) -> Unit,
) {
    val ourNode = state.ourNode ?: return
    val message = row.message
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
                val targetIndex = state.messages.indexOfFirst { it.packetId == message.replyId }
                if (targetIndex != -1) {
                    listState.animateScrollToItem(index = targetIndex)
                }
            }
        },
    )
}

@Composable
private fun MessageStatusDialog(
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
        onConfirm = onResend,
        onDismiss = onDismiss,
    )
}

@Composable
private fun rememberMessageRows(
    messages: List<Message>,
    showUnreadDivider: Boolean,
    unreadDividerIndex: Int?,
    initialUnreadMessageUuid: Long?,
) = remember(messages, showUnreadDivider, unreadDividerIndex, initialUnreadMessageUuid) {
    buildList<MessageListRow> {
        messages.forEachIndexed { index, message ->
            add(MessageListRow.ChatMessage(index = index, message = message))
            if (showUnreadDivider && unreadDividerIndex == index) {
                val key = initialUnreadMessageUuid?.let { "unread-divider-$it" } ?: "unread-divider-index-$index"
                add(MessageListRow.UnreadDivider(key = key))
            }
        }
    }
}

/**
 * Calculates the index of the first unread remote message.
 *
 * We track unread state with two sources: the persisted timestamp of the last read message and the in-memory
 * `Message.read` flag. The timestamp helps when the local flag state is stale (e.g. after app restarts), while the flag
 * catches messages that are already marked read locally. We take the maximum of the two indices to target the oldest
 * unread entry that still needs attention. The message list is newest-first, so we deliberately use `lastOrNull` for
 * the timestamp branch to land on the oldest unread item after the stored mark.
 */
internal fun findEarliestUnreadIndex(messages: List<Message>, lastReadMessageTimestamp: Long?): Int? {
    val remoteMessages = messages.withIndex().filter { !it.value.fromLocal }
    if (remoteMessages.isEmpty()) {
        return null
    }
    val timestampIndex =
        lastReadMessageTimestamp?.let { timestamp ->
            remoteMessages.lastOrNull { it.value.receivedTime > timestamp }?.index
        }
    val readFlagIndex = messages.indexOfLast { !it.read && !it.fromLocal }.takeIf { it != -1 }
    return listOfNotNull(timestampIndex, readFlagIndex).maxOrNull()
}

@Composable
private fun UnreadMessagesDivider(modifier: Modifier = Modifier) {
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
private fun <T> AutoScrollToBottom(
    listState: LazyListState,
    list: List<T>,
    hasUnreadMessages: Boolean,
    itemThreshold: Int = 3,
) = with(listState) {
    val shouldAutoScroll by
        remember(hasUnreadMessages) {
            derivedStateOf {
                val isAtBottom =
                    firstVisibleItemIndex == 0 &&
                        firstVisibleItemScrollOffset <= UnreadUiDefaults.AUTO_SCROLL_BOTTOM_OFFSET_TOLERANCE
                val isNearBottom = firstVisibleItemIndex <= itemThreshold
                isAtBottom || (!hasUnreadMessages && isNearBottom)
            }
        }
    if (shouldAutoScroll) {
        LaunchedEffect(list) {
            if (!isScrollInProgress) {
                scrollToItem(0)
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun UpdateUnreadCount(
    listState: LazyListState,
    messages: List<Message>,
    onUnreadChanged: (Long, Long) -> Unit,
) {
    LaunchedEffect(messages) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .debounce(timeoutMillis = UnreadUiDefaults.SCROLL_DEBOUNCE_MILLIS)
            .collectLatest { index ->
                val lastUnreadIndex = messages.indexOfLast { !it.read && !it.fromLocal }
                if (lastUnreadIndex != -1 && index <= lastUnreadIndex && index < messages.size) {
                    val visibleMessage = messages[index]
                    if (!visibleMessage.read && !visibleMessage.fromLocal) {
                        onUnreadChanged(visibleMessage.uuid, visibleMessage.receivedTime)
                    }
                }
            }
    }
}
