/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.ContactSettings
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.PortNum
import javax.inject.Inject

class PacketRepository
@Inject
constructor(
    private val dbManager: DatabaseManager,
    private val dispatchers: CoroutineDispatchers,
) {
    fun getWaypoints(): Flow<List<Packet>> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getAllWaypointsFlow() }

    fun getContacts(): Flow<Map<String, Packet>> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getContactKeys() }

    fun getContactsPaged(): Flow<PagingData<Packet>> = Pager(
        config =
        PagingConfig(
            pageSize = CONTACTS_PAGE_SIZE,
            enablePlaceholders = false,
            initialLoadSize = CONTACTS_PAGE_SIZE,
        ),
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

    suspend fun getMessagesFrom(
        contact: String,
        limit: Int? = null,
        includeFiltered: Boolean = true,
        getNode: suspend (String?) -> Node,
    ) = withContext(dispatchers.io) {
        val dao = dbManager.currentDb.value.packetDao()
        val flow =
            when {
                limit != null -> dao.getMessagesFrom(contact, limit)
                !includeFiltered -> dao.getMessagesFrom(contact, includeFiltered = false)
                else -> dao.getMessagesFrom(contact)
            }
        flow.mapLatest { packets ->
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
        config =
        PagingConfig(
            pageSize = MESSAGES_PAGE_SIZE,
            enablePlaceholders = false,
            initialLoadSize = MESSAGES_PAGE_SIZE,
        ),
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

    suspend fun findPacketsWithId(packetId: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().findPacketsWithId(packetId) }

    @Suppress("CyclomaticComplexMethod")
    suspend fun updateSFPPStatus(
        packetId: Int,
        from: Int,
        to: Int,
        hash: ByteArray,
        status: MessageStatus = MessageStatus.SFPP_CONFIRMED,
        rxTime: Long = 0,
        myNodeNum: Int? = null,
    ) = withContext(dispatchers.io) {
        val dao = dbManager.currentDb.value.packetDao()
        val packets = dao.findPacketsWithId(packetId)
        val reactions = dao.findReactionsWithId(packetId)
        val fromId = DataPacket.nodeNumToDefaultId(from)
        val isFromLocalNode = myNodeNum != null && from == myNodeNum
        val toId =
            if (to == 0 || to == DataPacket.NODENUM_BROADCAST) {
                DataPacket.ID_BROADCAST
            } else {
                DataPacket.nodeNumToDefaultId(to)
            }

        val hashByteString = hash.toByteString()

        packets.forEach { packet ->
            // For sent messages, from is stored as ID_LOCAL, but SFPP packet has node number
            val fromMatches =
                packet.data.from == fromId || (isFromLocalNode && packet.data.from == DataPacket.ID_LOCAL)
            co.touchlab.kermit.Logger.d {
                "SFPP match check: packetFrom=${packet.data.from} fromId=$fromId " +
                    "isFromLocal=$isFromLocalNode fromMatches=$fromMatches " +
                    "packetTo=${packet.data.to} toId=$toId toMatches=${packet.data.to == toId}"
            }
            if (fromMatches && packet.data.to == toId) {
                // If it's already confirmed, don't downgrade it to routing
                if (packet.data.status == MessageStatus.SFPP_CONFIRMED && status == MessageStatus.SFPP_ROUTING) {
                    return@forEach
                }
                val newTime = if (rxTime > 0) rxTime * MILLISECONDS_IN_SECOND else packet.received_time
                val updatedData = packet.data.copy(status = status, sfppHash = hashByteString, time = newTime)
                dao.update(packet.copy(data = updatedData, sfpp_hash = hashByteString, received_time = newTime))
            }
        }

        reactions.forEach { reaction ->
            val reactionFrom = reaction.userId
            // For sent reactions, from is stored as ID_LOCAL, but SFPP packet has node number
            val fromMatches = reactionFrom == fromId || (isFromLocalNode && reactionFrom == DataPacket.ID_LOCAL)

            val toMatches = reaction.to == toId

            co.touchlab.kermit.Logger.d {
                "SFPP reaction match check: reactionFrom=$reactionFrom fromId=$fromId " +
                    "isFromLocal=$isFromLocalNode fromMatches=$fromMatches " +
                    "reactionTo=${reaction.to} toId=$toId toMatches=$toMatches"
            }

            if (fromMatches && (reaction.to == null || toMatches)) {
                if (reaction.status == MessageStatus.SFPP_CONFIRMED && status == MessageStatus.SFPP_ROUTING) {
                    return@forEach
                }
                val newTime = if (rxTime > 0) rxTime * MILLISECONDS_IN_SECOND else reaction.timestamp
                val updatedReaction =
                    reaction.copy(status = status, sfpp_hash = hashByteString, timestamp = newTime)
                dao.update(updatedReaction)
            }
        }
    }

    suspend fun updateSFPPStatusByHash(
        hash: ByteArray,
        status: MessageStatus = MessageStatus.SFPP_CONFIRMED,
        rxTime: Long = 0,
    ) = withContext(dispatchers.io) {
        val dao = dbManager.currentDb.value.packetDao()
        val hashByteString = hash.toByteString()
        dao.findPacketBySfppHash(hashByteString)?.let { packet ->
            // If it's already confirmed, don't downgrade it
            if (packet.data.status == MessageStatus.SFPP_CONFIRMED && status == MessageStatus.SFPP_ROUTING) {
                return@let
            }
            val newTime = if (rxTime > 0) rxTime * MILLISECONDS_IN_SECOND else packet.received_time
            val updatedData = packet.data.copy(status = status, sfppHash = hashByteString, time = newTime)
            dao.update(packet.copy(data = updatedData, sfpp_hash = hashByteString, received_time = newTime))
        }

        dao.findReactionBySfppHash(hashByteString)?.let { reaction ->
            if (reaction.status == MessageStatus.SFPP_CONFIRMED && status == MessageStatus.SFPP_ROUTING) {
                return@let
            }
            val newTime = if (rxTime > 0) rxTime * MILLISECONDS_IN_SECOND else reaction.timestamp
            val updatedReaction = reaction.copy(status = status, sfpp_hash = hashByteString, timestamp = newTime)
            dao.update(updatedReaction)
        }
    }

    suspend fun deleteMessages(uuidList: List<Long>) = withContext(dispatchers.io) {
        for (chunk in uuidList.chunked(DELETE_CHUNK_SIZE)) {
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

    suspend fun updateReaction(reaction: ReactionEntity) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().update(reaction) }

    suspend fun getReactionByPacketId(packetId: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getReactionByPacketId(packetId) }

    suspend fun findReactionsWithId(packetId: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().findReactionsWithId(packetId) }

    fun getFilteredCountFlow(contactKey: String): Flow<Int> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getFilteredCountFlow(contactKey) }

    suspend fun getFilteredCount(contactKey: String): Int =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getFilteredCount(contactKey) }

    fun getMessagesFromPaged(
        contactKey: String,
        includeFiltered: Boolean,
        getNode: suspend (String?) -> Node,
    ): Flow<PagingData<Message>> = Pager(
        config =
        PagingConfig(
            pageSize = MESSAGES_PAGE_SIZE,
            enablePlaceholders = false,
            initialLoadSize = MESSAGES_PAGE_SIZE,
        ),
        pagingSourceFactory = {
            dbManager.currentDb.value.packetDao().getMessagesFromPaged(contactKey, includeFiltered)
        },
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

    suspend fun setContactFilteringDisabled(contactKey: String, disabled: Boolean) = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().setContactFilteringDisabled(contactKey, disabled)
    }

    suspend fun clearPacketDB() = withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().deleteAll() }

    suspend fun migrateChannelsByPSK(oldSettings: List<ChannelSettings>, newSettings: List<ChannelSettings>) =
        withContext(dispatchers.io) {
            dbManager.currentDb.value.packetDao().migrateChannelsByPSK(oldSettings, newSettings)
        }

    suspend fun updateFilteredBySender(senderId: String, filtered: Boolean) {
        val pattern = "%\"from\":\"${senderId}\"%"
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().updateFilteredBySender(pattern, filtered) }
    }

    private fun org.meshtastic.core.database.dao.PacketDao.getAllWaypointsFlow(): Flow<List<Packet>> =
        getAllPackets(PortNum.WAYPOINT_APP.value)

    companion object {
        private const val CONTACTS_PAGE_SIZE = 30
        private const val MESSAGES_PAGE_SIZE = 50
        private const val DELETE_CHUNK_SIZE = 500
        private const val MILLISECONDS_IN_SECOND = 1000L
    }
}
