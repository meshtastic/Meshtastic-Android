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
