/*
 * Copyright (c) 2024 Meshtastic LLC
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ChipDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Reply
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.CloudDone
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.CloudUpload
import androidx.compose.material.icons.twotone.HowToReg
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.database.entity.Reply
import com.geeksville.mesh.ui.components.AutoLinkText
import com.geeksville.mesh.ui.preview.NodeEntityPreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.getShortDateTime

@Suppress("LongMethod")
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun MessageItem(
    node: NodeEntity,
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
    onReplyClick: () -> Unit = {},
    replies: List<Reply> = emptyList(),

) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .background(color = if (selected) Color.Gray else MaterialTheme.colors.background),
    verticalAlignment = Alignment.CenterVertically,
) {
    val fromLocal = node.user.id == DataPacket.ID_LOCAL
    val messageColor = if (fromLocal) R.color.colorMyMsg else R.color.colorMsg
    val (topStart, topEnd) = if (fromLocal) 12.dp to 4.dp else 4.dp to 12.dp
    val messageModifier = if (fromLocal) {
        Modifier.padding(start = 48.dp, top = 8.dp, end = 8.dp, bottom = 6.dp)
    } else {
        Modifier.padding(start = 8.dp, top = 8.dp, end = 0.dp, bottom = 6.dp)
    }

    Card(
        modifier = Modifier
            .weight(1f)
            .then(messageModifier),
        elevation = 4.dp,
        shape = RoundedCornerShape(topStart, topEnd, 12.dp, 12.dp),
    ) {
        Surface(
            modifier = modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
            color = colorResource(id = messageColor),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!fromLocal) {
                        Chip(
                            onClick = onChipClick,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .width(72.dp),
                            colors = ChipDefaults.chipColors(
                                backgroundColor = Color(node.colors.second),
                                contentColor = Color(node.colors.first),
                            ),
                        ) {
                            Text(
                                text = node.user.shortName,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = MaterialTheme.typography.button.fontSize,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
//                    if (!fromLocal) {
//                        Text(
//                            text = with(node.user) { "$longName ($id)" },
//                            modifier = Modifier.padding(bottom = 4.dp),
//                            color = MaterialTheme.colors.onSurface,
//                            fontSize = MaterialTheme.typography.caption.fontSize,
//                        )
//                    }
                        AutoLinkText(
                            text = messageText.orEmpty(),
                            style = LocalTextStyle.current.copy(
                                color = LocalContentColor.current,
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = messageTime,
                                color = MaterialTheme.colors.onSurface,
                                fontSize = MaterialTheme.typography.caption.fontSize,
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
                replies.forEach { reply ->
                    ReplyRow(reply = reply)
                }
            }
        }
    }
    if (selected) {
        ReactionButton(Modifier.padding(16.dp), onSendReaction)

        ReplyButton(Modifier.padding(16.dp), onReplyClick)
    } else if (!fromLocal) {
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ReplyRow(
    reply: Reply,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, start = 32.dp, end = 4.dp),
        elevation = 4.dp,
    ) {
        Surface(
            color = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = reply.user.shortName,
                        fontSize = MaterialTheme.typography.caption.fontSize,
                        fontWeight = FontWeight.Light,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = reply.message,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colors.onSurface,
                        fontSize = MaterialTheme.typography.caption.fontSize,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.TwoTone.Reply,
                        contentDescription = stringResource(R.string.reply),
                        modifier = Modifier.size(8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = getShortDateTime(reply.timestamp),
                        fontSize = MaterialTheme.typography.caption.fontSize,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun MessageItemPreview() {
    AppTheme {
        MessageItem(
            node = NodeEntityPreviewParameterProvider().values.first(),
            messageText = stringResource(R.string.sample_message),
            messageTime = getShortDateTime(System.currentTimeMillis() - 720000),
            messageStatus = MessageStatus.DELIVERED,
            selected = false,
            replies = listOf(
                Reply(
                    user = NodeEntityPreviewParameterProvider().values.first().user,
                    message = "Nevermind, it's not scary. Phew!",
                    replyId = 1,
                    timestamp = System.currentTimeMillis() - 320000
                ),
                Reply(
                    user = NodeEntityPreviewParameterProvider().values.last().user,
                    message = "Nice, good job!",
                    replyId = 2,
                    timestamp = System.currentTimeMillis()
                )
            )
        )
    }
}
