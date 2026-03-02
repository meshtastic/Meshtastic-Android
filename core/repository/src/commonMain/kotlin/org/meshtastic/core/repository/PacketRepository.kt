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

import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus

interface PacketRepository {
    suspend fun savePacket(
        myNodeNum: Int,
        contactKey: String,
        packet: DataPacket,
        receivedTime: Long,
        read: Boolean = true,
        filtered: Boolean = false,
    )
    
    suspend fun updateMessageStatus(d: DataPacket, m: MessageStatus)
    
    suspend fun getQueuedPackets(): List<DataPacket>?
}
