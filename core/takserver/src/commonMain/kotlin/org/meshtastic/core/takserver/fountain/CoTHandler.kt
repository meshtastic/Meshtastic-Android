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
package org.meshtastic.core.takserver.fountain

import org.meshtastic.core.takserver.CoTMessage

/**
 * Handles incoming and outgoing generic Cursor on Target (CoT) messages wrapped in Meshtastic DataPackets.
 *
 * Defines the contract for routing Direct (unfragmented) vs Fountain-encoded packets, and processing decompressed
 * EXI/Zlib XML payloads.
 */
interface CoTHandler {
    suspend fun sendGenericCoT(cotMessage: CoTMessage)

    suspend fun handleIncomingForwarderPacket(payload: ByteArray, senderNodeNum: Int)
}
