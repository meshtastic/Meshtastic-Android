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

/** Interface for processing incoming radio messages and mesh packets. */
interface MeshMessageProcessor {
    /** Handles a raw message received from the radio. */
    fun handleFromRadio(bytes: ByteArray, myNodeNum: Int?)

    /** Handles a received mesh packet. */
    fun handleReceivedMeshPacket(packet: MeshPacket, myNodeNum: Int?)

    /** Clears the buffer of early received packets. */
    fun clearEarlyPackets()
}
