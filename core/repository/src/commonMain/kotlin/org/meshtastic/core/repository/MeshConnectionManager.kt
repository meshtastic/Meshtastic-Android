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

import org.meshtastic.proto.Telemetry

/** Interface for managing the connection lifecycle and status with the mesh radio. */
interface MeshConnectionManager {
    /** Called when the radio configuration has been fully loaded. */
    fun onRadioConfigLoaded()

    /** Initiates the configuration synchronization stage. */
    fun startConfigOnly()

    /** Initiates the node information synchronization stage. */
    fun startNodeInfoOnly()

    /** Called when the node database is ready and fully populated. */
    suspend fun onNodeDbReady()

    /**
     * Synchronously cancels the transport-aware handshake watchdog the moment the firmware signals Stage 2 completion
     * (NODE_INFO_NONCE received).
     *
     * This MUST be invoked before the asynchronous NodeDB install work begins so that a slow DB commit on a large mesh
     * cannot trip the 12 s fast-recovery timeout after the firmware handshake has already succeeded. The watchdog
     * cancellation it performs is a strict subset of [onNodeDbReady]; the remaining post-NodeDB side effects
     * (analytics, MQTT start, history replay, telemetry requests) stay gated on [onNodeDbReady] at the end of the DB
     * install block.
     */
    fun onHandshakeComplete()

    /**
     * Recovers from a failure after firmware handshake completion but before NodeDB install has completed.
     *
     * At this point the handshake watchdog has already been cancelled by [onHandshakeComplete], so recovery must
     * explicitly move the app-level state out of Connecting and restart the raw transport in the same ordering used by
     * handshake stall recovery.
     */
    fun recoverPostHandshakeFailure()

    /**
     * Called when meaningful handshake progress is observed on the wire (e.g. an inbound packet related to the
     * in-flight config or node-info exchange).
     *
     * On fast transports (TCP, USB serial) this re-arms the transport-aware handshake watchdog so a steady trickle of
     * progress does not trip the aggressive fast-recovery timeout while a true stall still fires on schedule. On BLE
     * this is a no-op: BLE keeps the original long-and-retry stall-guard budget because GATT latency is high and
     * variable.
     */
    fun onHandshakeProgress()

    /** Updates the telemetry information for the local node. */
    fun updateTelemetry(t: Telemetry)

    /** Updates the current status notification. */
    fun updateStatusNotification(telemetry: Telemetry? = null)

    /** Clears the cached radio configuration (local config, channel set, module config). */
    fun clearRadioConfig()
}
