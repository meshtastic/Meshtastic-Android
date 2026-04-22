/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

    /** Sends a data packet to the mesh. */
    fun sendData(p: DataPacket)

    /** Sends an admin message to a specific node. */
    fun sendAdmin(
        destNum: Int,
        requestId: Int = generatePacketId(),
        wantResponse: Boolean = false,
        initFn: () -> AdminMessage,
    )

    /**
     * Sends an admin message and suspends until the radio acknowledges it.
     *
     * This is used when the caller needs to guarantee a packet has been accepted by the radio before proceeding, such
     * as sending a shared contact before the first DM to a node.
     *
     * @return `true` if the radio accepted the packet, `false` on timeout or failure.
     */
    suspend fun sendAdminAwait(
        destNum: Int,
        requestId: Int = generatePacketId(),
        wantResponse: Boolean = false,
        initFn: () -> AdminMessage,
    ): Boolean

    /** Sends our current position to the mesh. */
    fun sendPosition(pos: org.meshtastic.proto.Position, destNum: Int? = null, wantResponse: Boolean = false)

    /** Requests the position of a specific node. */
    fun requestPosition(destNum: Int, currentPosition: Position)

    /** Sets a fixed position for a node. */
    fun setFixedPosition(destNum: Int, pos: Position)

    /** Requests user info from a specific node. */
    fun requestUserInfo(destNum: Int)

    /** Requests a traceroute to a specific node. */
    fun requestTraceroute(requestId: Int, destNum: Int)

    /** Requests telemetry from a specific node. */
    fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int)

    /** Requests neighbor info from a specific node. */
    fun requestNeighborInfo(requestId: Int, destNum: Int)
}
