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
    suspend fun sendToRadio(packet: MeshPacket)

    /**
     * Adds a mesh packet to the queue and suspends until its routing acknowledgement arrives.
     *
     * A successful [QueueStatus] only means that firmware accepted the packet into its transport queue; it does not
     * prove that a self-addressed admin command has been processed. This stricter acknowledgement is required when a
     * later packet depends on that command, such as installing a shared contact before sending the first DM.
     *
     * @return `true` on a routing ACK, `false` on a routing NAK, queue rejection, or timeout.
     */
    suspend fun sendToRadioAndAwait(packet: MeshPacket): Boolean

    /** Processes queue status updates from the radio. */
    fun handleQueueStatus(queueStatus: QueueStatus)

    /** Removes and completes a pending response for a request before the caller's lifecycle lease is released. */
    suspend fun removeResponse(dataRequestId: Int, complete: Boolean)

    /** Stops the packet queue. */
    fun stopPacketQueue()
}
