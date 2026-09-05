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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.DisplayInfo

@KoinViewModel
class DisplayMirrorViewModel(
    private val displayMirrorManager: DisplayMirrorManager,
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
        // A fresh mirror UI must never show a previous device's final screen as if live.
        displayMirrorManager.reset()
        // The device forgets the (non-persisted) mirror setting on disconnect/reboot;
        // mirror the reset locally so the toggle never claims a dead stream is live.
        viewModelScope.launch {
            connected.collect {
                if (!it) {
                    _mirroring.value = false
                    displayMirrorManager.reset()
                }
            }
        }
    }

    fun setMirror(enabled: Boolean) {
        adminController.setDisplayMirror(enabled)
        _mirroring.value = enabled
    }

    fun requestFrame() = adminController.requestDisplayFrame()

    fun sendKey(eventCode: Int) = adminController.sendInputEvent(eventCode)

    /** Forwards a typed character; the device UI receives it as a key press rather than a navigation event. */
    fun sendChar(codePoint: Int) = adminController.sendInputEvent(INPUT_ANYKEY, kbChar = codePoint)

    /** Forwards a tap/long-press on the mirrored image as a device touch event with panel coordinates. */
    fun sendTouch(eventCode: Int, x: Int, y: Int) = adminController.sendInputEvent(eventCode, touchX = x, touchY = y)

    /** Stops a live stream when the mirror UI goes away; safe to call redundantly. */
    fun stopMirroring() {
        if (_mirroring.value) setMirror(false)
    }

    override fun onCleared() = stopMirroring()
}
