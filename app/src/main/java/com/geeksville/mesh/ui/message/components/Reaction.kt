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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Badge
import androidx.compose.material.BadgedBox
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.database.entity.Reaction
import com.geeksville.mesh.ui.components.EmojiPicker
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun ReactionButton(
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit = {}
) {
    var showEmojiPickerDialog by remember { mutableStateOf(false) }
    if (showEmojiPickerDialog) {
        EmojiPickerDialog(
            onConfirm = {
                showEmojiPickerDialog = false
                onClick(it)
            },
            onDismiss = { showEmojiPickerDialog = false }
        )
    }
    IconButton(onClick = { showEmojiPickerDialog = true }) {
        Icon(
            imageVector = Icons.Default.EmojiEmotions,
            contentDescription = "emoji",
            modifier = modifier.size(16.dp),
            tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
        )
    }
}

@Composable
private fun ReactionItem(
    emoji: String,
    emojiCount: Int = 1,
    onClick: () -> Unit = {},
) {
    BadgedBox(
        modifier = Modifier.padding(start = 2.dp, top = 8.dp, end = 2.dp, bottom = 4.dp),
        badge = {
            if (emojiCount > 1) {
                Badge(
                    backgroundColor = MaterialTheme.colors.onBackground,
                    contentColor = MaterialTheme.colors.background,
                ) {
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = emojiCount.toString()
                    )
                }
            }
        }
    ) {
        Surface(
            modifier = Modifier
                .clickable { onClick() },
            color = MaterialTheme.colors.surface,
            shape = RoundedCornerShape(32.dp),
            elevation = 4.dp,
        ) {
            Text(
                text = emoji,
                modifier = Modifier
                    .padding(8.dp)
                    .clip(CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionRow(
    fromLocal: Boolean,
    reactions: List<Reaction> = emptyList(),
    onSendReaction: (String) -> Unit = {}
) {
    val emojiList by remember(reactions) {
        mutableStateOf(
            reduceEmojis(
                if (fromLocal) {
                    reactions.map { it.emoji }
                } else {
                    reactions.map { it.emoji }.reversed()
                }
            ).entries
        )
    }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = if (fromLocal) Arrangement.End else Arrangement.Start
    ) {
        emojiList.forEach { entry ->
            ReactionItem(
                emoji = entry.key,
                emojiCount = entry.value,
                onClick = {
                    onSendReaction(entry.key)
                }
            )
        }
    }
}

fun reduceEmojis(emojis: List<String>): Map<String, Int> = emojis.groupingBy { it }.eachCount()

@Composable
fun EmojiPickerDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        EmojiPicker(
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@PreviewLightDark
@Composable
fun ReactionItemPreview() {
    AppTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colors.background)
        ) {
            ReactionItem(emoji = "\uD83D\uDE42")
            ReactionItem(emoji = "\uD83D\uDE42", emojiCount = 2)
            ReactionButton()
        }
    }
}

@Preview
@Composable
fun ReactionRowPreview() {
    AppTheme {
        ReactionRow(
            fromLocal = true, reactions = listOf(
                Reaction(
                    replyId = 1,
                    user = MeshProtos.User.getDefaultInstance(),
                    emoji = "\uD83D\uDE42",
                    timestamp = 1L
                ),
                Reaction(
                    replyId = 1,
                    user = MeshProtos.User.getDefaultInstance(),
                    emoji = "\uD83D\uDE42",
                    timestamp = 1L
                ),
            )
        )
    }
}
