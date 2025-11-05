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

package org.meshtastic.feature.messaging.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.entity.Reaction
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.ui.component.AutoLinkText
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.Rssi
import org.meshtastic.core.ui.component.Snr
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.MessageItemColors
import org.meshtastic.core.strings.R as Res

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun MessageItem(
    modifier: Modifier = Modifier,
    node: Node,
    ourNode: Node,
    message: Message,
    selected: Boolean,
    onReply: () -> Unit = {},
    sendReaction: (String) -> Unit = {},
    onShowReactions: () -> Unit = {},
    emojis: List<Reaction> = emptyList(),
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onClickChip: (Node) -> Unit = {},
    onStatusClick: () -> Unit = {},
    onNavigateToOriginalMessage: (Int) -> Unit = {},
) = Column(
    modifier =
    modifier
        .fillMaxWidth()
        .background(color = if (selected) Color.Gray else MaterialTheme.colorScheme.background),
) {
    val containsBel = message.text.contains('\u0007')
    val containerColor =
        Color(
            if (message.fromLocal) {
                ourNode.colors.second
            } else {
                node.colors.second
            },
        )
            .copy(alpha = 0.2f)
    val cardColors =
        CardDefaults.cardColors()
            .copy(containerColor = containerColor, contentColor = contentColorFor(containerColor))
    val messageModifier =
        Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp)
            .then(
                if (containsBel) {
                    Modifier.border(2.dp, MessageItemColors.Red, shape = MaterialTheme.shapes.medium)
                } else {
                    Modifier
                },
            )
    Box {
        Card(
            modifier =
            Modifier.align(if (message.fromLocal) Alignment.BottomEnd else Alignment.BottomStart)
                .padding(
                    top = 4.dp,
                    start = if (!message.fromLocal) 0.dp else 16.dp,
                    end = if (message.fromLocal) 0.dp else 16.dp,
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .then(messageModifier),
            colors = cardColors,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OriginalMessageSnippet(
                    message = message,
                    ourNode = ourNode,
                    cardColors = cardColors,
                    onNavigateToOriginalMessage = onNavigateToOriginalMessage,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val chipNode = if (message.fromLocal) ourNode else node
                    NodeChip(node = chipNode, onClick = onClickChip)
                    Text(
                        text = with(if (message.fromLocal) ourNode.user else node.user) { "$longName ($id)" },
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f, fill = true),
                    )
                    if (message.viaMqtt) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = stringResource(Res.string.via_mqtt),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    MessageActions(
                        isLocal = message.fromLocal,
                        status = message.status,
                        onSendReaction = sendReaction,
                        onSendReply = onReply,
                        onStatusClick = onStatusClick,
                    )
                }

                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    AutoLinkText(
                        modifier = Modifier.fillMaxWidth(),
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardColors.contentColor,
                    )

                    val topPadding = if (!message.fromLocal) 2.dp else 0.dp
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = topPadding, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!message.fromLocal) {
                            if (message.hopsAway == 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Snr(message.snr)
                                    Rssi(message.rssi)
                                }
                            } else {
                                Text(
                                    text = stringResource(Res.string.hops_away_template, message.hopsAway),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (containsBel) {
                                Text(text = "\uD83D\uDD14", modifier = Modifier.padding(end = 4.dp))
                            }
                            Text(text = message.time, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    ReactionRow(
        modifier = Modifier.fillMaxWidth(),
        reactions = emojis,
        onSendReaction = sendReaction,
        onShowReactions = onShowReactions,
    )
}

@Composable
private fun OriginalMessageSnippet(
    message: Message,
    ourNode: Node,
    cardColors: CardColors = CardDefaults.cardColors(),
    onNavigateToOriginalMessage: (Int) -> Unit,
) {
    val originalMessage = message.originalMessage
    if (originalMessage != null && originalMessage.packetId != 0) {
        val originalMessageNode = if (originalMessage.fromLocal) ourNode else originalMessage.node
        OutlinedCard(
            modifier =
            Modifier.fillMaxWidth().padding(4.dp).clickable {
                onNavigateToOriginalMessage(originalMessage.packetId)
            },
            colors = cardColors,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.FormatQuote,
                    contentDescription = stringResource(Res.string.reply), // Add to strings.xml
                )
                Text(
                    text = originalMessageNode.user.shortName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.weight(1f, fill = true),
                    text = originalMessage.text, // Should not be null if isAReply is true
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, // Keep snippet brief
                    overflow = TextOverflow.Ellipsis,
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
            receivedTime = System.currentTimeMillis(),
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
            receivedTime = System.currentTimeMillis(),
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
            receivedTime = System.currentTimeMillis(),
            node = NodePreviewParameterProvider().minnieMouse,
            read = false,
            routingError = 0,
            packetId = 4545,
            emojis = listOf(),
            replyId = null,
            originalMessage = received,
            viaMqtt = true,
        )
    AppTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(vertical = 16.dp)) {
            MessageItem(
                message = sent,
                node = sent.node,
                selected = false,
                onClick = {},
                onLongClick = {},
                onStatusClick = {},
                ourNode = sent.node,
            )

            MessageItem(
                message = received,
                node = received.node,
                selected = false,
                onClick = {},
                onLongClick = {},
                onStatusClick = {},
                ourNode = sent.node,
            )

            MessageItem(
                message = receivedWithOriginalMessage,
                node = receivedWithOriginalMessage.node,
                selected = false,
                onClick = {},
                onLongClick = {},
                onStatusClick = {},
                ourNode = sent.node,
            )
        }
    }
}
