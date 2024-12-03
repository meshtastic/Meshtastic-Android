/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.message.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.model.Message
import com.geeksville.mesh.ui.components.SimpleAlertDialog
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

@Composable
internal fun MessageList(
    messages: List<Message>,
    selectedIds: MutableState<Set<Long>>,
    onUnreadChanged: (Long) -> Unit,
    contentPadding: PaddingValues,
    onSendReaction: (String, Int) -> Unit,
    onClick: (Message) -> Unit = {}
) {
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
        SimpleAlertDialog(title = title, text = text) { showStatusDialog = null }
    }

    fun toggle(uuid: Long) = if (selectedIds.value.contains(uuid)) {
        selectedIds.value -= uuid
    } else {
        selectedIds.value += uuid
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
        contentPadding = contentPadding
    ) {
        items(messages, key = { it.uuid }) { msg ->
            val fromLocal = msg.user.id == DataPacket.ID_LOCAL
            val selected by remember { derivedStateOf { selectedIds.value.contains(msg.uuid) } }

            ReactionRow(fromLocal, msg.emojis) { onSendReaction(it, msg.packetId) }
            MessageItem(
                shortName = msg.user.shortName.takeIf { !fromLocal },
                messageText = msg.text,
                messageTime = msg.time,
                messageStatus = msg.status,
                selected = selected,
                onClick = { if (inSelectionMode) toggle(msg.uuid) },
                onLongClick = { toggle(msg.uuid) },
                onChipClick = { onClick(msg) },
                onStatusClick = { showStatusDialog = msg }
            )
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
