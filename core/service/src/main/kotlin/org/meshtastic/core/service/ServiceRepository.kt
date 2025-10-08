/*
 * Copyright (c) 2025 Meshtastic LLC
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

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.MeshProtos.MeshPacket
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState>
        get() = _connectionState

    fun setConnectionState(connectionState: ConnectionState) {
        _connectionState.value = connectionState
    }

    // Current bluetooth link RSSI (dBm). Null if not connected or not a bluetooth interface.
    private val _bluetoothRssi = MutableStateFlow<Int?>(null)
    val bluetoothRssi: StateFlow<Int?>
        get() = _bluetoothRssi

    fun setBluetoothRssi(rssi: Int?) {
        _bluetoothRssi.value = rssi
    }

    private val _clientNotification = MutableStateFlow<MeshProtos.ClientNotification?>(null)
    val clientNotification: StateFlow<MeshProtos.ClientNotification?>
        get() = _clientNotification

    fun setClientNotification(notification: MeshProtos.ClientNotification?) {
        Timber.e(notification?.message.orEmpty())

        _clientNotification.value = notification
    }

    fun clearClientNotification() {
        _clientNotification.value = null
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?>
        get() = _errorMessage

    fun setErrorMessage(text: String) {
        Timber.e(text)
        _errorMessage.value = text
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?>
        get() = _statusMessage

    fun setStatusMessage(text: String) {
        if (connectionState.value != ConnectionState.CONNECTED) {
            _statusMessage.value = text
        }
    }

    private val _meshPacketFlow = MutableSharedFlow<MeshPacket>()
    val meshPacketFlow: SharedFlow<MeshPacket>
        get() = _meshPacketFlow

    suspend fun emitMeshPacket(packet: MeshPacket) {
        _meshPacketFlow.emit(packet)
    }

    private val _tracerouteResponse = MutableStateFlow<String?>(null)
    val tracerouteResponse: StateFlow<String?>
        get() = _tracerouteResponse

    fun setTracerouteResponse(value: String?) {
        _tracerouteResponse.value = value
    }

    fun clearTracerouteResponse() {
        setTracerouteResponse(null)
    }

    private val _serviceAction = Channel<ServiceAction>()
    val serviceAction = _serviceAction.receiveAsFlow()

    suspend fun onServiceAction(action: ServiceAction) {
        _serviceAction.send(action)
    }
}
