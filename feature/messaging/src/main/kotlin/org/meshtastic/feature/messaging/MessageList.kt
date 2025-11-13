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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.Reaction
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.close
import org.meshtastic.core.strings.new_messages_below
import org.meshtastic.core.strings.relayed_by
import org.meshtastic.core.strings.resend
import org.meshtastic.feature.messaging.component.MessageItem
import org.meshtastic.feature.messaging.component.ReactionDialog
import kotlin.collections.buildList

@Composable
fun DeliveryInfo(
    title: StringResource,
    text: StringResource? = null,
    relayNodeName: String? = null,
    onConfirm: (() -> Unit) = {},
    onDismiss: () -> Unit = {},
    resendOption: Boolean,
) = AlertDialog(
    onDismissRequest = onDismiss,
    dismissButton = {
        FilledTonalButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(text = stringResource(Res.string.close))
        }
    },
    confirmButton = {
        if (resendOption) {
            FilledTonalButton(onClick = onConfirm, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = stringResource(Res.string.resend))
            }
        }
    },
    title = {
        Text(
            text = stringResource(title),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
        )
    },
    text = {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            text?.let {
                Text(
                    text = stringResource(it),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            relayNodeName?.let {
                Text(
                    text = stringResource(Res.string.relayed_by, it),
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    },
    shape = RoundedCornerShape(16.dp),
    containerColor = MaterialTheme.colorScheme.surface,
)

@Suppress("LongMethod")
@Composable
internal fun MessageList(
    nodes: List<Node>,
    ourNode: Node?,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    messages: List<Message>,
    selectedIds: MutableState<Set<Long>>,
    hasUnreadMessages: Boolean,
    initialUnreadMessageUuid: Long?,
    fallbackUnreadIndex: Int?,
    onUnreadChanged: (Long, Long) -> Unit,
    onSendReaction: (String, Int) -> Unit,
    onClickChip: (Node) -> Unit,
    onDeleteMessages: (List<Long>) -> Unit,
    onSendMessage: (messageText: String, contactKey: String) -> Unit,
    contactKey: String,
    onReply: (Message?) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val inSelectionMode by remember { derivedStateOf { selectedIds.value.isNotEmpty() } }
    val unreadDividerIndex by
        remember(messages, initialUnreadMessageUuid, fallbackUnreadIndex) {
            derivedStateOf {
                initialUnreadMessageUuid
                    ?.let { uuid ->
                        messages.indexOfFirst { it.uuid == uuid }.takeIf { it >= 0 }
                    } ?: fallbackUnreadIndex
            }
        }
    val showUnreadDivider = hasUnreadMessages && unreadDividerIndex != null
    AutoScrollToBottom(listState, messages, hasUnreadMessages)
    UpdateUnreadCount(listState, messages, onUnreadChanged)

    var showStatusDialog by remember { mutableStateOf<Message?>(null) }
    if (showStatusDialog != null) {
        val msg = showStatusDialog ?: return
        val (title, text) = msg.getStatusStringRes()
        val relayNodeName by
            remember(msg.relayNode, nodes) {
                derivedStateOf {
                    msg.relayNode?.let { relayNodeId -> Packet.getRelayNode(relayNodeId, nodes)?.user?.longName }
                }
            }
        DeliveryInfo(
            title = title,
            text = text,
            relayNodeName = relayNodeName,
            onConfirm = {
                val deleteList: List<Long> = listOf(msg.uuid)
                onDeleteMessages(deleteList)
                showStatusDialog = null
                onSendMessage(msg.text, contactKey)
            },
            onDismiss = { showStatusDialog = null },
            resendOption = msg.status?.equals(MessageStatus.ERROR) ?: false,
        )
    }

    var showReactionDialog by remember { mutableStateOf<List<Reaction>?>(null) }
    if (showReactionDialog != null) {
        val reactions = showReactionDialog ?: return
        ReactionDialog(reactions) { showReactionDialog = null }
    }

    fun MutableState<Set<Long>>.toggle(uuid: Long) = if (value.contains(uuid)) {
        value -= uuid
    } else {
        value += uuid
    }

    val coroutineScope = rememberCoroutineScope()
    val messageRows by
        remember(messages, showUnreadDivider, unreadDividerIndex, initialUnreadMessageUuid) {
            derivedStateOf {
                buildList<MessageListRow> {
                    messages.forEachIndexed { index, message ->
                        if (showUnreadDivider && unreadDividerIndex == index) {
                            val key =
                                initialUnreadMessageUuid?.let { "unread-divider-$it" }
                                    ?: "unread-divider-index-$index"
                            add(MessageListRow.UnreadDivider(key = key))
                        }
                        add(MessageListRow.ChatMessage(index = index, message = message))
                    }
                }
            }
        }

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
                is MessageListRow.ChatMessage -> {
                    if (ourNode != null) {
                        val msg = row.message
                        val selected = selectedIds.value.contains(msg.uuid)
                        val node by
                            remember(msg.node.num, nodes) {
                                derivedStateOf { nodes.find { it.num == msg.node.num } ?: msg.node }
                            }

                        MessageItem(
                            modifier = Modifier.animateItem(),
                            node = node,
                            ourNode = ourNode,
                            message = msg,
                            selected = selected,
                            onClick = { if (inSelectionMode) selectedIds.toggle(msg.uuid) },
                            onLongClick = {
                                selectedIds.toggle(msg.uuid)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onClickChip = onClickChip,
                            onStatusClick = { showStatusDialog = msg },
                            onReply = { onReply(msg) },
                            emojis = msg.emojis,
                            sendReaction = { onSendReaction(it, msg.packetId) },
                            onShowReactions = { showReactionDialog = msg.emojis },
                            onNavigateToOriginalMessage = {
                                coroutineScope.launch {
                                    val targetIndex = messages.indexOfFirst { it.packetId == msg.replyId }
                                    if (targetIndex != -1) {
                                        listState.animateScrollToItem(index = targetIndex)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface MessageListRow {
    data class ChatMessage(val index: Int, val message: Message) : MessageListRow
    data class UnreadDivider(val key: String) : MessageListRow
}

internal fun findEarliestUnreadIndex(messages: List<Message>, lastReadMessageTimestamp: Long?): Int? {
    if (messages.isEmpty()) {
        return null
    }
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
    val shouldAutoScroll by remember(hasUnreadMessages) {
        derivedStateOf { !hasUnreadMessages && firstVisibleItemIndex < itemThreshold }
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
            .debounce(timeoutMillis = 500L)
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
