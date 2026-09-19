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
package org.meshtastic.core.database.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.MapColumn
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import okio.ByteString
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.DatabaseConstants.SQLITE_MAX_BIND_PARAMETERS
import org.meshtastic.core.database.entity.ContactSettings
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.PacketEntity
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.ChannelKeyChange
import org.meshtastic.core.model.util.ConversationSlot
import org.meshtastic.proto.MeshPacket

@Suppress("TooManyFunctions", "LargeClass")
@Dao
interface PacketDao {

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = :portNum
    ORDER BY received_time ASC
    """,
    )
    fun getAllPackets(portNum: Int): Flow<List<Packet>>

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND filtered = 0
    ORDER BY received_time DESC
    """,
    )
    fun getContactKeys(): Flow<
        Map<
            @MapColumn(columnName = "contact_key")
            String,
            Packet,
            >,
        >

    @Query(
        """
    SELECT p.* FROM packet p
    INNER JOIN (
        SELECT contact_key, MAX(received_time) as max_time
        FROM packet
        WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
            AND port_num = 1 AND filtered = 0
        GROUP BY contact_key
    ) latest ON p.contact_key = latest.contact_key AND p.received_time = latest.max_time
    WHERE (p.myNodeNum = 0 OR p.myNodeNum = (SELECT myNodeNum FROM my_node))
        AND p.port_num = 1 AND p.filtered = 0
    GROUP BY p.contact_key
    ORDER BY p.received_time DESC
    """,
    )
    fun getContactKeysPaged(): PagingSource<Int, Packet>

    @Query(
        """
    SELECT COUNT(*) FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact
    """,
    )
    suspend fun getMessageCount(contact: String): Int

    @Query(
        """
    SELECT COUNT(*) FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact AND read = 0 AND filtered = 0
    """,
    )
    suspend fun getUnreadCount(contact: String): Int

    @Query(
        """
    SELECT COUNT(*) FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact AND read = 0 AND filtered = 0
    """,
    )
    fun getUnreadCountFlow(contact: String): Flow<Int>

    @Query(
        """
    SELECT uuid FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact AND read = 0 AND filtered = 0
    ORDER BY received_time ASC
    LIMIT 1
    """,
    )
    fun getFirstUnreadMessageUuid(contact: String): Flow<Long?>

    @Query(
        """
    SELECT COUNT(*) > 0 FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact AND read = 0 AND filtered = 0
    """,
    )
    fun hasUnreadMessages(contact: String): Flow<Boolean>

    @Query(
        """
    SELECT COUNT(*) FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND read = 0 AND filtered = 0
    """,
    )
    fun getUnreadCountTotal(): Flow<Int>

    @Query(
        """
    UPDATE packet
    SET read = 1
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact AND read = 0 AND filtered = 0 AND received_time <= :timestamp
    """,
    )
    suspend fun clearUnreadCount(contact: String, timestamp: Long)

    @Query(
        """
    UPDATE packet
    SET read = 1
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND read = 0 AND filtered = 0
    """,
    )
    suspend fun clearAllUnreadCounts()

    @Upsert suspend fun insert(packet: Packet)

    /** Inserts a new packet row and returns its auto-generated stable database UUID. */
    @Insert suspend fun insertAndGetId(packet: Packet): Long

    @Transaction
    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact
    ORDER BY received_time DESC
    """,
    )
    fun getMessagesFrom(contact: String): Flow<List<PacketEntity>>

    @Transaction
    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact
    ORDER BY received_time DESC
    LIMIT :limit
    """,
    )
    fun getMessagesFrom(contact: String, limit: Int): Flow<List<PacketEntity>>

    @Transaction
    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact
        AND (filtered = 0 OR :includeFiltered = 1)
    ORDER BY received_time DESC
    """,
    )
    fun getMessagesFrom(contact: String, includeFiltered: Boolean): Flow<List<PacketEntity>>

    @Transaction
    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact
    ORDER BY received_time DESC
    """,
    )
    fun getMessagesFromPaged(contact: String): PagingSource<Int, PacketEntity>

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND data = :data
    """,
    )
    suspend fun findDataPacket(data: DataPacket): Packet?

    @Query("DELETE FROM packet WHERE uuid in (:uuidList)")
    suspend fun deletePackets(uuidList: List<Long>)

    @Query(
        """
    DELETE FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND contact_key IN (:contactList)
    """,
    )
    suspend fun deleteContacts(contactList: List<String>)

    @Query("DELETE FROM packet WHERE uuid=:uuid")
    suspend fun delete(uuid: Long)

    @Transaction
    suspend fun delete(packet: Packet) {
        delete(packet.uuid)
    }

    @Query("SELECT packet_id FROM packet WHERE uuid IN (:uuidList)")
    suspend fun getPacketIdsFrom(uuidList: List<Long>): List<Int>

    @Query(
        """
        DELETE FROM reactions 
        WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND reply_id IN (:packetIds)
        """,
    )
    suspend fun deleteReactions(packetIds: List<Int>)

    @Transaction
    suspend fun deleteMessages(uuidList: List<Long>) {
        val packetIds = getPacketIdsFrom(uuidList)
        if (packetIds.isNotEmpty()) {
            deleteReactions(packetIds)
        }
        deletePackets(uuidList)
    }

    @Update suspend fun update(packet: Packet)

    @Transaction
    suspend fun updateMessageStatus(data: DataPacket, m: MessageStatus) {
        val new = data.copy(status = m)
        // Match on key fields that identify the packet, rather than the entire data object
        findPacketsWithId(data.id)
            .find { it.data.id == data.id && it.data.from == data.from && it.data.to == data.to }
            ?.let { update(it.copy(data = new)) }
    }

    @Transaction
    suspend fun updateMessageStatusByPersistedId(myNodeNum: Int, uuid: Long, status: MessageStatus) {
        getPacketByPersistedId(myNodeNum, uuid)?.let { update(it.copy(data = it.data.copy(status = status))) }
    }

    /**
     * Atomically claims one stable packet row for sending. A returned QUEUED packet means this call performed the
     * QUEUED -> ENROUTE transition and owns the send; any other returned status means another path already handled it.
     *
     * A packet whose conversation was archived while it sat in the queue is never claimed. Its stored channel index
     * still points at the slot it was composed for, and something else occupies that slot now, so sending it would put
     * the message on a channel the user never chose. This runs in the same transaction as the status transition, which
     * is what makes the check hold against a reconciliation landing mid-send.
     */
    @Transaction
    suspend fun claimQueuedPacket(myNodeNum: Int, uuid: Long): Packet? {
        val packet =
            getPacketByPersistedId(myNodeNum, uuid)?.takeUnless { ContactKey(it.contact_key).isRetired } ?: return null
        if (packet.data.status == MessageStatus.QUEUED) {
            update(packet.copy(data = packet.data.copy(status = MessageStatus.ENROUTE)))
        }
        return packet
    }

    /** Legacy claim used only by pre-upgrade WorkManager jobs whose mesh packet ID still resolves to one row. */
    @Transaction
    suspend fun claimQueuedPacketByPacketIdIfUnique(packetId: Int): Packet? {
        val packet =
            findPacketsWithId(packetId).singleOrNull()?.takeUnless { ContactKey(it.contact_key).isRetired }
                ?: return null
        if (packet.data.status == MessageStatus.QUEUED) {
            update(packet.copy(data = packet.data.copy(status = MessageStatus.ENROUTE)))
        }
        return packet
    }

    /** Rolls back a failed send only while the exact claimed row is still ENROUTE, preserving any racing ACK update. */
    @Transaction
    suspend fun rollbackEnroutePacket(myNodeNum: Int, uuid: Long): Boolean {
        val packet = getPacketByPersistedId(myNodeNum, uuid)
        val canRollback = packet?.data?.status == MessageStatus.ENROUTE
        if (canRollback) update(packet.copy(data = packet.data.copy(status = MessageStatus.QUEUED)))
        return canRollback
    }

    /**
     * Resolves only an unambiguous row matching the identity available on an outgoing protobuf packet. Mesh packet IDs
     * are sender-scoped, so ID-only lookup can select an inbound packet or an unrelated command with the same ID.
     */
    @Transaction
    suspend fun resolveOutgoingPacket(packet: MeshPacket): Packet? =
        outgoingCandidates(packet, findPacketsWithId(packet.id)).singleOrNull()

    /**
     * Resolves and conditionally applies a queue-stage status without racing a terminal ACK/NAK update.
     *
     * @return the resolved row as read before the update, or null when no unambiguous outgoing row matches.
     */
    @Transaction
    suspend fun applyOutgoingQueueStatus(packet: MeshPacket, status: MessageStatus): Packet? {
        val match = resolveOutgoingPacket(packet) ?: return null
        if (shouldApplyOutgoingQueueStatus(match.data.status, status)) {
            update(match.copy(data = match.data.copy(status = status)))
        }
        return match
    }

    /**
     * Reaction equivalent of [applyOutgoingQueueStatus], restricted to one unambiguous outgoing row.
     *
     * @return the resolved reaction as read before the update, or null when no unambiguous non-received row matches.
     */
    @Transaction
    suspend fun applyOutgoingReactionQueueStatus(packetId: Int, status: MessageStatus): ReactionEntity? {
        val match =
            findReactionsWithId(packetId).filter { it.status != MessageStatus.RECEIVED }.singleOrNull() ?: return null
        if (shouldApplyOutgoingQueueStatus(match.status, status)) update(match.copy(status = status))
        return match
    }

    private fun outgoingCandidates(packet: MeshPacket, stored: List<Packet>): List<Packet> {
        val portNum = packet.decoded?.portnum?.value
        return stored.filter {
            it.data.from.matchesNodeNum(packet.from, packet.from) &&
                it.data.to.matchesNodeNum(packet.to, packet.from) &&
                (portNum == null || it.data.dataType == portNum)
        }
    }

    /** Updates the unique outgoing row, preferring the only candidate already at [status] when duplicates exist. */
    @Transaction
    suspend fun updateOutgoingMessageStatus(packet: MeshPacket, status: MessageStatus): Packet? {
        val matches = outgoingCandidates(packet, findPacketsWithId(packet.id))
        val alreadyAtStatus = matches.filter { it.data.status == status }
        val match =
            when {
                alreadyAtStatus.size == 1 -> alreadyAtStatus.single()
                alreadyAtStatus.isNotEmpty() -> null
                else -> matches.singleOrNull()
            }
        return match?.also { if (it.data.status != status) update(it.copy(data = it.data.copy(status = status))) }
    }

    @Transaction
    suspend fun updateMessageId(data: DataPacket, id: Int) {
        val new = data.copy(id = id)
        // Match on key fields that identify the packet
        findPacketsWithId(data.id)
            .find { it.data.id == data.id && it.data.from == data.from && it.data.to == data.to }
            ?.let { update(it.copy(data = new, packetId = id)) }
    }

    @Query(
        """
    SELECT data FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
    ORDER BY received_time ASC
    """,
    )
    suspend fun getDataPackets(): List<DataPacket>

    @Transaction
    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND packet_id = :requestId
    ORDER BY received_time DESC
    """,
    )
    suspend fun getPacketById(requestId: Int): Packet?

    @Transaction
    @Query(
        """
        SELECT * FROM packet 
        WHERE packet_id = :packetId 
        AND (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        LIMIT 1
        """,
    )
    suspend fun getPacketByPacketId(packetId: Int): PacketEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM packet
        WHERE packet_id = :packetId
        AND contact_key = :contactKey
        AND (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        """,
    )
    suspend fun getPacketsByPacketIdAndContact(packetId: Int, contactKey: String): List<PacketEntity>

    @Query(
        """
        SELECT * FROM packet
        WHERE uuid = :uuid
        AND myNodeNum = :myNodeNum
        AND (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        LIMIT 1
        """,
    )
    suspend fun getPacketByPersistedId(myNodeNum: Int, uuid: Long): Packet?

    @Transaction
    @Query(
        """
        SELECT * FROM packet
        WHERE packet_id IN (:packetIds)
        AND (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        """,
    )
    suspend fun getPacketsByPacketIds(packetIds: List<Int>): List<PacketEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM packet
        WHERE packet_id IN (:packetIds)
        AND contact_key = :contactKey
        AND (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        """,
    )
    suspend fun getPacketsByPacketIdsAndContact(packetIds: List<Int>, contactKey: String): List<PacketEntity>

    @Query(
        """
        SELECT * FROM packet 
        WHERE packet_id = :packetId 
        AND (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        """,
    )
    suspend fun findPacketsWithId(packetId: Int): List<Packet>

    @Transaction
    @Query(
        """
        SELECT * FROM packet 
        WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND substr(sfpp_hash, 1, 8) = substr(:hash, 1, 8)
        """,
    )
    suspend fun findPacketBySfppHash(hash: ByteString): Packet?

    // Fetches all DataPackets for the current node, ordered by time.
    // Callers should filter by status in Kotlin (avoids SQLite json_extract dependency).
    @Query(
        """
    SELECT data FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
    ORDER BY received_time ASC
    """,
    )
    suspend fun getAllDataPackets(): List<DataPacket>

    @Query(
        """
        SELECT * FROM packet
        WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        ORDER BY received_time ASC
        """,
    )
    suspend fun getAllPersistedPackets(): List<Packet>

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 8
    ORDER BY received_time ASC
    """,
    )
    suspend fun getAllWaypoints(): List<Packet>

    @Transaction
    suspend fun deleteWaypoint(id: Int) {
        val uuidList = getAllWaypoints().filter { it.data.waypoint?.id == id }.map { it.uuid }
        deleteMessages(uuidList)
    }

    @Query("SELECT * FROM contact_settings")
    fun getContactSettings(): Flow<
        Map<
            @MapColumn(columnName = "contact_key")
            String,
            ContactSettings,
            >,
        >

    @Query("SELECT * FROM contact_settings WHERE contact_key = :contact")
    suspend fun getContactSettings(contact: String): ContactSettings?

    @Upsert suspend fun upsertContactSettings(contacts: List<ContactSettings>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContactSettingsIgnore(contacts: List<ContactSettings>)

    @Query("UPDATE contact_settings SET muteUntil = :muteUntil WHERE contact_key IN (:contactKeys)")
    suspend fun updateMuteUntil(contactKeys: List<String>, muteUntil: Long)

    @Query("UPDATE contact_settings SET draft = :draft WHERE contact_key = :contact")
    suspend fun updateDraft(contact: String, draft: String)

    /**
     * Persists the unsent composer text for [contact], creating the settings row if absent. INSERT OR IGNORE plus a
     * targeted UPDATE, like [updateLastReadMessage], so mute and filtering survive without a read-modify-write.
     */
    @Transaction
    suspend fun setDraft(contact: String, draft: String) {
        insertContactSettingsIgnore(listOf(ContactSettings(contact_key = contact)))
        updateDraft(contact, draft)
    }

    @Query("SELECT draft FROM contact_settings WHERE contact_key = :contact LIMIT 1")
    suspend fun getDraft(contact: String): String?

    @Query("UPDATE contact_settings SET pinned = :pinned WHERE contact_key IN (:contactKeys)")
    suspend fun updatePinned(contactKeys: List<String>, pinned: Boolean)

    @Transaction
    suspend fun setPinned(contactKeys: List<String>, pinned: Boolean) {
        // Select-all can hand this every conversation, and SQLite rejects an IN clause past its bind-parameter limit.
        for (chunk in contactKeys.chunked(SQLITE_MAX_BIND_PARAMETERS)) {
            insertContactSettingsIgnore(chunk.map { ContactSettings(contact_key = it) })
            updatePinned(chunk, pinned)
        }
    }

    /**
     * Unconditional watermark rewind, for marking a conversation unread again.
     *
     * [updateLastReadMessageIfNewer] refuses to move the watermark backwards, which is right for the read path but
     * would strand a mark-unread: the packet's `read` flag would be 0 while the watermark still claimed everything was
     * read, and the message screen's clear path — which no-ops unless the incoming timestamp is newer — could never
     * clear the badge again.
     */
    @Query(
        """
        UPDATE contact_settings
        SET last_read_message_uuid = :messageUuid,
            last_read_message_timestamp = :lastReadTimestamp
        WHERE contact_key = :contact
        """,
    )
    suspend fun rewindLastReadMessage(contact: String, messageUuid: Long?, lastReadTimestamp: Long?)

    @Query(
        """
    SELECT uuid FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact AND filtered = 0
    ORDER BY received_time DESC
    LIMIT 1
    """,
    )
    suspend fun getNewestMessageUuid(contact: String): Long?

    @Query("UPDATE packet SET read = 0 WHERE uuid = :uuid")
    suspend fun markPacketUnread(uuid: Long)

    /**
     * Marks [contact] unread by flipping its newest message back to unread and rewinding the read watermark past it.
     * Both writes are needed: the badge counts `read = 0` packets, while the watermark is what the message screen
     * compares against when it later clears the conversation.
     */
    @Transaction
    suspend fun markContactUnread(contact: String) {
        val newest = getNewestMessageUuid(contact) ?: return
        markPacketUnread(newest)
        insertContactSettingsIgnore(listOf(ContactSettings(contact_key = contact)))
        rewindLastReadMessage(contact, messageUuid = null, lastReadTimestamp = null)
    }

    @Transaction
    suspend fun setMuteUntil(contacts: List<String>, until: Long) {
        val absoluteMuteUntil =
            when {
                until == Long.MAX_VALUE -> Long.MAX_VALUE
                until == 0L -> 0L
                else -> nowMillis + until
            }
        // Ensure rows exist for all contacts (IGNORE avoids overwriting existing data)
        insertContactSettingsIgnore(contacts.map { ContactSettings(contact_key = it) })
        // Atomic column-level update — no read-then-write race
        updateMuteUntil(contacts, absoluteMuteUntil)
    }

    @Upsert suspend fun insert(reaction: ReactionEntity)

    @Update suspend fun update(reaction: ReactionEntity)

    @Query(
        """
        SELECT * FROM reactions 
        WHERE packet_id = :packetId
        AND (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        """,
    )
    suspend fun findReactionsWithId(packetId: Int): List<ReactionEntity>

    @Query(
        """
        SELECT * FROM reactions 
        WHERE packet_id = :packetId 
        AND (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        LIMIT 1
        """,
    )
    suspend fun getReactionByPacketId(packetId: Int): ReactionEntity?

    @Query(
        """
        SELECT * FROM reactions
        WHERE status = :status
        AND (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        """,
    )
    suspend fun getReactionsByStatus(status: MessageStatus): List<ReactionEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM reactions 
        WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND substr(sfpp_hash, 1, 8) = substr(:hash, 1, 8)
        """,
    )
    suspend fun findReactionBySfppHash(hash: ByteString): ReactionEntity?

    @Query(
        """
        SELECT COUNT(*) FROM packet
        WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
            AND port_num = 1 AND contact_key = :contact AND filtered = 1
        """,
    )
    suspend fun getFilteredCount(contact: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM packet
        WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
            AND port_num = 1 AND contact_key = :contact AND filtered = 1
        """,
    )
    fun getFilteredCountFlow(contact: String): Flow<Int>

    @Transaction
    @Query(
        """
        SELECT * FROM packet
        WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
            AND port_num = 1 AND contact_key = :contact
            AND (filtered = 0 OR :includeFiltered = 1)
        ORDER BY received_time DESC
        """,
    )
    fun getMessagesFromPaged(contact: String, includeFiltered: Boolean): PagingSource<Int, PacketEntity>

    @Query("SELECT filtering_disabled FROM contact_settings WHERE contact_key = :contact")
    suspend fun getContactFilteringDisabled(contact: String): Boolean?

    @Transaction
    suspend fun setContactFilteringDisabled(contact: String, disabled: Boolean) {
        val settings =
            getContactSettings(contact)?.copy(filteringDisabled = disabled)
                ?: ContactSettings(contact_key = contact, filteringDisabled = disabled)
        upsertContactSettings(listOf(settings))
    }

    @Transaction
    suspend fun deleteAll() {
        deleteAllPackets()
        deleteAllReactions()
        deleteAllContactSettings()
    }

    @Query("DELETE FROM packet")
    suspend fun deleteAllPackets()

    @Query("DELETE FROM reactions")
    suspend fun deleteAllReactions()

    @Query("DELETE FROM contact_settings")
    suspend fun deleteAllContactSettings()

    // region ── Cross-transport merge ──
    // Snapshots + inserts used by DatabaseMerger to fold one transport's DB into another for the same node.

    @Query("SELECT * FROM packet")
    suspend fun getAllPacketsSnapshot(): List<Packet>

    /** Insert a packet copied from another DB. Pass uuid = 0 so a fresh auto-generated id is assigned. */
    @Insert suspend fun insertPacketForMerge(packet: Packet)

    @Query("SELECT * FROM reactions")
    suspend fun getAllReactionsSnapshot(): List<ReactionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReactionsIgnore(reactions: List<ReactionEntity>)

    @Query("SELECT * FROM contact_settings")
    suspend fun getAllContactSettingsSnapshot(): List<ContactSettings>

    // endregion

    /**
     * Re-keys conversations after the radio's channel set changed, applying a plan from `planChannelReconciliation`.
     *
     * Conversations are keyed by channel *index*, so a slot that changes occupant would otherwise show the previous
     * channel's history under the new channel. Each change moves one conversation: to another slot (the channel was
     * reordered), to a retired key (the channel is gone), or back from a retired key (the channel came back).
     *
     * Direct messages move with their slot but are never retired — a DM is a conversation with a node, not with a
     * channel, and losing that history because the channel set changed would be worse than the misfiling this repairs.
     *
     * All the rows that will move are read before any is written, so a slot swap cannot double-move a conversation.
     */
    @Transaction
    suspend fun applyChannelReconciliation(changes: List<ChannelKeyChange>) {
        if (changes.isEmpty()) return
        val liveMoves = changes.filter { it.from is ConversationSlot.Live && it.to is ConversationSlot.Live }
        val retirements = changes.filter { it.to is ConversationSlot.Retired }
        val reclaims = changes.filter { it.from is ConversationSlot.Retired }

        // Retirements first. A retirement frees its slot with a wholesale key rewrite, so any move that targets
        // that slot has to land afterwards -- otherwise the moved conversation is sitting on the key the retirement
        // is about to sweep, and both end up in the same archive.
        for (change in retirements) {
            val fromIndex = (change.from as ConversationSlot.Live).index
            val token = (change.to as ConversationSlot.Retired).token
            moveContact(
                from = ContactKey.broadcast(fromIndex).value,
                to = ContactKey.retiredBroadcast(token).value,
                displayName = change.displayName,
            )
        }

        applyLiveMoves(liveMoves)

        for (change in reclaims) {
            val token = (change.from as ConversationSlot.Retired).token
            val toIndex = (change.to as ConversationSlot.Live).index
            // After the moves above, never before: a reclaimed reaction stamped with its new index would otherwise be
            // swept up by a move out of that same index. Scoped by the retired conversation's packets, which still
            // carry the retired key at this point.
            retargetReactionsForContact(ContactKey.retiredBroadcast(token).value, toIndex)
            moveContact(
                from = ContactKey.retiredBroadcast(token).value,
                to = ContactKey.broadcast(toIndex).value,
                displayName = "",
                toChannel = toIndex,
            )
        }
    }

    /**
     * Every retired conversation currently holding messages, by its channel-identity token.
     *
     * Messages, not settings: deleting an archived conversation removes its packets but leaves its settings row, and
     * counting that row as parked would resurrect the deleted conversation's mute and pin onto the fresh channel if
     * that channel were ever re-added.
     */
    suspend fun getRetiredContactTokens(): Set<String> =
        getRetiredPacketContactKeys().mapNotNullTo(mutableSetOf()) { ContactKey(it).retiredToken }

    @Query("SELECT DISTINCT contact_key FROM packet WHERE contact_key LIKE '~%'")
    suspend fun getRetiredPacketContactKeys(): List<String>

    /**
     * Moves one whole conversation to [to], carrying its settings (mute, pin, draft, read marker) with it.
     *
     * [displayName] labels a retired conversation, whose slot no longer names it; it is cleared on the way back.
     * [toChannel], when given, restores the packets' channel index — a retired conversation keeps the index it was
     * retired from so a later reclaim knows which reactions belong to it.
     */
    @Transaction
    suspend fun moveContact(from: String, to: String, displayName: String, toChannel: Int? = null) {
        val existing = getContactSettings(from)
        if (existing != null) {
            deleteContactSettings(from)
            deleteContactSettings(to)
            insertContactSettingsIgnore(listOf(existing.copy(contact_key = to, displayName = displayName)))
        } else if (displayName.isNotEmpty()) {
            insertContactSettingsIgnore(listOf(ContactSettings(contact_key = to, displayName = displayName)))
        }
        if (toChannel != null) {
            for (packet in getPacketsForContact(from)) {
                update(packet.copy(contact_key = to, data = packet.data.copy(channel = toChannel)))
            }
        } else {
            retargetContactKey(from = from, to = to)
        }
    }

    @Query("UPDATE packet SET contact_key = :to WHERE contact_key = :from")
    suspend fun retargetContactKey(from: String, to: String)

    @Query("SELECT * FROM packet WHERE contact_key = :contact")
    suspend fun getPacketsForContact(contact: String): List<Packet>

    @Query("DELETE FROM contact_settings WHERE contact_key = :contact")
    suspend fun deleteContactSettings(contact: String)

    @Query("UPDATE reactions SET channel = :to WHERE channel = :from")
    suspend fun retargetReactionChannel(from: Int, to: Int)

    /**
     * Re-stamps the reactions belonging to one conversation's messages.
     *
     * Scoped through the conversation's own packet ids rather than by channel, because a retired conversation keeps the
     * index it was retired from and a live channel may since have taken that index over. A mesh packet id is only
     * unique per sender, so the node scope keeps another device's rows out of it.
     */
    @Suppress("MaxLineLength")
    @Query(
        "UPDATE reactions SET channel = :to WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node)) AND reply_id IN (SELECT packet_id FROM packet WHERE contact_key = :contact)",
    )
    suspend fun retargetReactionsForContact(contact: String, to: Int)

    @Query("SELECT * FROM packet WHERE port_num = 1")
    suspend fun getAllUserPacketsForMigration(): List<Packet>

    @Suppress("MaxLineLength")
    @Query(
        "UPDATE packet SET filtered = :filtered WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node)) AND data LIKE :senderIdPattern",
    )
    suspend fun updateFilteredBySender(senderIdPattern: String, filtered: Boolean)

    /** Persists an on-device translation and switches the message to display it in one write. */
    @Query("UPDATE packet SET translated_text = :translatedText, show_translated = 1 WHERE uuid = :uuid")
    suspend fun setTranslation(uuid: Long, translatedText: String)

    @Query("UPDATE packet SET show_translated = :show WHERE uuid = :uuid")
    suspend fun setShowTranslated(uuid: Long, show: Boolean)

    // region ── Atomic read-modify-write transactions ──

    /**
     * Atomically updates the last-read message pointer for [contact], preserving the monotonic-timestamp guard: if
     * [lastReadTimestamp] is not newer than the stored value, the method is a no-op. Creates the contact-settings row
     * if absent. Uses INSERT OR IGNORE + conditional UPDATE instead of a read-then-@Upsert so the existing row's
     * unrelated columns (muteUntil, filteringDisabled) are preserved without a Kotlin-side read-modify-write.
     */
    @Transaction
    suspend fun updateLastReadMessage(contact: String, messageUuid: Long, lastReadTimestamp: Long) {
        insertContactSettingsIgnore(listOf(ContactSettings(contact_key = contact)))
        updateLastReadMessageIfNewer(contact, messageUuid, lastReadTimestamp)
    }

    /**
     * Conditional UPDATE that only writes newer timestamps. Callers must ensure the contact-settings row exists (e.g.
     * via [insertContactSettingsIgnore]) before calling this inside the same transaction.
     *
     * Returns the affected row count (1 = updated, 0 = no-op) for testability; callers outside tests typically ignore
     * it.
     */
    @Query(
        """
        UPDATE contact_settings
        SET last_read_message_uuid = :messageUuid,
            last_read_message_timestamp = :lastReadTimestamp
        WHERE contact_key = :contact
          AND (
              last_read_message_timestamp IS NULL
              OR last_read_message_timestamp < :lastReadTimestamp
          )
        """,
    )
    suspend fun updateLastReadMessageIfNewer(contact: String, messageUuid: Long, lastReadTimestamp: Long): Int

    /**
     * Atomically finds a packet by identity key (id + from + to) and updates its data, optionally stamping
     * [routingError] when it is non-negative. Mirrors the existing [updateMessageStatus] / [updateMessageId] pattern.
     */
    @Transaction
    suspend fun updatePacketByKey(data: DataPacket, routingError: Int) {
        findPacketsWithId(data.id)
            .filter { it.data.id == data.id && it.data.from == data.from && it.data.to == data.to }
            .forEach { existing ->
                val updated =
                    if (routingError >= 0) {
                        existing.copy(data = data, routingError = routingError)
                    } else {
                        existing.copy(data = data)
                    }
                update(updated)
            }
    }

    /**
     * Stamps [routingError] on a sent packet only while it is still [MessageStatus.ENROUTE]. The read and the write
     * share one transaction so an ACK/NAK that resolves the packet concurrently is never overwritten by a send-ack
     * timeout that sampled the row before it landed.
     *
     * @return true if a row was timed out.
     */
    @Transaction
    suspend fun timeOutEnroutePacket(myNodeNum: Int, uuid: Long, routingError: Int): Boolean {
        val existing = getPacketByPersistedId(myNodeNum, uuid)
        val canTimeOut = existing?.data?.status == MessageStatus.ENROUTE
        if (canTimeOut) {
            update(existing.copy(data = existing.data.copy(status = MessageStatus.ERROR), routingError = routingError))
        }
        return canTimeOut
    }

    @Query(
        """
        UPDATE reactions
        SET status = :failedStatus, routing_error = :routingError
        WHERE myNodeNum = :myNodeNum
        AND reply_id = :replyId
        AND user_id = :userId
        AND emoji = :emoji
        AND status = :enrouteStatus
        """,
    )
    suspend fun updateEnrouteReactionStatus(
        myNodeNum: Int,
        replyId: Int,
        userId: String,
        emoji: String,
        routingError: Int,
        enrouteStatus: MessageStatus,
        failedStatus: MessageStatus,
    ): Int

    /**
     * Atomically stamps [routingError] on a sent reaction only while it is still [MessageStatus.ENROUTE].
     *
     * @return true if a row was timed out.
     */
    suspend fun timeOutEnrouteReaction(
        myNodeNum: Int,
        replyId: Int,
        userId: String,
        emoji: String,
        routingError: Int,
    ): Boolean = updateEnrouteReactionStatus(
        myNodeNum = myNodeNum,
        replyId = replyId,
        userId = userId,
        emoji = emoji,
        routingError = routingError,
        enrouteStatus = MessageStatus.ENROUTE,
        failedStatus = MessageStatus.ERROR,
    ) == 1

    /**
     * Atomically finds reactions by [replacement]'s packetId + userId + emoji and updates every ownership-scoped copy,
     * borrowing [myNodeNum][ReactionEntity.myNodeNum] from each existing row. No-op if no match is found.
     */
    @Transaction
    suspend fun updateReactionByKey(replacement: ReactionEntity) {
        findReactionsWithId(replacement.packetId)
            .filter { it.userId == replacement.userId && it.emoji == replacement.emoji }
            .forEach { existing ->
                // myNodeNum is part of the composite PK and must come from each existing row.
                update(replacement.copy(myNodeNum = existing.myNodeNum))
            }
    }

    // ── SFPP helpers: shared no-downgrade guard + timestamp resolution used by both applySFPPStatus and
    //     applySFPPStatusByHash. Extracted so a future status-guard change only touches one predicate.
    private fun MessageStatus.isDowngradeFrom(current: MessageStatus?) =
        current == MessageStatus.SFPP_CONFIRMED && this == MessageStatus.SFPP_ROUTING

    private fun resolveNewTime(rxTime: Long, fallback: Long) = if (rxTime > 0) rxTime * MILLIS_PER_SECOND else fallback

    /**
     * Atomically applies an SFPP delivery-status transition to every packet and reaction matching [packetId] + address
     * ([from]/[to]). Preserves the no-downgrade invariant: an item already
     * [SFPP_CONFIRMED][MessageStatus.SFPP_CONFIRMED] is not regressed to [SFPP_ROUTING][MessageStatus.SFPP_ROUTING].
     * All updates land in one transaction so a packet and its reactions cannot end up in inconsistent delivery states.
     */
    @Suppress("CyclomaticComplexMethod", "LongParameterList")
    @Transaction
    suspend fun applySFPPStatus(
        packetId: Int,
        from: Int,
        to: Int,
        hash: ByteString,
        status: MessageStatus,
        rxTime: Long,
        myNodeNum: Int?,
    ) {
        val packets = findPacketsWithId(packetId)
        val reactions = findReactionsWithId(packetId)
        val fromId = NodeAddress.numToDefaultId(from)
        val isFromLocalNode = myNodeNum != null && from == myNodeNum
        val toId =
            if (to == 0 || to == NodeAddress.NODENUM_BROADCAST) {
                NodeAddress.ID_BROADCAST
            } else {
                NodeAddress.numToDefaultId(to)
            }

        packets.forEach { packet ->
            val fromMatches =
                packet.data.from == fromId || (isFromLocalNode && packet.data.from == NodeAddress.ID_LOCAL)
            if (fromMatches && packet.data.to == toId) {
                if (status.isDowngradeFrom(packet.data.status)) return@forEach
                val newTime = resolveNewTime(rxTime, packet.received_time)
                val updatedData = packet.data.copy(status = status, sfppHash = hash, time = newTime)
                update(packet.copy(data = updatedData, sfpp_hash = hash, received_time = newTime))
            }
        }

        reactions.forEach { reaction ->
            val fromMatches = reaction.userId == fromId || (isFromLocalNode && reaction.userId == NodeAddress.ID_LOCAL)
            if (fromMatches && (reaction.to == null || reaction.to == toId)) {
                if (status.isDowngradeFrom(reaction.status)) return@forEach
                val newTime = resolveNewTime(rxTime, reaction.timestamp)
                update(reaction.copy(status = status, sfpp_hash = hash, timestamp = newTime))
            }
        }
    }

    /**
     * Atomically applies an SFPP delivery-status transition to the single packet and single reaction matching [hash]
     * (8-byte prefix). Same no-downgrade invariant as [applySFPPStatus]. Both updates land in one transaction.
     */
    @Transaction
    suspend fun applySFPPStatusByHash(hash: ByteString, status: MessageStatus, rxTime: Long) {
        findPacketBySfppHash(hash)?.let { packet ->
            if (status.isDowngradeFrom(packet.data.status)) return@let
            val newTime = resolveNewTime(rxTime, packet.received_time)
            val updatedData = packet.data.copy(status = status, sfppHash = hash, time = newTime)
            update(packet.copy(data = updatedData, sfpp_hash = hash, received_time = newTime))
        }
        findReactionBySfppHash(hash)?.let { reaction ->
            if (status.isDowngradeFrom(reaction.status)) return@let
            val newTime = resolveNewTime(rxTime, reaction.timestamp)
            update(reaction.copy(status = status, sfpp_hash = hash, timestamp = newTime))
        }
    }

    /**
     * Atomically deletes all messages (and their reactions) for the given [uuidList], chunking internally to stay under
     * SQLite's bind-parameter limit. The entire batch is all-or-nothing: a failure rolls back every chunk.
     */
    @Transaction
    suspend fun deleteMessagesAtomic(uuidList: List<Long>) {
        if (uuidList.isEmpty()) return
        for (chunk in uuidList.chunked(SQLITE_MAX_BIND_PARAMETERS)) {
            deleteMessages(chunk)
        }
    }

    // endregion

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
    }

    // region ── FTS5 Search ──

    @Query(
        "SELECT packet.* FROM packet JOIN packet_fts ON packet.rowid = packet_fts.rowid " +
            "WHERE packet_fts MATCH :query AND packet.myNodeNum = (SELECT myNodeNum FROM my_node) " +
            "ORDER BY packet.received_time DESC LIMIT 100",
    )
    suspend fun searchMessages(query: String): List<Packet>

    @Query(
        "SELECT packet.* FROM packet JOIN packet_fts ON packet.rowid = packet_fts.rowid " +
            "WHERE packet_fts MATCH :query AND packet.contact_key = :contactKey " +
            "AND packet.myNodeNum = (SELECT myNodeNum FROM my_node) " +
            "ORDER BY packet.received_time DESC LIMIT 100",
    )
    suspend fun searchMessagesInConversation(query: String, contactKey: String): List<Packet>

    @Query("UPDATE packet SET message_text = :text WHERE uuid = :uuid")
    suspend fun updateMessageText(uuid: Long, text: String)

    @Query("SELECT COUNT(*) FROM packet WHERE port_num = 1 AND (message_text IS NULL OR message_text = '')")
    suspend fun countPacketsNeedingBackfill(): Int

    @Query("SELECT * FROM packet WHERE port_num = 1 AND (message_text IS NULL OR message_text = '')")
    suspend fun getPacketsNeedingBackfill(): List<Packet>

    /**
     * Populates [Packet.messageText] for historical text packets that predate the FTS5 schema (v39) so they become
     * searchable. The text is decoded in Kotlin from each packet's [DataPacket.text]; it cannot be read with a SQL
     * `json_extract(data, '$.text')` because [DataPacket.text] is a computed property that is never serialized into the
     * stored JSON (the payload is persisted as `bytes`). Returns the number of rows updated; the caller rebuilds the
     * FTS index via [rebuildFtsIndex] when this is greater than zero.
     */
    @Transaction
    suspend fun backfillMessageTexts(): Int {
        var updated = 0
        getPacketsNeedingBackfill().forEach { packet ->
            val text = packet.data.text
            if (!text.isNullOrEmpty()) {
                updateMessageText(packet.uuid, text)
                updated++
            }
        }
        return updated
    }

    @Query("INSERT INTO packet_fts(packet_fts) VALUES('rebuild')")
    suspend fun rebuildFtsIndex()

    // endregion
}

private fun String?.matchesNodeNum(nodeNum: Int, localNodeNum: Int): Boolean =
    when (val address = NodeAddress.fromString(this)) {
        NodeAddress.Broadcast -> nodeNum == NodeAddress.NODENUM_BROADCAST
        NodeAddress.Local -> nodeNum == localNodeNum
        is NodeAddress.ByNum -> address.num == nodeNum
        is NodeAddress.ById -> false
    }

/**
 * Queue-stage status guard shared with callers that decide whether to arm follow-up work.
 *
 * Only [MessageStatus.ENROUTE] and [MessageStatus.ERROR] are owned by this transition. Terminal routing and SFPP
 * statuses are applied by their dedicated state transitions.
 */
fun shouldApplyOutgoingQueueStatus(current: MessageStatus?, status: MessageStatus): Boolean = when (status) {
    MessageStatus.ENROUTE -> current == null || current == MessageStatus.UNKNOWN || current == MessageStatus.QUEUED

    MessageStatus.ERROR ->
        current == null ||
            current == MessageStatus.UNKNOWN ||
            current == MessageStatus.QUEUED ||
            current == MessageStatus.ENROUTE

    else -> false
}

/**
 * Parks a reaction's channel out of range while conversations swap slots. No live slot or PKI channel can hold a
 * negative index, so a staged row cannot be picked up by the second phase of another move.
 */
private const val REACTION_CHANNEL_STAGING_OFFSET = 1000

/**
 * Re-keys the conversations that only changed slot.
 *
 * Every affected row is read before any is written, so a 0<->1 swap cannot be applied twice, and reaction channels are
 * staged through a value no live slot can hold for the same reason. Only text messages are re-keyed: every other port
 * is queried by port_num rather than by contact key, so a stale key on a waypoint row is invisible and rewriting one
 * costs a blob write for nothing.
 *
 * Callers must have applied retirements already - a move into a slot a retirement is about to sweep would be swept with
 * it.
 */
private suspend fun PacketDao.applyLiveMoves(liveMoves: List<ChannelKeyChange>) {
    val sourceIndices = liveMoves.map { (it.from as ConversationSlot.Live).index }.toSet()
    val movingPackets =
        if (sourceIndices.isEmpty()) {
            emptyList()
        } else {
            getAllUserPacketsForMigration().filter { ContactKey(it.contact_key).channelOrNull in sourceIndices }
        }
    val moveByIndex =
        liveMoves.associate { change ->
            (change.from as ConversationSlot.Live).index to (change.to as ConversationSlot.Live).index
        }

    // Settings are re-keyed the same way: read every affected row, then rewrite, so a swap cannot land a
    // conversation's mute or pin on the channel it traded places with.
    val movingSettings =
        getAllContactSettingsSnapshot().mapNotNull { settings ->
            val key = ContactKey(settings.contact_key)
            val newIndex = moveByIndex[key.channelOrNull] ?: return@mapNotNull null
            settings to "$newIndex${key.addressString}"
        }

    for (packet in movingPackets) {
        val newIndex = moveByIndex[ContactKey(packet.contact_key).channelOrNull] ?: continue
        val suffix = ContactKey(packet.contact_key).addressString
        update(packet.copy(contact_key = "$newIndex$suffix", data = packet.data.copy(channel = newIndex)))
    }
    for ((settings, _) in movingSettings) {
        deleteContactSettings(settings.contact_key)
    }
    for ((_, newKey) in movingSettings) {
        deleteContactSettings(newKey)
    }
    insertContactSettingsIgnore(movingSettings.map { (settings, newKey) -> settings.copy(contact_key = newKey) })
    // Two-phase, via a value no live slot can hold: a direct 0->1 then 1->0 would collapse both onto one channel.
    for ((from, _) in moveByIndex) {
        retargetReactionChannel(from = from, to = from - REACTION_CHANNEL_STAGING_OFFSET)
    }
    for ((from, to) in moveByIndex) {
        retargetReactionChannel(from = from - REACTION_CHANNEL_STAGING_OFFSET, to = to)
    }
}
