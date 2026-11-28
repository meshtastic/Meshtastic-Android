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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.model.service.LockdownTokenInfo
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MeshPacket
import org.meshtastic.sdk.ConnectionState

/**
 * Interface for managing background service state, connection status, and mesh events.
 *
 * This repository acts as the primary data bridge between the long-running mesh service and the UI/Feature layers. It
 * maintains reactive flows for connection status, error messages, and incoming mesh traffic.
 *
 * **Connection state contract:** [connectionState] is the selected radio's SDK [ConnectionState], which all UI, feature
 * modules, and ViewModels observe. [RadioInterfaceService] derives it from the SDK client's state, showing
 * [ConnectionState.Connected] only once the app holds the session's config and nodes.
 */
@Suppress("TooManyFunctions")
interface ServiceRepository :
    ConnectionStateProvider,
    TracerouteResponseProvider,
    NeighborInfoResponseProvider,
    ServiceStateWriter {
    /**
     * The selected radio's connection state, as its SDK client reports it: [ConnectionState.Connecting] and
     * [ConnectionState.Configuring] while a session comes up, [ConnectionState.Connected] once the app has installed
     * the session's snapshot, [ConnectionState.Reconnecting] and [ConnectionState.DeviceSleeping] while the client
     * retries, [ConnectionState.Failed] once it stops, and [ConnectionState.Disconnecting] then
     * [ConnectionState.Disconnected] when the user disconnects.
     */
    override val connectionState: StateFlow<ConnectionState>

    /** Publishes the connection state. Only the radio service writes it. */
    override fun setConnectionState(connectionState: ConnectionState)

    /**
     * Reactive flow of high-level client notifications.
     *
     * These represent events from the mesh client that may require UI feedback.
     */
    val clientNotification: StateFlow<ClientNotification?>

    /**
     * Sets the current client notification.
     *
     * @param notification The [ClientNotification] to display or act upon.
     */
    override fun setClientNotification(notification: ClientNotification?)

    /** Clears the current client notification. */
    override fun clearClientNotification()

    /**
     * Reactive flow of human-readable error messages.
     *
     * These are typically shown as snackbars or dialogs in the UI.
     */
    val errorMessage: StateFlow<String?>

    /**
     * Sets an error message to be displayed.
     *
     * @param text The error message text.
     * @param severity The [Severity] level of the error.
     */
    override fun setErrorMessage(text: String, severity: Severity)

    /** Clears the current error message. */
    override fun clearErrorMessage()

    /**
     * Flow of all raw [MeshPacket] objects received from the mesh.
     *
     * Subscribing to this flow allows components to react to any incoming traffic. The underlying implementation may be
     * backed by a hot shared flow, but this API intentionally exposes only the [Flow] interface. That implementation
     * detail is hidden via [kotlinx.coroutines.flow.SharedFlow.asFlow] (kotlinx.coroutines 1.11+).
     */
    val meshPacketFlow: Flow<MeshPacket>

    /**
     * Emits a mesh packet into the flow.
     *
     * Called by the packet processor when new data arrives from the radio.
     *
     * @param packet The received [MeshPacket].
     */
    override suspend fun emitMeshPacket(packet: MeshPacket)

    /** Reactive flow of the most recent traceroute result. */
    override val tracerouteResponse: StateFlow<TracerouteResponse?>

    /**
     * Sets the traceroute response.
     *
     * @param value The [TracerouteResponse] result.
     */
    override fun setTracerouteResponse(value: TracerouteResponse?)

    /** Clears the current traceroute response. */
    override fun clearTracerouteResponse()

    /** Reactive flow of the most recent neighbor info response (formatted string). */
    override val neighborInfoResponse: StateFlow<String?>

    /**
     * Sets the neighbor info response.
     *
     * @param value The human-readable neighbor info string.
     */
    override fun setNeighborInfoResponse(value: String?)

    /** Clears the current neighbor info response. */
    override fun clearNeighborInfoResponse()

    /** Reactive flow of the current lockdown authentication state. */
    val lockdownState: StateFlow<LockdownState>

    /** Updates the lockdown state. */
    fun setLockdownState(state: LockdownState)

    /** Resets lockdown state to [LockdownState.None]. */
    fun clearLockdownState()

    /** Reactive flow of the most recent lockdown session token info. */
    val lockdownTokenInfo: StateFlow<LockdownTokenInfo?>

    /** Sets the lockdown token info from a successful UNLOCKED status. */
    fun setLockdownTokenInfo(info: LockdownTokenInfo?)

    /** True once the passphrase was accepted for the current BLE connection. */
    val sessionAuthorized: StateFlow<Boolean>

    /** Updates the session authorization flag. */
    fun setSessionAuthorized(authorized: Boolean)
}
