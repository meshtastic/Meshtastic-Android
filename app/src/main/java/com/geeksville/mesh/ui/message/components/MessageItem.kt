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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ChipDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.ui.components.AutoLinkText
import com.geeksville.mesh.ui.preview.NodePreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
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
                    if (!fromLocal) {
                        Text(
                            text = with(node.user) { "$longName ($id)" },
                            modifier = Modifier.padding(bottom = 4.dp),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            style = MaterialTheme.typography.button.copy(
                                color = MaterialTheme.colors.onSurface,
                                letterSpacing = 0.1.sp,
                            )
                        )
                    }
                    AutoLinkText(
                        text = messageText.orEmpty(),
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colors.onBackground,
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
