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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.remote_shell
import org.meshtastic.core.resources.remote_shell_command_hint
import org.meshtastic.core.resources.remote_shell_last_contact
import org.meshtastic.core.resources.remote_shell_menu_line_mode
import org.meshtastic.core.resources.remote_shell_menu_paste
import org.meshtastic.core.resources.remote_shell_menu_text_larger
import org.meshtastic.core.resources.remote_shell_menu_text_smaller
import org.meshtastic.core.resources.remote_shell_more_options
import org.meshtastic.core.resources.remote_shell_reconnect
import org.meshtastic.core.resources.remote_shell_send
import org.meshtastic.core.resources.remote_shell_status_closed
import org.meshtastic.core.resources.remote_shell_status_closing
import org.meshtastic.core.resources.remote_shell_status_failed
import org.meshtastic.core.resources.remote_shell_status_not_connected
import org.meshtastic.core.resources.remote_shell_status_opening
import org.meshtastic.core.resources.remote_shell_subtitle_round_trip
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.Add
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.More
import org.meshtastic.core.ui.icon.Remove
import org.meshtastic.core.ui.icon.Send
import org.meshtastic.core.ui.input.RemoteKey
import org.meshtastic.core.ui.input.RemoteKeyHandler
import org.meshtastic.core.ui.input.RemoteKeyboardSink
import org.meshtastic.core.ui.util.plainText
import org.meshtastic.feature.node.metrics.terminal.RemoteShellViewModel.InputMode
import org.meshtastic.feature.node.metrics.terminal.RemoteShellViewModel.SessionState

private val TERMINAL_PADDING = 8.dp
private val BAR_PADDING = 4.dp
private val CHIP_SPACING = 8.dp

/** Let the composition settle before taking focus, or the request is dropped. */
private const val FOCUS_REQUEST_DELAY_MS = 100L

/** Inbound silence, with something of ours in flight, before the screen says the node has gone quiet. */
private const val STALE_AFTER_MS = 4_000L
private const val STALE_POLL_MS = 1_000L
private const val MS_PER_SECOND = 1_000L
private const val RECENT_HISTORY_CHIPS = 5

/**
 * Terminal screen for a DMShell session.
 *
 * In character mode a zero-size field holds keyboard focus, so hardware keys and the soft keyboard both reach the
 * session; what was sent shows underlined at the cursor until the node echoes it. In line mode a composer replaces it
 * and each command goes out as one frame. The extra-keys rows serve both.
 */
@Suppress("LongMethod")
@Composable
fun RemoteShellScreen(viewModel: RemoteShellViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val modifiers by viewModel.modifiers.collectAsStateWithLifecycle()
    val inputMode by viewModel.inputMode.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSizeSp.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    // The remote PTY wraps to whatever size we declare, so measure the viewport in monospace cells rather than
    // shipping a hardcoded 80x24. Opening waits for the measurement so OPEN carries the real size.
    var terminalSize by remember { mutableStateOf(IntSize.Zero) }
    val (cols, rows) = rememberTerminalGrid(terminalSize, fontSize)
    val grid by rememberUpdatedState(cols to rows)
    val measuredGrid = remember { snapshotFlow { grid }.filter { (c, r) -> c > 0 && r > 0 }.distinctUntilChanged() }
    LaunchedEffect(Unit) { measuredGrid.collect { (c, r) -> viewModel.resize(c, r) } }

    // Opens once, on the first measurement: the IME resizes the viewport, and reopening the session every time the
    // keyboard moves would churn a session per keystroke burst.
    LaunchedEffect(Unit) {
        measuredGrid.first()
        viewModel.openSession()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(inputMode) {
        if (inputMode == InputMode.CHARACTER) {
            delay(FOCUS_REQUEST_DELAY_MS)
            focusRequester.requestFocus()
        }
    }

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { viewModel.bell.collect { haptics.performHapticFeedback(HapticFeedbackType.LongPress) } }

    val roundTrip = health.roundTripMs
    val subtitle =
        if (roundTrip != null && sessionState == SessionState.OPEN) {
            stringResource(
                Res.string.remote_shell_subtitle_round_trip,
                stringResource(Res.string.remote_shell),
                roundTrip,
            )
        } else {
            stringResource(Res.string.remote_shell)
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = viewModel.nodeLongName,
                subtitle = subtitle,
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    ShellMenu(
                        inputMode = inputMode,
                        onToggleLineMode = {
                            viewModel.setInputMode(
                                if (inputMode == InputMode.LINE) InputMode.CHARACTER else InputMode.LINE,
                            )
                        },
                        onPaste = viewModel::paste,
                        onFontSize = viewModel::adjustFontSize,
                    )
                },
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding()) {
            SessionStatusBar(state = sessionState, onReconnect = viewModel::openSession)

            Box(modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { terminalSize = it }) {
                TerminalPane(
                    screen = screen,
                    fontSizeSp = fontSize,
                    showCursor = inputMode == InputMode.CHARACTER,
                    onTap = { if (inputMode == InputMode.CHARACTER) focusRequester.requestFocus() },
                )
                if (inputMode == InputMode.CHARACTER) {
                    RemoteKeyboardSink(
                        focusRequester = focusRequester,
                        handler =
                        RemoteKeyHandler(
                            onText = { text -> text.forEach(viewModel::typeKey) },
                            onEnter = viewModel::typeEnter,
                            onBackspace = viewModel::typeBackspace,
                            onKey = viewModel::sendKey,
                            onChord = viewModel::typeChord,
                        ),
                    )
                }
                if (sessionState == SessionState.OPEN) {
                    LastContactBanner(health, modifier = Modifier.align(Alignment.TopCenter))
                }
            }

            if (inputMode == InputMode.LINE) {
                CommandComposer(
                    composer = composer,
                    history = history,
                    masked = screen.secretPrompt,
                    actions =
                    ComposerActions(
                        onChange = viewModel::setComposer,
                        onInsert = viewModel::insertCommand,
                        onSubmit = viewModel::submitComposer,
                        onRecall = viewModel::recallHistory,
                    ),
                )
            }

            // A tapped key cap can take keyboard focus, after which typing would reach nothing; give it straight back.
            val refocus = { if (inputMode == InputMode.CHARACTER) focusRequester.requestFocus() }
            ExtraKeysBar(
                modifiers = modifiers,
                onKey = { key ->
                    when {
                        inputMode == InputMode.LINE && key == RemoteKey.UP -> viewModel.recallHistory(older = true)
                        inputMode == InputMode.LINE && key == RemoteKey.DOWN -> viewModel.recallHistory(older = false)
                        else -> viewModel.sendKey(key)
                    }
                    refocus()
                },
                onChar = { c ->
                    if (inputMode == InputMode.LINE && modifiers.isEmpty) {
                        viewModel.setComposer(composer + c)
                    } else {
                        viewModel.typeKey(c)
                    }
                    refocus()
                },
                onToggleCtrl = {
                    viewModel.toggleCtrl()
                    refocus()
                },
                onToggleAlt = {
                    viewModel.toggleAlt()
                    refocus()
                },
            )
        }
    }
}

@Composable
private fun TerminalPane(screen: TerminalScreenState, fontSizeSp: Int, showCursor: Boolean, onTap: () -> Unit) {
    val palette = TerminalPalette.from(MaterialTheme.colorScheme)
    val listState = rememberLazyListState()

    // Follow new output only while the reader is at the bottom; scrolling up to read must not be yanked back.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) -> if (scrolling) follow = !canScrollForward }
    }
    LaunchedEffect(screen) { if (follow && screen.lines.isNotEmpty()) listState.scrollToItem(screen.lines.lastIndex) }

    val textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSizeSp.sp, color = palette.foreground)
    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(TERMINAL_PADDING).clickable(onClick = onTap),
        ) {
            val last = screen.lines.lastIndex
            itemsIndexed(screen.lines) { index, line ->
                val text =
                    if (index == last) {
                        line.withCursor(
                            cursorColumn = screen.cursorColumn,
                            predicted = screen.predicted,
                            unsent = if (screen.typingVisible) screen.unsent else "",
                            showCursor = showCursor,
                            palette = palette,
                        )
                    } else {
                        line.toAnnotatedString(palette)
                    }
                Text(text = text, style = textStyle, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** What the line-mode composer can do, grouped so the composable takes state and one handler. */
private class ComposerActions(
    val onChange: (String) -> Unit,
    val onInsert: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onRecall: (older: Boolean) -> Unit,
)

@Composable
private fun CommandComposer(composer: String, history: List<String>, masked: Boolean, actions: ComposerActions) {
    val chips = remember(history) { (history.take(RECENT_HISTORY_CHIPS) + QUICK_COMMANDS).distinct() }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING),
            contentPadding = PaddingValues(horizontal = TERMINAL_PADDING),
        ) {
            items(chips) { command ->
                AssistChip(
                    onClick = { actions.onInsert(command) },
                    label = { Text(command, fontFamily = FontFamily.Monospace, maxLines = 1) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = TERMINAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = composer,
                onValueChange = actions.onChange,
                modifier = Modifier.weight(1f).onPreviewKeyEvent { event -> composerKey(event, actions) },
                placeholder = { Text(stringResource(Res.string.remote_shell_command_hint)) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                singleLine = true,
                // At a password prompt the answer must not be readable, nor offered to the keyboard's suggestions.
                visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions =
                KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = if (masked) KeyboardType.Password else KeyboardType.Text,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { actions.onSubmit() }),
            )
            IconButton(onClick = actions.onSubmit) {
                Icon(MeshtasticIcons.Send, contentDescription = stringResource(Res.string.remote_shell_send))
            }
        }
    }
}

/** Up and Down walk local history, Enter sends; everything else is ordinary text editing. */
private fun composerKey(event: KeyEvent, actions: ComposerActions): Boolean {
    val handled = event.type == KeyEventType.KeyDown && event.key in COMPOSER_KEYS
    if (handled) {
        when (event.key) {
            Key.DirectionUp -> actions.onRecall(true)
            Key.DirectionDown -> actions.onRecall(false)
            else -> actions.onSubmit()
        }
    }
    return handled
}

private val COMPOSER_KEYS = setOf(Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.NumPadEnter)

@Composable
private fun ShellMenu(
    inputMode: InputMode,
    onToggleLineMode: () -> Unit,
    onPaste: (String) -> Unit,
    onFontSize: (deltaSp: Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(MeshtasticIcons.More, contentDescription = stringResource(Res.string.remote_shell_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.remote_shell_menu_line_mode)) },
                trailingIcon = { Checkbox(checked = inputMode == InputMode.LINE, onCheckedChange = null) },
                onClick = {
                    onToggleLineMode()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.remote_shell_menu_paste)) },
                onClick = {
                    expanded = false
                    scope.launch { clipboard.getClipEntry()?.plainText()?.let(onPaste) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.remote_shell_menu_text_larger)) },
                leadingIcon = { Icon(MeshtasticIcons.Add, contentDescription = null) },
                onClick = { onFontSize(1) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.remote_shell_menu_text_smaller)) },
                leadingIcon = { Icon(MeshtasticIcons.Remove, contentDescription = null) },
                onClick = { onFontSize(-1) },
            )
        }
    }
}

/** Reports what the session is doing; without it a refused or stalled session is just a blank screen. */
@Composable
private fun SessionStatusBar(state: SessionState, onReconnect: () -> Unit) {
    val label =
        when (state) {
            SessionState.OPEN -> return
            SessionState.IDLE -> Res.string.remote_shell_status_not_connected
            SessionState.OPENING -> Res.string.remote_shell_status_opening
            SessionState.CLOSING -> Res.string.remote_shell_status_closing
            SessionState.CLOSED -> Res.string.remote_shell_status_closed
            SessionState.ERROR -> Res.string.remote_shell_status_failed
        }
    val reconnectable = state == SessionState.CLOSED || state == SessionState.ERROR || state == SessionState.IDLE
    StatusRow(text = stringResource(label)) {
        if (reconnectable) TextButton(onClick = onReconnect) { Text(stringResource(Res.string.remote_shell_reconnect)) }
    }
}

/**
 * Mosh's "last contact" line, shown once a frame of ours has gone unacknowledged for [STALE_AFTER_MS], so a slow mesh
 * reads as slow rather than broken. It overlays the terminal instead of taking a row: a banner that resized the
 * viewport would resize the remote PTY every time it came and went.
 */
@Composable
private fun LastContactBanner(health: LinkHealth, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(nowMillis) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(STALE_POLL_MS)
            now = nowMillis
        }
    }
    val waitingSince = health.waitingSinceMs ?: return
    if (now - waitingSince < STALE_AFTER_MS) return
    val silentSeconds = ((now - health.lastInboundMs) / MS_PER_SECOND).toInt()
    StatusRow(text = stringResource(Res.string.remote_shell_last_contact, silentSeconds), modifier = modifier)
}

@Composable
private fun StatusRow(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = TERMINAL_PADDING, vertical = BAR_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Viewport size in monospace cells, so the remote PTY can be told how wide to wrap. */
@Composable
private fun rememberTerminalGrid(size: IntSize, fontSizeSp: Int): Pair<Int, Int> {
    val textMeasurer = rememberTextMeasurer()
    val cell =
        remember(textMeasurer, fontSizeSp) {
            textMeasurer.measure("0", TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSizeSp.sp)).size
        }
    if (cell.width <= 0 || cell.height <= 0) return 0 to 0
    return (size.width / cell.width) to (size.height / cell.height)
}
