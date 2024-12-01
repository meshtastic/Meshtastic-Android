/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.service

import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.android.Logging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository class for managing the [IMeshService] instance and connection state
 */
@Singleton
class ServiceRepository @Inject constructor() : Logging {
    var meshService: IMeshService? = null
        private set

    fun setMeshService(service: IMeshService?) {
        meshService = service
    }

    // Connection state to our radio device
    private val _connectionState = MutableStateFlow(MeshService.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MeshService.ConnectionState> get() = _connectionState

    fun setConnectionState(connectionState: MeshService.ConnectionState) {
        _connectionState.value = connectionState
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> get() = _errorMessage

    fun setErrorMessage(text: String) {
        errormsg(text)
        _errorMessage.value = text
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> get() = _statusMessage

    fun setStatusMessage(text: String) {
        if (connectionState.value != MeshService.ConnectionState.CONNECTED) {
            _statusMessage.value = text
        }
    }

    private val _meshPacketFlow = MutableSharedFlow<MeshPacket>()
    val meshPacketFlow: SharedFlow<MeshPacket> get() = _meshPacketFlow

    suspend fun emitMeshPacket(packet: MeshPacket) {
        _meshPacketFlow.emit(packet)
    }

    private val _tracerouteResponse = MutableStateFlow<String?>(null)
    val tracerouteResponse: StateFlow<String?> get() = _tracerouteResponse

    fun setTracerouteResponse(value: String?) {
        _tracerouteResponse.value = value
    }

    fun clearTracerouteResponse() {
        setTracerouteResponse(null)
    }

    private val _serviceAction = MutableSharedFlow<ServiceAction>()
    val serviceAction: SharedFlow<ServiceAction> get() = _serviceAction

    suspend fun onServiceAction(action: ServiceAction) {
        _serviceAction.emit(action)
    }
}
