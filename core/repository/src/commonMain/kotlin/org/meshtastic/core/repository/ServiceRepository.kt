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
package org.meshtastic.core.repository

import co.touchlab.kermit.Severity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MeshPacket

/**
 * Interface for managing service state, connection status, and mesh events.
 */
interface ServiceRepository {
    /** Reactive connection state. */
    val connectionState: StateFlow<ConnectionState>
    
    /** Sets the connection state. */
    fun setConnectionState(connectionState: ConnectionState)

    /** Reactive client notification. */
    val clientNotification: StateFlow<ClientNotification?>
    
    /** Sets the current client notification. */
    fun setClientNotification(notification: ClientNotification?)
    
    /** Clears the current client notification. */
    fun clearClientNotification()

    /** Reactive error message. */
    val errorMessage: StateFlow<String?>
    
    /** Sets an error message to be displayed. */
    fun setErrorMessage(text: String, severity: Severity = Severity.Error)
    
    /** Clears the current error message. */
    fun clearErrorMessage()

    /** Reactive connection progress message. */
    val connectionProgress: StateFlow<String?>
    
    /** Sets the connection progress message. */
    fun setConnectionProgress(text: String)

    /** Flow of all mesh packets. */
    val meshPacketFlow: SharedFlow<MeshPacket>
    
    /** Emits a mesh packet into the flow. */
    suspend fun emitMeshPacket(packet: MeshPacket)

    /** Reactive traceroute response. */
    val tracerouteResponse: StateFlow<TracerouteResponse?>
    
    /** Sets the traceroute response. */
    fun setTracerouteResponse(value: TracerouteResponse?)
    
    /** Clears the traceroute response. */
    fun clearTracerouteResponse()

    /** Reactive neighbor info response. */
    val neighborInfoResponse: StateFlow<String?>
    
    /** Sets the neighbor info response. */
    fun setNeighborInfoResponse(value: String?)
    
    /** Clears the neighbor info response. */
    fun clearNeighborInfoResponse()

    /** Flow of service actions requested by the UI. */
    val serviceAction: Flow<ServiceAction>
    
    /** Dispatches a service action. */
    suspend fun onServiceAction(action: ServiceAction)
}
