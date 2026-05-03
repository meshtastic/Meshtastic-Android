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
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node

/** Interface for broadcasting service-level events to the application. */
interface ServiceBroadcasts {
    /** Subscribes a receiver to mesh broadcasts. */
    fun subscribeReceiver(receiverName: String, packageName: String)

    /** Broadcasts received data to the application. */
    fun broadcastReceivedData(dataPacket: DataPacket)

    /** Broadcasts that the radio connection state has changed. */
    fun broadcastConnection()

    /** Broadcasts that node information has changed. */
    fun broadcastNodeChange(node: Node)

    /** Broadcasts that the status of a message has changed. */
    fun broadcastMessageStatus(packetId: Int, status: MessageStatus)
}
