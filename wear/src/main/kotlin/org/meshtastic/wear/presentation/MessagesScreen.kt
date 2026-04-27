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
package org.meshtastic.wear.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.wear.presentation.components.ConversationRow
import org.meshtastic.wear.presentation.components.SectionHeader
import org.meshtastic.wear.presentation.components.UnreadBadge

@Composable
fun MessagesRootScreen(
    onChatsClick: () -> Unit,
    onDMsClick: () -> Unit,
    onThreadSelect: (String) -> Unit,
    viewModel: MessagesViewModel = koinViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()
    
    val threads = remember(messages) {
        messages.sortedByDescending { it.timestamp }
            .distinctBy { it.contactKey }
    }

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(COLOR_BG_DEEP),
        ) {
            item { SectionHeader("MESSAGES") }
            
            item {
                ConvTile(
                    icon = "DMS",
                    title = "Private Messages",
                    subtitle = "Direct chat with nodes",
                    unread = 0,
                    onClick = onDMsClick,
                )
            }
            
            item {
                ConvTile(
                    icon = "CHATS",
                    title = "Channels",
                    subtitle = "Public & Private mesh groups",
                    unread = 0,
                    onClick = onChatsClick,
                )
            }

            item { SectionHeader("RECENT ACTIVITY") }
            
            if (threads.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No recent activity", fontSize = 11.sp, color = COLOR_TEXT_SECONDARY)
                    }
                }
            } else {
                items(threads.take(8)) { msg ->
                    DMRow(msg = msg, onClick = { onThreadSelect(msg.contactKey) })
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun ConvTile(icon: String, title: String, subtitle: String, unread: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = COLOR_SURFACE2),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(COLOR_TEAL_DIM),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = COLOR_BG_DEEP)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = COLOR_TEXT_PRIMARY)
                Text(subtitle, fontSize = 10.sp, color = COLOR_TEXT_SECONDARY)
            }
            if (unread > 0) {
                UnreadBadge(unread)
            }
        }
    }
}

@Composable
fun ChatsMenuScreen(onChatSelect: (String) -> Unit, viewModel: ChannelsViewModel = koinViewModel()) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(COLOR_BG_DEEP),
        ) {
            item { SectionHeader("CHANNELS") }
            if (channels.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No channels synced", fontSize = 12.sp, color = COLOR_TEXT_SECONDARY)
                    }
                }
            } else {
                items(channels) { channel ->
                    ConvTile(
                        icon = "#${channel.index}",
                        title = channel.name,
                        subtitle = if (channel.index == 0) "Primary" else "Secondary",
                        unread = 0,
                        onClick = { onChatSelect(channel.contactKey) }
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun DMListScreen(onDMSelect: (String) -> Unit = {}, viewModel: MessagesViewModel = koinViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()
    
    // Filter for DMs (address != null) and group by contactKey
    val threads = remember(messages) {
        messages.filter { it.address != null }
            .sortedByDescending { it.timestamp }
            .distinctBy { it.contactKey }
    }

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(COLOR_BG_DEEP),
        ) {
            item { SectionHeader("PRIVATE MESSAGES") }
            if (threads.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        Text("No messages yet", fontSize = 12.sp, color = COLOR_TEXT_SECONDARY)
                    }
                }
            } else {
                items(threads) { msg -> 
                    DMRow(
                        msg = msg, 
                        onClick = { onDMSelect(msg.contactKey) }
                    ) 
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun DMRow(msg: WearableMessage, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        colors = ButtonDefaults.buttonColors(containerColor = COLOR_SURFACE1),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    modifier =
                    Modifier.size(30.dp)
                        .clip(CircleShape)
                        .background(COLOR_TEAL_DIM),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        msg.senderShortName,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = COLOR_TEAL,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    msg.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = COLOR_TEXT_PRIMARY,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    msg.text,
                    fontSize = 10.sp,
                    color = COLOR_TEXT_SECONDARY,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
