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

import androidx.annotation.StringRes
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.meshtastic.core.database.entity.Reaction
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.strings.R
import org.meshtastic.feature.messaging.component.MessageItem
import org.meshtastic.feature.messaging.component.ReactionDialog

@Composable
fun DeliveryInfo(
    @StringRes title: Int,
    @StringRes text: Int? = null,
    onConfirm: (() -> Unit) = {},
    onDismiss: () -> Unit = {},
    resendOption: Boolean,
) = AlertDialog(
    onDismissRequest = onDismiss,
    dismissButton = {
        FilledTonalButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(text = stringResource(id = R.string.close))
        }
    },
    confirmButton = {
        if (resendOption) {
            FilledTonalButton(onClick = onConfirm, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = stringResource(id = R.string.resend))
            }
        }
    },
    title = {
        Text(
            text = stringResource(id = title),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
        )
    },
    text = {
        text?.let {
            Text(
                text = stringResource(id = it),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
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
    onUnreadChanged: (Long) -> Unit,
    onSendReaction: (String, Int) -> Unit,
    onClickChip: (Node) -> Unit,
    onDeleteMessages: (List<Long>) -> Unit,
    onSendMessage: (messageText: String, contactKey: String) -> Unit,
    contactKey: String,
    onReply: (Message?) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val inSelectionMode by remember { derivedStateOf { selectedIds.value.isNotEmpty() } }
    AutoScrollToBottom(listState, messages)
    UpdateUnreadCount(listState, messages, onUnreadChanged)

    var showStatusDialog by remember { mutableStateOf<Message?>(null) }
    if (showStatusDialog != null) {
        val msg = showStatusDialog ?: return
        val (title, text) = msg.getStatusStringRes()
        DeliveryInfo(
            title = title,
            text = text,
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
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState, reverseLayout = true) {
        items(messages, key = { it.uuid }) { msg ->
            if (ourNode != null) {
                val selected by remember { derivedStateOf { selectedIds.value.contains(msg.uuid) } }
                val node by remember { derivedStateOf { nodes.find { it.num == msg.node.num } ?: msg.node } }

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

@Composable
private fun <T> AutoScrollToBottom(listState: LazyListState, list: List<T>, itemThreshold: Int = 3) = with(listState) {
    val shouldAutoScroll by remember { derivedStateOf { firstVisibleItemIndex < itemThreshold } }
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
private fun UpdateUnreadCount(listState: LazyListState, messages: List<Message>, onUnreadChanged: (Long) -> Unit) {
    LaunchedEffect(messages) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .debounce(timeoutMillis = 500L)
            .collectLatest { index ->
                val lastUnreadIndex = messages.indexOfLast { !it.read }
                if (lastUnreadIndex != -1 && index <= lastUnreadIndex && index < messages.size) {
                    val visibleMessage = messages[index]
                    onUnreadChanged(visibleMessage.receivedTime)
                }
            }
    }
}
