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
package org.meshtastic.core.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MeshPacket
import javax.inject.Inject
import javax.inject.Singleton

data class TracerouteResponse(
    val message: String,
    val destinationNodeNum: Int,
    val requestId: Int,
    val forwardRoute: List<Int> = emptyList(),
    val returnRoute: List<Int> = emptyList(),
    val logUuid: String? = null,
) {
    val hasOverlay: Boolean
        get() = forwardRoute.isNotEmpty() || returnRoute.isNotEmpty()
}

/** Repository class for managing the [IMeshService] instance and connection state */
@Suppress("TooManyFunctions")
@Singleton
class ServiceRepository @Inject constructor() {
    var meshService: IMeshService? = null
        private set

    fun setMeshService(service: IMeshService?) {
        meshService = service
    }

    // Connection state to our radio device
    private val _connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState>
        get() = _connectionState

    fun setConnectionState(connectionState: ConnectionState) {
        _connectionState.value = connectionState
    }

    private val _clientNotification = MutableStateFlow<ClientNotification?>(null)
    val clientNotification: StateFlow<ClientNotification?>
        get() = _clientNotification

    fun setClientNotification(notification: ClientNotification?) {
        Logger.e { notification?.message.orEmpty() }

        _clientNotification.value = notification
    }

    fun clearClientNotification() {
        _clientNotification.value = null
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?>
        get() = _errorMessage

    fun setErrorMessage(text: String) {
        Logger.e { text }
        _errorMessage.value = text
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private val _connectionProgress = MutableStateFlow<String?>(null)
    val connectionProgress: StateFlow<String?>
        get() = _connectionProgress

    fun setConnectionProgress(text: String) {
        if (connectionState.value != ConnectionState.Connected) {
            _connectionProgress.value = text
        }
    }

    private val _meshPacketFlow = MutableSharedFlow<MeshPacket>()
    val meshPacketFlow: SharedFlow<MeshPacket>
        get() = _meshPacketFlow

    suspend fun emitMeshPacket(packet: MeshPacket) {
        _meshPacketFlow.emit(packet)
    }

    private val _tracerouteResponse = MutableStateFlow<TracerouteResponse?>(null)
    val tracerouteResponse: StateFlow<TracerouteResponse?>
        get() = _tracerouteResponse

    fun setTracerouteResponse(value: TracerouteResponse?) {
        _tracerouteResponse.value = value
    }

    fun clearTracerouteResponse() {
        setTracerouteResponse(null)
    }

    private val _neighborInfoResponse = MutableStateFlow<String?>(null)
    val neighborInfoResponse: StateFlow<String?>
        get() = _neighborInfoResponse

    fun setNeighborInfoResponse(value: String?) {
        _neighborInfoResponse.value = value
    }

    fun clearNeighborInfoResponse() {
        setNeighborInfoResponse(null)
    }

    private val _serviceAction = Channel<ServiceAction>()
    val serviceAction = _serviceAction.receiveAsFlow()

    suspend fun onServiceAction(action: ServiceAction) {
        _serviceAction.send(action)
    }
}
