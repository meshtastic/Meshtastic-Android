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
package org.meshtastic.core.testing

import co.touchlab.kermit.Severity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MeshPacket

@Suppress("TooManyFunctions")
class FakeServiceRepository : ServiceRepository {
    /** Canonical app-level connection state — the single source of truth for UI/feature tests. */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    override fun setConnectionState(connectionState: ConnectionState) {
        _connectionState.value = connectionState
    }

    private val _clientNotification = MutableStateFlow<ClientNotification?>(null)
    override val clientNotification: StateFlow<ClientNotification?> = _clientNotification

    override fun setClientNotification(notification: ClientNotification?) {
        _clientNotification.value = notification
    }

    override fun clearClientNotification() {
        _clientNotification.value = null
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage

    override fun setErrorMessage(text: String, severity: Severity) {
        _errorMessage.value = text
    }

    override fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private val _connectionProgress = MutableStateFlow<String?>(null)
    override val connectionProgress: StateFlow<String?> = _connectionProgress

    override fun setConnectionProgress(text: String) {
        _connectionProgress.value = text
    }

    private val _meshPacketFlow = MutableSharedFlow<MeshPacket>()
    override val meshPacketFlow: Flow<MeshPacket> = _meshPacketFlow.asFlow()

    override suspend fun emitMeshPacket(packet: MeshPacket) {
        _meshPacketFlow.emit(packet)
    }

    private val _tracerouteResponse = MutableStateFlow<TracerouteResponse?>(null)
    override val tracerouteResponse: StateFlow<TracerouteResponse?> = _tracerouteResponse

    override fun setTracerouteResponse(value: TracerouteResponse?) {
        _tracerouteResponse.value = value
    }

    override fun clearTracerouteResponse() {
        _tracerouteResponse.value = null
    }

    private val _neighborInfoResponse = MutableStateFlow<String?>(null)
    override val neighborInfoResponse: StateFlow<String?> = _neighborInfoResponse

    override fun setNeighborInfoResponse(value: String?) {
        _neighborInfoResponse.value = value
    }

    override fun clearNeighborInfoResponse() {
        _neighborInfoResponse.value = null
    }

    private val _serviceAction = MutableSharedFlow<ServiceAction>(replay = 1)
    override val serviceAction: Flow<ServiceAction> = _serviceAction

    override suspend fun onServiceAction(action: ServiceAction) {
        _serviceAction.emit(action)
    }
}
