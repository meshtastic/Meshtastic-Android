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
package org.meshtastic.core.repository

import co.touchlab.kermit.Severity
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MeshPacket

/**
 * Write-side interface for service state mutations.
 *
 * Only background handlers, managers, and the service layer should inject this interface. UI/ViewModel code should
 * never write service state directly — it observes via [ConnectionStateProvider], [TracerouteResponseProvider], or
 * [NeighborInfoResponseProvider].
 */
interface ServiceStateWriter {
    /** Updates the canonical app-level connection state. Only [MeshConnectionManager] should call this. */
    fun setConnectionState(connectionState: ConnectionState)

    /** Sets the current client notification. */
    fun setClientNotification(notification: ClientNotification?)

    /** Clears the current client notification. */
    fun clearClientNotification()

    /** Sets an error message to be displayed. */
    fun setErrorMessage(text: String, severity: Severity = Severity.Error)

    /** Clears the current error message. */
    fun clearErrorMessage()

    /** Sets the connection progress message. */
    fun setConnectionProgress(text: String)

    /** Emits a mesh packet into the shared flow. */
    suspend fun emitMeshPacket(packet: MeshPacket)

    /** Sets the traceroute response. */
    fun setTracerouteResponse(value: TracerouteResponse?)

    /** Sets the neighbor info response. */
    fun setNeighborInfoResponse(value: String?)
}
