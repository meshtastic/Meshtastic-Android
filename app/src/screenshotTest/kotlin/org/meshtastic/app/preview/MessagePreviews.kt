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
package org.meshtastic.app.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.messaging.component.MessageItem
import org.meshtastic.feature.messaging.component.MessageStatusDialog
import org.meshtastic.feature.messaging.component.ReplySnippet
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User

/** Reusable preview [Node] for message previews. */
private val senderNode =
    Node(
        num = 1001,
        user = User(id = "!a1b2c3d4", long_name = "Mickey Mouse", short_name = "MM", hw_model = HardwareModel.TBEAM),
    )

/** Reusable preview [Node] representing the local user. */
private val ourNode =
    Node(
        num = 9999,
        user = User(id = "!00000001", long_name = "My Device", short_name = "ME", hw_model = HardwareModel.HELTEC_V3),
    )

/** Creates a preview [Message] with sensible defaults. */
private fun previewMessage(
    uuid: Long = 1L,
    text: String = "Hello from the mesh!",
    fromLocal: Boolean = false,
    status: MessageStatus? = MessageStatus.RECEIVED,
    replyId: Int? = null,
    originalMessage: Message? = null,
) = Message(
    uuid = uuid,
    receivedTime = 1700000000L,
    node = if (fromLocal) ourNode else senderNode,
    text = text,
    fromLocal = fromLocal,
    time = "10:30 AM",
    read = true,
    status = status,
    routingError = 0,
    packetId = uuid.toInt(),
    emojis = emptyList(),
    snr = 10.5f,
    rssi = -55,
    hopsAway = 1,
    replyId = replyId,
    originalMessage = originalMessage,
)

@MultiPreview
@Composable
fun MessageItemPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Received message:", style = MaterialTheme.typography.labelSmall)
                MessageItem(
                    node = senderNode,
                    ourNode = ourNode,
                    message = previewMessage(uuid = 1L, text = "Hey, are you out on the trail today?"),
                    selected = false,
                )
                Text("Sent message (delivered):", style = MaterialTheme.typography.labelSmall)
                MessageItem(
                    node = ourNode,
                    ourNode = ourNode,
                    message =
                    previewMessage(
                        uuid = 2L,
                        text = "Yes! Just hit the summit.",
                        fromLocal = true,
                        status = MessageStatus.DELIVERED,
                    ),
                    selected = false,
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun ReplySnippetPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                ReplySnippet(
                    originalMessage = previewMessage(uuid = 1L, text = "Hey, are you out on the trail today?"),
                    onClearReply = {},
                    ourNode = ourNode,
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun MessageStatusDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            MessageStatusDialog(
                message =
                previewMessage(
                    uuid = 1L,
                    text = "Check in when you reach the waypoint.",
                    fromLocal = true,
                    status = MessageStatus.DELIVERED,
                ),
                nodes = listOf(senderNode),
                ourNode = ourNode,
                resendOption = true,
                onResend = {},
                onDismiss = {},
            )
        }
    }
}
