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

package com.geeksville.mesh.ui.message

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.Reaction
import com.geeksville.mesh.model.Message
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.message.components.MessageItem
import com.geeksville.mesh.ui.message.components.ReactionDialog
import com.geeksville.mesh.ui.message.components.ReactionRow
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

@Composable
fun DeliveryInfo(
    @StringRes title: Int,
    @StringRes text: Int? = null,
    message: Message,
    onConfirm: (() -> Unit) = {},
    onDismiss: () -> Unit = {},
    onRetryNow: (() -> Unit) = {},
    onCancelQueue: (() -> Unit) = {},
) = AlertDialog(
    onDismissRequest = onDismiss,
    dismissButton = if (message.status != MessageStatus.QUEUED_FOR_RETRY) {
        {
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text(text = stringResource(id = R.string.close)) }
        }
    } else null,
    confirmButton = {
        when (message.status) {
            MessageStatus.QUEUED_FOR_RETRY -> {
                // For queued messages, show retry now and cancel queue options vertically stacked
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = onRetryNow,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(text = stringResource(id = R.string.message_queue_manual_retry)) }
                    
                    FilledTonalButton(
                        onClick = onCancelQueue,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(text = stringResource(id = R.string.delete)) }
                    
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(text = stringResource(id = R.string.close)) }
                }
            }
            MessageStatus.ERROR -> {
                // For failed messages, show resend option
                FilledTonalButton(
                    onClick = onConfirm,
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) { Text(text = stringResource(id = R.string.resend)) }
            }
            else -> {
                // For other statuses, no action buttons needed
            }
        }
    },
    title = {
        Text(
            text = stringResource(id = title),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall
        )
    },
    text = {
        Column {
            text?.let {
                Text(
                    text = stringResource(id = it),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // Show additional details for queued messages
            if (message.status == MessageStatus.QUEUED_FOR_RETRY) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.message_queue_retry_details),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    },
    shape = RoundedCornerShape(16.dp),
    containerColor = MaterialTheme.colorScheme.surface
)

@Suppress("LongMethod")
@Composable
internal fun MessageList(
    modifier: Modifier = Modifier,
    messages: List<Message>,
    selectedIds: MutableState<Set<Long>>,
    onUnreadChanged: (Long) -> Unit,
    onSendReaction: (String, Int) -> Unit,
    onNodeMenuAction: (NodeMenuAction) -> Unit,
    viewModel: UIViewModel,
    contactKey: String,
) {
    val haptics = LocalHapticFeedback.current
    val inSelectionMode by remember { derivedStateOf { selectedIds.value.isNotEmpty() } }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = messages.indexOfLast { !it.read }.coerceAtLeast(0)
    )
    AutoScrollToBottom(listState, messages)
    UpdateUnreadCount(listState, messages, onUnreadChanged)

    var showStatusDialog by remember { mutableStateOf<Message?>(null) }
    if (showStatusDialog != null) {
        val msg = showStatusDialog ?: return
        val (title, text) = msg.getStatusStringRes()
        DeliveryInfo(
            title = title,
            text = text,
            message = msg,
            onConfirm = {
                val deleteList: List<Long> = listOf(msg.uuid)
                viewModel.deleteMessages(deleteList)
                showStatusDialog = null
                viewModel.sendMessage(msg.text, contactKey)
            },
            onDismiss = { showStatusDialog = null },
            onRetryNow = {
                viewModel.retryQueuedMessage(msg.uuid, contactKey)
                showStatusDialog = null
            },
            onCancelQueue = {
                val deleteList: List<Long> = listOf(msg.uuid)
                viewModel.deleteMessages(deleteList)
                showStatusDialog = null
            }
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

    val nodes by viewModel.nodeList.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle(false)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
    ) {
        items(messages, key = { it.uuid }) { msg ->
            val fromLocal = msg.node.user.id == DataPacket.ID_LOCAL
            val selected by remember { derivedStateOf { selectedIds.value.contains(msg.uuid) } }
            var node by remember {
                mutableStateOf(nodes.find { it.num == msg.node.num } ?: msg.node)
            }
            LaunchedEffect(nodes) {
                node = nodes.find { it.num == msg.node.num } ?: msg.node
            }
            ReactionRow(fromLocal, msg.emojis) { showReactionDialog = msg.emojis }
            Box(Modifier.wrapContentSize(Alignment.TopStart)) {
                MessageItem(
                    node = node,
                    messageText = msg.text,
                    messageTime = msg.time,
                    messageStatus = msg.status,
                    selected = selected,
                    onClick = { if (inSelectionMode) selectedIds.toggle(msg.uuid) },
                    onLongClick = {
                        selectedIds.toggle(msg.uuid)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onAction = onNodeMenuAction,
                    onStatusClick = { showStatusDialog = msg },
                    onSendReaction = { onSendReaction(it, msg.packetId) },
                    isConnected = isConnected,
                    snr = msg.snr,
                    rssi = msg.rssi,
                    hopsAway = if (msg.hopsAway > 0) { "%s: %d".format(
                            stringResource(id = R.string.hops_away),
                            msg.hopsAway
                        ) } else {
                            null
                        }
                )
            }
        }
    }
}

@Composable
private fun <T> AutoScrollToBottom(
    listState: LazyListState,
    list: List<T>,
    itemThreshold: Int = 3,
) = with(listState) {
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
private fun UpdateUnreadCount(
    listState: LazyListState,
    messages: List<Message>,
    onUnreadChanged: (Long) -> Unit,
) {
    val unreadIndex by remember { derivedStateOf { messages.indexOfLast { !it.read } } }
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    if (unreadIndex != -1 && firstVisibleItemIndex != -1 && firstVisibleItemIndex <= unreadIndex) {
        LaunchedEffect(firstVisibleItemIndex, unreadIndex) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .debounce(timeoutMillis = 500L)
                .collectLatest { index ->
                    val lastVisibleItem = messages[index]
                    onUnreadChanged(lastVisibleItem.receivedTime)
                }
        }
    }
}
