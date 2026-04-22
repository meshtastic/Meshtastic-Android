/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_terminal
import org.meshtastic.core.resources.phosphor_colour
import org.meshtastic.core.resources.remote_shell
import org.meshtastic.core.ui.component.MainAppBar

/** Cursor blink period in milliseconds. */
@Suppress("MagicNumber")
private const val CURSOR_BLINK_MS = 530L

/**
 * Full retro-CRT terminal screen for the RemoteShell feature (portnum = 13).
 *
 * ### Input model
 * Input is **raw / streaming** — there is no visible text field. A zero-size [BasicTextField] holds keyboard focus and
 * is the sole entry point for both hardware key events and the Android soft keyboard.
 * - Each printable character is routed to [RemoteShellViewModel.typeKey].
 * - Enter / newline is routed to [RemoteShellViewModel.typeEnter] (immediate flush).
 * - Backspace is routed to [RemoteShellViewModel.typeBackspace].
 * - The ViewModel batches keystrokes and flushes over the mesh after a 50 ms debounce or when the 64-byte buffer fills.
 *
 * A small "tap to type" button at the bottom re-acquires focus when the soft keyboard is dismissed.
 *
 * ### Pending input rendering
 * Unflushed characters from [RemoteShellViewModel.pendingInput] are passed to [TerminalCanvas] and drawn inline after
 * the last confirmed output line using [PhosphorPreset.dim], giving immediate visual feedback without implying the
 * bytes have been transmitted.
 *
 * @param viewModel [RemoteShellViewModel] for this destination node.
 * @param onNavigateUp Callback invoked when the user presses the navigation-up button.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun RemoteShellScreen(viewModel: RemoteShellViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val outputLines by viewModel.outputLines.collectAsStateWithLifecycle()
    val pendingInput by viewModel.pendingInput.collectAsStateWithLifecycle()
    val phosphor by viewModel.phosphor.collectAsStateWithLifecycle()

    // Cursor blink
    var showCursor by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CURSOR_BLINK_MS)
            showCursor = !showCursor
        }
    }

    // Open session on composition entry
    LaunchedEffect(Unit) { viewModel.openSession() }

    // Flicker animation
    val flickerAlpha by rememberFlickerAlpha()

    // Phosphor picker dropdown state
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Focus management for the hidden input sink
    val focusRequester = remember { FocusRequester() }

    // Request focus when the screen first appears
    LaunchedEffect(Unit) {
        // Small delay to let the composition settle before requesting focus
        delay(FOCUS_REQUEST_DELAY_MS)
        focusRequester.requestFocus()
    }

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
                actions = {
                    Box {
                        IconButton(onClick = { dropdownExpanded = true }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_terminal),
                                contentDescription = stringResource(Res.string.phosphor_colour),
                                tint = phosphor.fg,
                            )
                        }
                        DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                            PhosphorPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = {
                                        Text(text = preset.name, color = preset.fg, fontFamily = FontFamily.Monospace)
                                    },
                                    onClick = {
                                        viewModel.setPhosphor(preset)
                                        dropdownExpanded = false
                                    },
                                    modifier = Modifier.background(preset.bg),
                                )
                            }
                        }
                    }
                },
                onClickChip = {},
            )
        },
        containerColor = phosphor.bg,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding()) {
            // --- Terminal surface ---
            Box(
                modifier =
                Modifier.fillMaxSize()
                    .crtCurvature()
                    // Tapping anywhere on the terminal re-acquires keyboard focus
                    .clickable { focusRequester.requestFocus() },
            ) {
                TerminalCanvas(
                    lines = outputLines,
                    pendingInput = pendingInput,
                    preset = phosphor,
                    flickerAlpha = flickerAlpha,
                    showCursor = showCursor,
                    modifier = Modifier.fillMaxSize(),
                )
                ScanlinesOverlay(preset = phosphor, flickerAlpha = flickerAlpha, modifier = Modifier.fillMaxSize())
            }

            // --- Hidden keyboard sink ---
            // Zero-size BasicTextField that holds focus so both hardware and soft keyboard input
            // is captured.  onKeyEvent handles hardware keys (including backspace and enter);
            // onValueChange handles soft-keyboard input where key events may not fire.
            BasicTextField(
                value = "",
                onValueChange = { newText ->
                    // Soft keyboard delivers text via onValueChange.  Since we always reset to ""
                    // the entire newText string is fresh input.
                    newText.forEach { char ->
                        when {
                            char == '\n' || char == '\r' -> viewModel.typeEnter()
                            char == '\b' -> viewModel.typeBackspace()
                            char == '\t' -> viewModel.typeKey('\t') // tab: immediate flush in VM
                            char.isISOControl() -> Unit // ignore other control chars
                            else -> viewModel.typeKey(char)
                        }
                    }
                },
                modifier =
                Modifier.size(1.dp) // zero visible footprint
                    .align(Alignment.BottomStart)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
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
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent, fontSize = 1.sp),
                cursorBrush = SolidColor(Color.Transparent),
            )
        }
    }
}

/** Delay before the initial focus request, in milliseconds. */
@Suppress("MagicNumber")
private const val FOCUS_REQUEST_DELAY_MS = 100L
