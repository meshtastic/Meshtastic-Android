/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.messaging.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.sample_message
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Routing

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun MessageItemSignedPreview() {
    val signed =
        Message(
            text = "Net check — anyone copy on the ridge?",
            time = "14:02",
            fromLocal = false,
            status = MessageStatus.RECEIVED,
            snr = 6.5f,
            rssi = 95,
            hopsAway = 0,
            uuid = 10L,
            receivedTime = nowMillis,
            node = NodePreviewParameterProvider().minnieMouse,
            read = false,
            routingError = 0,
            packetId = 5001,
            emojis = listOf(),
            replyId = null,
            viaMqtt = false,
            xeddsaSigned = true,
        )
    val unsigned = signed.copy(text = "Copy, weak but readable.", time = "14:03", uuid = 11L, xeddsaSigned = false)
    AppTheme {
        Column(
            modifier =
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 16.dp),
        ) {
            listOf(signed, unsigned).forEach { msg ->
                MessageItem(
                    message = msg,
                    node = msg.node,
                    selected = false,
                    ourNode = NodePreviewParameterProvider().mickeyMouse,
                    onReply = {},
                    sendReaction = {},
                    onShowReactions = {},
                    onClick = {},
                    onLongClick = {},
                    onDoubleClick = {},
                    onClickChip = {},
                    onNavigateToOriginalMessage = {},
                )
            }
        }
    }
}

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun MessageItemStatusStatesPreview() {
    val ourNode = NodePreviewParameterProvider().mickeyMouse
    val messages =
        listOf(
            StatusPreviewMessage(
                outgoingStatusPreviewMessage(
                    text = "Explicit recipient ACK",
                    time = "10:00",
                    status = MessageStatus.RECEIVED,
                    node = ourNode,
                ),
            ),
            StatusPreviewMessage(
                outgoingStatusPreviewMessage(
                    text = "Channel implicit ACK",
                    time = "10:01",
                    status = MessageStatus.DELIVERED,
                    node = ourNode,
                ),
            ),
            StatusPreviewMessage(
                outgoingStatusPreviewMessage(
                    text = "Direct implicit ACK",
                    time = "10:02",
                    status = MessageStatus.DELIVERED,
                    node = ourNode,
                ),
                isDirectMessage = true,
            ),
            StatusPreviewMessage(
                outgoingStatusPreviewMessage(
                    text = "Queued for send",
                    time = "10:03",
                    status = MessageStatus.QUEUED,
                    node = ourNode,
                ),
            ),
            StatusPreviewMessage(
                outgoingStatusPreviewMessage(
                    text = "No ACK received",
                    time = "10:04",
                    status = MessageStatus.ERROR,
                    routingError = Routing.Error.MAX_RETRANSMIT.value,
                    node = ourNode,
                ),
            ),
            StatusPreviewMessage(
                outgoingStatusPreviewMessage(
                    text = "No channel selected",
                    time = "10:05",
                    status = MessageStatus.ERROR,
                    routingError = Routing.Error.NO_CHANNEL.value,
                    node = ourNode,
                ),
            ),
            StatusPreviewMessage(
                outgoingStatusPreviewMessage(
                    text = "Encrypted send failed",
                    time = "10:06",
                    status = MessageStatus.ERROR,
                    routingError = Routing.Error.PKI_FAILED.value,
                    node = ourNode,
                ),
            ),
            StatusPreviewMessage(
                outgoingStatusPreviewMessage(
                    text = "Recipient key unavailable",
                    time = "10:07",
                    status = MessageStatus.ERROR,
                    routingError = Routing.Error.PKI_SEND_FAIL_PUBLIC_KEY.value,
                    node = ourNode,
                ),
            ),
            StatusPreviewMessage(
                outgoingStatusPreviewMessage(
                    text = "Recipient needs your key",
                    time = "10:08",
                    status = MessageStatus.ERROR,
                    routingError = Routing.Error.PKI_UNKNOWN_PUBKEY.value,
                    node = ourNode,
                ),
            ),
            StatusPreviewMessage(
                outgoingStatusPreviewMessage(
                    text = "Too large to send",
                    time = "10:09",
                    status = MessageStatus.ERROR,
                    routingError = Routing.Error.TOO_LARGE.value,
                    node = ourNode,
                ),
            ),
        )

    AppTheme {
        Column(
            modifier =
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            messages.forEach { preview ->
                MessageItem(
                    message = preview.message,
                    node = preview.message.node,
                    selected = false,
                    ourNode = ourNode,
                    onReply = {},
                    sendReaction = {},
                    onShowReactions = {},
                    onClick = {},
                    onLongClick = {},
                    onDoubleClick = {},
                    onClickChip = {},
                    onNavigateToOriginalMessage = {},
                    onStatusClick = {},
                    isDirectMessage = preview.isDirectMessage,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun MessageItemPreview() {
    val sent =
        Message(
            text = stringResource(Res.string.sample_message),
            time = "10:00",
            fromLocal = true,
            status = MessageStatus.DELIVERED,
            snr = 20.5f,
            rssi = 90,
            hopsAway = 0,
            uuid = 1L,
            receivedTime = nowMillis,
            node = NodePreviewParameterProvider().mickeyMouse,
            read = false,
            routingError = 0,
            packetId = 4545,
            emojis = listOf(),
            replyId = null,
            viaMqtt = false,
        )
    val received =
        Message(
            text = "This is a received message",
            time = "10:10",
            fromLocal = false,
            status = MessageStatus.RECEIVED,
            snr = 2.5f,
            rssi = 90,
            hopsAway = 0,
            uuid = 2L,
            receivedTime = nowMillis,
            node = NodePreviewParameterProvider().minnieMouse,
            read = false,
            routingError = 0,
            packetId = 4545,
            emojis = listOf(),
            replyId = null,
            viaMqtt = false,
        )
    val receivedWithOriginalMessage =
        Message(
            text = "This is a received message w/ original, this is a longer message to test next-lining.",
            time = "10:20",
            fromLocal = false,
            status = MessageStatus.RECEIVED,
            snr = 2.5f,
            rssi = 90,
            hopsAway = 2,
            uuid = 2L,
            receivedTime = nowMillis,
            node = NodePreviewParameterProvider().minnieMouse,
            read = false,
            routingError = 0,
            packetId = 4545,
            emojis = listOf(),
            replyId = null,
            originalMessage = received,
            viaMqtt = true,
        )
    val translatedMessage =
        Message(
            text = "Hola, ¿cómo estás?",
            translatedText = "Hello, how are you?",
            showTranslated = true,
            time = "10:25",
            fromLocal = false,
            status = MessageStatus.RECEIVED,
            snr = 2.0f,
            rssi = 80,
            hopsAway = 1,
            uuid = 4L,
            receivedTime = nowMillis,
            node = NodePreviewParameterProvider().minnieMouse,
            read = false,
            routingError = 0,
            packetId = 4547,
            emojis = listOf(),
            replyId = null,
            viaMqtt = false,
        )
    val filteredMessage =
        Message(
            text = "This message was filtered",
            time = "10:30",
            fromLocal = false,
            status = MessageStatus.RECEIVED,
            snr = 1.5f,
            rssi = 70,
            hopsAway = 1,
            uuid = 3L,
            receivedTime = nowMillis,
            node = NodePreviewParameterProvider().minnieMouse,
            read = false,
            routingError = 0,
            packetId = 4546,
            emojis = listOf(),
            replyId = null,
            viaMqtt = false,
            filtered = true,
        )
    AppTheme {
        Column(
            modifier =
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 16.dp),
        ) {
            MessageItem(
                message = sent,
                node = sent.node,
                selected = false,
                ourNode = sent.node,
                onReply = {},
                sendReaction = {},
                onShowReactions = {},
                onClick = {},
                onLongClick = {},
                onDoubleClick = {},
                onClickChip = {},
                onNavigateToOriginalMessage = {},
            )

            MessageItem(
                message = received,
                node = received.node,
                selected = false,
                ourNode = sent.node,
                onReply = {},
                sendReaction = {},
                onShowReactions = {},
                onClick = {},
                onLongClick = {},
                onDoubleClick = {},
                onClickChip = {},
                onNavigateToOriginalMessage = {},
            )

            MessageItem(
                message = receivedWithOriginalMessage,
                node = receivedWithOriginalMessage.node,
                selected = false,
                ourNode = sent.node,
                onReply = {},
                sendReaction = {},
                onShowReactions = {},
                onClick = {},
                onLongClick = {},
                onDoubleClick = {},
                onClickChip = {},
                onNavigateToOriginalMessage = {},
            )

            MessageItem(
                message = translatedMessage,
                node = translatedMessage.node,
                selected = false,
                ourNode = sent.node,
                translationAvailable = true,
                onReply = {},
                sendReaction = {},
                onShowReactions = {},
                onClick = {},
                onLongClick = {},
                onDoubleClick = {},
                onClickChip = {},
                onNavigateToOriginalMessage = {},
            )

            MessageItem(
                message = filteredMessage,
                node = filteredMessage.node,
                selected = false,
                ourNode = sent.node,
                onReply = {},
                sendReaction = {},
                onShowReactions = {},
                onClick = {},
                onLongClick = {},
                onDoubleClick = {},
                onClickChip = {},
                onNavigateToOriginalMessage = {},
            )
        }
    }
}

private data class StatusPreviewMessage(val message: Message, val isDirectMessage: Boolean = false)

private fun outgoingStatusPreviewMessage(
    text: String,
    time: String,
    status: MessageStatus,
    node: org.meshtastic.core.model.Node,
    routingError: Int = 0,
) = Message(
    text = text,
    time = time,
    fromLocal = true,
    status = status,
    snr = 0f,
    rssi = 0,
    hopsAway = 0,
    uuid = time.filter(Char::isDigit).toLong(),
    receivedTime = nowMillis,
    node = node,
    read = false,
    routingError = routingError,
    packetId = time.filter(Char::isDigit).toInt(),
    emojis = listOf(),
    replyId = null,
    viaMqtt = false,
)
