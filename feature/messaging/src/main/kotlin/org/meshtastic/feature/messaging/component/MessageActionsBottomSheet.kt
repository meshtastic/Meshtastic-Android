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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.copy
import org.meshtastic.core.strings.delete
import org.meshtastic.core.strings.reply
import org.meshtastic.core.strings.select

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsBottomSheet(
    quickEmojis: List<String>,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onMoreReactions: () -> Unit,
    onCopy: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        MessageActionsContent(
            quickEmojis = quickEmojis,
            onReply = onReply,
            onReact = onReact,
            onMoreReactions = onMoreReactions,
            onCopy = onCopy,
            onSelect = onSelect,
            onDelete = onDelete
        )
    }
}

@Composable
fun MessageActionsContent(
    quickEmojis: List<String>,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onMoreReactions: () -> Unit,
    onCopy: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        QuickEmojiRow(quickEmojis = quickEmojis, onReact = onReact, onMoreReactions = onMoreReactions)
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ListItem(
            headlineContent = { Text(stringResource(Res.string.reply)) },
            leadingContent = { Icon(Icons.Default.Reply, contentDescription = stringResource(Res.string.reply)) },
            modifier = Modifier.clickable(onClick = onReply),
        )
        
        ListItem(
            headlineContent = { Text(stringResource(Res.string.copy)) },
            leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(Res.string.copy)) },
            modifier = Modifier.clickable(onClick = onCopy),
        )

        ListItem(
            headlineContent = { Text(stringResource(Res.string.select)) },
            leadingContent = { Icon(Icons.Default.SelectAll, contentDescription = stringResource(Res.string.select)) },
            modifier = Modifier.clickable(onClick = onSelect),
        )

        ListItem(
            headlineContent = { Text(stringResource(Res.string.delete)) },
            leadingContent = { Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete)) },
            modifier = Modifier.clickable(onClick = onDelete),
        )
    }
}

@Composable
private fun QuickEmojiRow(quickEmojis: List<String>, onReact: (String) -> Unit, onMoreReactions: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        quickEmojis.take(6).forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onReact(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 20.sp)
            }
        }
        
        IconButton(
            onClick = onMoreReactions,
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Icon(
                Icons.Default.AddReaction,
                contentDescription = "More reactions",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
