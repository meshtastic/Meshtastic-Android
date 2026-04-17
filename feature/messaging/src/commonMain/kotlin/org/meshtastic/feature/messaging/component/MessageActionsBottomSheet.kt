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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.action_copy_message
import org.meshtastic.core.resources.action_delete_message
import org.meshtastic.core.resources.action_react_with_emoji
import org.meshtastic.core.resources.action_select_message
import org.meshtastic.core.resources.action_send_reply
import org.meshtastic.core.resources.action_show_message_status
import org.meshtastic.core.resources.copy
import org.meshtastic.core.resources.delete
import org.meshtastic.core.resources.device_metrics_label_value
import org.meshtastic.core.resources.message_delivery_status
import org.meshtastic.core.resources.more_reactions
import org.meshtastic.core.resources.reply
import org.meshtastic.core.resources.select
import org.meshtastic.core.ui.icon.AddReaction
import org.meshtastic.core.ui.icon.Copy
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Reply
import org.meshtastic.core.ui.icon.SelectAll

@Suppress("LongMethod")
@Composable
fun MessageActionsContent(
    quickEmojis: List<String>,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onMoreReactions: () -> Unit,
    onCopy: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    statusString: Pair<StringResource, StringResource>? = null,
    status: MessageStatus? = null,
    onStatus: (() -> Unit),
) {
    Column {
        QuickEmojiRow(quickEmojis = quickEmojis, onReact = onReact, onMoreReactions = onMoreReactions)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (status != null) {
            val title =
                statusString?.first?.let { stringResource(it) } ?: stringResource(Res.string.message_delivery_status)
            val statusText = statusString?.second?.let { stringResource(it) }

            ListItem(
                headlineContent = {
                    Text(stringResource(Res.string.device_metrics_label_value, title, statusText.orEmpty()))
                },
                leadingContent = { MessageStatusIcon(status = status) },
                modifier =
                Modifier.clickable(
                    onClickLabel = stringResource(Res.string.action_show_message_status),
                    role = Role.Button,
                    onClick = onStatus,
                ),
            )
        }

        ListItem(
            headlineContent = { Text(stringResource(Res.string.reply)) },
            leadingContent = { Icon(MeshtasticIcons.Reply, contentDescription = stringResource(Res.string.reply)) },
            modifier =
            Modifier.clickable(
                onClickLabel = stringResource(Res.string.action_send_reply),
                role = Role.Button,
                onClick = onReply,
            ),
        )

        ListItem(
            headlineContent = { Text(stringResource(Res.string.copy)) },
            leadingContent = { Icon(MeshtasticIcons.Copy, contentDescription = stringResource(Res.string.copy)) },
            modifier =
            Modifier.clickable(
                onClickLabel = stringResource(Res.string.action_copy_message),
                role = Role.Button,
                onClick = onCopy,
            ),
        )

        ListItem(
            headlineContent = { Text(stringResource(Res.string.select)) },
            leadingContent = {
                Icon(MeshtasticIcons.SelectAll, contentDescription = stringResource(Res.string.select))
            },
            modifier =
            Modifier.clickable(
                onClickLabel = stringResource(Res.string.action_select_message),
                role = Role.Button,
                onClick = onSelect,
            ),
        )

        ListItem(
            headlineContent = { Text(stringResource(Res.string.delete)) },
            leadingContent = { Icon(MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.delete)) },
            modifier =
            Modifier.clickable(
                onClickLabel = stringResource(Res.string.action_delete_message),
                role = Role.Button,
                onClick = onDelete,
            ),
        )
    }
}

private const val MAX_EMOJI_ROW_SIZE = 6

@Composable
private fun QuickEmojiRow(quickEmojis: List<String>, onReact: (String) -> Unit, onMoreReactions: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        quickEmojis.take(MAX_EMOJI_ROW_SIZE).forEach { emoji ->
            Box(
                modifier =
                Modifier.size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(
                        onClickLabel = stringResource(Res.string.action_react_with_emoji),
                        role = Role.Button,
                    ) {
                        onReact(emoji)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, style = MaterialTheme.typography.titleMedium)
            }
        }

        IconButton(
            onClick = onMoreReactions,
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        ) {
            Icon(
                MeshtasticIcons.AddReaction,
                contentDescription = stringResource(Res.string.more_reactions),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
