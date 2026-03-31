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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Reaction
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.a11y_message_from
import org.meshtastic.core.resources.filter_message_label
import org.meshtastic.core.resources.reply
import org.meshtastic.core.ui.component.AutoLinkText
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.Rssi
import org.meshtastic.core.ui.component.Snr
import org.meshtastic.core.ui.component.TransportIcon
import org.meshtastic.core.ui.emoji.EmojiPickerDialog
import org.meshtastic.core.ui.icon.Hops
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.MessageItemColors
import org.meshtastic.core.ui.util.createClipEntry

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun MessageItem(
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
                                clipboardManager.setClipEntry(createClipEntry(message.text, "message"))
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
                    EmojiPickerDialog(
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
    val senderName = if (message.fromLocal) ourNode.user.long_name else node.user.long_name
    val messageA11yText = stringResource(Res.string.a11y_message_from, senderName, message.text)
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
            .semantics(mergeDescendants = true) { contentDescription = messageA11yText },
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
                        TransportIcon(
                            transport = message.transportMechanism,
                            viaMqtt = message.viaMqtt,
                            modifier = Modifier.size(16.dp).padding(start = 4.dp),
                        )
                    }
                    if (containsBel) {
                        Text(text = "\uD83D\uDD14", modifier = Modifier.padding(start = 4.dp))
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
