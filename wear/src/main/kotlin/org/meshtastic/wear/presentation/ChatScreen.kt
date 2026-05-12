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

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import co.touchlab.kermit.Logger
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.ui.icon.Acknowledged
import org.meshtastic.core.ui.icon.CloudUpload
import org.meshtastic.core.ui.icon.MessageEnroute
import org.meshtastic.core.ui.icon.MessageError
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MqttDelivered
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.wear.presentation.components.PulsingDot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(contactKey: String, viewModel: ChatViewModel = koinViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()

    val chatMessages = messages.filter { it.contactKey == contactKey }.sortedByDescending { it.timestamp }
    val chatName = chatMessages.firstOrNull { !it.isMe }?.senderName 
                  ?: chatMessages.firstOrNull()?.senderName 
                  ?: "Chat"

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data: Intent? = result.data
            if (data != null) {
                val results = RemoteInput.getResultsFromIntent(data)
                val text = results?.getCharSequence("extra_voice_reply")
                if (text != null) {
                    viewModel.sendMessage(contactKey, text.toString())
                }
            }
        }

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(COLOR_BG_DEEP),
        ) {
            item { ChatHeader(chatName = chatName) }

            items(chatMessages.reversed()) { msg -> MessageBubble(msg) }

            item { ChatDivider() }

            item {
                val lastMsgText = chatMessages.lastOrNull { !it.isMe }?.text?.lowercase() ?: ""
                val quickReplies = when {
                    lastMsgText.contains("?") -> listOf("Yes", "No", "Maybe")
                    lastMsgText.contains("where") -> listOf("On my way!", "At home", "ETA?")
                    lastMsgText.contains("when") -> listOf("Now", "In 10m", "Later")
                    else -> listOf("On it!", "OK", "Thanks!")
                }
                QuickReplySection(replies = quickReplies, onReply = { viewModel.sendMessage(contactKey, it) })
            }

            item { ReplyActions(chatName = chatName, launcher = launcher, onOpenOnPhone = { viewModel.openOnPhone(contactKey) }) }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ChatHeader(chatName: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(color = COLOR_NEON_GREEN, sizeDp = 6)
        Spacer(Modifier.width(5.dp))
        Text(
            text = chatName,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEAL,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatDivider() {
    Box(
        modifier =
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(1.dp).background(COLOR_SURFACE2),
    )
}

@Composable
private fun QuickReplySection(replies: List<String>, onReply: (String) -> Unit) {
    Column {
        Text(
            text = "QUICK REPLY",
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp, end = 0.dp, bottom = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            replies.forEach { qr ->
                Box(
                    modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(COLOR_SURFACE2)
                        .clickable { onReply(qr) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(qr, fontSize = 10.sp, color = COLOR_TEAL, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ReplyActions(
    chatName: String, 
    launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    onOpenOnPhone: () -> Unit
) {
    Column {
        Button(
            onClick = {
                val remoteInput = RemoteInput.Builder("extra_voice_reply").setLabel("Reply to $chatName").build()
                val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                launcher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = COLOR_TEAL_DIM),
        ) {
            Text(text = "Reply (Mic / Keys)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        FilledTonalButton(
            onClick = onOpenOnPhone, 
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Text("Open on Phone", fontSize = 11.sp)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun MessageBubble(msg: WearableMessage) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start,
    ) {
        if (!msg.isMe) {
            Text(
                msg.senderName,
                fontSize = 9.sp,
                color = COLOR_TEAL,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 1.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start,
        ) {
            if (msg.isMe) {
                MessageStatusIcon(
                    status = msg.status,
                    modifier = Modifier.padding(end = 4.dp, bottom = 1.dp).size(11.dp)
                )
            }
            Box(
                modifier =
                Modifier.clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (msg.isMe) 12.dp else 3.dp,
                        bottomEnd = if (msg.isMe) 3.dp else 12.dp,
                    ),
                )
                    .background(
                        if (msg.isMe) {
                            Brush.linearGradient(listOf(COLOR_TEAL_DIM, COLOR_TEAL_DEEP))
                        } else {
                            Brush.linearGradient(listOf(COLOR_SURFACE2, COLOR_SURFACE1))
                        },
                    )
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Text(msg.text, fontSize = 12.sp, color = COLOR_TEXT_PRIMARY)
            }
        }
        Text(
            text = formatTime(msg.timestamp),
            fontSize = 8.sp,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 1.dp),
        )
    }
}

@Composable
fun MessageStatusIcon(status: MessageStatus, modifier: Modifier = Modifier) {
    val icon = when (status) {
        MessageStatus.RECEIVED -> MeshtasticIcons.Acknowledged
        MessageStatus.QUEUED -> MeshtasticIcons.CloudUpload
        MessageStatus.DELIVERED -> MeshtasticIcons.MqttDelivered
        MessageStatus.ENROUTE -> MeshtasticIcons.MessageEnroute
        MessageStatus.ERROR -> MeshtasticIcons.MessageError
        else -> MeshtasticIcons.Warning
    }
    val tint = if (status == MessageStatus.ERROR) COLOR_ERROR_RED else COLOR_TEAL
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}
