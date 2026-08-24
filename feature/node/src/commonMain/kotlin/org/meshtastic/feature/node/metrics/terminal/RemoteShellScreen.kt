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
package org.meshtastic.feature.node.metrics.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.remote_shell
import org.meshtastic.core.ui.component.MainAppBar

/**
 * Terminal screen for the RemoteShell feature (portnum = 13).
 *
 * ### Input model
 * Input is **raw / streaming** — there is no visible text field. A zero-size [BasicTextField] holds keyboard focus and
 * is the sole entry point for both hardware key events and the Android soft keyboard.
 * - Each printable character is routed to [RemoteShellViewModel.typeKey].
 * - Enter / newline is routed to [RemoteShellViewModel.typeEnter] (immediate flush).
 * - Backspace is routed to [RemoteShellViewModel.typeBackspace].
 * - The ViewModel batches keystrokes and flushes over the mesh after a debounce or when the 64-byte buffer fills.
 *
 * Tapping the output area re-acquires keyboard focus when the soft keyboard is dismissed.
 *
 * Unflushed characters from [RemoteShellViewModel.pendingInput] are drawn after the last confirmed output line, dimmed,
 * so they read as local echo rather than transmitted bytes.
 *
 * @param viewModel [RemoteShellViewModel] for this destination node.
 * @param onNavigateUp Callback invoked when the user presses the navigation-up button.
 */
@Composable
fun RemoteShellScreen(viewModel: RemoteShellViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val outputLines by viewModel.outputLines.collectAsStateWithLifecycle()
    val pendingInput by viewModel.pendingInput.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.openSession() }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Let the composition settle before taking focus, or the request is dropped.
        delay(FOCUS_REQUEST_DELAY_MS)
        focusRequester.requestFocus()
    }

    // The sink's value must be state-backed. With a constant "" the field's internal buffer is not cleared
    // synchronously, so a callback that lands first re-delivers characters we already consumed. Forward only the delta.
    var sinkText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val lastIndex = outputLines.size // pending-input row sits one past the output
    LaunchedEffect(lastIndex, pendingInput) { if (lastIndex >= 0) listState.animateScrollToItem(lastIndex) }

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = viewModel.nodeLongName,
                subtitle = stringResource(Res.string.remote_shell),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(TERMINAL_PADDING).clickable { focusRequester.requestFocus() },
            ) {
                items(outputLines) { line -> TerminalLine(text = line) }
                item { TerminalLine(text = pendingInput + CURSOR, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            // Zero-size sink that holds focus so both hardware and soft-keyboard input is captured.
            // onKeyEvent covers hardware keys; onValueChange covers soft keyboards, which may fire neither.
            BasicTextField(
                value = sinkText,
                onValueChange = { newText ->
                    val consumed = sinkText
                    if (newText.length < consumed.length) {
                        repeat(consumed.length - newText.length) { viewModel.typeBackspace() }
                    } else {
                        val fresh = if (newText.startsWith(consumed)) newText.substring(consumed.length) else newText
                        fresh.forEach { char ->
                            when {
                                char == '\n' || char == '\r' -> viewModel.typeEnter()
                                char == '\b' -> viewModel.typeBackspace()
                                char == '\t' -> viewModel.typeKey('\t')
                                char.isISOControl() -> Unit
                                else -> viewModel.typeKey(char)
                            }
                        }
                    }
                    sinkText = if (newText.length > SINK_TRIM_LENGTH) "" else newText
                },
                modifier =
                Modifier.size(
                    1.dp,
                ).align(Alignment.BottomStart).focusRequester(focusRequester).onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        -> {
                            viewModel.typeEnter()
                            true
                        }

                        Key.Tab -> {
                            viewModel.typeKey('\t')
                            true
                        }

                        Key.Backspace -> {
                            viewModel.typeBackspace()
                            true
                        }

                        else -> false
                    }
                },
                textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                cursorBrush = SolidColor(Color.Transparent),
            )
        }
    }
}

@Composable
private fun TerminalLine(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = TERMINAL_FONT_SIZE,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Reset the invisible sink past this length so it does not accumulate a whole session of keystrokes. */
private const val SINK_TRIM_LENGTH = 256

/** Block drawn after the pending input to mark the caret position. */
private const val CURSOR = "█"

private val TERMINAL_PADDING = 8.dp

private val TERMINAL_FONT_SIZE = 13.sp

/** Delay before the initial focus request, in milliseconds. */
@Suppress("MagicNumber")
private const val FOCUS_REQUEST_DELAY_MS = 100L
