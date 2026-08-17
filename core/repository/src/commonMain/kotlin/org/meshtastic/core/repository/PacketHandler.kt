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
     * Adds [packet] only while [expectedConnectionVersion] still owns the connected lifecycle generation.
     *
     * This is the admission path for work captured by a specific connection and closes the check-then-send race across
     * disconnect/reconnect.
     *
     * @return `true` when the expected generation is still current and the packet is admitted, or `false` when the
     *   generation is stale or admission fails for any reason documented by [sendToRadio].
     */
    suspend fun sendToRadioForConnection(packet: MeshPacket, expectedConnectionVersion: Long): Boolean

    /**
     * Adds a mesh packet to the queue and suspends until its routing acknowledgement arrives.
     *
     * A successful [QueueStatus] only means that firmware accepted the packet into its transport queue; it does not
     * prove that a self-addressed admin command has been processed. This stricter acknowledgement is required when a
     * later packet depends on that command, such as installing a shared contact before sending the first DM.
     *
     * Time spent behind packets already in the FIFO is not part of the response timeout. The timeout begins after an
     * active transport admits this packet and ends on its routing ACK/NAK or synchronous local-loopback result.
     *
     * @return `true` on a routing ACK or synchronous local-loopback delivery (`ERRNO_SHOULD_RELEASE`); `false` when
     *   disconnected, transport sending fails, a routing NAK or queue rejection arrives, or the operation times out.
     */
    suspend fun sendToRadioAndAwait(packet: MeshPacket): Boolean = sendToRadioAndAwaitResult(packet).accepted

    /** Detailed form of [sendToRadioAndAwait], including whether an active transport admitted the packet. */
    suspend fun sendToRadioAndAwaitResult(packet: MeshPacket): AwaitedSendResult

    /** Processes queue status updates from the radio. */
    fun handleQueueStatus(queueStatus: QueueStatus)

    /**
     * Completes the strict routing response for [dataRequestId] when an active transport already dispatched that
     * packet. Replies that arrive before dispatch are stale and ignored. The packet ID remains reserved until both the
     * firmware queue stage and this routing stage are terminal.
     */
    suspend fun completeDispatchedResponse(dataRequestId: Int, complete: Boolean)

    /** Stops the packet queue. */
    fun stopPacketQueue()

    /**
     * Re-arms the send-ACK timeout for every persisted packet still awaiting its routing ACK/NAK, so sends orphaned by
     * a disconnect or app restart become retryable instead of showing as sending forever. Call once per connection,
     * after the radio config is loaded.
     */
    fun rearmSendAckTimeouts()
}
