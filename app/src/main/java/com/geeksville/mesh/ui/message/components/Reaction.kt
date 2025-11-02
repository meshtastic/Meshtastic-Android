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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.database.entity.Reaction
import com.geeksville.mesh.ui.common.components.BottomSheetDialog
import com.geeksville.mesh.ui.common.theme.AppTheme

@Composable
fun ReactionRow(
    modifier: Modifier = Modifier,
    reactions: List<Reaction> = emptyList(),
    onShowReactions: () -> Unit = {},
) {
    val emojiList = reactions.map { it.emoji }.distinct()

    Box(modifier = modifier) {
        EmojiStack(
            offset = 12.dp,
            modifier =
            Modifier.combinedClickable(
                interactionSource = null,
                indication = null,
                onLongClick = onShowReactions,
                onClick = {},
            ),
        ) {
            emojiList.forEach { emoji ->
                Box(
                    modifier =
                    Modifier.size(24.dp)
                        .aspectRatio(1f)
                        .background(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emoji,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        modifier = Modifier.wrapContentSize(),
                    )
                }
            }
        }
    }
}

@Composable
fun EmojiStack(modifier: Modifier = Modifier, offset: Dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeables = measurables.map { measurable -> measurable.measure(constraints) }

        val height = if (placeables.isNotEmpty()) placeables.first().height else 0

        val width =
            if (placeables.isNotEmpty()) {
                placeables.first().width + (offset.toPx().toInt() * (placeables.size - 1))
            } else {
                0
            }

        layout(width = width, height = height) {
            placeables.mapIndexed { index, placeable -> placeable.place(x = offset.toPx().toInt() * index, y = 0) }
        }
    }
}

@Composable
fun ReactionDialog(reactions: List<Reaction>, onDismiss: () -> Unit = {}) =
    BottomSheetDialog(onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(fraction = .3f)) {
        val groupedEmojis = reactions.groupBy { it.emoji }
        var selectedEmoji by remember { mutableStateOf<String?>(null) }
        val filteredReactions = selectedEmoji?.let { groupedEmojis[it] ?: emptyList() } ?: reactions

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(groupedEmojis.entries.toList()) { (emoji, reactions) ->
                Text(
                    text = "$emoji${reactions.size}",
                    modifier =
                    Modifier.clip(CircleShape)
                        .background(if (selectedEmoji == emoji) Color.Gray else Color.Transparent)
                        .padding(8.dp)
                        .clickable { selectedEmoji = if (selectedEmoji == emoji) null else emoji },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filteredReactions) { reaction ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = reaction.user.longName, style = MaterialTheme.typography.titleMedium)
                    Text(text = reaction.emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }

@Preview
@Composable
fun ReactionRowPreview() {
    AppTheme {
        ReactionRow(
            reactions =
            listOf(
                Reaction(
                    replyId = 1,
                    user = MeshProtos.User.getDefaultInstance(),
                    emoji = "\uD83D\uDE42",
                    timestamp = 1L,
                ),
                Reaction(
                    replyId = 1,
                    user = MeshProtos.User.getDefaultInstance(),
                    emoji = "\uD83D\uDE42",
                    timestamp = 1L,
                ),
                Reaction(
                    replyId = 1,
                    user = MeshProtos.User.getDefaultInstance(),
                    emoji = "\uD83E\uDEE0",
                    timestamp = 1L,
                ),
                Reaction(
                    replyId = 1,
                    user = MeshProtos.User.getDefaultInstance(),
                    emoji = "\uD83D\uDD12",
                    timestamp = 1L,
                ),
            ),
        )
    }
}
