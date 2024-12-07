/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.database

import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.Portnums.PortNum
import com.geeksville.mesh.database.dao.PacketDao
import com.geeksville.mesh.database.entity.ContactSettings
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.ReactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PacketRepository @Inject constructor(private val packetDaoLazy: dagger.Lazy<PacketDao>) {
    private val packetDao by lazy {
        packetDaoLazy.get()
    }

    fun getWaypoints(): Flow<List<Packet>> = packetDao.getAllPackets(PortNum.WAYPOINT_APP_VALUE)

    fun getContacts(): Flow<Map<String, Packet>> = packetDao.getContactKeys()

    suspend fun getMessageCount(contact: String): Int = withContext(Dispatchers.IO) {
        packetDao.getMessageCount(contact)
    }

    suspend fun getUnreadCount(contact: String): Int = withContext(Dispatchers.IO) {
        packetDao.getUnreadCount(contact)
    }

    suspend fun clearUnreadCount(contact: String, timestamp: Long) = withContext(Dispatchers.IO) {
        packetDao.clearUnreadCount(contact, timestamp)
    }

    suspend fun getQueuedPackets(): List<DataPacket>? = withContext(Dispatchers.IO) {
        packetDao.getQueuedPackets()
    }

    suspend fun insert(packet: Packet) = withContext(Dispatchers.IO) {
        packetDao.insert(packet)
    }

    fun getMessagesFrom(contact: String) = packetDao.getMessagesFrom(contact)

    suspend fun updateMessageStatus(d: DataPacket, m: MessageStatus) = withContext(Dispatchers.IO) {
        packetDao.updateMessageStatus(d, m)
    }

    suspend fun updateMessageId(d: DataPacket, id: Int) = withContext(Dispatchers.IO) {
        packetDao.updateMessageId(d, id)
    }

    suspend fun getPacketById(requestId: Int) = withContext(Dispatchers.IO) {
        packetDao.getPacketById(requestId)
    }

    suspend fun deleteMessages(uuidList: List<Long>) = withContext(Dispatchers.IO) {
        for (chunk in uuidList.chunked(500)) { // limit number of UUIDs per query
            packetDao.deleteMessages(chunk)
        }
    }

    suspend fun deleteContacts(contactList: List<String>) = withContext(Dispatchers.IO) {
        packetDao.deleteContacts(contactList)
    }

    suspend fun deleteWaypoint(id: Int) = withContext(Dispatchers.IO) {
        packetDao.deleteWaypoint(id)
    }

    suspend fun delete(packet: Packet) = withContext(Dispatchers.IO) {
        packetDao.delete(packet)
    }

    suspend fun update(packet: Packet) = withContext(Dispatchers.IO) {
        packetDao.update(packet)
    }

    fun getContactSettings(): Flow<Map<String, ContactSettings>> = packetDao.getContactSettings()

    suspend fun getContactSettings(contact: String) = withContext(Dispatchers.IO) {
        packetDao.getContactSettings(contact) ?: ContactSettings(contact)
    }

    suspend fun setMuteUntil(contacts: List<String>, until: Long) = withContext(Dispatchers.IO) {
        packetDao.setMuteUntil(contacts, until)
    }

    suspend fun insertReaction(reaction: ReactionEntity) = withContext(Dispatchers.IO) {
        packetDao.insert(reaction)
    }
}
