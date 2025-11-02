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

package com.geeksville.mesh.ui.message.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.room.util.newStringBuilder
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.Reaction
import com.geeksville.mesh.model.Message
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.getStringResFrom
import com.geeksville.mesh.ui.common.components.EmojiPickerDialog
import com.geeksville.mesh.ui.common.components.MDText
import com.geeksville.mesh.ui.common.preview.NodePreviewParameterProvider
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.common.theme.MessageItemColors
import com.geeksville.mesh.ui.node.components.NodeChip
import com.geeksville.mesh.ui.node.components.NodeMenuAction

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
    onAction: (NodeMenuAction) -> Unit = {},
    onStatusClick: () -> Unit = {},
    isConnected: Boolean,
    onNavigateToOriginalMessage: (Int) -> Unit = {},
    showNodeInfo: Boolean,
) = Column(modifier = modifier.fillMaxWidth()) {
    var showMessageActionsDialog by remember { mutableStateOf(false) }
    var showEmojiPickerDialog by remember { mutableStateOf(false) }

    if (showMessageActionsDialog) {
        MessageActionsDialog(
            status = if (message.fromLocal) message.status else null,
            onDismiss = { showMessageActionsDialog = false },
            onShowEmojiDialog = { showEmojiPickerDialog = true },
            onClickReply = { onReply() },
            onClickStatus = onStatusClick,
        )
    }

    if (showEmojiPickerDialog) {
        EmojiPickerDialog(
            onConfirm = { selectedEmoji ->
                showEmojiPickerDialog = false
                sendReaction(selectedEmoji)
            },
            onDismiss = { showEmojiPickerDialog = false },
        )
    }

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

    if (!message.fromLocal && showNodeInfo) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NodeChip(node = node, onAction = onAction, isConnected = isConnected, isThisNode = false)

            Column {
                Text(
                    text = with(node.user) { longName },
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                )

                Text(
                    text = with(node.user) { id },
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }

    Box(
        modifier =
        Modifier.fillMaxWidth()
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onLongClick = { showMessageActionsDialog = true },
                onClick = {},
            )
            .padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
    ) {
        @Suppress("MagicNumber")
        Column(
            modifier =
            Modifier.fillMaxWidth(.75f)
                .align(if (message.fromLocal) Alignment.CenterEnd else Alignment.CenterStart),
            horizontalAlignment = if (message.fromLocal) Alignment.End else Alignment.Start,
        ) {
            Box {
                Card(
                    modifier =
                    Modifier.wrapContentSize()
                        .then(
                            if (containsBel) {
                                Modifier.border(
                                    2.dp,
                                    MessageItemColors.Red,
                                    shape = MaterialTheme.shapes.medium,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .align(if (message.fromLocal) Alignment.CenterEnd else Alignment.CenterStart),
                    shape = RoundedCornerShape(20.dp),
                    colors = cardColors,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OriginalMessageSnippet(
                            message = message,
                            ourNode = ourNode,
                            cardColors = cardColors,
                            onNavigateToOriginalMessage = onNavigateToOriginalMessage,
                        )

                            MDText(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cardColors.contentColor,
                            )

                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp).align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (message.viaMqtt) {
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = stringResource(R.string.via_mqtt),
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }

                ReactionRow(
                    reactions = emojis,
                    onShowReactions = onShowReactions,
                    modifier =
                    Modifier.align(if (message.fromLocal) Alignment.TopStart else Alignment.TopEnd)
                        .offset(y = (-14).dp),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val bottomLabel = buildString {
                    if (containsBel) {
                        append("\uD83D\uDD14")
                        append(" · ")
                    }

                    if(message.fromLocal) {
                        append(stringResource(message.getStatusStringRes().second))
                        append(" · ")
                    }

                    append(message.time)
                }

                Text(text = bottomLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
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
                    Icons.Rounded.FormatQuote,
                    contentDescription = stringResource(R.string.reply), // Add to strings.xml
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
            text = stringResource(R.string.sample_message),
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
            text = "Yo",
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
            emojis =
            listOf(
                Reaction(
                    replyId = 1,
                    user = MeshProtos.User.getDefaultInstance(),
                    emoji = "\uD83D\uDE42",
                    timestamp = 1L,
                ),
                Reaction(
                    replyId = 1,
                    user = MeshProtos.User.getDefaultInstance(),
                    emoji = "\uD83E\uDEE0",
                    timestamp = 1L,
                ),
            ),
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
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MessageItem(
                message = sent,
                node = sent.node,
                selected = false,
                onStatusClick = {},
                isConnected = true,
                ourNode = sent.node,
                showNodeInfo = true,
            )

            MessageItem(
                message = received,
                node = received.node,
                selected = false,
                onStatusClick = {},
                isConnected = true,
                ourNode = sent.node,
                showNodeInfo = true,
            )

            MessageItem(
                message = receivedWithOriginalMessage,
                node = receivedWithOriginalMessage.node,
                selected = false,
                onStatusClick = {},
                isConnected = true,
                ourNode = sent.node,
                showNodeInfo = true,
            )
        }
    }
}
