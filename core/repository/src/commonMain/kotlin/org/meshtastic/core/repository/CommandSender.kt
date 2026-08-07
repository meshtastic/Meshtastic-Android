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
import org.meshtastic.core.model.Position
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.Position as ProtoPosition

/** Interface for sending commands and packets to the mesh network. */
@Suppress("TooManyFunctions")
interface CommandSender {
    /** Returns the current packet ID. */
    fun getCurrentPacketId(): Long

    /** Returns the cached local configuration. */
    fun getCachedLocalConfig(): LocalConfig

    /** Returns the cached channel set. */
    fun getCachedChannelSet(): ChannelSet

    /** Generates a new unique packet ID. */
    fun generatePacketId(): Int

    /**
     * Sends a data packet to the mesh. If the outbound queue refuses admission, the packet is marked
     * [org.meshtastic.core.model.MessageStatus.ERROR] and [PacketQueueRejectedException] is thrown so the persistence
     * owner can requeue or fail its durable record instead of treating a normal return as successful admission.
     */
    suspend fun sendData(p: DataPacket)

    /** Sends an admin message to a specific node, or throws if the outbound queue rejects it. */
    suspend fun sendAdmin(
        destNum: Int,
        requestId: Int = generatePacketId(),
        wantResponse: Boolean = false,
        initFn: () -> AdminMessage,
    )

    /**
     * Sends an admin message immediately, bypassing the outbound packet queue. The queue only drains while the
     * connection state is Connected, so mid-handshake sends (e.g. set_time_only at MyNodeInfo) must use this path or
     * they stall until the handshake finishes.
     */
    fun sendAdminImmediate(destNum: Int, initFn: () -> AdminMessage)

    /**
     * Sends an admin message and suspends until the radio acknowledges it.
     *
     * This is used when the caller needs to guarantee a packet has been accepted by the radio before proceeding, such
     * as sending a shared contact before the first DM to a node. Time spent behind existing FIFO entries does not count
     * against the radio-response timeout.
     *
     * @return `true` if the radio accepted the packet; `false` on rejection, timeout, transport stop, or send failure.
     */
    suspend fun sendAdminAwait(
        destNum: Int,
        requestId: Int = generatePacketId(),
        wantResponse: Boolean = false,
        initFn: () -> AdminMessage,
    ): Boolean = sendAdminAwaitResult(destNum, requestId, wantResponse, initFn).accepted

    /**
     * Detailed form of [sendAdminAwait], including whether an active transport admitted the packet. Queue or transport
     * rejection is represented by a non-accepted [AwaitedSendResult] with `dispatched == false`; it is not reported as
     * [PacketQueueRejectedException].
     */
    suspend fun sendAdminAwaitResult(
        destNum: Int,
        requestId: Int = generatePacketId(),
        wantResponse: Boolean = false,
        initFn: () -> AdminMessage,
    ): AwaitedSendResult

    /** Sends our current position to the mesh, or throws if the outbound queue rejects it. */
    suspend fun sendPosition(pos: ProtoPosition, destNum: Int? = null, wantResponse: Boolean = false)

    /** Requests the position of a specific node, or throws if the outbound queue rejects it. */
    suspend fun requestPosition(destNum: Int, currentPosition: Position)

    /** Sets a fixed position for a node, or throws if the outbound queue rejects the admin command. */
    suspend fun setFixedPosition(destNum: Int, pos: Position)

    /** Requests user info from a specific node, or throws if the outbound queue rejects it. */
    suspend fun requestUserInfo(destNum: Int)

    /** Requests a traceroute to a specific node, or throws if the outbound queue rejects it. */
    suspend fun requestTraceroute(requestId: Int, destNum: Int)

    /** Requests telemetry from a specific node, or throws if the outbound queue rejects it. */
    suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int)

    /** Requests neighbor info from a specific node, or throws if the outbound queue rejects it. */
    suspend fun requestNeighborInfo(requestId: Int, destNum: Int)

    /**
     * Sends a lockdown passphrase to authenticate with a locked device.
     *
     * @param disable when `true`, instructs the device to decrypt storage back to plaintext and leave lockdown (the off
     *   switch). The device reboots and reconnects reporting `DISABLED`.
     */
    fun sendLockdownPassphrase(
        passphrase: String,
        boots: Int = 0,
        hours: Int = 0,
        maxSessionSeconds: Int = 0,
        disable: Boolean = false,
    )

    /** Sends a Lock Now command to immediately lock a locked-firmware device. */
    fun sendLockNow()
}
