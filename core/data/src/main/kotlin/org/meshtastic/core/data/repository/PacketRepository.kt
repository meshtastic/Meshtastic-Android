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

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.ContactSettings
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.proto.ChannelProtos.ChannelSettings
import org.meshtastic.proto.Portnums.PortNum
import javax.inject.Inject

class PacketRepository
@Inject
constructor(
    private val dbManager: DatabaseManager,
    private val dispatchers: CoroutineDispatchers,
) {
    fun getWaypoints(): Flow<List<Packet>> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getAllPackets(PortNum.WAYPOINT_APP_VALUE) }

    fun getContacts(): Flow<Map<String, Packet>> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getContactKeys() }

    fun getContactsPaged(): Flow<PagingData<Packet>> = Pager(
        config = PagingConfig(pageSize = 30, enablePlaceholders = false, initialLoadSize = 30),
        pagingSourceFactory = { dbManager.currentDb.value.packetDao().getContactKeysPaged() },
    )
        .flow

    suspend fun getMessageCount(contact: String): Int =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getMessageCount(contact) }

    suspend fun getUnreadCount(contact: String): Int =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getUnreadCount(contact) }

    fun getFirstUnreadMessageUuid(contact: String): Flow<Long?> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getFirstUnreadMessageUuid(contact) }

    fun hasUnreadMessages(contact: String): Flow<Boolean> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().hasUnreadMessages(contact) }

    fun getUnreadCountTotal(): Flow<Int> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getUnreadCountTotal() }

    suspend fun clearUnreadCount(contact: String, timestamp: Long) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().clearUnreadCount(contact, timestamp) }

    suspend fun updateLastReadMessage(contact: String, messageUuid: Long, lastReadTimestamp: Long) =
        withContext(dispatchers.io) {
            val dao = dbManager.currentDb.value.packetDao()
            val current = dao.getContactSettings(contact)
            val existingTimestamp = current?.lastReadMessageTimestamp ?: Long.MIN_VALUE
            if (lastReadTimestamp <= existingTimestamp) {
                return@withContext
            }
            val updated =
                (current ?: ContactSettings(contact_key = contact)).copy(
                    lastReadMessageUuid = messageUuid,
                    lastReadMessageTimestamp = lastReadTimestamp,
                )
            dao.upsertContactSettings(listOf(updated))
        }

    suspend fun getQueuedPackets(): List<DataPacket>? =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getQueuedPackets() }

    suspend fun insert(packet: Packet) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().insert(packet) }

    suspend fun getMessagesFrom(contact: String, getNode: suspend (String?) -> Node) = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().getMessagesFrom(contact).mapLatest { packets ->
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

    fun getMessagesFromPaged(contact: String, getNode: suspend (String?) -> Node): Flow<PagingData<Message>> = Pager(
        config = PagingConfig(pageSize = 50, enablePlaceholders = false, initialLoadSize = 50),
        pagingSourceFactory = { dbManager.currentDb.value.packetDao().getMessagesFromPaged(contact) },
    )
        .flow
        .map { pagingData ->
            pagingData.map { packet ->
                val message = packet.toMessage(getNode)
                message.replyId
                    .takeIf { it != null && it != 0 }
                    ?.let { getPacketByPacketId(it) }
                    ?.toMessage(getNode)
                    ?.let { originalMessage -> message.copy(originalMessage = originalMessage) } ?: message
            }
        }

    suspend fun updateMessageStatus(d: DataPacket, m: MessageStatus) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().updateMessageStatus(d, m) }

    suspend fun updateMessageId(d: DataPacket, id: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().updateMessageId(d, id) }

    suspend fun getPacketById(requestId: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getPacketById(requestId) }

    suspend fun getPacketByPacketId(packetId: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getPacketByPacketId(packetId) }

    suspend fun deleteMessages(uuidList: List<Long>) = withContext(dispatchers.io) {
        for (chunk in uuidList.chunked(500)) {
            // Fetch DAO per chunk to avoid holding a stale reference if the active DB switches
            dbManager.currentDb.value.packetDao().deleteMessages(chunk)
        }
    }

    suspend fun deleteContacts(contactList: List<String>) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().deleteContacts(contactList) }

    suspend fun deleteWaypoint(id: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().deleteWaypoint(id) }

    suspend fun delete(packet: Packet) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().delete(packet) }

    suspend fun update(packet: Packet) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().update(packet) }

    fun getContactSettings(): Flow<Map<String, ContactSettings>> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getContactSettings() }

    suspend fun getContactSettings(contact: String) = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().getContactSettings(contact) ?: ContactSettings(contact)
    }

    suspend fun setMuteUntil(contacts: List<String>, until: Long) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().setMuteUntil(contacts, until) }

    suspend fun insertReaction(reaction: ReactionEntity) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().insert(reaction) }

    suspend fun clearPacketDB() = withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().deleteAll() }

    suspend fun migrateChannelsByPSK(oldSettings: List<ChannelSettings>, newSettings: List<ChannelSettings>) =
        withContext(dispatchers.io) {
            dbManager.currentDb.value.packetDao().migrateChannelsByPSK(oldSettings, newSettings)
        }
}
