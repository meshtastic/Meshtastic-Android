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
import org.meshtastic.core.database.entity.ContactSettings
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.PacketEntity
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.proto.ChannelSettings

@Suppress("TooManyFunctions")
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
        WHERE packet_id IN (:packetIds)
        AND (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        """,
    )
    suspend fun getPacketsByPacketIds(packetIds: List<Int>): List<PacketEntity>

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
     * One-time migration: Remap all message DataPacket.channel indices to new mapping using PSK after a channel
     * reorder. For each Packet (with port_num = 1), finds the old PSK then sets the channel index to the matching
     * newSettings index. Skips if PSKs do not match or are missing.
     */
    @Transaction
    suspend fun migrateChannelsByPSK(oldSettings: List<ChannelSettings>, newSettings: List<ChannelSettings>) {
        // Pre-calculate mapping from old index to new index
        val indexMap =
            oldSettings
                .mapIndexed { oldIndex, oldChannel ->
                    val pskMatches =
                        newSettings.mapIndexedNotNull { index, channel ->
                            if (channel.psk == oldChannel.psk) index to channel else null
                        }

                    val newIndex =
                        when {
                            pskMatches.isEmpty() -> null

                            pskMatches.size == 1 -> pskMatches.first().first

                            else -> {
                                // Multiple matches with same PSK. Disambiguate by Name.
                                val nameMatches = pskMatches.filter { it.second.name == oldChannel.name }
                                if (nameMatches.size == 1) {
                                    nameMatches.first().first
                                } else {
                                    // Still ambiguous. Prefer keeping same index.
                                    pskMatches.find { it.first == oldIndex }?.first ?: pskMatches.first().first
                                }
                            }
                        }
                    oldIndex to newIndex
                }
                .toMap()

        val allPackets = getAllUserPacketsForMigration()
        for (packet in allPackets) {
            val oldIndex = packet.data.channel
            val newIndex = indexMap[oldIndex]
            if (newIndex != null && oldIndex != newIndex) {
                // Rebuild contact_key with the new index, keeping the rest unchanged
                val oldKeySuffix = packet.contact_key.dropWhile { it.isDigit() }
                val newContactKey = "$newIndex$oldKeySuffix"
                update(packet.copy(contact_key = newContactKey, data = packet.data.copy(channel = newIndex)))
            }
        }
    }

    @Query("SELECT * FROM packet WHERE port_num = 1")
    suspend fun getAllUserPacketsForMigration(): List<Packet>

    @Suppress("MaxLineLength")
    @Query(
        "UPDATE packet SET filtered = :filtered WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node)) AND data LIKE :senderIdPattern",
    )
    suspend fun updateFilteredBySender(senderIdPattern: String, filtered: Boolean)

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
