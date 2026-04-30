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
package org.meshtastic.core.service

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MeshPacket

/**
 * Platform-agnostic implementation of [ServiceRepository].
 *
 * Manages reactive state for connection status, error messages, mesh packets, and service actions using only
 * KMP-compatible primitives (StateFlow, SharedFlow, Channel, Kermit Logger). This implementation can be used directly
 * on any KMP target — Android extends it with AIDL binding via [AndroidServiceRepository].
 */
@Suppress("TooManyFunctions")
open class ServiceRepositoryImpl : ServiceRepository {

    // Canonical app-level connection state — written exclusively by MeshConnectionManager.
    private val _connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState>
        get() = _connectionState

    override fun setConnectionState(connectionState: ConnectionState) {
        _connectionState.value = connectionState
    }

    private val _clientNotification = MutableStateFlow<ClientNotification?>(null)
    override val clientNotification: StateFlow<ClientNotification?>
        get() = _clientNotification

    override fun setClientNotification(notification: ClientNotification?) {
        notification?.message?.let { Logger.w { it } }
        _clientNotification.value = notification
    }

    override fun clearClientNotification() {
        _clientNotification.value = null
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?>
        get() = _errorMessage

    override fun setErrorMessage(text: String, severity: Severity) {
        Logger.log(severity, "ServiceRepository", null, text)
        _errorMessage.value = text
    }

    override fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private val _connectionProgress = MutableStateFlow<String?>(null)
    override val connectionProgress: StateFlow<String?>
        get() = _connectionProgress

    override fun setConnectionProgress(text: String) {
        if (connectionState.value != ConnectionState.Connected) {
            _connectionProgress.value = text
        }
    }

    private val _meshPacketFlow = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    override val meshPacketFlow: SharedFlow<MeshPacket>
        get() = _meshPacketFlow

    override suspend fun emitMeshPacket(packet: MeshPacket) {
        _meshPacketFlow.emit(packet)
    }

    private val _tracerouteResponse = MutableStateFlow<TracerouteResponse?>(null)
    override val tracerouteResponse: StateFlow<TracerouteResponse?>
        get() = _tracerouteResponse

    override fun setTracerouteResponse(value: TracerouteResponse?) {
        _tracerouteResponse.value = value
    }

    override fun clearTracerouteResponse() {
        setTracerouteResponse(null)
    }

    private val _neighborInfoResponse = MutableStateFlow<String?>(null)
    override val neighborInfoResponse: StateFlow<String?>
        get() = _neighborInfoResponse

    override fun setNeighborInfoResponse(value: String?) {
        _neighborInfoResponse.value = value
    }

    override fun clearNeighborInfoResponse() {
        setNeighborInfoResponse(null)
    }

    private val _serviceAction = Channel<ServiceAction>()
    override val serviceAction: Flow<ServiceAction> = _serviceAction.receiveAsFlow()

    override suspend fun onServiceAction(action: ServiceAction) {
        _serviceAction.send(action)
    }
}
