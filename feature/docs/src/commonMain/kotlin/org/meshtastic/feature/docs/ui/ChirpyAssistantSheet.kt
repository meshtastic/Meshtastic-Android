/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.docs.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.meshtastic.feature.docs.model.AIDocAssistantSessionState
import org.meshtastic.feature.docs.model.ChirpyMessage
import org.meshtastic.feature.docs.model.ChirpyRole

/** Chirpy AI Assistant bottom sheet with chat UI. Hidden entirely when the assistant reports unsupported. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChirpyAssistantSheet(
    state: AIDocAssistantSessionState,
    isSupported: Boolean,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isSupported) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().imePadding().padding(16.dp)) {
            Text(
                text = "Chirpy Assistant",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Message list
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(state.messages, key = { it.id }) { message ->
                    ChirpyMessageBubble(message = message)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (state.isLoading) {
                    item {
                        Text(
                            text = "Chirpy is thinking...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            // Pinned input bar
            OutlinedTextField(
                value = state.draftQuestion,
                onValueChange = onDraftChange,
                placeholder = { Text("Ask about Meshtastic...") },
                trailingIcon = {
                    TextButton(onClick = onSubmit, enabled = state.draftQuestion.isNotBlank() && !state.isLoading) {
                        Text("Send")
                    }
                },
                singleLine = false,
                maxLines = 3,
                modifier =
                Modifier.fillMaxWidth().padding(top = 8.dp).semantics {
                    contentDescription = "Ask Chirpy a question"
                },
            )
        }
    }
}

private const val BUBBLE_WIDTH_FRACTION = 0.85f

@Composable
private fun ChirpyMessageBubble(message: ChirpyMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == ChirpyRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val colors =
        if (isUser) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Card(colors = colors, modifier = Modifier.fillMaxWidth(BUBBLE_WIDTH_FRACTION)) {
            Text(text = message.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }

        if (message.sourcePageIds.isNotEmpty()) {
            Text(
                text = "Sources: ${message.sourcePageIds.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}
