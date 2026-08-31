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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.RadioController

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
    private val radioController: RadioController,
) : ViewModel() {

    val frame: StateFlow<MirrorFrame?> = displayMirrorManager.frame

    private val _mirroring = MutableStateFlow(false)
    val mirroring: StateFlow<Boolean> = _mirroring.asStateFlow()

    fun setMirror(enabled: Boolean) {
        viewModelScope.launch {
            radioController.setDisplayMirror(enabled)
            _mirroring.value = enabled
        }
    }

    fun sendKey(eventCode: Int) {
        viewModelScope.launch { radioController.sendInputEvent(eventCode) }
    }
}

/** PoC live view of the connected device's screen, with remote D-pad control. Strings are deliberately unlocalized. */
@Composable
fun DisplayMirrorContent(modifier: Modifier = Modifier, viewModel: DisplayMirrorViewModel = koinViewModel()) {
    val frame by viewModel.frame.collectAsStateWithLifecycle()
    val mirroring by viewModel.mirroring.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = mirroring, onCheckedChange = viewModel::setMirror)
            Text(text = if (mirroring) "Mirroring" else "Mirror off", style = MaterialTheme.typography.titleMedium)
        }

        val currentFrame = frame
        if (currentFrame != null) {
            MirrorFrameCanvas(currentFrame)
            Text(
                text = "${currentFrame.width}x${currentFrame.height}  frame #${currentFrame.frameId}",
                style = MaterialTheme.typography.labelSmall,
            )
        } else {
            Text(text = "No frame received yet — enable mirroring above.")
        }

        // Remote D-pad
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_UP) }) { Text("Up") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_LEFT) }) { Text("Left") }
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_SELECT) }) { Text("OK") }
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_RIGHT) }) { Text("Right") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_DOWN) }) { Text("Down") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.sendKey(INPUT_BACK) }) { Text("Back") }
        }
    }
}

/** Draws a MONO_VLSB 1bpp framebuffer scaled to the available width, one filled rect per lit pixel. */
@Composable
private fun MirrorFrameCanvas(frame: MirrorFrame) {
    val aspect = frame.width.toFloat() / frame.height.toFloat()
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(aspect).background(Color.Black)) {
        val scale = size.width / frame.width
        val pixel = Size(scale, scale)
        for (y in 0 until frame.height) {
            val page = (y / 8) * frame.width
            val bit = 1 shl (y % 8)
            for (x in 0 until frame.width) {
                val index = page + x
                if (index < frame.pixels.size && frame.pixels[index].toInt() and bit != 0) {
                    drawRect(color = Color.White, topLeft = Offset(x * scale, y * scale), size = pixel)
                }
            }
        }
    }
}
