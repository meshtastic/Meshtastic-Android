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
package org.meshtastic.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
import org.meshtastic.core.model.NodeAddress
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

    override fun getContactsPaged(): Flow<PagingData<Pair<String, DataPacket>>> = Pager(
        config =
        PagingConfig(
            pageSize = CONTACTS_PAGE_SIZE,
            enablePlaceholders = false,
            initialLoadSize = CONTACTS_PAGE_SIZE,
        ),
        pagingSourceFactory = { dbManager.currentDb.value.packetDao().getContactKeysPaged() },
    )
        .flow
        .map { pagingData -> pagingData.map { it.contact_key to it.data } }

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

    // One-shot writes go through withDb so they register with the cross-transport merge drain barrier and pick up
    // its closed-pool retry (see DatabaseProvider). Reads and Flow/Paging factories stay on currentDb by design.

    override suspend fun clearUnreadCount(contact: String, timestamp: Long) {
        withContext(dispatchers.io + NonCancellable) {
            dbManager.withDb { it.packetDao().clearUnreadCount(contact, timestamp) }
        }
    }

    override suspend fun clearAllUnreadCounts() {
        withContext(dispatchers.io + NonCancellable) { dbManager.withDb { it.packetDao().clearAllUnreadCounts() } }
    }

    override suspend fun updateLastReadMessage(contact: String, messageUuid: Long, lastReadTimestamp: Long) {
        withContext(dispatchers.io + NonCancellable) {
            dbManager.withDb { it.packetDao().updateLastReadMessage(contact, messageUuid, lastReadTimestamp) }
        }
    }

    override suspend fun getQueuedPackets(): List<DataPacket> = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().getAllDataPackets().filter { it.status == MessageStatus.QUEUED }
    }

    suspend fun insertRoomPacket(packet: RoomPacket) {
        withContext(dispatchers.io + NonCancellable) { dbManager.withDb { it.packetDao().insert(packet) } }
    }

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
                messageText = packet.text.orEmpty(),
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

    override suspend fun updateMessageStatus(d: DataPacket, m: MessageStatus) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().updateMessageStatus(d, m) } }
    }

    override suspend fun updateMessageId(d: DataPacket, id: Int) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().updateMessageId(d, id) } }
    }

    override suspend fun setMessageTranslation(uuid: Long, translatedText: String) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().setTranslation(uuid, translatedText) } }
    }

    override suspend fun setShowTranslated(uuid: Long, showTranslated: Boolean) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().setShowTranslated(uuid, showTranslated) } }
    }

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
                messageText = packet.text.orEmpty(),
            )
        insertRoomPacket(packetToSave)
    }

    override suspend fun update(packet: DataPacket, routingError: Int): Unit =
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().updatePacketByKey(packet, routingError) } }

    override suspend fun insertReaction(reaction: Reaction, myNodeNum: Int) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().insert(reaction.toEntity(myNodeNum)) } }
    }

    override suspend fun updateReaction(reaction: Reaction) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().updateReactionByKey(reaction.toEntity(0)) } }
    }

    override suspend fun getReactionByPacketId(packetId: Int): Reaction? = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().getReactionByPacketId(packetId)?.toReaction { null }
    }

    override suspend fun findPacketsWithId(packetId: Int): List<DataPacket> = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().findPacketsWithId(packetId).map { it.data }
    }

    override suspend fun findReactionsWithId(packetId: Int): List<Reaction> = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().findReactionsWithId(packetId).toReaction { null }
    }

    override suspend fun updateSFPPStatus(
        packetId: Int,
        from: Int,
        to: Int,
        hash: ByteArray,
        status: MessageStatus,
        rxTime: Long,
        myNodeNum: Int?,
    ) = withContext(dispatchers.io) {
        dbManager.withDb {
            it.packetDao().applySFPPStatus(packetId, from, to, hash.toByteString(), status, rxTime, myNodeNum)
        }
        Unit
    }

    override suspend fun updateSFPPStatusByHash(hash: ByteArray, status: MessageStatus, rxTime: Long): Unit =
        withContext(dispatchers.io) {
            dbManager.withDb { it.packetDao().applySFPPStatusByHash(hash.toByteString(), status, rxTime) }
            Unit
        }

    override suspend fun deleteMessages(uuidList: List<Long>) = withContext(dispatchers.io) {
        dbManager.withDb { it.packetDao().deleteMessagesAtomic(uuidList) }
        Unit
    }

    override suspend fun deleteContacts(contactList: List<String>) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().deleteContacts(contactList) } }
    }

    override suspend fun deleteWaypoint(id: Int) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().deleteWaypoint(id) } }
    }

    suspend fun delete(packet: RoomPacket) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().delete(packet) } }
    }

    suspend fun update(packet: RoomPacket) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().update(packet) } }
    }

    override fun getContactSettings(): Flow<Map<String, ContactSettings>> = dbManager.currentDb
        .flatMapLatest { db -> db.packetDao().getContactSettings() }
        .map { map -> map.mapValues { it.value.toShared() } }

    override suspend fun getContactSettings(contact: String): ContactSettings = withContext(dispatchers.io) {
        dbManager.currentDb.value.packetDao().getContactSettings(contact)?.toShared() ?: ContactSettings(contact)
    }

    override suspend fun setMuteUntil(contacts: List<String>, until: Long) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().setMuteUntil(contacts, until) } }
    }

    suspend fun insertReaction(reaction: RoomReaction) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().insert(reaction) } }
    }

    suspend fun updateReaction(reaction: RoomReaction) {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().update(reaction) } }
    }

    override fun getFilteredCountFlow(contactKey: String): Flow<Int> =
        dbManager.currentDb.flatMapLatest { db -> db.packetDao().getFilteredCountFlow(contactKey) }

    override suspend fun getFilteredCount(contactKey: String): Int =
        withContext(dispatchers.io) { dbManager.currentDb.value.packetDao().getFilteredCount(contactKey) }

    override suspend fun setContactFilteringDisabled(contactKey: String, disabled: Boolean) {
        withContext(dispatchers.io) {
            dbManager.withDb { it.packetDao().setContactFilteringDisabled(contactKey, disabled) }
        }
    }

    override suspend fun clearPacketDB() {
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().deleteAll() } }
    }

    override suspend fun migrateChannelsByPSK(oldSettings: List<ChannelSettings>, newSettings: List<ChannelSettings>) {
        withContext(dispatchers.io) {
            dbManager.withDb { it.packetDao().migrateChannelsByPSK(oldSettings, newSettings) }
        }
    }

    override suspend fun updateFilteredBySender(senderId: String, filtered: Boolean) {
        val pattern = "%\"from\":\"${senderId}\"%"
        withContext(dispatchers.io) { dbManager.withDb { it.packetDao().updateFilteredBySender(pattern, filtered) } }
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

    override fun searchMessages(query: String, contactKey: String?, getNode: (String?) -> Node): Flow<List<Message>> {
        val sanitized = sanitizeFtsQuery(query)
        if (sanitized.isBlank()) return flowOf(emptyList())
        return dbManager.currentDb.flatMapLatest { db ->
            kotlinx.coroutines.flow.flow {
                val dao = db.packetDao()
                val packets =
                    if (contactKey != null) {
                        dao.searchMessagesInConversation(sanitized, contactKey)
                    } else {
                        dao.searchMessages(sanitized)
                    }
                emit(
                    packets.map { packet ->
                        val node = getNode(packet.data.from)
                        val isFromLocal =
                            node.user.id == NodeAddress.ID_LOCAL ||
                                (packet.myNodeNum != 0 && node.num == packet.myNodeNum)
                        Message(
                            uuid = packet.uuid,
                            receivedTime = packet.received_time,
                            node = node,
                            text = packet.data.text.orEmpty(),
                            fromLocal = isFromLocal,
                            time = org.meshtastic.core.model.util.getShortDateTime(packet.data.time),
                            snr = packet.snr,
                            rssi = packet.rssi,
                            hopsAway = packet.hopsAway,
                            read = packet.read,
                            status = packet.data.status,
                            routingError = packet.routingError,
                            packetId = packet.packetId,
                            emojis = emptyList(),
                            replyId = packet.data.replyId,
                        )
                    },
                )
            }
        }
    }

    /**
     * Sanitizes a user query for FTS5 by wrapping each token in double quotes. This escapes FTS5 special characters (*,
     * -, NEAR, etc.) while still allowing multi-word searches as implicit AND queries.
     */
    private fun sanitizeFtsQuery(query: String): String =
        query.split("\\s+".toRegex()).filter { it.isNotBlank() }.joinToString(" ") { "\"${it.replace("\"", "")}\"" }

    companion object {
        private const val CONTACTS_PAGE_SIZE = 30
        private const val MESSAGES_PAGE_SIZE = 50
    }
}
