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

package org.meshtastic.core.data.repository

import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.meshtastic.core.database.dao.PacketDao
import org.meshtastic.core.database.entity.ContactSettings
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.proto.Portnums.PortNum
import javax.inject.Inject

class PacketRepository @Inject constructor(private val packetDaoLazy: Lazy<PacketDao>) {
    private val packetDao by lazy { packetDaoLazy.get() }

    fun getWaypoints(): Flow<List<Packet>> = packetDao.getAllPackets(PortNum.WAYPOINT_APP_VALUE)

    fun getContacts(): Flow<Map<String, Packet>> = packetDao.getContactKeys()

    suspend fun getMessageCount(contact: String): Int =
        withContext(Dispatchers.IO) { packetDao.getMessageCount(contact) }

    suspend fun getUnreadCount(contact: String): Int = withContext(Dispatchers.IO) { packetDao.getUnreadCount(contact) }

    fun getUnreadCountTotal(): Flow<Int> = packetDao.getUnreadCountTotal()

    suspend fun clearUnreadCount(contact: String, timestamp: Long) =
        withContext(Dispatchers.IO) { packetDao.clearUnreadCount(contact, timestamp) }

    suspend fun getQueuedPackets(): List<DataPacket>? = withContext(Dispatchers.IO) { packetDao.getQueuedPackets() }

    suspend fun insert(packet: Packet) = withContext(Dispatchers.IO) { packetDao.insert(packet) }

    suspend fun getMessagesFrom(contact: String, getNode: suspend (String?) -> Node) = withContext(Dispatchers.IO) {
        packetDao.getMessagesFrom(contact).mapLatest { packets ->
            packets.map { packet ->
                val message = packet.toMessage(getNode)
                message.replyId
                    .takeIf { it != null && it != 0 }
                    ?.let { getPacketByPacketId(it) }
                    ?.toMessage(getNode)
                    ?.let { originalMessage -> message.copy(originalMessage = originalMessage) } ?: message
            }
        }
    }

    suspend fun updateMessageStatus(d: DataPacket, m: MessageStatus) =
        withContext(Dispatchers.IO) { packetDao.updateMessageStatus(d, m) }

    suspend fun updateMessageId(d: DataPacket, id: Int) =
        withContext(Dispatchers.IO) { packetDao.updateMessageId(d, id) }

    suspend fun getPacketById(requestId: Int) = withContext(Dispatchers.IO) { packetDao.getPacketById(requestId) }

    suspend fun getPacketByPacketId(packetId: Int) =
        withContext(Dispatchers.IO) { packetDao.getPacketByPacketId(packetId) }

    suspend fun deleteMessages(uuidList: List<Long>) = withContext(Dispatchers.IO) {
        for (chunk in uuidList.chunked(500)) { // limit number of UUIDs per query
            packetDao.deleteMessages(chunk)
        }
    }

    suspend fun deleteContacts(contactList: List<String>) =
        withContext(Dispatchers.IO) { packetDao.deleteContacts(contactList) }

    suspend fun deleteWaypoint(id: Int) = withContext(Dispatchers.IO) { packetDao.deleteWaypoint(id) }

    suspend fun delete(packet: Packet) = withContext(Dispatchers.IO) { packetDao.delete(packet) }

    suspend fun update(packet: Packet) = withContext(Dispatchers.IO) { packetDao.update(packet) }

    fun getContactSettings(): Flow<Map<String, ContactSettings>> = packetDao.getContactSettings()

    suspend fun getContactSettings(contact: String) =
        withContext(Dispatchers.IO) { packetDao.getContactSettings(contact) ?: ContactSettings(contact) }

    suspend fun setMuteUntil(contacts: List<String>, until: Long) =
        withContext(Dispatchers.IO) { packetDao.setMuteUntil(contacts, until) }

    suspend fun insertReaction(reaction: ReactionEntity) = withContext(Dispatchers.IO) { packetDao.insert(reaction) }

    suspend fun clearPacketDB() = withContext(Dispatchers.IO) { packetDao.deleteAll() }
}
