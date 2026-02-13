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

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.Reaction
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.filter_message_label
import org.meshtastic.core.strings.message_delivery_status
import org.meshtastic.core.strings.reply
import org.meshtastic.core.strings.sample_message
import org.meshtastic.core.ui.component.AutoLinkText
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.Rssi
import org.meshtastic.core.ui.component.Snr
import org.meshtastic.core.ui.component.TransportIcon
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.emoji.EmojiPicker
import org.meshtastic.core.ui.icon.Acknowledged
import org.meshtastic.core.ui.icon.CloudDone
import org.meshtastic.core.ui.icon.CloudOffTwoTone
import org.meshtastic.core.ui.icon.CloudSync
import org.meshtastic.core.ui.icon.CloudTwoTone
import org.meshtastic.core.ui.icon.Hops
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Warning
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
        .fillMaxWidth()
        .padding(
            top =
            if (showUserName) {
                6.dp
            } else {
                1.dp
            },
        ),
) {
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isLocal = node.num == ourNode.num
    if (activeSheet != null) {
        ModalBottomSheet(onDismissRequest = { activeSheet = null }, sheetState = sheetState) {
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
                        onMoreReactions = { activeSheet = ActiveSheet.Emoji },
                        onCopy = {
                            activeSheet = null
                            coroutineScope.launch {
                                clipboardManager.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("message", message.text)),
                                )
                            }
                        },
                        onSelect = {
                            activeSheet = null
                            onSelect()
                        },
                        onDelete = {
                            activeSheet = null
                            onDelete()
                        },
                        statusString = message.getStatusStringRes(),
                        status =
                        if (isLocal) {
                            message.status
                        } else {
                            null
                        },
                        onStatus = onStatusClick,
                    )
                }

                ActiveSheet.Emoji -> {
                    // Limit height of emoji picker so it doesn't look weird full screen
                    EmojiPicker(
                        onDismiss = { activeSheet = null },
                        onConfirm = { emoji ->
                            activeSheet = null
                            sendReaction(emoji)
                        },
                    )
                }

                null -> {}
            }
        }
    }

    val containsBel = message.text.contains('\u0007')

    val alpha =
        if (message.filtered) {
            FILTERED_ALPHA
        } else if (inSelectionMode) {
            if (selected) SELECTED_ALPHA else UNSELECTED_ALPHA
        } else {
            NORMAL_ALPHA
        }
    val containerColor =
        if (message.fromLocal) {
            Color(ourNode.colors.second).copy(alpha = alpha)
        } else {
            Color(node.colors.second).copy(alpha = alpha)
        }
    val cardColors =
        CardDefaults.cardColors()
            .copy(containerColor = containerColor, contentColor = contentColorFor(containerColor))
    val messageShape =
        getMessageBubbleShape(
            cornerRadius = 8.dp,
            isSender = message.fromLocal,
            hasSamePrev = hasSamePrev,
            hasSameNext = hasSameNext,
        )
    val messageModifier =
        Modifier.padding(horizontal = 8.dp)
            .then(
                if (containsBel) {
                    Modifier.border(2.dp, color = MessageItemColors.Red, shape = messageShape)
                } else {
                    Modifier
                },
            )
    if (showUserName && !message.fromLocal) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NodeChip(node = node, onClick = onClickChip, modifier = Modifier.height(28.dp))
            Text(
                text = node.user.long_name,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
            TransportIcon(
                transport = message.transportMechanism,
                viaMqtt = message.viaMqtt,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    Surface(
        modifier =
        Modifier.align(if (message.fromLocal) Alignment.End else Alignment.Start)
            .padding(
                start = if (!message.fromLocal) 0.dp else 24.dp,
                end = if (message.fromLocal) 0.dp else 24.dp,
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
                val senderName = if (message.fromLocal) ourNode.user.long_name else node.user.long_name
                contentDescription = "Message from $senderName: ${message.text}"
            },
        color = containerColor,
        contentColor = contentColorFor(containerColor),
        shape = messageShape,
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            OriginalMessageSnippet(
                modifier = Modifier.fillMaxWidth(),
                message = message,
                ourNode = ourNode,
                hasSamePrev = hasSamePrev,
                onNavigateToOriginalMessage = onNavigateToOriginalMessage,
            )

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                AutoLinkText(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cardColors.contentColor,
                )

                Row(modifier = Modifier, verticalAlignment = Alignment.CenterVertically) {
                    if (!message.fromLocal) {
                        if (message.hopsAway == 0 && !message.viaMqtt) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Snr(message.snr)
                                Rssi(message.rssi)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = MeshtasticIcons.Hops,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = cardColors.contentColor.copy(alpha = 0.7f),
                                )
                                Text(
                                    text =
                                    if (message.hopsAway >= 0) {
                                        message.hopsAway.toString()
                                    } else {
                                        "?"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    if (containsBel) {
                        Text(text = "\uD83D\uDD14", modifier = Modifier.padding(end = 4.dp))
                    }
                    if (message.filtered) {
                        Text(
                            text = stringResource(Res.string.filter_message_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                        )
                    }
                    if (message.fromLocal) {
                        MessageStatusIcon(
                            status = message.status ?: MessageStatus.UNKNOWN,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        modifier = Modifier.padding(start = 16.dp),
                        text = message.time,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    ReactionRow(
        modifier =
        Modifier.align(if (message.fromLocal) Alignment.End else Alignment.Start)
            .padding(
                start = if (!message.fromLocal) 0.dp else 24.dp,
                end = if (message.fromLocal) 0.dp else 24.dp,
            ),
        reactions = if (message.fromLocal) emojis.reversed() else emojis,
        myId = ourNode.user.id,
        onSendReaction = sendReaction,
        onShowReactions = onShowReactions,
    )
}

private const val SELECTED_ALPHA = 0.6f
private const val UNSELECTED_ALPHA = 0.2f
private const val NORMAL_ALPHA = 0.4f
private const val FILTERED_ALPHA = 0.5f

private enum class ActiveSheet {
    Actions,
    Emoji,
}

@Composable
fun MessageStatusIcon(status: MessageStatus, modifier: Modifier = Modifier) {
    val icon =
        when (status) {
            MessageStatus.RECEIVED -> MeshtasticIcons.Acknowledged
            MessageStatus.QUEUED -> MeshtasticIcons.CloudSync
            MessageStatus.DELIVERED -> MeshtasticIcons.CloudDone
            MessageStatus.SFPP_ROUTING -> MeshtasticIcons.CloudSync
            MessageStatus.SFPP_CONFIRMED -> MeshtasticIcons.CloudDone
            MessageStatus.ENROUTE -> MeshtasticIcons.CloudTwoTone
            MessageStatus.ERROR -> MeshtasticIcons.CloudOffTwoTone
            else -> MeshtasticIcons.Warning
        }
    Icon(
        modifier = modifier,
        imageVector = icon,
        contentDescription = stringResource(Res.string.message_delivery_status),
    )
}

@Composable
private fun OriginalMessageSnippet(
    message: Message,
    ourNode: Node,
    hasSamePrev: Boolean,
    onNavigateToOriginalMessage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val originalMessage = message.originalMessage
    if (originalMessage != null && originalMessage.packetId != 0) {
        val originalMessageNode = if (originalMessage.fromLocal) ourNode else originalMessage.node
        val cardColors =
            CardDefaults.cardColors()
                .copy(
                    containerColor = Color(originalMessageNode.colors.second).copy(alpha = 0.8f),
                    contentColor = Color(originalMessageNode.colors.first),
                )
        Surface(
            modifier = modifier.fillMaxWidth().clickable { onNavigateToOriginalMessage(originalMessage.packetId) },
            contentColor = cardColors.contentColor,
            color = cardColors.containerColor,
            shape =
            getMessageBubbleShape(
                cornerRadius = 16.dp,
                isSender = originalMessage.fromLocal,
                hasSamePrev = hasSamePrev,
                hasSameNext = true, // always square off original message bottom
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Rounded.FormatQuote,
                    contentDescription = stringResource(Res.string.reply),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = originalMessageNode.user.short_name,
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
