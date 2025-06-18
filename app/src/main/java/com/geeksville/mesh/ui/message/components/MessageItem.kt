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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.CloudDone
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.CloudUpload
import androidx.compose.material.icons.twotone.HowToReg
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Card
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
    originalMessage: Message? = null,
    onNavigateToOriginalMessage: (Int) -> Unit = {},
) = Column(
    modifier = modifier
        .fillMaxWidth()
        .background(color = if (selected) Color.Gray else MaterialTheme.colorScheme.background),
) {
    val fromLocal = node.user.id == DataPacket.ID_LOCAL
    val messageColor = if (fromLocal) {
        Color(ourNode.colors.second).copy(alpha = 0.25f)
    } else {
        Color(node.colors.second).copy(alpha = 0.25f)
    }
    val messageModifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp)

    Card(
        modifier = Modifier
            .padding(
                start = if (fromLocal) 0.dp else 8.dp,
                end = if (!fromLocal) 0.dp else 8.dp,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .then(messageModifier),
        colors = CardDefaults.cardColors(
            containerColor = messageColor,
            contentColor = contentColorFor(messageColor),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            if (originalMessage != null) {
                val originalMessageIsFromLocal = originalMessage.node.user.id == DataPacket.ID_LOCAL
                val originalMessageNode =
                    if (originalMessageIsFromLocal) ourNode else originalMessage.node
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable { onNavigateToOriginalMessage(originalMessage.packetId) },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(originalMessageNode.colors.second).copy(alpha = 0.8f),
                        contentColor = Color(originalMessageNode.colors.first),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FormatQuote,
                            contentDescription = stringResource(R.string.reply), // Add to strings.xml
                            modifier = Modifier.size(14.dp), // Smaller icon
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "${originalMessageNode.user.shortName} ${originalMessageNode.user.longName
                                    ?: stringResource(R.string.unknown_username)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = originalMessage.text, // Should not be null if isAReply is true
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, // Keep snippet brief
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!fromLocal) {
                    NodeChip(
                        node = node,
                        onAction = onAction,
                        isConnected = isConnected,
                        isThisNode = false,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = with(node.user) { "$longName ($id)" },
                        modifier = Modifier.padding(bottom = 4.dp),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            AutoLinkText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
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
                            text = "${message.hopsAway}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Text(
                    text = message.time,
                    style = MaterialTheme.typography.labelSmall,
                )
                AnimatedVisibility(visible = fromLocal) {
                    Icon(
                        imageVector = when (message.status) {
                            MessageStatus.RECEIVED -> Icons.TwoTone.HowToReg
                            MessageStatus.QUEUED -> Icons.TwoTone.CloudUpload
                            MessageStatus.DELIVERED -> Icons.TwoTone.CloudDone
                            MessageStatus.ENROUTE -> Icons.TwoTone.Cloud
                            MessageStatus.ERROR -> Icons.TwoTone.CloudOff
                            else -> Icons.TwoTone.Warning
                        },
                        contentDescription = stringResource(R.string.message_delivery_status),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onStatusClick() },
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
        onSendReply = onReply
    )
}

@OptIn(ExperimentalUuidApi::class)
@PreviewLightDark
@Composable
private fun MessageItemPreview() {
    val message = Message(
        text = stringResource(R.string.sample_message),
        time = "10:00",
        status = MessageStatus.DELIVERED,
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
    }
}
