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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.ui.icon.KeyboardArrowDown
import org.meshtastic.core.ui.icon.KeyboardArrowLeft
import org.meshtastic.core.ui.icon.KeyboardArrowRight
import org.meshtastic.core.ui.icon.KeyboardArrowUp
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.proto.DisplayInfo

// M3 comfortable target for remote-control keys (48dp minimum + breathing room).
private val DPAD_KEY_SIZE = 56.dp
private val DPAD_GAP = 8.dp

// OS-typematic-style auto-repeat, slowed to what a LoRa radio UI can render.
private const val REPEAT_INITIAL_DELAY_MS = 500L
private const val REPEAT_INTERVAL_MS = 100L

// Swipes on the mirror shorter than this are ignored as accidental.
private val SWIPE_THRESHOLD = 48.dp

// Firmware input_broker_event codes (src/input/InputBroker.h).
private const val INPUT_SELECT = 10
private const val INPUT_SELECT_LONG = 11
private const val INPUT_UP = 17
private const val INPUT_DOWN = 18
private const val INPUT_LEFT = 19
private const val INPUT_RIGHT = 20
private const val INPUT_BACK = 27

@KoinViewModel
class DisplayMirrorViewModel(
    displayMirrorManager: DisplayMirrorManager,
    connectionStateProvider: ConnectionStateProvider,
    nodeRepository: NodeRepository,
    private val adminController: AdminController,
) : ViewModel() {

    val frame: StateFlow<MirrorFrame?> = displayMirrorManager.frame

    val palette: StateFlow<MirrorPalette?> = displayMirrorManager.palette

    val connected: StateFlow<Boolean> =
        connectionStateProvider.connectionState
            .map { it is ConnectionState.Connected }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Panel description from the connect handshake; null on display-less nodes and pre-DisplayInfo firmware. */
    val displayInfo: StateFlow<DisplayInfo?> =
        nodeRepository.ourNodeInfo.map { it?.metadata?.display }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _mirroring = MutableStateFlow(false)
    val mirroring: StateFlow<Boolean> = _mirroring.asStateFlow()

    init {
        // The device forgets the (non-persisted) mirror setting on disconnect/reboot;
        // mirror the reset locally so the toggle never claims a dead stream is live.
        viewModelScope.launch { connected.collect { if (!it) _mirroring.value = false } }
    }

    fun setMirror(enabled: Boolean) {
        adminController.setDisplayMirror(enabled)
        _mirroring.value = enabled
    }

    fun requestFrame() = adminController.requestDisplayFrame()

    fun sendKey(eventCode: Int) = adminController.sendInputEvent(eventCode)

    /** Stops a live stream when the mirror UI goes away; safe to call redundantly. */
    fun stopMirroring() {
        if (_mirroring.value) setMirror(false)
    }

    override fun onCleared() = stopMirroring()
}

/** PoC live view of the connected device's screen, with remote D-pad control. Strings are deliberately unlocalized. */
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
            Text(text = if (mirroring) "Mirroring" else "Mirror off", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = viewModel::requestFrame, enabled = connected) { Text("Refresh") }
            if (currentFrame != null) {
                Text(
                    text = "${currentFrame.width}x${currentFrame.height}  frame #${currentFrame.frameId}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        if (displayInfo?.panel_class == DisplayInfo.PanelClass.EINK) {
            Text(
                text = "E-ink display: refresh is slow — prefer one-shot Refresh over continuous mirroring.",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        when {
            !connected -> Text(text = "Not connected to a device.")
            currentFrame != null -> MirrorSurface(currentFrame, palette, onEvent = viewModel::sendKey)
            else -> Text(text = "No frame received yet — enable mirroring or tap Refresh.")
        }

        DpadCluster(enabled = connected, onEvent = viewModel::sendKey)
    }
}

/**
 * The live mirror plus its direct input affordances: click to focus (keyboard capture — arrows, Enter/Space = OK,
 * Esc/Backspace = Back), swipe in a cardinal direction for a single direction event.
 */
@Composable
private fun MirrorSurface(
    frame: MirrorFrame,
    palette: MirrorPalette?,
    onEvent: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val focusColor = MaterialTheme.colorScheme.primary

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
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    keyToInputEvent(event.key)?.let {
                        onEvent(it)
                        true
                    } ?: false
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { focusRequester.requestFocus() }) }
                .swipeToDirection(onEvent)
                .border(width = 2.dp, color = if (focused) focusColor else Color.Transparent),
        ) {
            MirrorFrameImage(frame, palette)
        }
        Text(
            text =
            if (focused) {
                "Keyboard active: arrows navigate · Enter selects · Esc goes back"
            } else {
                "Click the screen for keyboard control, or swipe it to navigate"
            },
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun keyToInputEvent(key: Key): Int? = when (key) {
    Key.DirectionUp -> INPUT_UP

    Key.DirectionDown -> INPUT_DOWN

    Key.DirectionLeft -> INPUT_LEFT

    Key.DirectionRight -> INPUT_RIGHT

    Key.Enter,
    Key.NumPadEnter,
    Key.Spacebar,
    -> INPUT_SELECT

    Key.Escape,
    Key.Backspace,
    -> INPUT_BACK

    else -> null
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

/**
 * Cross-shaped 5-way cluster with Back below-left, following the TV-remote convention: directions auto-repeat on hold
 * (500ms delay, then 10Hz — slow enough for the radio to render), OK long-press sends the firmware's SELECT_LONG.
 */
@Composable
private fun DpadCluster(enabled: Boolean, onEvent: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DPAD_GAP),
    ) {
        RepeatingKey(MeshtasticIcons.KeyboardArrowUp, "Up", enabled) { onEvent(INPUT_UP) }
        Row(horizontalArrangement = Arrangement.spacedBy(DPAD_GAP)) {
            RepeatingKey(MeshtasticIcons.KeyboardArrowLeft, "Left", enabled) { onEvent(INPUT_LEFT) }
            OkKey(
                enabled = enabled,
                onSelect = { onEvent(INPUT_SELECT) },
                onSelectLong = { onEvent(INPUT_SELECT_LONG) },
            )
            RepeatingKey(MeshtasticIcons.KeyboardArrowRight, "Right", enabled) { onEvent(INPUT_RIGHT) }
        }
        RepeatingKey(MeshtasticIcons.KeyboardArrowDown, "Down", enabled) { onEvent(INPUT_DOWN) }
        Row(modifier = Modifier.widthIn(min = DPAD_KEY_SIZE * 3 + DPAD_GAP * 2)) {
            BackKey(enabled = enabled) { onEvent(INPUT_BACK) }
        }
    }
}

/** A direction key: fires on press, then auto-repeats while held. */
@Composable
private fun RepeatingKey(icon: ImageVector, label: String, enabled: Boolean, onEvent: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val background =
        when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            pressed -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier.size(DPAD_KEY_SIZE).clip(CircleShape).background(background).pointerInput(enabled) {
            if (!enabled) return@pointerInput
            coroutineScope {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onEvent()
                        val repeater = launch {
                            delay(REPEAT_INITIAL_DELAY_MS)
                            while (isActive) {
                                onEvent()
                                delay(REPEAT_INTERVAL_MS)
                            }
                        }
                        tryAwaitRelease()
                        repeater.cancel()
                        pressed = false
                    },
                )
            }
        },
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = contentColorFor(enabled))
    }
}

/** Center OK: tap selects, long-press sends SELECT_LONG. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OkKey(enabled: Boolean, onSelect: () -> Unit, onSelectLong: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier.size(DPAD_KEY_SIZE)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            )
            .combinedClickable(enabled = enabled, onLongClick = onSelectLong, onClick = onSelect),
    ) {
        Text(
            text = "OK",
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BackKey(enabled: Boolean, onBack: () -> Unit) {
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
            .combinedClickable(enabled = enabled, onClick = onBack),
    ) {
        Text(text = "Back", style = MaterialTheme.typography.labelMedium, color = contentColorFor(enabled))
    }
}

@Composable
private fun contentColorFor(enabled: Boolean): Color =
    if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
