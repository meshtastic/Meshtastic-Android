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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.CloudDone
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.CloudUpload
import androidx.compose.material.icons.twotone.HowToReg
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.Reaction
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.ui.common.components.AutoLinkText
import com.geeksville.mesh.ui.common.components.Rssi
import com.geeksville.mesh.ui.common.components.Snr
import com.geeksville.mesh.ui.common.preview.NodePreviewParameterProvider
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.node.components.NodeChip
import com.geeksville.mesh.ui.node.components.NodeMenuAction

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageItem(
    node: Node,
    messageText: String?,
    messageTime: String,
    messageStatus: MessageStatus?,
    emojis: List<Reaction> = emptyList(),
    sendReaction: (String) -> Unit = {},
    onShowReactions: () -> Unit = {},
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onAction: (NodeMenuAction) -> Unit = {},
    onStatusClick: () -> Unit = {},
    isConnected: Boolean,
    snr: Float,
    rssi: Int,
    hopsAway: String?,
) = Column(
    modifier = modifier
        .fillMaxWidth()
        .background(color = if (selected) Color.Gray else MaterialTheme.colorScheme.background),
) {
    val fromLocal = node.user.id == DataPacket.ID_LOCAL
    val messageColor = if (fromLocal) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color(node.colors.second).copy(alpha = 0.25f)
    }
    val messageModifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp)

    Card(
        modifier = Modifier
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
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
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
                text = messageText.orEmpty(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!fromLocal) {
                    if (hopsAway == null) {
                        Snr(snr, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        Rssi(rssi, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    } else {
                        Text(
                            text = hopsAway,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        )
                    }
                }
                Text(
                    text = messageTime,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                )
                AnimatedVisibility(visible = fromLocal) {
                    Icon(
                        imageVector = when (messageStatus) {
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
        onShowReactions = onShowReactions
    )
}

@PreviewLightDark
@Composable
private fun MessageItemPreview() {
    AppTheme {
        MessageItem(
            node = NodePreviewParameterProvider().values.first(),
            messageText = stringResource(R.string.sample_message),
            messageTime = "10:00",
            messageStatus = MessageStatus.DELIVERED,
            selected = false,
            isConnected = true,
            snr = 20.5f,
            rssi = 90,
            hopsAway = null,
            emojis = listOf(
                Reaction(
                    emoji = "\uD83D\uDE42",
                    user = NodePreviewParameterProvider().values.first().user,
                    replyId = 0,
                    timestamp = 0L
                ),
            )
        )
    }
}
