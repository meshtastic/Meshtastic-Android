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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MirrorFrame

// 4x scale for the common 128px-wide OLED; caps the image so the D-pad stays above the fold on desktop.
private val MAX_CANVAS_WIDTH = 512.dp

// MONO_VLSB packs 8 vertically adjacent pixels per byte (one "page" row).
private const val PIXELS_PER_PAGE = 8

// Firmware input_broker_event codes (src/input/InputBroker.h).
private const val INPUT_SELECT = 10
private const val INPUT_UP = 17
private const val INPUT_DOWN = 18
private const val INPUT_LEFT = 19
private const val INPUT_RIGHT = 20
private const val INPUT_BACK = 27

@KoinViewModel
class DisplayMirrorViewModel(
    displayMirrorManager: DisplayMirrorManager,
    connectionStateProvider: ConnectionStateProvider,
    private val adminController: AdminController,
) : ViewModel() {

    val frame: StateFlow<MirrorFrame?> = displayMirrorManager.frame

    val connected: StateFlow<Boolean> =
        connectionStateProvider.connectionState
            .map { it is ConnectionState.Connected }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
    val mirroring by viewModel.mirroring.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()

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

        when {
            !connected -> Text(text = "Not connected to a device.")
            currentFrame != null -> MirrorFrameImage(currentFrame)
            else -> Text(text = "No frame received yet — enable mirroring or tap Refresh.")
        }

        // Remote D-pad
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_UP) }, enabled = connected) { Text("Up") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_LEFT) }, enabled = connected) { Text("Left") }
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_SELECT) }, enabled = connected) { Text("OK") }
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_RIGHT) }, enabled = connected) { Text("Right") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_DOWN) }, enabled = connected) { Text("Down") }
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_BACK) }, enabled = connected) { Text("Back") }
        }
    }
}

/**
 * Renders a MONO_VLSB 1bpp framebuffer once per frame into a 1:1 [ImageBitmap] and scales it up with nearest-neighbor
 * filtering — crisp device pixels, no fractional-scale seams, one pixel walk per frame instead of per recomposition.
 */
@Composable
private fun MirrorFrameImage(frame: MirrorFrame, modifier: Modifier = Modifier) {
    val bitmap = remember(frame) { renderFrame(frame) }
    Image(
        bitmap = bitmap,
        contentDescription = "Device screen",
        modifier =
        modifier
            .widthIn(max = MAX_CANVAS_WIDTH)
            .fillMaxWidth()
            .aspectRatio(frame.width.toFloat() / frame.height.toFloat()),
        filterQuality = FilterQuality.None,
    )
}

private fun renderFrame(frame: MirrorFrame): ImageBitmap {
    val bitmap = ImageBitmap(frame.width, frame.height)
    val size = Size(frame.width.toFloat(), frame.height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
        drawRect(color = Color.Black)
        val pixel = Size(1f, 1f)
        for (y in 0 until frame.height) {
            val page = (y / PIXELS_PER_PAGE) * frame.width
            val bit = 1 shl (y % PIXELS_PER_PAGE)
            for (x in 0 until frame.width) {
                if (frame.pixels[page + x].toInt() and bit != 0) {
                    drawRect(color = Color.White, topLeft = Offset(x.toFloat(), y.toFloat()), size = pixel)
                }
            }
        }
    }
    return bitmap
}
