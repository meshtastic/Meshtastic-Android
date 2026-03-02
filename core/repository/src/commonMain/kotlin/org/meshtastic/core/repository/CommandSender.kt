/*
 * Copyright (c) 2025 Meshtastic LLC
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

import kotlinx.coroutines.CoroutineScope
import okio.ByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Position
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.NeighborInfo

/**
 * Interface for sending commands and packets to the mesh network.
 */
interface CommandSender {
    /** Starts the command sender with the given coroutine scope. */
    fun start(scope: CoroutineScope)

    /** Returns the current packet ID. */
    fun getCurrentPacketId(): Long

    /** Returns the cached local configuration. */
    fun getCachedLocalConfig(): LocalConfig

    /** Returns the cached channel set. */
    fun getCachedChannelSet(): ChannelSet

    /** Generates a new unique packet ID. */
    fun generatePacketId(): Int

    /** The latest neighbor info received from the connected radio. */
    var lastNeighborInfo: NeighborInfo?

    /** Start times of traceroute requests for duration calculation. */
    val tracerouteStartTimes: MutableMap<Int, Long>

    /** Start times of neighbor info requests for duration calculation. */
    val neighborInfoStartTimes: MutableMap<Int, Long>

    /** Sets the session passkey for admin messages. */
    fun setSessionPasskey(key: ByteString)

    /** Sends a data packet to the mesh. */
    fun sendData(p: DataPacket)

    /** Sends an admin message to a specific node. */
    fun sendAdmin(
        destNum: Int,
        requestId: Int = generatePacketId(),
        wantResponse: Boolean = false,
        initFn: () -> AdminMessage,
    )

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
