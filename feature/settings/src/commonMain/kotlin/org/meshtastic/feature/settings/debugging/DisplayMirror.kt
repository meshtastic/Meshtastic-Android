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
package org.meshtastic.feature.settings.debugging

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.repository.MirrorFormat
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mirror_active
import org.meshtastic.core.resources.mirror_back
import org.meshtastic.core.resources.mirror_eink_hint
import org.meshtastic.core.resources.mirror_frame_info
import org.meshtastic.core.resources.mirror_hint_keyboard_active
import org.meshtastic.core.resources.mirror_hint_tap_keyboard
import org.meshtastic.core.resources.mirror_hint_tap_touch
import org.meshtastic.core.resources.mirror_keyboard
import org.meshtastic.core.resources.mirror_no_frame
import org.meshtastic.core.resources.mirror_not_connected
import org.meshtastic.core.resources.mirror_off
import org.meshtastic.core.resources.mirror_view_only
import org.meshtastic.core.resources.refresh
import org.meshtastic.proto.DisplayInfo

// M3 comfortable target for remote-control keys (48dp minimum + breathing room).
private val DPAD_KEY_SIZE = 56.dp

// OS-typematic-style auto-repeat, slowed to what a LoRa radio UI can render.
internal const val REPEAT_INITIAL_DELAY_MS = 500L
internal const val REPEAT_INTERVAL_MS = 100L

// Swipes on the mirror shorter than this are ignored as accidental.
private val SWIPE_THRESHOLD = 48.dp

// Mirror + D-pad fit comfortably side by side above this content width.
private val SIDE_BY_SIDE_MIN_WIDTH = 760.dp

/** Live view of the connected device's screen, with remote D-pad, keyboard, and touch control. */
@Composable
fun DisplayMirrorContent(modifier: Modifier = Modifier, viewModel: DisplayMirrorViewModel = koinViewModel()) {
    val frame by viewModel.frame.collectAsStateWithLifecycle()
    val palette by viewModel.palette.collectAsStateWithLifecycle()
    val mirroring by viewModel.mirroring.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val displayInfo by viewModel.displayInfo.collectAsStateWithLifecycle()

    // Don't leave the device streaming to a hidden tab or abandoned screen.
    DisposableEffect(Unit) { onDispose { viewModel.stopMirroring() } }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val currentFrame = frame
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = mirroring, onCheckedChange = viewModel::setMirror, enabled = connected)
            Text(
                text = stringResource(if (mirroring) Res.string.mirror_active else Res.string.mirror_off),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(onClick = viewModel::requestFrame, enabled = connected) {
                Text(stringResource(Res.string.refresh))
            }
            if (currentFrame != null) {
                Text(
                    text =
                    stringResource(
                        Res.string.mirror_frame_info,
                        currentFrame.width,
                        currentFrame.height,
                        currentFrame.frameId,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        if (displayInfo?.panel_class == DisplayInfo.PanelClass.EINK) {
            Text(text = stringResource(Res.string.mirror_eink_hint), style = MaterialTheme.typography.labelSmall)
        }

        when {
            !connected -> {
                Text(text = stringResource(Res.string.mirror_not_connected))
                DpadCluster(enabled = false, onEvent = viewModel::sendKey)
            }

            currentFrame == null -> {
                Text(text = stringResource(Res.string.mirror_no_frame))
                DpadCluster(enabled = connected, onEvent = viewModel::sendKey)
            }

            else ->
                MirrorWithControls(
                    currentFrame,
                    palette,
                    enabled = connected,
                    // MUI (RGB565) devices read their own input drivers, not the
                    // InputBroker remote events inject into — mirror-only for now.
                    inputSupported = currentFrame.format != MirrorFormat.RGB565,
                    hasTouch = displayInfo?.has_touch == true,
                    onEvent = viewModel::sendKey,
                    onTouch = viewModel::sendTouch,
                )
        }
    }
}

/** Controls sit beside the mirror when the window is wide enough, below it otherwise. */
@Suppress("LongParameterList")
@Composable
private fun MirrorWithControls(
    frame: MirrorFrame,
    palette: MirrorPalette?,
    enabled: Boolean,
    inputSupported: Boolean,
    hasTouch: Boolean,
    onEvent: (Int) -> Unit,
    onTouch: (Int, Int, Int) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= SIDE_BY_SIDE_MIN_WIDTH && inputSupported) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MirrorSurface(frame, palette, hasTouch, onEvent, onTouch, modifier = Modifier.weight(1f, fill = false))
                DpadCluster(enabled = enabled, onEvent = onEvent)
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (inputSupported) {
                    MirrorSurface(frame, palette, hasTouch, onEvent, onTouch)
                    DpadCluster(enabled = enabled, onEvent = onEvent)
                } else {
                    ViewOnlyMirror(frame, palette)
                }
            }
        }
    }
}

/** Mirror without any input affordances, for device UIs that do not accept remote input yet. */
@Composable
private fun ViewOnlyMirror(frame: MirrorFrame, palette: MirrorPalette?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MirrorFrameImage(frame, palette)
        Text(text = stringResource(Res.string.mirror_view_only), style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * The live mirror plus its direct input affordances: a Keyboard chip toggles key capture (arrows, Enter/Space = OK,
 * Esc/Backspace = Back), swiping in a cardinal direction sends one direction event, and on touch-capable devices
 * tapping or long-pressing the image forwards real touch coordinates; elsewhere a tap toggles capture.
 */
@Composable
@Suppress("LongParameterList")
private fun MirrorSurface(
    frame: MirrorFrame,
    palette: MirrorPalette?,
    hasTouch: Boolean,
    onEvent: (Int) -> Unit,
    onTouch: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val currentFrame by rememberUpdatedState(frame)
    val focusColor = MaterialTheme.colorScheme.primary

    fun toggleKeyboard() = if (focused) focusManager.clearFocus() else focusRequester.requestFocus()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
            Modifier.focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onPreviewKeyEvent { event ->
                    val mapped = keyToInputEvent(event.key) ?: return@onPreviewKeyEvent false
                    // Consume KeyUp of mapped keys too, or Space/arrows leak to the scroll container.
                    if (event.type == KeyEventType.KeyDown) onEvent(mapped)
                    true
                }
                .pointerInput(hasTouch) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (hasTouch) {
                                onTouch(
                                    INPUT_USER_PRESS,
                                    offset.toDeviceX(size.width, currentFrame),
                                    offset.toDeviceY(size.height, currentFrame),
                                )
                            } else {
                                // Tap toggles keyboard control so there is always a way out of capture.
                                toggleKeyboard()
                            }
                        },
                        onLongPress = { offset ->
                            // Physical touch drivers map a long-press to SELECT with coordinates.
                            if (hasTouch) {
                                onTouch(
                                    INPUT_SELECT,
                                    offset.toDeviceX(size.width, currentFrame),
                                    offset.toDeviceY(size.height, currentFrame),
                                )
                            }
                        },
                    )
                }
                .swipeToDirection(onEvent)
                .border(width = 2.dp, color = if (focused) focusColor else Color.Transparent),
        ) {
            MirrorFrameImage(frame, palette)
        }
        MirrorControlHints(focused = focused, hasTouch = hasTouch, onToggleKeyboard = { toggleKeyboard() })
    }
}

@Composable
private fun MirrorControlHints(focused: Boolean, hasTouch: Boolean, onToggleKeyboard: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = focused,
            onClick = onToggleKeyboard,
            label = { Text(stringResource(Res.string.mirror_keyboard)) },
        )
        Text(
            text =
            stringResource(
                when {
                    focused -> Res.string.mirror_hint_keyboard_active
                    hasTouch -> Res.string.mirror_hint_tap_touch
                    else -> Res.string.mirror_hint_tap_keyboard
                },
            ),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Converts a completed drag into one direction event along its dominant axis, ignoring short accidental swipes. */
private fun Modifier.swipeToDirection(onEvent: (Int) -> Unit): Modifier = pointerInput(Unit) {
    val threshold = SWIPE_THRESHOLD.toPx()
    var drag = Offset.Zero
    detectDragGestures(
        onDragStart = { drag = Offset.Zero },
        onDrag = { change, amount ->
            change.consume()
            drag += amount
        },
        onDragEnd = {
            val horizontal = drag.x
            val vertical = drag.y
            when {
                kotlin.math.abs(horizontal) < threshold && kotlin.math.abs(vertical) < threshold -> Unit

                kotlin.math.abs(horizontal) >= kotlin.math.abs(vertical) ->
                    onEvent(if (horizontal > 0) INPUT_RIGHT else INPUT_LEFT)

                else -> onEvent(if (vertical > 0) INPUT_DOWN else INPUT_UP)
            }
        },
    )
}

/** The remote cluster: circular 5-way ring (see [DpadRing]) with Back below-left, per TV-remote convention. */
@Composable
private fun DpadCluster(enabled: Boolean, onEvent: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DpadRing(
            enabled = enabled,
            onEvent = onEvent,
            onSelect = { onEvent(INPUT_SELECT) },
            onSelectLong = { onEvent(INPUT_SELECT_LONG) },
        )
        BackKey(enabled = enabled) { onEvent(INPUT_BACK) }
    }
}

@Composable
private fun BackKey(enabled: Boolean, onBack: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier.size(DPAD_KEY_SIZE)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .combinedClickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onBack()
            },
    ) {
        Text(
            text = stringResource(Res.string.mirror_back),
            style = MaterialTheme.typography.labelMedium,
            color = contentColorFor(enabled),
        )
    }
}
