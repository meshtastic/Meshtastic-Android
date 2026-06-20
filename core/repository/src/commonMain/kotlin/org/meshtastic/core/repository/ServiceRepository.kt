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
import org.meshtastic.core.model.ConnectionState
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
interface ServiceRepository :
    ConnectionStateProvider,
    TracerouteResponseProvider,
    NeighborInfoResponseProvider,
    ServiceStateWriter {
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
    override val connectionState: StateFlow<ConnectionState>

    /**
     * Updates the canonical app-level connection state.
     *
     * **This should only be called by [MeshConnectionManager].** Direct mutation from other components would bypass the
     * transport-to-app reconciliation logic and create state inconsistencies.
     *
     * @param connectionState The new [ConnectionState].
     */
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
    override fun setConnectionProgress(text: String)

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

    companion object {
        /**
         * The cross-module contract for the WiFi/TCP handshake-watchdog recovery progress signal.
         *
         * [MeshConnectionManager] writes this exact literal to [connectionProgress] immediately before its recovery
         * sibling transitions the transport to [ConnectionState.Disconnected], and `ConnectionsViewModel` (and its
         * tests) compare incoming progress against it to surface `RECONNECTING` UI state instead of a final-feeling
         * `NOT_CONNECTED`. The literal text and the U+2026 HORIZONTAL ELLIPSIS character MUST match exactly across both
         * writers and readers — centralizing it here removes the prior cross-module string-contract hazard.
         */
        const val RECONNECTING_PROGRESS_TEXT = "Reconnecting\u2026"
    }
}
