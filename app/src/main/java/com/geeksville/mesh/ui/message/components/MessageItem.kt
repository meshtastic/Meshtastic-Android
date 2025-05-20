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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.ui.components.AutoLinkText
import com.geeksville.mesh.ui.components.UserAvatar
import com.geeksville.mesh.ui.preview.NodePreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageItem(
    node: Node,
    messageText: String?,
    messageTime: String,
    messageStatus: MessageStatus?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onChipClick: () -> Unit = {},
    onStatusClick: () -> Unit = {},
    onSendReaction: (String) -> Unit = {},
) = Row(
    modifier = modifier
        .fillMaxWidth()
        .background(color = if (selected) Color.Gray else MaterialTheme.colorScheme.background),
    verticalAlignment = Alignment.CenterVertically,
) {
    val fromLocal = node.user.id == DataPacket.ID_LOCAL
    val messageColor = if (fromLocal) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val (topStart, topEnd) = if (fromLocal) 12.dp to 4.dp else 4.dp to 12.dp
    val messageModifier = if (fromLocal) {
        Modifier.padding(start = 48.dp, top = 8.dp, end = 8.dp, bottom = 6.dp)
    } else {
        Modifier.padding(start = 8.dp, top = 8.dp, end = 0.dp, bottom = 6.dp)
    }

    if (!fromLocal) {
        UserAvatar(
            node = node,
            modifier = Modifier
                .padding(start = 8.dp, top = 8.dp)
                .align(Alignment.Top),
        ) { onChipClick() }
    }

    Card(
        modifier = Modifier
            .weight(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .then(messageModifier),
        colors = CardDefaults.cardColors(
            containerColor = messageColor
        ),
        shape = RoundedCornerShape(topStart, topEnd, bottomStart = 12.dp, bottomEnd = 12.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
            ) {
                if (!fromLocal) {
                    Text(
                        text = with(node.user) { "$longName ($id)" },
                        modifier = Modifier.padding(bottom = 4.dp),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                AutoLinkText(
                    text = messageText.orEmpty(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
    }
    if (!fromLocal) {
        ReactionButton(Modifier.padding(16.dp), onSendReaction)
    }
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
        )
    }
}
