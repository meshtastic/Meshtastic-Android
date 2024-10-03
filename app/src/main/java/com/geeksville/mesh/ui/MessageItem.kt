package com.geeksville.mesh.ui

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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.components.AutoLinkText
import com.geeksville.mesh.ui.theme.AppTheme

@Suppress("LongMethod")
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun MessageItem(
    shortName: String?,
    messageText: String?,
    messageTime: String,
    messageStatus: MessageStatus?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onChipClick: () -> Unit = {},
    onStatusClick: () -> Unit = {},
) {
    val fromLocal = shortName == null
    val messageColor = if (fromLocal) R.color.colorMyMsg else R.color.colorMsg
    val (topStart, topEnd) =  if (fromLocal) 12.dp to 4.dp else 4.dp to 12.dp
    val messageModifier = if (fromLocal) {
        Modifier.padding(start = 48.dp, top = 8.dp, end = 8.dp, bottom = 6.dp)
    } else {
        Modifier.padding(start = 8.dp, top = 8.dp, end = 48.dp, bottom = 6.dp)
    }

    Card(
        modifier = Modifier
            .background(color = if (selected) Color.Gray else MaterialTheme.colors.background)
            .fillMaxWidth()
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
                if (shortName != null) {
                    Chip(
                        onClick = onChipClick,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .width(72.dp),
                    ) {
                        Text(
                            text = shortName,
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
//                    Text(
//                        text = longName ?: stringResource(id = R.string.unknown_username),
//                        color = MaterialTheme.colors.onSurface,
//                        fontSize = MaterialTheme.typography.button.fontSize,
//                    )
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
        }
    }
}

@PreviewLightDark
@Composable
private fun MessageItemPreview() {
    AppTheme {
        MessageItem(
            shortName = stringResource(R.string.some_username),
            // longName = stringResource(R.string.unknown_username),
            messageText = stringResource(R.string.sample_message),
            messageTime = "10:00",
            messageStatus = MessageStatus.DELIVERED,
            selected = false,
        )
    }
}
