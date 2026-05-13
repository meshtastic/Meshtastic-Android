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

import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio

/** Interface for handling the transmission of packets to the radio and managing the packet queue. */
interface PacketHandler {
    /** Sends a command/packet directly to the radio. */
    fun sendToRadio(p: ToRadio)

    /** Adds a mesh packet to the queue for sending. */
    fun sendToRadio(packet: MeshPacket)

    /**
     * Adds a mesh packet to the queue and suspends until the radio acknowledges it via [QueueStatus].
     *
     * Unlike [sendToRadio], which is fire-and-forget, this method provides back-pressure so the caller can ensure a
     * packet has been accepted by the radio before proceeding. This is critical for operations where ordering matters
     * (e.g., sending a shared contact before the first DM).
     *
     * @return `true` if the radio accepted the packet, `false` on timeout or failure.
     */
    suspend fun sendToRadioAndAwait(packet: MeshPacket): Boolean

    /** Processes queue status updates from the radio. */
    fun handleQueueStatus(queueStatus: QueueStatus)

    /** Removes a pending response for a request. */
    fun removeResponse(dataRequestId: Int, complete: Boolean)

    /** Stops the packet queue. */
    fun stopPacketQueue()
}
