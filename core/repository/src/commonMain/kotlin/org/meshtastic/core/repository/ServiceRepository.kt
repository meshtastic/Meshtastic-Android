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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MeshPacket

/**
 * Interface for managing background service state, connection status, and mesh events.
 *
 * This repository acts as the primary data bridge between the long-running mesh service and the UI/Feature layers. It
 * maintains reactive flows for connection status, error messages, and incoming mesh traffic.
 *
 * **Connection state contract:** [connectionState] is the **canonical, app-level** connection state that all UI,
 * feature modules, and ViewModels should observe. It incorporates handshake progress, light-sleep policy, and transport
 * reconciliation — unlike [RadioInterfaceService.connectionState], which only reflects the raw hardware link status.
 * The [MeshConnectionManager] is the sole writer of this state; it bridges [RadioInterfaceService.connectionState]
 * changes into app-level transitions via [setConnectionState].
 *
 * @see RadioInterfaceService.connectionState
 */
@Suppress("TooManyFunctions")
interface ServiceRepository {
    /**
     * Canonical app-level connection state.
     *
     * This is the **single source of truth** for connection status across the entire application. All UI components,
     * feature modules, and ViewModels should observe this flow — never [RadioInterfaceService.connectionState].
     *
     * State transitions are managed exclusively by [MeshConnectionManager], which reconciles transport-level events
     * with handshake progress and device sleep policy:
     * - [ConnectionState.Disconnected] — no active connection to a radio
     * - [ConnectionState.Connecting] — transport is up, mesh handshake (config + node-info) in progress
     * - [ConnectionState.Connected] — handshake complete, radio fully operational
     * - [ConnectionState.DeviceSleep] — radio entered light-sleep (transient disconnect)
     *
     * @see RadioInterfaceService.connectionState
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Updates the canonical app-level connection state.
     *
     * **This should only be called by [MeshConnectionManager].** Direct mutation from other components would bypass the
     * transport-to-app reconciliation logic and create state inconsistencies.
     *
     * @param connectionState The new [ConnectionState].
     */
    fun setConnectionState(connectionState: ConnectionState)

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
    fun setClientNotification(notification: ClientNotification?)

    /** Clears the current client notification. */
    fun clearClientNotification()

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
    fun setErrorMessage(text: String, severity: Severity = Severity.Error)

    /** Clears the current error message. */
    fun clearErrorMessage()

    /**
     * Reactive flow of connection progress messages.
     *
     * Used during the handshake and config loading phase to provide status updates to the user.
     */
    val connectionProgress: StateFlow<String?>

    /**
     * Sets the connection progress message.
     *
     * @param text The progress description (e.g., "Downloading Node DB...").
     */
    fun setConnectionProgress(text: String)

    /**
     * Flow of all raw [MeshPacket] objects received from the mesh.
     *
     * Subscribing to this flow allows components to react to any incoming traffic.
     */
    val meshPacketFlow: SharedFlow<MeshPacket>

    /**
     * Emits a mesh packet into the flow.
     *
     * Called by the packet processor when new data arrives from the radio.
     *
     * @param packet The received [MeshPacket].
     */
    suspend fun emitMeshPacket(packet: MeshPacket)

    /** Reactive flow of the most recent traceroute result. */
    val tracerouteResponse: StateFlow<TracerouteResponse?>

    /**
     * Sets the traceroute response.
     *
     * @param value The [TracerouteResponse] result.
     */
    fun setTracerouteResponse(value: TracerouteResponse?)

    /** Clears the current traceroute response. */
    fun clearTracerouteResponse()

    /** Reactive flow of the most recent neighbor info response (formatted string). */
    val neighborInfoResponse: StateFlow<String?>

    /**
     * Sets the neighbor info response.
     *
     * @param value The human-readable neighbor info string.
     */
    fun setNeighborInfoResponse(value: String?)

    /** Clears the current neighbor info response. */
    fun clearNeighborInfoResponse()

    /** Flow of service actions requested by the UI (e.g., "Favorite Node", "Mute Node"). */
    val serviceAction: Flow<ServiceAction>

    /**
     * Dispatches a service action to be handled by the background service.
     *
     * @param action The [ServiceAction] to perform.
     */
    suspend fun onServiceAction(action: ServiceAction)
}
