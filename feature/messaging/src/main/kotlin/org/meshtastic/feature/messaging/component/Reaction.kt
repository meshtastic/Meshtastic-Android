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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.entity.Reaction
import org.meshtastic.core.ui.component.BottomSheetDialog
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.MeshProtos

@Composable
private fun ReactionItem(emoji: String, emojiCount: Int = 1, onClick: () -> Unit = {}, onLongClick: () -> Unit = {}) {
    BadgedBox(
        badge = {
            if (emojiCount > 1) {
                Badge { Text(fontWeight = FontWeight.Bold, text = emojiCount.toString()) }
            }
        },
    ) {
        Surface(
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
        ) {
            Text(text = emoji, modifier = Modifier.padding(4.dp).clip(CircleShape))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReactionRow(
    modifier: Modifier = Modifier,
    reactions: List<Reaction> = emptyList(),
    onSendReaction: (String) -> Unit = {},
    onShowReactions: () -> Unit = {},
) {
    val emojiList = reduceEmojis(reactions.reversed().map { it.emoji }).entries

    AnimatedVisibility(emojiList.isNotEmpty()) {
        LazyRow(
            modifier = modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(emojiList.size) { index ->
                val entry = emojiList.elementAt(index)
                ReactionItem(
                    emoji = entry.key,
                    emojiCount = entry.value,
                    onClick = { onSendReaction(entry.key) },
                    onLongClick = onShowReactions,
                )
            }
        }
    }
}

private fun reduceEmojis(emojis: List<String>): Map<String, Int> = emojis.groupingBy { it }.eachCount()

@Composable
internal fun ReactionDialog(reactions: List<Reaction>, onDismiss: () -> Unit = {}) =
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

@PreviewLightDark
@Composable
private fun ReactionItemPreview() {
    AppTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            ReactionItem(emoji = "\uD83D\uDE42")
            ReactionItem(emoji = "\uD83D\uDE42", emojiCount = 2)
            ReactionButton()
        }
    }
}

@Preview
@Composable
private fun ReactionRowPreview() {
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
            ),
        )
    }
}
