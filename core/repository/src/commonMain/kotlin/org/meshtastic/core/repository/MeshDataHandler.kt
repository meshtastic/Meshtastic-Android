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

import kotlinx.coroutines.Job
import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.MeshPacket

/** Interface for handling incoming mesh data packets and routing them to the appropriate handlers. */
interface MeshDataHandler {
    /**
     * Processes a received mesh packet.
     *
     * @param packet The received mesh packet.
     * @param myNodeNum The local node number.
     * @param logUuid Optional UUID for logging purposes.
     * @param logInsertJob Optional job that tracks the insertion of the packet into the log.
     */
    fun handleReceivedData(packet: MeshPacket, myNodeNum: Int, logUuid: String? = null, logInsertJob: Job? = null)

    /**
     * Persists a data packet in the history and triggers notifications if necessary.
     *
     * @param dataPacket The data packet to remember.
     * @param myNodeNum The local node number.
     * @param updateNotification Whether to trigger a notification for this packet.
     */
    fun rememberDataPacket(dataPacket: DataPacket, myNodeNum: Int, updateNotification: Boolean = true)
}
