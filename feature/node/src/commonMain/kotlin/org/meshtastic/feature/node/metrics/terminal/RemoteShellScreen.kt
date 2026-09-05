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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
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
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    // The remote PTY wraps to whatever size we declare, so measure the viewport in monospace cells rather than
    // shipping a hardcoded 80x24. Opening waits for the measurement so OPEN carries the real size.
    var terminalSize by remember { mutableStateOf(IntSize.Zero) }
    val (cols, rows) = rememberTerminalGrid(terminalSize)

    val measured = cols > 0 && rows > 0
    LaunchedEffect(cols, rows) { if (measured) viewModel.resize(cols, rows) }

    // Keyed on whether we have a measurement, not on its value: the IME resizes the viewport, and reopening the
    // session every time the keyboard moves would churn a session per keystroke burst.
    LaunchedEffect(measured) { if (measured) viewModel.openSession() }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Let the composition settle before taking focus, or the request is dropped.
        delay(FOCUS_REQUEST_DELAY_MS)
        focusRequester.requestFocus()
    }

    val listState = rememberLazyListState()
    LaunchedEffect(outputLines.size, pendingInput) { listState.animateScrollToItem(outputLines.size) }

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
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding()) {
            SessionStatusBar(state = sessionState, onReconnect = { viewModel.openSession() })

            Box(modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { terminalSize = it }) {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier =
                        Modifier.fillMaxSize().padding(TERMINAL_PADDING).clickable {
                            focusRequester.requestFocus()
                        },
                    ) {
                        // The caret belongs after the prompt, not under it, so the trailing line carries the pending
                        // input.
                        items(outputLines.dropLast(1)) { line -> TerminalLine(text = line) }
                        item { TerminalLine(text = outputLines.lastOrNull().orEmpty() + pendingInput + CURSOR) }
                    }
                }

                KeyboardSink(
                    focusRequester = focusRequester,
                    onChar = { viewModel.dispatchTypedChar(it) },
                    onEnter = viewModel::typeEnter,
                    onBackspace = viewModel::typeBackspace,
                    onTab = { viewModel.typeKey('\t') },
                )
            }

            ControlKeyBar(onSend = { viewModel.typeControlSequence(it) })
        }
    }
}

/**
 * Zero-size field that holds keyboard focus so both hardware keys and the soft keyboard reach the session.
 *
 * The value is state-backed and never cleared outright: Compose hands back the field's whole content, and a reset only
 * lands on the next recomposition, so a callback arriving first would re-deliver characters already sent. We track what
 * we consumed and forward the delta, mapping a shrinking field to backspaces.
 */
@Composable
private fun KeyboardSink(
    focusRequester: FocusRequester,
    onChar: (Char) -> Unit,
    onEnter: () -> Unit,
    onBackspace: () -> Unit,
    onTab: () -> Unit,
) {
    var sinkText by remember { mutableStateOf("") }
    BasicTextField(
        value = sinkText,
        onValueChange = { newText ->
            val consumed = sinkText
            if (newText.length < consumed.length) {
                repeat(consumed.length - newText.length) { onBackspace() }
            } else {
                val fresh = if (newText.startsWith(consumed)) newText.substring(consumed.length) else newText
                fresh.forEach(onChar)
            }
            sinkText = if (newText.length > SINK_TRIM_LENGTH) "" else newText
        },
        modifier =
        Modifier.size(1.dp).focusRequester(focusRequester).onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.key) {
                Key.Enter,
                Key.NumPadEnter,
                -> {
                    onEnter()
                    true
                }

                Key.Tab -> {
                    onTab()
                    true
                }

                Key.Backspace -> {
                    onBackspace()
                    true
                }

                else -> false
            }
        },
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        cursorBrush = SolidColor(Color.Transparent),
    )
}

/** Routes one typed character, keeping control bytes out of the stream the PTY sees as text. */
private fun RemoteShellViewModel.dispatchTypedChar(char: Char) {
    when {
        char == '\n' || char == '\r' -> typeEnter()
        char == '\b' -> typeBackspace()
        char == '\t' -> typeKey('\t')
        char.isISOControl() -> Unit
        else -> typeKey(char)
    }
}

/** Reports what the session is actually doing; without it a refused or stalled session is just a blank screen. */
@Composable
private fun SessionStatusBar(state: RemoteShellViewModel.SessionState, onReconnect: () -> Unit) {
    if (state == RemoteShellViewModel.SessionState.OPEN) return
    val label =
        when (state) {
            RemoteShellViewModel.SessionState.IDLE -> "Not connected"
            RemoteShellViewModel.SessionState.OPENING -> "Opening session\u2026"
            RemoteShellViewModel.SessionState.CLOSING -> "Closing\u2026"
            RemoteShellViewModel.SessionState.CLOSED -> "Session closed"
            RemoteShellViewModel.SessionState.ERROR -> "Session failed"
            RemoteShellViewModel.SessionState.OPEN -> return
        }
    val reconnectable =
        state == RemoteShellViewModel.SessionState.CLOSED ||
            state == RemoteShellViewModel.SessionState.ERROR ||
            state == RemoteShellViewModel.SessionState.IDLE
    Row(
        modifier =
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = TERMINAL_PADDING, vertical = STATUS_BAR_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (reconnectable) {
            TextButton(onClick = onReconnect) { Text(text = "Reconnect") }
        }
    }
}

/** Keys a soft keyboard has no room for and a shell cannot do without. */
@Composable
private fun ControlKeyBar(onSend: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TERMINAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(CONTROL_KEY_SPACING),
    ) {
        CONTROL_KEYS.forEach { (label, sequence) ->
            TextButton(onClick = { onSend(sequence) }) { Text(text = label, fontFamily = FontFamily.Monospace) }
        }
    }
}

private val CONTROL_KEYS =
    listOf(
        "^C" to "\u0003",
        "^D" to "\u0004",
        "TAB" to "\t",
        "ESC" to "\u001b",
        "\u2191" to "\u001b[A",
        "\u2193" to "\u001b[B",
    )

/** Viewport size in monospace cells, so the remote PTY can be told how wide to wrap. */
@Composable
private fun rememberTerminalGrid(size: IntSize): Pair<Int, Int> {
    val textMeasurer = rememberTextMeasurer()
    val cell =
        remember(textMeasurer) {
            textMeasurer.measure("0", TextStyle(fontFamily = FontFamily.Monospace, fontSize = TERMINAL_FONT_SIZE)).size
        }
    if (cell.width <= 0 || cell.height <= 0) return 0 to 0
    return (size.width / cell.width) to (size.height / cell.height)
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

private val STATUS_BAR_PADDING = 4.dp

private val CONTROL_KEY_SPACING = 4.dp

private val TERMINAL_FONT_SIZE = 13.sp

/** Delay before the initial focus request, in milliseconds. */
@Suppress("MagicNumber")
private const val FOCUS_REQUEST_DELAY_MS = 100L
