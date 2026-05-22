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
package org.meshtastic.feature.car.panels

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.feature.car.model.CarSessionState

/**
 * Manages persistent mesh status state for the car display. Provides connection status, node count, and last message
 * time that can be rendered as a Minimized Control Panel or header info.
 */
@Single
class MeshStatusPanel {

    private val _state =
        MutableStateFlow(
            CarSessionState(
                connectionStatus = ConnectionState.Disconnected,
                onlineNodeCount = 0,
                lastMessageTime = null,
                activeEmergencies = emptyList(),
                meshName = null,
            ),
        )
    val state: StateFlow<CarSessionState> = _state.asStateFlow()

    fun updateConnectionStatus(status: ConnectionState) {
        _state.value = _state.value.copy(connectionStatus = status)
    }

    fun updateNodeCount(count: Int) {
        _state.value = _state.value.copy(onlineNodeCount = count)
    }

    fun updateLastMessageTime(time: Long) {
        _state.value = _state.value.copy(lastMessageTime = time)
    }

    fun updateMeshName(name: String?) {
        _state.value = _state.value.copy(meshName = name)
    }

    fun getStatusTitle(): String {
        val state = _state.value
        return when (state.connectionStatus) {
            ConnectionState.Connected -> "${state.onlineNodeCount} nodes online"
            ConnectionState.Connecting -> "Connecting..."
            else -> "Disconnected"
        }
    }

    fun getStatusSubtitle(): String? {
        val state = _state.value
        val lastMsg = state.lastMessageTime?.takeIf { it != 0L } ?: return null
        return "Last msg: ${DateFormatter.formatRelativeTime(lastMsg)}"
    }
}
