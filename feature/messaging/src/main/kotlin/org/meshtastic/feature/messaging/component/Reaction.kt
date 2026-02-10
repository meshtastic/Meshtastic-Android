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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.Reaction
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.getStringResFrom
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.util.getShortDateTime
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.delivery_confirmed
import org.meshtastic.core.strings.error
import org.meshtastic.core.strings.message_delivery_status
import org.meshtastic.core.strings.message_status_enroute
import org.meshtastic.core.strings.message_status_queued
import org.meshtastic.core.strings.react
import org.meshtastic.core.strings.you
import org.meshtastic.core.ui.component.BottomSheetDialog
import org.meshtastic.core.ui.component.Rssi
import org.meshtastic.core.ui.component.Snr
import org.meshtastic.core.ui.emoji.EmojiPickerDialog
import org.meshtastic.core.ui.icon.Hops
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.messaging.DeliveryInfo
import org.meshtastic.proto.User

@Composable
private fun ReactionItem(
    modifier: Modifier = Modifier,
    emoji: String,
    emojiCount: Int = 1,
    status: MessageStatus = MessageStatus.UNKNOWN,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val isSending = status == MessageStatus.QUEUED || status == MessageStatus.ENROUTE
    val isError = status == MessageStatus.ERROR

    Surface(
        modifier =
        modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (isSending) Modifier.graphicsLayer(alpha = 0.5f) else Modifier),
        color =
        when {
            isError -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        shape = if (emojiCount > 1) MaterialTheme.shapes.small else CircleShape,
        border =
        BorderStroke(
            width = 1.dp,
            color =
            if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = emoji, fontSize = 14.sp)
            if (emojiCount > 1) {
                Text(
                    text = emojiCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReactionRow(
    modifier: Modifier = Modifier,
    reactions: List<Reaction> = emptyList(),
    myId: String? = null,
    onSendReaction: (String) -> Unit = {},
    onShowReactions: () -> Unit = {},
) {
    val emojiGroups = reactions.groupBy { it.emoji }

    AnimatedVisibility(emojiGroups.isNotEmpty(), modifier = modifier) {
        LazyRow(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(emojiGroups.entries.toList()) { (emoji, reactions) ->
                val localReaction = reactions.find { it.user.id == DataPacket.ID_LOCAL || it.user.id == myId }
                ReactionItem(
                    emoji = emoji,
                    emojiCount = reactions.size,
                    status = localReaction?.status ?: MessageStatus.RECEIVED,
                    onClick = { onSendReaction(emoji) },
                    onLongClick = onShowReactions,
                )
            }
            item { AddReactionButton(onSendReaction = onSendReaction) }
        }
    }
}

@Composable
private fun AddReactionButton(modifier: Modifier = Modifier, onSendReaction: (String) -> Unit = {}) {
    var showEmojiPickerDialog by remember { mutableStateOf(false) }
    if (showEmojiPickerDialog) {
        EmojiPickerDialog(
            onConfirm = { selectedEmoji ->
                showEmojiPickerDialog = false
                onSendReaction(selectedEmoji)
            },
            onDismiss = { showEmojiPickerDialog = false },
        )
    }
    Surface(
        onClick = { showEmojiPickerDialog = true },
        modifier = modifier.size(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    ) {
        Icon(
            imageVector = Icons.Rounded.AddReaction,
            contentDescription = stringResource(Res.string.react),
            modifier = Modifier.padding(6.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun ReactionDialog(
    reactions: List<Reaction>,
    onDismiss: () -> Unit = {},
    myId: String? = null,
    onResend: (Reaction) -> Unit = {},
    nodes: List<Node> = emptyList(),
    ourNode: Node? = null,
) = BottomSheetDialog(onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(fraction = .3f)) {
    val groupedEmojis = reactions.groupBy { it.emoji }
    var selectedEmoji by remember { mutableStateOf<String?>(null) }
    val filteredReactions = selectedEmoji?.let { groupedEmojis[it] ?: emptyList() } ?: reactions

    var showStatusDialog by remember { mutableStateOf<Reaction?>(null) }
    showStatusDialog?.let { reaction ->
        val title = if (reaction.routingError > 0) Res.string.error else Res.string.message_delivery_status
        val text =
            when (reaction.status) {
                MessageStatus.RECEIVED -> Res.string.delivery_confirmed
                MessageStatus.QUEUED -> Res.string.message_status_queued
                MessageStatus.ENROUTE -> Res.string.message_status_enroute
                else -> getStringResFrom(reaction.routingError)
            }

        val relayNodeName =
            reaction.relayNode?.let { relayNodeId ->
                Packet.getRelayNode(relayNodeId, nodes, ourNode?.num)?.user?.long_name
            }

        DeliveryInfo(
            title = title,
            text = text,
            resendOption = reaction.status == MessageStatus.ERROR,
            onConfirm = {
                onResend(reaction)
                showStatusDialog = null
            },
            onDismiss = { showStatusDialog = null },
            relayNodeName = relayNodeName,
            relays = reaction.relays,
        )
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(groupedEmojis.entries.toList()) { (emoji, reactions) ->
            val localReaction = reactions.find { it.user.id == DataPacket.ID_LOCAL || it.user.id == myId }
            val isSending =
                localReaction?.status == MessageStatus.QUEUED || localReaction?.status == MessageStatus.ENROUTE
            Text(
                text = "$emoji${reactions.size}",
                modifier =
                Modifier.clip(CircleShape)
                    .background(if (selectedEmoji == emoji) Color.Gray else Color.Transparent)
                    .then(if (isSending) Modifier.graphicsLayer(alpha = 0.5f) else Modifier)
                    .padding(8.dp)
                    .clickable { selectedEmoji = if (selectedEmoji == emoji) null else emoji },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 8.dp))

    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(filteredReactions) { reaction ->
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isLocal = reaction.user.id == myId || reaction.user.id == DataPacket.ID_LOCAL
                    val displayName =
                        if (isLocal) {
                            "${reaction.user.long_name} (${stringResource(Res.string.you)})"
                        } else {
                            reaction.user.long_name
                        }
                    Text(text = displayName, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLocal) {
                            MessageStatusButton(
                                status = reaction.status,
                                fromLocal = true,
                                onStatusClick = { showStatusDialog = reaction },
                            )
                        }
                        Text(text = reaction.emoji, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isLocalOrPreDbUpdateReaction = (reaction.rssi == 0)
                    if (!isLocalOrPreDbUpdateReaction) {
                        if (reaction.hopsAway == 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Snr(reaction.snr)
                                Rssi(reaction.rssi)
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = reaction.hopsAway.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = getShortDateTime(reaction.timestamp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ReactionItemPreview() {
    AppTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            ReactionItem(emoji = "\uD83D\uDE42")
            ReactionItem(emoji = "\uD83D\uDE42", emojiCount = 2)
            AddReactionButton()
        }
    }
}

@Preview
@Composable
private fun ReactionRowPreview() {
    AppTheme {
        ReactionRow(
            reactions =
            listOf(
                Reaction(
                    replyId = 1,
                    user = User(),
                    emoji = "\uD83D\uDE42",
                    timestamp = 1L,
                    snr = -1.0f,
                    rssi = -99,
                    hopsAway = 1,
                ),
                Reaction(
                    replyId = 1,
                    user = User(),
                    emoji = "\uD83D\uDE42",
                    timestamp = 1L,
                    snr = -1.0f,
                    rssi = -99,
                    hopsAway = 1,
                ),
            ),
        )
    }
}
