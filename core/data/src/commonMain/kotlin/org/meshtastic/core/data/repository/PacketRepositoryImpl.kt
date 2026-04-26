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
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.dao.NodeInfoDao
import org.meshtastic.core.database.entity.PacketEntity
import org.meshtastic.core.database.entity.toReaction
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ContactSettings
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Reaction
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.PortNum
import org.meshtastic.core.database.entity.ContactSettings as ContactSettingsEntity
import org.meshtastic.core.database.entity.Packet as RoomPacket
import org.meshtastic.core.database.entity.ReactionEntity as RoomReaction
import org.meshtastic.core.repository.PacketRepository as SharedPacketRepository

@Suppress("TooManyFunctions", "LongParameterList")
@Single
class PacketRepositoryImpl(private val dbManager: DatabaseProvider, private val dispatchers: CoroutineDispatchers) :
    SharedPacketRepository {

    override fun getWaypoints(): Flow<List<DataPacket>> = dbManager.currentDb
        .flatMapLatest { db -> db.packetDao().getAllWaypointsFlow() }
        .map { list -> list.map { it.data } }

    override fun getContacts(): Flow<Map<String, DataPacket>> = dbManager.currentDb
        .flatMapLatest { db -> db.packetDao().getContactKeys() }
        .map { map -> map.mapValues { it.value.data } }

    override fun getContactsPaged(): Flow<PagingData<DataPacket>> = Pager(
        config =
        PagingConfig(
            pageSize = CONTACTS_PAGE_SIZE,
            enablePlaceholders = false,
            initialLoadSize = CONTACTS_PAGE_SIZE,
        ),
        pagingSourceFactory = { dbManager.currentDb.value.packetDao().getContactKeysPaged() },
    )
        .flow
        .map { pagingData -> pagingData.map { it.data } }

    override suspend fun getMessageCount(contact: String): Int =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getMessageCount(contact) }

    override suspend fun getUnreadCount(contact: String): Int =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getUnreadCount(contact) }

    override fun getUnreadCountFlow(contact: String): Flow<Int> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getUnreadCountFlow(contact) }

    override fun getFirstUnreadMessageUuid(contact: String): Flow<Long?> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getFirstUnreadMessageUuid(contact) }

    override fun hasUnreadMessages(contact: String): Flow<Boolean> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().hasUnreadMessages(contact) }

    override fun getUnreadCountTotal(): Flow<Int> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getUnreadCountTotal() }

    override suspend fun clearUnreadCount(contact: String, timestamp: Long) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().clearUnreadCount(contact, timestamp) }

    override suspend fun clearAllUnreadCounts() =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().clearAllUnreadCounts() }

    override suspend fun updateLastReadMessage(contact: String, messageUuid: Long, lastReadTimestamp: Long) =
        withContext(dispatchers.io) {
            val dao = dbManager.currentDb.value.packetDao()
            val current = dao.getContactSettings(contact)
            val existingTimestamp = current?.lastReadMessageTimestamp ?: Long.MIN_VALUE
            if (lastReadTimestamp <= existingTimestamp) {
                return@withContext
            }
            val updated =
                (current ?: ContactSettingsEntity(contact_key = contact)).copy(
                    lastReadMessageUuid = messageUuid,
                    lastReadMessageTimestamp = lastReadTimestamp,
                )
            dao.upsertContactSettings(listOf(updated))
        }

    override suspend fun getQueuedPackets(): List<DataPacket> = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().getAllDataPackets().filter { it.status == MessageStatus.QUEUED }
    }

    suspend fun insertRoomPacket(packet: RoomPacket) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().insert(packet) }

    override suspend fun savePacket(
        myNodeNum: Int,
        contactKey: String,
        packet: DataPacket,
        receivedTime: Long,
        read: Boolean,
        filtered: Boolean,
    ) {
        val packetToSave =
            RoomPacket(
                uuid = 0L,
                myNodeNum = myNodeNum,
                packetId = packet.id,
                port_num = packet.dataType,
                contact_key = contactKey,
                received_time = receivedTime,
                read = read,
                data = packet,
                snr = packet.snr,
                rssi = packet.rssi,
                hopsAway = packet.hopsAway,
                filtered = filtered,
            )
        insertRoomPacket(packetToSave)
    }

    override suspend fun getMessagesFrom(
        contact: String,
        limit: Int?,
        includeFiltered: Boolean,
        getNode: suspend (String?) -> Node,
    ): Flow<List<Message>> = withContext(dispatchers.io) {
        val dao = dbManager.currentDb.value.packetDao()
        val flow =
            when {
                limit != null -> dao.getMessagesFrom(contact, limit)
                !includeFiltered -> dao.getMessagesFrom(contact, includeFiltered = false)
                else -> dao.getMessagesFrom(contact)
            }
        flow.mapLatest { packets ->
            val cachedGetNode = memoize(getNode)
            val replyIds = packets.mapNotNull { it.packet.data.replyId?.takeIf { id -> id != 0 } }.distinct()
            val replyMap = batchGetPacketsByIds(replyIds)
            packets.map { packet ->
                val message = packet.toMessage(cachedGetNode)
                val replyId = message.replyId?.takeIf { it != 0 }
                val originalMessage = replyId?.let { replyMap[it] }?.toMessage(cachedGetNode)
                if (originalMessage != null) message.copy(originalMessage = originalMessage) else message
            }
        }
    }

    override fun getMessagesFromPaged(contact: String, getNode: suspend (String?) -> Node): Flow<PagingData<Message>> =
        Pager(
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
                val cachedGetNode = memoize(getNode)
                val replyCache = mutableMapOf<Int, PacketEntity?>()
                pagingData.map { packet ->
                    val message = packet.toMessage(cachedGetNode)
                    val replyId = message.replyId?.takeIf { it != 0 }
                    val originalMessage =
                        replyId
                            ?.let { id -> replyCache.getOrPut(id) { getPacketByPacketIdInternal(id) } }
                            ?.toMessage(cachedGetNode)
                    if (originalMessage != null) message.copy(originalMessage = originalMessage) else message
                }
            }

    override fun getMessagesFromPaged(
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
            val cachedGetNode = memoize(getNode)
            val replyCache = mutableMapOf<Int, PacketEntity?>()
            pagingData.map { packet ->
                val message = packet.toMessage(cachedGetNode)
                val replyId = message.replyId?.takeIf { it != 0 }
                val originalMessage =
                    replyId
                        ?.let { id -> replyCache.getOrPut(id) { getPacketByPacketIdInternal(id) } }
                        ?.toMessage(cachedGetNode)
                if (originalMessage != null) message.copy(originalMessage = originalMessage) else message
            }
        }

    override suspend fun updateMessageStatus(d: DataPacket, m: MessageStatus) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().updateMessageStatus(d, m) }

    override suspend fun updateMessageId(d: DataPacket, id: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().updateMessageId(d, id) }

    override suspend fun getPacketById(id: Int): DataPacket? =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getPacketById(id)?.data }

    override suspend fun getPacketByPacketId(packetId: Int): DataPacket? = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().getPacketByPacketId(packetId)?.packet?.data
    }

    private suspend fun getPacketByPacketIdInternal(packetId: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getPacketByPacketId(packetId) }

    private suspend fun batchGetPacketsByIds(ids: List<Int>): Map<Int, PacketEntity> = if (ids.isEmpty()) {
        emptyMap()
    } else {
        withContext(dispatchers.io) {
            val dao = dbManager.currentDb.value.packetDao()
            ids.chunked(NodeInfoDao.MAX_BIND_PARAMS)
                .flatMap { dao.getPacketsByPacketIds(it) }
                .associateBy { it.packet.packetId }
        }
    }

    private fun memoize(getNode: suspend (String?) -> Node): suspend (String?) -> Node {
        val cache = mutableMapOf<String?, Node>()
        return { id -> cache.getOrPut(id) { getNode(id) } }
    }

    override suspend fun insert(
        packet: DataPacket,
        myNodeNum: Int,
        contactKey: String,
        receivedTime: Long,
        read: Boolean,
        filtered: Boolean,
    ) {
        val packetToSave =
            RoomPacket(
                uuid = 0L,
                myNodeNum = myNodeNum,
                packetId = packet.id,
                port_num = packet.dataType,
                contact_key = contactKey,
                received_time = receivedTime,
                read = read,
                data = packet,
                snr = packet.snr,
                rssi = packet.rssi,
                hopsAway = packet.hopsAway,
                filtered = filtered,
            )
        insertRoomPacket(packetToSave)
    }

    override suspend fun update(packet: DataPacket, routingError: Int): Unit = withContext(dispatchers.io) {
        val dao = dbManager.currentDb.value.packetDao()
        // Match on key fields that identify the packet, rather than the entire data object
        dao.findPacketsWithId(packet.id)
            .find { it.data.id == packet.id && it.data.from == packet.from && it.data.to == packet.to }
            ?.let { existing ->
                val updated =
                    if (routingError >= 0) {
                        existing.copy(data = packet, routingError = routingError)
                    } else {
                        existing.copy(data = packet)
                    }
                dao.update(updated)
            }
    }

    override suspend fun insertReaction(reaction: Reaction, myNodeNum: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().insert(reaction.toEntity(myNodeNum)) }

    override suspend fun updateReaction(reaction: Reaction) = withContext(dispatchers.io) {
        val dao = dbManager.currentDb.value.packetDao()
        dao.findReactionsWithId(reaction.packetId)
            .find { it.userId == reaction.user.id && it.emoji == reaction.emoji }
            ?.let { dao.update(reaction.toEntity(it.myNodeNum)) } ?: Unit
    }

    override suspend fun getReactionByPacketId(packetId: Int): Reaction? = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().getReactionByPacketId(packetId)?.toReaction { null }
    }

    override suspend fun findPacketsWithId(packetId: Int): List<DataPacket> = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().findPacketsWithId(packetId).map { it.data }
    }

    private suspend fun findPacketsWithIdInternal(packetId: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().findPacketsWithId(packetId) }

    override suspend fun findReactionsWithId(packetId: Int): List<Reaction> = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().findReactionsWithId(packetId).toReaction { null }
    }

    private suspend fun findReactionsWithIdInternal(packetId: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().findReactionsWithId(packetId) }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun updateSFPPStatus(
        packetId: Int,
        from: Int,
        to: Int,
        hash: ByteArray,
        status: MessageStatus,
        rxTime: Long,
        myNodeNum: Int?,
    ) = withContext(dispatchers.io) {
        val dao = dbManager.currentDb.value.packetDao()
        val packets = findPacketsWithIdInternal(packetId)
        val reactions = findReactionsWithIdInternal(packetId)
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

    override suspend fun updateSFPPStatusByHash(hash: ByteArray, status: MessageStatus, rxTime: Long): Unit =
        withContext(dispatchers.io) {
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

    override suspend fun deleteMessages(uuidList: List<Long>) = withContext(dispatchers.io) {
        for (chunk in uuidList.chunked(DELETE_CHUNK_SIZE)) {
            // Fetch DAO per chunk to avoid holding a stale reference if the active DB switches
            dbManager.currentDb.value.packetDao().deleteMessages(chunk)
        }
    }

    override suspend fun deleteContacts(contactList: List<String>) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().deleteContacts(contactList) }

    override suspend fun deleteWaypoint(id: Int) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().deleteWaypoint(id) }

    suspend fun delete(packet: RoomPacket) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().delete(packet) }

    suspend fun update(packet: RoomPacket) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().update(packet) }

    override fun getContactSettings(): Flow<Map<String, ContactSettings>> = dbManager.currentDb
        .flatMapLatest { db -> db.packetDao().getContactSettings() }
        .map { map -> map.mapValues { it.value.toShared() } }

    override suspend fun getContactSettings(contact: String): ContactSettings = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().getContactSettings(contact)?.toShared() ?: ContactSettings(contact)
    }

    override suspend fun setMuteUntil(contacts: List<String>, until: Long) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().setMuteUntil(contacts, until) }

    suspend fun insertReaction(reaction: RoomReaction) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().insert(reaction) }

    suspend fun updateReaction(reaction: RoomReaction) =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().update(reaction) }

    override fun getFilteredCountFlow(contactKey: String): Flow<Int> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getFilteredCountFlow(contactKey) }

    override suspend fun getFilteredCount(contactKey: String): Int =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getFilteredCount(contactKey) }

    override suspend fun setContactFilteringDisabled(contactKey: String, disabled: Boolean) =
        withContext(dispatchers.io) {
            dbManager.currentDb.value.packetDao().setContactFilteringDisabled(contactKey, disabled)
        }

    override suspend fun clearPacketDB() =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().deleteAll() }

    override suspend fun migrateChannelsByPSK(oldSettings: List<ChannelSettings>, newSettings: List<ChannelSettings>) =
        withContext(dispatchers.io) {
            dbManager.currentDb.value.packetDao().migrateChannelsByPSK(oldSettings, newSettings)
        }

    override suspend fun updateFilteredBySender(senderId: String, filtered: Boolean) {
        val pattern = "%\"from\":\"${senderId}\"%"
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().updateFilteredBySender(pattern, filtered) }
    }

    private fun org.meshtastic.core.database.dao.PacketDao.getAllWaypointsFlow(): Flow<List<RoomPacket>> =
        getAllPackets(PortNum.WAYPOINT_APP.value)

    private fun ContactSettingsEntity.toShared() = ContactSettings(
        contactKey = contact_key,
        muteUntil = muteUntil,
        lastReadMessageUuid = lastReadMessageUuid,
        lastReadMessageTimestamp = lastReadMessageTimestamp,
        filteringDisabled = filteringDisabled,
        isMuted = isMuted,
    )

    private fun Reaction.toEntity(myNodeNum: Int) = RoomReaction(
        myNodeNum = myNodeNum,
        replyId = replyId,
        userId = user.id,
        emoji = emoji,
        timestamp = timestamp,
        snr = snr,
        rssi = rssi,
        hopsAway = hopsAway,
        packetId = packetId,
        status = status,
        routingError = routingError,
        relays = relays,
        relayNode = relayNode,
        to = to,
        channel = channel,
        sfpp_hash = sfppHash,
    )

    companion object {
        private const val CONTACTS_PAGE_SIZE = 30
        private const val MESSAGES_PAGE_SIZE = 50
        private const val DELETE_CHUNK_SIZE = 500
        private const val MILLISECONDS_IN_SECOND = 1000L
    }
}
