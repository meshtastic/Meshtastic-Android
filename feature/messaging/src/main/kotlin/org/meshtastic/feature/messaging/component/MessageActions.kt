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

package org.meshtastic.feature.messaging.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.CloudDone
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.CloudUpload
import androidx.compose.material.icons.twotone.HowToReg
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.emoji.EmojiPickerDialog

@Composable
internal fun ReactionButton(onSendReaction: (String) -> Unit = {}) {
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
    IconButton(onClick = { showEmojiPickerDialog = true }) {
        Icon(imageVector = Icons.Default.EmojiEmotions, contentDescription = stringResource(R.string.react))
    }
}

@Composable
private fun ReplyButton(onClick: () -> Unit = {}) = IconButton(
    onClick = onClick,
    content = {
        Icon(imageVector = Icons.AutoMirrored.Filled.Reply, contentDescription = stringResource(R.string.reply))
    },
)

@Composable
private fun MessageStatusButton(onStatusClick: () -> Unit = {}, status: MessageStatus, fromLocal: Boolean) =
    AnimatedVisibility(visible = fromLocal) {
        IconButton(onClick = onStatusClick) {
            Icon(
                imageVector =
                when (status) {
                    MessageStatus.RECEIVED -> Icons.TwoTone.HowToReg
                    MessageStatus.QUEUED -> Icons.TwoTone.CloudUpload
                    MessageStatus.DELIVERED -> Icons.TwoTone.CloudDone
                    MessageStatus.ENROUTE -> Icons.TwoTone.Cloud
                    MessageStatus.ERROR -> Icons.TwoTone.CloudOff
                    else -> Icons.TwoTone.Warning
                },
                contentDescription = stringResource(R.string.message_delivery_status),
            )
        }
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MessageActions(
    modifier: Modifier = Modifier,
    isLocal: Boolean = false,
    status: MessageStatus?,
    onSendReaction: (String) -> Unit = {},
    onSendReply: () -> Unit = {},
    onStatusClick: () -> Unit = {},
) {
    Row(modifier = modifier.wrapContentSize()) {
        ReactionButton { onSendReaction(it) }
        ReplyButton { onSendReply() }
        MessageStatusButton(
            onStatusClick = onStatusClick,
            status = status ?: MessageStatus.UNKNOWN,
            fromLocal = isLocal,
        )
    }
}
