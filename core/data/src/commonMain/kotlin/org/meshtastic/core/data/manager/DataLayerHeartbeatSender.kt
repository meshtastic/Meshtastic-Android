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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.ToRadio

/**
 * Centralized heartbeat sender for the data layer.
 *
 * Consolidates heartbeat nonce management into a single monotonically increasing counter, preventing the firmware's
 * per-connection duplicate-write filter (byte-level memcmp) from silently dropping consecutive heartbeats.
 *
 * This is distinct from [org.meshtastic.core.network.transport.HeartbeatSender], which operates at the transport layer
 * with raw byte encoding. This class works at the protobuf/data layer through [PacketHandler].
 */
@Single
class DataLayerHeartbeatSender(private val packetHandler: PacketHandler) {
    private val nonce = atomic(0)

    /**
     * Enqueues a heartbeat with a unique nonce.
     *
     * @param tag descriptive label for log messages (e.g. "pre-handshake", "inter-stage")
     */
    @Suppress("TooGenericExceptionCaught")
    fun sendHeartbeat(tag: String = "handshake") {
        try {
            val n = nonce.incrementAndGet()
            packetHandler.sendToRadio(ToRadio(heartbeat = Heartbeat(nonce = n)))
            Logger.d { "[$tag] Heartbeat enqueued (nonce=$n)" }
        } catch (e: Exception) {
            Logger.w(e) { "[$tag] Failed to enqueue heartbeat; proceeding" }
        }
    }
}
