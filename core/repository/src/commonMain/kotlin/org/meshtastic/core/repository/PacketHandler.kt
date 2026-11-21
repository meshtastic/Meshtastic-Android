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

import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.QueueStatus
import org.meshtastic.sdk.PacedMessageHandle

/**
 * Sends packets through the session's SDK paced queue and projects each send's delivery status onto its durable Room
 * row.
 */
interface PacketHandler {
    /** The last packet ID [generatePacketId] handed out. */
    val currentPacketId: Long

    /** Generates a fresh non-zero packet ID, so a durable row and its wire packet share one identity. */
    fun generatePacketId(): Int

    /**
     * Sends a data packet under its persisted ID, assigning one when it has none. If the radio session refuses
     * admission, the packet is marked [org.meshtastic.core.model.MessageStatus.ERROR] and
     * [PacketQueueRejectedException] is thrown so the persistence owner can requeue or fail its durable record instead
     * of treating a normal return as successful admission.
     */
    suspend fun sendData(p: DataPacket)

    /**
     * Records a request the SDK sent to [to] on [port] under [requestId] in the mesh log, which the request logs and
     * reply matching read.
     */
    fun logRequest(requestId: Int, to: Int, port: PortNum)

    /**
     * Adds a mesh packet to the queue for sending.
     *
     * A completed packet ID may be reused by a later retry. A duplicate is rejected only while that ID is still queued
     * or in flight, preserving single ownership of its response.
     *
     * @return `true` when the packet's non-zero ID was reserved and queued, or `false` when the packet was invalid, its
     *   ID was already reserved, the radio is not connected, or the owning service scope has shut down.
     */
    suspend fun sendToRadio(packet: MeshPacket): Boolean = sendTracked(packet) != null

    /** Queues [packet] as [sendToRadio] does and returns its SDK handle, or null when admission fails. */
    suspend fun sendTracked(packet: MeshPacket): PacedMessageHandle?

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

    /**
     * [sendToRadioAndAwaitResult] admitted only while [expectedConnectionVersion] still owns the connected lifecycle
     * generation. A stale generation is reported as [AwaitedSendStatus.TRANSPORT_STOPPED] with `dispatched == false`.
     */
    suspend fun sendToRadioAndAwaitResultForConnection(
        packet: MeshPacket,
        expectedConnectionVersion: Long,
    ): AwaitedSendResult

    /**
     * Re-arms the send-ACK timeout for every persisted packet still awaiting its routing ACK/NAK, so sends orphaned by
     * a disconnect or app restart become retryable instead of showing as sending forever. Call once per connection,
     * after the radio config is loaded.
     */
    fun rearmSendAckTimeouts()
}
