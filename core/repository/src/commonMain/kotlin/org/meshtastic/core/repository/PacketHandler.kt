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

    /**
     * Adds a mesh packet to the queue for sending.
     *
     * A completed packet ID may be reused by a later retry. A duplicate is rejected only while that ID is still queued
     * or in flight, preserving single ownership of its response.
     *
     * @return `true` when the packet's non-zero ID was reserved and queued, or `false` when the packet was invalid, its
     *   ID was already reserved, the radio is not connected, or the owning service scope has shut down.
     */
    suspend fun sendToRadio(packet: MeshPacket): Boolean

    /**
     * Adds a mesh packet to the queue and suspends until the radio acknowledges it via [QueueStatus].
     *
     * Unlike [sendToRadio], which is fire-and-forget, this method provides back-pressure so the caller can ensure a
     * packet has been accepted by the radio before proceeding. This is critical for operations where ordering matters
     * (e.g., sending a shared contact before the first DM).
     *
     * Time spent behind packets already in the FIFO is not part of the response timeout. The timeout begins when this
     * packet reaches the head of the queue and is transmitted.
     *
     * @return `true` if the radio accepted the packet, `false` on timeout or failure.
     */
    suspend fun sendToRadioAndAwait(packet: MeshPacket): Boolean = sendToRadioAndAwaitResult(packet).accepted

    /** Detailed form of [sendToRadioAndAwait], including whether an active transport admitted the packet. */
    suspend fun sendToRadioAndAwaitResult(packet: MeshPacket): AwaitedSendResult

    /** Processes queue status updates from the radio. */
    fun handleQueueStatus(queueStatus: QueueStatus)

    /**
     * Completes the pending response for [dataRequestId] when an active transport already dispatched that packet.
     * Replies that arrive before dispatch are stale and are ignored. This method does not release the packet ID
     * reservation; the queue worker owns that removal.
     */
    suspend fun completeDispatchedResponse(dataRequestId: Int, complete: Boolean)

    /** Stops the packet queue. */
    fun stopPacketQueue()
}
