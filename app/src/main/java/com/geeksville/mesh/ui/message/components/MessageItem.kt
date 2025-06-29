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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.Portnums
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.Reaction
import com.geeksville.mesh.model.Message
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.ui.common.components.AutoLinkText
import com.geeksville.mesh.ui.common.components.Rssi
import com.geeksville.mesh.ui.common.components.Snr
import com.geeksville.mesh.ui.common.preview.NodePreviewParameterProvider
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.node.components.NodeChip
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import kotlin.uuid.ExperimentalUuidApi

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
    onAction: (NodeMenuAction) -> Unit = {},
    onStatusClick: () -> Unit = {},
    isConnected: Boolean,
    onNavigateToOriginalMessage: (Int) -> Unit = {},
    onPlayStop: () -> Unit = {},
    playingMessage: Message? = null,
) = Column(
    modifier = modifier
        .fillMaxWidth()
        .background(color = if (selected) Color.Gray else MaterialTheme.colorScheme.background),
) {
    val fromLocal = node.user.id == DataPacket.ID_LOCAL
    val containerColor = Color(
        if (fromLocal) {
            ourNode.colors.second
        } else {
            node.colors.second
        }
    ).copy(alpha = 0.2f)
    val cardColors = CardDefaults.cardColors().copy(
        containerColor = containerColor,
        contentColor = contentColorFor(containerColor)
    )
    val messageModifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp)
    Box {
        Card(
            modifier = Modifier
                .align(if (fromLocal) Alignment.BottomEnd else Alignment.BottomStart)
                .padding(
                    top = 4.dp,
                    start = if (!fromLocal) 0.dp else 16.dp,
                    end = if (fromLocal) 0.dp else 16.dp,
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .then(messageModifier),
            colors = cardColors,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                if (message.portNum != Portnums.PortNum.AUDIO_APP_VALUE) {
                    OriginalMessageSnippet(
                        message = message,
                        ourNode = ourNode,
                        cardColors = cardColors,
                        onNavigateToOriginalMessage = onNavigateToOriginalMessage
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NodeChip(
                        node = if (fromLocal) ourNode else node,
                        onAction = onAction,
                        isConnected = isConnected,
                        isThisNode = fromLocal,
                    )
                    Text(
                        text = with(if (fromLocal) ourNode.user else node.user) { "$longName ($id)" },
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f, fill = true)
                    )
                    MessageActions(
                        isLocal = fromLocal,
                        status = message.status,
                        onSendReaction = sendReaction,
                        onSendReply = onReply,
                        onStatusClick = onStatusClick,
                    )
                }
                if (message.portNum == Portnums.PortNum.AUDIO_APP_VALUE) {
                    Row {
                        val colors = if (playingMessage == message) ButtonDefaults.buttonColors().copy(containerColor=Color.Red) else ButtonDefaults.buttonColors()
                        Button(
                            enabled = playingMessage == null || playingMessage == message,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            onClick = onPlayStop,
                            colors = colors,
                        ) {
                            Icon(
                                imageVector = if (playingMessage == message) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = stringResource(id = if (playingMessage == message) R.string.stop else R.string.play),
                            )
                        }
                        Text(
                            text = "${stringResource(id = R.string.audio_message)} (${
                                "%.2f".format(
                                    message.getTotalAudioTime()
                                )
                            } ${
                                stringResource(
                                    id = R.string.seconds_abbreviated
                                )
                            })",
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .align(Alignment.CenterVertically),
                        )
                    }
                } else {
                    AutoLinkText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardColors.contentColor
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!fromLocal) {
                        if (message.hopsAway == 0) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Snr(
                                    message.snr,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                                )
                                Rssi(
                                    message.rssi,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.hops_away_template,
                                    message.hopsAway
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = message.time,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
    ReactionRow(
        modifier = Modifier
            .fillMaxWidth(),
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
    onNavigateToOriginalMessage: (Int) -> Unit
) {
    message.originalMessage?.let { originalMessage ->
        val originalMessageIsFromLocal =
            originalMessage.node.user.id == DataPacket.ID_LOCAL
        val originalMessageNode =
            if (originalMessageIsFromLocal) ourNode else originalMessage.node
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToOriginalMessage(originalMessage.packetId) },
            colors = cardColors,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.FormatQuote,
                    contentDescription = stringResource(R.string.reply), // Add to strings.xml
                )
                Text(
                    text = "${originalMessageNode.user.shortName}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

@OptIn(ExperimentalUuidApi::class)
@PreviewLightDark
@Composable
private fun MessageItemPreview() {
    val message = Message(
        text = stringResource(R.string.sample_message),
        time = "10:00",
        status = MessageStatus.DELIVERED,
        portNum = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
        raw = null,
        snr = 20.5f,
        rssi = 90,
        hopsAway = 0,
        uuid = 1L,
        receivedTime = System.currentTimeMillis(),
        node = NodePreviewParameterProvider().values.first(),
        read = false,
        routingError = 0,
        packetId = 4545,
        emojis = listOf(),
        replyId = null,
    )
    AppTheme {
        Column {
            MessageItem(
                message = message,
                node = message.node,
                selected = false,
                onClick = {},
                onLongClick = {},
                onStatusClick = {},
                isConnected = true,
                ourNode = message.node,
            )

            MessageItem(
                message = message.let { message ->
                    val originalMessage = message.copy(
                        replyId = message.packetId,
                        node = NodePreviewParameterProvider().values.last(),
                        text = "This is a reply to the original message."
                    )
                    message.copy(originalMessage = originalMessage)
                },
                node = message.node,
                selected = false,
                onClick = {},
                onLongClick = {},
                onStatusClick = {},
                isConnected = true,
                ourNode = message.node,
                onNavigateToOriginalMessage = {}
            )
        }
    }
}
