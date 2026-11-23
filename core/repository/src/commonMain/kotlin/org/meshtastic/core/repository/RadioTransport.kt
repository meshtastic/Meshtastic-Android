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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.meshtastic.proto.LockdownAuth
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.sdk.AdminApi
import org.meshtastic.sdk.AdminSessionStatus
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.PacedMessageHandle
import org.meshtastic.sdk.RadioClient

/** One radio link (BLE, serial, TCP, etc.) and the SDK PhoneAPI session that runs over it. */
interface RadioTransport {
    val admin: AdminApi?
        get() = null

    /** The SDK client of this link's current PhoneAPI session, or null before it starts and after it ends. */
    val client: RadioClient?
        get() = null

    fun adminSessionStatus(node: NodeId): Flow<AdminSessionStatus> = flowOf(AdminSessionStatus.NoSession)

    /** Submits a mesh packet to the SDK send queue on this link. */
    fun sendPaced(packet: MeshPacket): PacedMessageHandle? = null

    /** Queues local lockdown authentication on the current link. */
    fun tryLockdown(auth: LockdownAuth): Boolean = false

    /** Queues a nodes-only refresh when this transport owns the PhoneAPI session. */
    fun requestNodeDbRefresh(): Boolean = false

    /** Hands a broker message to the radio's MQTT client proxy; false when no session can take it. */
    suspend fun deliverMqttProxy(message: MqttClientProxyMessage): Boolean = false

    /**
     * Initializes the transport after construction. Called by the service after publishing the transport.
     *
     * This separates construction from side effects (connecting, launching coroutines), making transports easier to
     * test and reason about.
     */
    fun start() {}

    /**
     * Closes the connection to the device.
     *
     * Implementations that perform potentially-blocking teardown (e.g. BLE GATT disconnect) MUST run that work inside
     * `withContext(NonCancellable)` so a cancelled caller cannot skip cleanup, leaving the underlying resource leaked.
     * Callers must invoke this from a coroutine — it must never be called from a blocking context (no `runBlocking`).
     */
    suspend fun close()
}
