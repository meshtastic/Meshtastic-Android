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

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.chirpy_assistant_title
import org.meshtastic.core.resources.chirpy_checking
import org.meshtastic.core.resources.chirpy_downloading
import org.meshtastic.core.resources.chirpy_downloading_subtitle
import org.meshtastic.core.resources.chirpy_search_placeholder
import org.meshtastic.core.resources.chirpy_thinking
import org.meshtastic.core.resources.img_chirpy
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Send
import org.meshtastic.feature.docs.model.AIDocAssistantSessionState
import org.meshtastic.feature.docs.model.ChirpyMessage
import org.meshtastic.feature.docs.model.ChirpyRole
import org.meshtastic.feature.docs.model.ModelReadiness
import org.meshtastic.core.resources.Res as CoreRes

/** Chirpy AI Assistant bottom sheet with chat UI. Hidden entirely when the assistant reports unsupported. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChirpyAssistantSheet(
    state: AIDocAssistantSessionState,
    modelReadiness: ModelReadiness,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToPage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (modelReadiness) {
        is ModelReadiness.Unavailable -> Unit

        is ModelReadiness.Checking -> ChirpyCheckingSheet(onDismiss = onDismiss, modifier = modifier)

        is ModelReadiness.Downloading ->
            ChirpyDownloadingSheet(readiness = modelReadiness, onDismiss = onDismiss, modifier = modifier)

        is ModelReadiness.Available ->
            ChirpyChatSheet(
                state = state,
                onDraftChange = onDraftChange,
                onSubmit = onSubmit,
                onDismiss = onDismiss,
                onNavigateToPage = onNavigateToPage,
                modifier = modifier,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChirpyChatSheet(
    state: AIDocAssistantSessionState,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToPage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().imePadding().padding(16.dp)) {
            Text(
                text = stringResource(CoreRes.string.chirpy_assistant_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Message list
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(state.messages, key = { it.id }) { message ->
                    ChirpyMessageBubble(message = message, onNavigateToPage = onNavigateToPage)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (state.isLoading) {
                    item { ThinkingBubble() }
                }
            }

            // Input bar — matches messaging MessageInput style
            val canSend = state.draftQuestion.isNotBlank() && !state.isLoading
            val keyboardController = LocalSoftwareKeyboardController.current

            fun doSend() {
                if (canSend) {
                    onSubmit()
                    keyboardController?.hide()
                }
            }

            OutlinedTextField(
                value = state.draftQuestion,
                onValueChange = onDraftChange,
                placeholder = { Text(stringResource(CoreRes.string.chirpy_search_placeholder)) },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(INPUT_CORNER_PERCENT),
                keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { doSend() }),
                trailingIcon = {
                    IconButton(onClick = ::doSend, enabled = canSend) {
                        Icon(imageVector = MeshtasticIcons.Send, contentDescription = "Send")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

private const val PERCENT_MULTIPLIER = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChirpyCheckingSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(CoreRes.string.chirpy_checking), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChirpyDownloadingSheet(
    readiness: ModelReadiness.Downloading,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(CoreRes.drawable.img_chirpy),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(CoreRes.string.chirpy_downloading),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            val progress = readiness.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${(progress * PERCENT_MULTIPLIER).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(CoreRes.string.chirpy_downloading_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val AVATAR_SIZE = 24.dp
private val BUBBLE_CORNER = 8.dp
private val BUBBLE_BORDER_WIDTH = 0.5.dp
private const val INPUT_CORNER_PERCENT = 50f

/** User bubble shape: rounded everywhere except bottom-end (like MessageItem sender). */
private val UserBubbleShape =
    RoundedCornerShape(topStart = BUBBLE_CORNER, topEnd = BUBBLE_CORNER, bottomStart = BUBBLE_CORNER, bottomEnd = 0.dp)

/** Chirpy bubble shape: rounded everywhere except top-start (like MessageItem receiver). */
private val ChirpyBubbleShape =
    RoundedCornerShape(topStart = 0.dp, topEnd = BUBBLE_CORNER, bottomStart = BUBBLE_CORNER, bottomEnd = BUBBLE_CORNER)

@Composable
private fun ChirpyMessageBubble(
    message: ChirpyMessage,
    onNavigateToPage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == ChirpyRole.USER

    if (isUser) {
        UserBubble(message = message, modifier = modifier)
    } else {
        AssistantBubble(message = message, onNavigateToPage = onNavigateToPage, modifier = modifier)
    }
}

@Composable
private fun UserBubble(message: ChirpyMessage, modifier: Modifier = Modifier) {
    val bubbleColor = MaterialTheme.colorScheme.primaryContainer
    val borderColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth().padding(start = 48.dp), horizontalAlignment = Alignment.End) {
        Surface(
            shape = UserBubbleShape,
            color = bubbleColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(BUBBLE_BORDER_WIDTH, borderColor),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** Simplified NodeChip-style label shown above Chirpy's message bubbles. */
@Composable
private fun ChirpyChip(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(CHIRPY_CHIP_HEIGHT),
        shape = MaterialTheme.shapes.small,
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp).height(CHIRPY_CHIP_HEIGHT),
        ) {
            Image(
                painter = painterResource(CoreRes.drawable.img_chirpy),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Chirpy",
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private val CHIRPY_CHIP_HEIGHT = 28.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantBubble(message: ChirpyMessage, onNavigateToPage: (String) -> Unit, modifier: Modifier = Modifier) {
    val bubbleColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline

    Column(modifier = modifier.fillMaxWidth().padding(end = 48.dp), horizontalAlignment = Alignment.Start) {
        // NodeChip-style sender label above the bubble (like MessageItem)
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChirpyChip()
        }

        Surface(
            shape = ChirpyBubbleShape,
            color = bubbleColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(BUBBLE_BORDER_WIDTH, borderColor),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Markdown(content = message.text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        }

        if (message.sources.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp, start = 8.dp),
            ) {
                message.sources.forEach { source ->
                    SuggestionChip(
                        onClick = { onNavigateToPage(source.id) },
                        label = { Text(text = source.title, style = MaterialTheme.typography.labelSmall) },
                        colors =
                        SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        border = BorderStroke(BUBBLE_BORDER_WIDTH, MaterialTheme.colorScheme.outline),
                    )
                }
            }
        }
    }
}

/** Thinking bubble — shows while Chirpy generates a response, styled as an assistant bubble with pulsing alpha. */
@Composable
private fun ThinkingBubble(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha by
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec =
            infiniteRepeatable(animation = tween(durationMillis = 800), repeatMode = RepeatMode.Reverse),
            label = "thinkingAlpha",
        )

    val bubbleColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline

    Column(modifier = modifier.fillMaxWidth().padding(end = 48.dp), horizontalAlignment = Alignment.Start) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChirpyChip()
        }

        Surface(
            shape = ChirpyBubbleShape,
            color = bubbleColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(BUBBLE_BORDER_WIDTH, borderColor),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                text = stringResource(CoreRes.string.chirpy_thinking),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).alpha(alpha),
            )
        }
    }
}
