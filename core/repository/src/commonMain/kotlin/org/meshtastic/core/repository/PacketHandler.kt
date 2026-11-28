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

/**
 * Sends packets through the session's SDK paced queue and projects each send's delivery status onto its durable Room
 * row.
 */
interface PacketHandler {
    /**
     * A fresh non-zero packet ID from the active session's SDK client, which numbers its own sends and RPCs from the
     * same counter, so a durable row and its wire packet share one identity.
     */
    fun generatePacketId(): Int

    /**
     * Sends a data packet under its persisted ID, assigning one when it has none. If the radio session refuses
     * admission, the packet is marked [org.meshtastic.core.model.MessageStatus.ERROR] and
     * [PacketQueueRejectedException] is thrown so the persistence owner can requeue or fail its durable record instead
     * of treating a normal return as successful admission.
     */
    suspend fun sendData(p: DataPacket)

    /**
     * Re-arms the send-ACK timeout for every persisted packet still awaiting its routing ACK/NAK, so sends orphaned by
     * a disconnect or app restart become retryable instead of showing as sending forever. Call once per connection,
     * after the radio config is loaded.
     */
    fun rearmSendAckTimeouts()
}
