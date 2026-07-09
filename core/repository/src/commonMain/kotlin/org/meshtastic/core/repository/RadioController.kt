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

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.proto.ClientNotification

/**
 * Central interface for controlling the radio and mesh network.
 *
 * This is a composite interface that extends the focused sub-interfaces below. Feature modules that need the full
 * surface inject [RadioController]; modules that need only a subset can inject the narrower interface for better
 * testability and clearer dependency intent.
 *
 * **Sub-interfaces (mirrors SDK's layered API design):**
 * - [AdminController] — config, channels, owner, device lifecycle (→ SDK `AdminApi`)
 * - [MessagingController] — send packets, reactions, contacts (→ SDK `RadioClient.send*`)
 * - [NodeController] — favorite, ignore, mute, remove nodes (→ SDK `AdminApi` node ops)
 * - [QueryController] — telemetry, traceroute, position queries (→ SDK `TelemetryApi` / `RoutingApi`)
 *
 * When migrating to the SDK, each sub-interface becomes a thin adapter over the corresponding SDK API. The composite
 * [RadioController] can then be deprecated and consumers migrated to the narrower interfaces one at a time.
 */
interface RadioController :
    AdminController,
    MessagingController,
    NodeController,
    QueryController,
    ConnectionStateProvider {
    /**
     * Flow of notifications from the radio client.
     *
     * These represent high-level events like "Handshake completed" or "Channel configuration updated."
     */
    val clientNotification: StateFlow<ClientNotification?>

    /** Clears the current [clientNotification]. */
    fun clearClientNotification()

    /**
     * Generates a unique packet ID for a new request.
     *
     * @return A unique 32-bit integer.
     */
    fun generatePacketId(): Int

    /** Starts providing the phone's location to the mesh. */
    fun startProvideLocation()

    /** Stops providing the phone's location to the mesh. */
    fun stopProvideLocation()

    /**
     * Changes the device address (e.g., BLE MAC, IP address) we are communicating with.
     *
     * Suspends until the database has been switched, the in-memory node DB cleared, and the transport reconfigured.
     * Callers that depend on the device switch being effective before their next call (e.g. OTA disconnect-then-delay
     * sequences) can rely on this ordering.
     *
     * @param address The new device identifier.
     */
    suspend fun setDeviceAddress(address: String)

    /**
     * Requests that the next BLE connection invalidates Android's GATT service cache. Call before [setDeviceAddress]
     * when reconnecting after a firmware update.
     */
    fun requestGattCacheInvalidationOnNextConnect()
}
