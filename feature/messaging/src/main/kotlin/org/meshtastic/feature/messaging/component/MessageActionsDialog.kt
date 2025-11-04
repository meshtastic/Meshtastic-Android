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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.CloudDone
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.CloudUpload
import androidx.compose.material.icons.twotone.HowToReg
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.theme.AppTheme

@Composable
fun MessageActionsDialog(
    status: MessageStatus?,
    onDismiss: () -> Unit,
    onClickReact: () -> Unit,
    onClickReply: () -> Unit,
    onClickSelect: () -> Unit,
    onClickStatus: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            ListItem(
                leadingIcon = Icons.Rounded.EmojiEmotions,
                text = stringResource(R.string.react),
                trailingIcon = null,
            ) {
                onClickReact()
                onDismiss()
            }

            ListItem(leadingIcon = Icons.Rounded.CheckCircleOutline, text = "Select", trailingIcon = null) {
                onClickSelect()
                onDismiss()
            }

            ListItem(
                leadingIcon = Icons.AutoMirrored.Rounded.Reply,
                text = stringResource(R.string.reply),
                trailingIcon = null,
            ) {
                onClickReply()
                onDismiss()
            }

            status?.let {
                ListItem(
                    leadingIcon =
                    when (it) {
                        MessageStatus.RECEIVED -> Icons.TwoTone.HowToReg
                        MessageStatus.QUEUED -> Icons.TwoTone.CloudUpload
                        MessageStatus.DELIVERED -> Icons.TwoTone.CloudDone
                        MessageStatus.ENROUTE -> Icons.TwoTone.Cloud
                        MessageStatus.ERROR -> Icons.TwoTone.CloudOff
                        else -> Icons.TwoTone.Warning
                    },
                    text = stringResource(R.string.message_delivery_status),
                    trailingIcon = null,
                ) {
                    onClickStatus()
                    onDismiss()
                }
            }
        }
    }
}

@Preview
@Composable
private fun MessageActionsDialogPreview() {
    AppTheme {
        MessageActionsDialog(
            status = MessageStatus.DELIVERED,
            onDismiss = {},
            onClickReact = {},
            onClickReply = {},
            onClickSelect = {},
            onClickStatus = {},
        )
    }
}
