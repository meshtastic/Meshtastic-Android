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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.twotone.AddLink
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.CloudDone
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.CloudUpload
import androidx.compose.material.icons.twotone.HowToReg
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.Reaction
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.hops_away_template
import org.meshtastic.core.strings.message_delivery_status
import org.meshtastic.core.strings.reply
import org.meshtastic.core.strings.sample_message
import org.meshtastic.core.strings.via_mqtt
import org.meshtastic.core.ui.component.AutoLinkText
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.Rssi
import org.meshtastic.core.ui.component.Snr
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.emoji.EmojiPicker
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.MessageItemColors

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun MessageItem(
    modifier: Modifier = Modifier,
    node: Node,
    ourNode: Node,
    message: Message,
    selected: Boolean,
    inSelectionMode: Boolean = false,
    onReply: () -> Unit = {},
    sendReaction: (String) -> Unit = {},
    onShowReactions: () -> Unit = {},
    showUserName: Boolean = true,
    emojis: List<Reaction> = emptyList(),
    quickEmojis: List<String> = listOf("👍", "👎", "😂", "🔥", "❤️", "😮"),
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    onSelect: () -> Unit = {},
    onDelete: () -> Unit = {},
    onClickChip: (Node) -> Unit = {},
    onNavigateToOriginalMessage: (Int) -> Unit = {},
    onStatusClick: () -> Unit = {},
    hasSamePrev: Boolean = false,
    hasSameNext: Boolean = false,
) = Column(
    modifier =
        modifier
            .padding(top = if (showUserName) 32.dp else 4.dp),
) {
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
        ) {
            when (activeSheet) {
                ActiveSheet.Actions -> {
                    MessageActionsContent(
                        quickEmojis = quickEmojis,
                        onReply = {
                            activeSheet = null
                            onReply()
                        },
                        onReact = { emoji ->
                            activeSheet = null
                            sendReaction(emoji)
                        },
                        onMoreReactions = {
                            activeSheet = ActiveSheet.Emoji
                        },
                        onCopy = {
                            activeSheet = null
                            clipboardManager.setText(AnnotatedString(message.text))
                        },
                        onSelect = {
                            activeSheet = null
                            onSelect()
                        },
                        onDelete = {
                            activeSheet = null
                            onDelete()
                        }
                    )
                }
                ActiveSheet.Emoji -> {
                    // Limit height of emoji picker so it doesn't look weird full screen
                    Box(modifier = Modifier.heightIn(max = 400.dp)) {
                        EmojiPicker(
                            onDismiss = { activeSheet = null },
                            onConfirm = { emoji ->
                                activeSheet = null
                                sendReaction(emoji)
                            },
                        )
                    }
                }
                null -> {}
            }
        }
    }

    val containsBel = message.text.contains('\u0007')
    val containerColor =if (message.fromLocal) {
            Color(ourNode.colors.second).copy(alpha = if (selected) 0.2f else 0.4f)
        } else {
            Color(node.colors.second).copy(alpha = if (selected) 0.2f else 0.4f)
        }
    val cardColors =
        CardDefaults.cardColors()
            .copy(containerColor = containerColor, contentColor = contentColorFor(containerColor))
    val messageModifier =
        Modifier
            .padding(horizontal = 8.dp)
            .then(
                if (containsBel) {
                    Modifier.border(2.dp, MessageItemColors.Red)
                } else {
                    Modifier
                },
            )
    Box(modifier = Modifier.wrapContentSize()) {
        Surface(
            modifier =
                Modifier
                    .align(if (message.fromLocal) Alignment.TopEnd else Alignment.TopStart)
                    .padding(
                        start = if (!message.fromLocal) 0.dp else 16.dp,
                        end = if (message.fromLocal) 0.dp else 16.dp,
                    )
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            onLongClick()
                            if (!inSelectionMode) {
                                activeSheet = ActiveSheet.Actions
                            }
                        },
                        onDoubleClick = onDoubleClick,
                    )
                    .then(messageModifier)
                    .semantics(mergeDescendants = true) {
                        val senderName =
                            if (message.fromLocal) ourNode.user.longName else node.user.longName
                        contentDescription = "Message from $senderName: ${message.text}"
                    },
            color = containerColor,
            contentColor = contentColorFor(containerColor),
            shape =
                getMessageBubbleShape(
                    cornerRadius = 16.dp,
                    isSender = message.fromLocal,
                    hasSamePrev = hasSamePrev,
                    hasSameNext = hasSameNext,
                ),
        ) {
            val hasPrevPadding = if (hasSamePrev) {
                12.dp
            } else {
                0.dp
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = hasPrevPadding)
            ) {
                OriginalMessageSnippet(
                    message = message,
                    ourNode = ourNode,
                    onNavigateToOriginalMessage = onNavigateToOriginalMessage,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showUserName) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val chipNode = if (message.fromLocal) ourNode else node
                            NodeChip(node = chipNode, onClick = onClickChip)
                            Text(
                                text = (if (message.fromLocal) ourNode.user else node.user).longName,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            if (message.viaMqtt) {
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = stringResource(Res.string.via_mqtt),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = message.time, style = MaterialTheme.typography.labelSmall)
                    if (message.fromLocal) {
                        Spacer(modifier = Modifier.size(4.dp))
                        MessageStatusIcon(
                            status = message.status ?: MessageStatus.UNKNOWN,
                            onClick = onStatusClick,
                        )
                    }
                }

                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                    AutoLinkText(
                        modifier = Modifier.fillMaxWidth(),
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardColors.contentColor,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!message.fromLocal) {
                            if (message.hopsAway == 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Snr(message.snr)
                                    Rssi(message.rssi)
                                }
                            } else {
                                Text(
                                    text = stringResource(
                                        Res.string.hops_away_template,
                                        message.hopsAway
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (containsBel) {
                            Text(text = "\uD83D\uDD14", modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(if (message.fromLocal) Alignment.BottomEnd else Alignment.BottomStart)
                .padding(horizontal = 24.dp)
                .offset(y = 24.dp),
        ) {
            ReactionRow(
                reactions = emojis,
                myId = ourNode.user.id,
                onSendReaction = sendReaction,
                onShowReactions = onShowReactions,
            )
        }
    }
}

private enum class ActiveSheet {
    Actions,
    Emoji
}

@Composable
private fun MessageStatusIcon(status: MessageStatus, onClick: () -> Unit) {
    val icon = when (status) {
        MessageStatus.RECEIVED -> Icons.TwoTone.HowToReg
        MessageStatus.QUEUED -> Icons.TwoTone.CloudUpload
        MessageStatus.DELIVERED -> Icons.TwoTone.CloudDone
        MessageStatus.SFPP_ROUTING -> Icons.TwoTone.AddLink
        MessageStatus.SFPP_CONFIRMED -> Icons.TwoTone.Link
        MessageStatus.ENROUTE -> Icons.TwoTone.Cloud
        MessageStatus.ERROR -> Icons.TwoTone.CloudOff
        else -> Icons.TwoTone.Warning
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(Res.string.message_delivery_status),
        modifier = Modifier
            .size(24.dp)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun OriginalMessageSnippet(
    message: Message,
    ourNode: Node,
    onNavigateToOriginalMessage: (Int) -> Unit,
) {
    val originalMessage = message.originalMessage
    if (originalMessage != null && originalMessage.packetId != 0) {
        val originalMessageNode = if (originalMessage.fromLocal) ourNode else originalMessage.node
        val cardColors = CardDefaults.cardColors().copy(
            containerColor = Color(originalMessageNode.colors.second).copy(alpha = 0.8f),
            contentColor = Color(originalMessageNode.colors.first),
        )
        OutlinedCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onNavigateToOriginalMessage(originalMessage.packetId)
                    },
            colors = cardColors,
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.FormatQuote,
                    contentDescription = stringResource(Res.string.reply),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = originalMessageNode.user.shortName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = originalMessage.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp),
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
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 16.dp)
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
        }
    }
}
