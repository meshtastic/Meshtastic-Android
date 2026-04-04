/*
 * Copyright (c) 2026 Chris7X
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

package org.meshtastic.feature.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.meshtastic.feature.ai.llm.ConversationContext
import org.meshtastic.feature.ai.llm.MessageContext
import org.meshtastic.feature.ai.llm.SmartReplyGenerator

/**
 * Displays smart reply suggestions as horizontal scrolling chips.
 *
 * @param generator The [SmartReplyGenerator] instance for generating replies.
 * @param lastReceivedMessage The last received message text (triggers generation).
 * @param senderName The name of the message sender.
 * @param recentHistory Recent message history for context.
 * @param enabled Whether the chips are enabled for interaction.
 * @param languageCode ISO 639-1 language code for localized replies (e.g., "en", "it", "de").
 * @param onReplySelected Callback when a reply chip is selected.
 * @param modifier The modifier for this composable.
 */
@Composable
fun SmartReplyChips(
    generator: SmartReplyGenerator,
    lastReceivedMessage: String?,
    senderName: String?,
    recentHistory: List<MessageContext>,
    enabled: Boolean,
    languageCode: String = "en",
    onReplySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Generate suggestions when last received message changes OR language changes
    LaunchedEffect(lastReceivedMessage, languageCode) {
        if (lastReceivedMessage.isNullOrBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }

        isLoading = true
        val result = withContext(Dispatchers.IO) {
            generator.suggestReplies(
                receivedMessage = lastReceivedMessage,
                senderName = senderName,
                recentHistory = recentHistory,
                languageCode = languageCode
            )
        }
        isLoading = false

        result.getOrElse { emptyList() }.let { replies ->
            suggestions = replies
        }
    }

    AnimatedVisibility(
        visible = suggestions.isNotEmpty() && enabled,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { reply ->
                SmartReplyChip(
                    text = reply,
                    onClick = { onReplySelected(reply) },
                    enabled = enabled && !isLoading
                )
            }
        }
    }
}

/**
 * A single smart reply chip.
 */
@Composable
private fun SmartReplyChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = text,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium
            )
        },
        modifier = modifier.semantics {
            contentDescription = "Smart reply: $text"
        },
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = AssistChipDefaults.assistChipBorder(
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            enabled = enabled
        )
    )
}
