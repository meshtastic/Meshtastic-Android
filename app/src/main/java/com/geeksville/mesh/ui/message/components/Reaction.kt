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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.database.entity.Reaction
import com.geeksville.mesh.ui.components.BottomSheetDialog
import com.geeksville.mesh.ui.components.EmojiPickerDialog
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
            imageVector = Icons.Default.AddReaction,
            contentDescription = "emoji",
            modifier = modifier.size(16.dp),
            tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
        )
    }
}

@Composable
fun ReplyButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = "reply",
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

    Surface(
        modifier = Modifier
            .padding(2.dp)
            .border(
                1.dp,
                MaterialTheme.colors.onSurface.copy(ContentAlpha.medium),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        color = MaterialTheme.colors.surface.copy(alpha = ContentAlpha.medium),
        contentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
        shape = RoundedCornerShape(8.dp),
        elevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colors.surface)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                style = MaterialTheme.typography.h6,
                text = emoji
            )
            if (emojiCount > 0) {
                Spacer(
                    modifier = Modifier.width(2.dp)
                )
                AnimatedContent(
                    targetState = emojiCount,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInVertically { -it } togetherWith slideOutVertically { it }
                        } else {
                            slideInVertically { it } togetherWith slideOutVertically { -it }
                        }
                    }
                ) {
                    Text(
                        text = "$it",
                        style = MaterialTheme.typography.body2,
                    )
                }
            }
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

    var maxLines by remember { mutableStateOf(1) }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = if (fromLocal) Arrangement.End else Arrangement.Start,
        maxLines = maxLines,
        overflow = FlowRowOverflow.expandIndicator {
            ReactionItem(
                emoji = "...",
                emojiCount = 0
            ) {
                maxLines += 1
            }
        }
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

@Composable
internal fun Ellipsis(text: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Text(
            modifier = Modifier
                .padding(3.dp),
            text = text,
            fontSize = 18.sp
        )
    }
}

fun reduceEmojis(emojis: List<String>): Map<String, Int> = emojis.groupingBy { it }.eachCount()

@Composable
fun ReactionDialog(
    reactions: List<Reaction>,
    onDismiss: () -> Unit = {}
) = BottomSheetDialog(
    onDismiss = onDismiss,
    modifier = Modifier.fillMaxHeight(fraction = .3f),
) {
    val groupedEmojis = reactions.groupBy { it.emoji }
    var selectedEmoji by remember { mutableStateOf<String?>(null) }
    val filteredReactions = selectedEmoji?.let { groupedEmojis[it] ?: emptyList() } ?: reactions

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(groupedEmojis.entries.toList()) { (emoji, reactions) ->
            Text(
                text = "$emoji${reactions.size}",
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selectedEmoji == emoji) Color.Gray else Color.Transparent)
                    .padding(8.dp)
                    .clickable {
                        selectedEmoji = if (selectedEmoji == emoji) null else emoji
                    },
                style = MaterialTheme.typography.body2
            )
        }
    }

    Divider(Modifier.padding(vertical = 8.dp))

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(filteredReactions) { reaction ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reaction.user.longName,
                    style = MaterialTheme.typography.subtitle1
                )
                Text(
                    text = reaction.emoji,
                    style = MaterialTheme.typography.h6
                )
            }
        }
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
            ReactionItem(emoji = "\uD83D\uDE42", emojiCount = 222)
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
