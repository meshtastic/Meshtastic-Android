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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.Reaction
import com.geeksville.mesh.model.Message
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.message.components.MessageItem
import com.geeksville.mesh.ui.message.components.ReactionDialog
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

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
        FilledTonalButton(
            onClick = onDismiss,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text(text = stringResource(id = R.string.close)) }
    },
    confirmButton = {
        if (resendOption) {
            FilledTonalButton(
                onClick = onConfirm,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text(text = stringResource(id = R.string.resend)) }
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
        text?.let {
            Text(
                text = stringResource(id = it),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
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
    onReply: (Message?) -> Unit,
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
            onConfirm = {
                val deleteList: List<Long> = listOf(msg.uuid)
                viewModel.deleteMessages(deleteList)
                showStatusDialog = null
                viewModel.sendMessage(msg.text, contactKey)
            },
            onDismiss = { showStatusDialog = null },
            resendOption = msg.status?.equals(MessageStatus.ERROR) ?: false
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
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle(false)
    val coroutineScope = rememberCoroutineScope()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
    ) {
        items(messages, key = { it.uuid }) { msg ->
            if (ourNode != null) {
                val selected by remember { derivedStateOf { selectedIds.value.contains(msg.uuid) } }
                var node by remember {
                    mutableStateOf(nodes.find { it.num == msg.node.num } ?: msg.node)
                }
                MessageItem(
                    modifier = Modifier.animateItem(),
                    node = node,
                    ourNode = ourNode!!,
                    message = msg,
                    selected = selected,
                    onClick = { if (inSelectionMode) selectedIds.toggle(msg.uuid) },
                    onLongClick = {
                        selectedIds.toggle(msg.uuid)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onAction = onNodeMenuAction,
                    onStatusClick = { showStatusDialog = msg },
                    onReply = { onReply(msg) },
                    emojis = msg.emojis,
                    sendReaction = { onSendReaction(it, msg.packetId) },
                    onShowReactions = { showReactionDialog = msg.emojis },
                    isConnected = isConnected,
                    onNavigateToOriginalMessage = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(
                                index = messages.indexOfFirst { it.packetId == msg.replyId }
                            )
                        }
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
