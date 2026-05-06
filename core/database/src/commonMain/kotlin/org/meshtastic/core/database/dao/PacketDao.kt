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
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = :portNum
    ORDER BY received_time ASC
    """,
    )
    fun getAllPackets(myNodeNum: Int, portNum: Int): Flow<List<Packet>>

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND filtered = 0
    ORDER BY received_time DESC
    """,
    )
    fun getContactKeys(myNodeNum: Int): Flow<
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
        WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
            AND port_num = 1 AND filtered = 0
        GROUP BY contact_key
    ) latest ON p.contact_key = latest.contact_key AND p.received_time = latest.max_time
    WHERE (p.myNodeNum = 0 OR p.myNodeNum = :myNodeNum)
        AND p.port_num = 1 AND p.filtered = 0
    GROUP BY p.contact_key
    ORDER BY p.received_time DESC
    """,
    )
    fun getContactKeysPaged(myNodeNum: Int): PagingSource<Int, Packet>

    @Query(
        """
    SELECT COUNT(*) FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND contact_key = :contact
    """,
    )
    suspend fun getMessageCount(myNodeNum: Int, contact: String): Int

    @Query(
        """
    SELECT COUNT(*) FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND contact_key = :contact AND read = 0 AND filtered = 0
    """,
    )
    suspend fun getUnreadCount(myNodeNum: Int, contact: String): Int

    @Query(
        """
    SELECT COUNT(*) FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND contact_key = :contact AND read = 0 AND filtered = 0
    """,
    )
    fun getUnreadCountFlow(myNodeNum: Int, contact: String): Flow<Int>

    @Query(
        """
    SELECT uuid FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND contact_key = :contact AND read = 0 AND filtered = 0
    ORDER BY received_time ASC
    LIMIT 1
    """,
    )
    fun getFirstUnreadMessageUuid(myNodeNum: Int, contact: String): Flow<Long?>

    @Query(
        """
    SELECT COUNT(*) > 0 FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND contact_key = :contact AND read = 0 AND filtered = 0
    """,
    )
    fun hasUnreadMessages(myNodeNum: Int, contact: String): Flow<Boolean>

    @Query(
        """
    SELECT COUNT(*) FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND read = 0 AND filtered = 0
    """,
    )
    fun getUnreadCountTotal(myNodeNum: Int): Flow<Int>

    @Query(
        """
    UPDATE packet
    SET read = 1
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND contact_key = :contact AND read = 0 AND filtered = 0 AND received_time <= :timestamp
    """,
    )
    suspend fun clearUnreadCount(myNodeNum: Int, contact: String, timestamp: Long)

    @Query(
        """
    UPDATE packet
    SET read = 1
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND read = 0 AND filtered = 0
    """,
    )
    suspend fun clearAllUnreadCounts(myNodeNum: Int)

    @Upsert suspend fun insert(packet: Packet)

    @Transaction
    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND contact_key = :contact
    ORDER BY received_time DESC
    """,
    )
    fun getMessagesFrom(myNodeNum: Int, contact: String): Flow<List<PacketEntity>>

    @Transaction
    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND contact_key = :contact
    ORDER BY received_time DESC
    LIMIT :limit
    """,
    )
    fun getMessagesFrom(myNodeNum: Int, contact: String, limit: Int): Flow<List<PacketEntity>>

    @Transaction
    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND contact_key = :contact
        AND (filtered = 0 OR :includeFiltered = 1)
    ORDER BY received_time DESC
    """,
    )
    fun getMessagesFrom(myNodeNum: Int, contact: String, includeFiltered: Boolean): Flow<List<PacketEntity>>

    @Transaction
    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 1 AND contact_key = :contact
    ORDER BY received_time DESC
    """,
    )
    fun getMessagesFromPaged(myNodeNum: Int, contact: String): PagingSource<Int, PacketEntity>

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND data = :data
    """,
    )
    suspend fun findDataPacket(myNodeNum: Int, data: DataPacket): Packet?

    @Query("DELETE FROM packet WHERE uuid in (:uuidList)")
    suspend fun deletePackets(uuidList: List<Long>)

    @Query(
        """
    DELETE FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND contact_key IN (:contactList)
    """,
    )
    suspend fun deleteContacts(myNodeNum: Int, contactList: List<String>)

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
        WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND reply_id IN (:packetIds)
        """,
    )
    suspend fun deleteReactions(myNodeNum: Int, packetIds: List<Int>)

    @Transaction
    suspend fun deleteMessages(myNodeNum: Int, uuidList: List<Long>) {
        val packetIds = getPacketIdsFrom(uuidList)
        if (packetIds.isNotEmpty()) {
            deleteReactions(myNodeNum, packetIds)
        }
        deletePackets(uuidList)
    }

    @Update suspend fun update(packet: Packet)

    @Transaction
    suspend fun updateMessageStatus(myNodeNum: Int, data: DataPacket, m: MessageStatus) {
        val new = data.copy(status = m)
        findPacketsWithId(myNodeNum, data.id)
            .find { it.data.id == data.id && it.data.from == data.from && it.data.to == data.to }
            ?.let { update(it.copy(data = new)) }
    }

    @Transaction
    suspend fun updateMessageId(myNodeNum: Int, data: DataPacket, id: Int) {
        val new = data.copy(id = id)
        findPacketsWithId(myNodeNum, data.id)
            .find { it.data.id == data.id && it.data.from == data.from && it.data.to == data.to }
            ?.let { update(it.copy(data = new, packetId = id)) }
    }

    @Query(
        """
    SELECT data FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
    ORDER BY received_time ASC
    """,
    )
    suspend fun getDataPackets(myNodeNum: Int): List<DataPacket>

    @Transaction
    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND packet_id = :requestId
    ORDER BY received_time DESC
    """,
    )
    suspend fun getPacketById(myNodeNum: Int, requestId: Int): Packet?

    @Transaction
    @Query(
        """
        SELECT * FROM packet 
        WHERE packet_id = :packetId 
        AND (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        LIMIT 1
        """,
    )
    suspend fun getPacketByPacketId(myNodeNum: Int, packetId: Int): PacketEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM packet
        WHERE packet_id IN (:packetIds)
        AND (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        """,
    )
    suspend fun getPacketsByPacketIds(myNodeNum: Int, packetIds: List<Int>): List<PacketEntity>

    @Query(
        """
        SELECT * FROM packet 
        WHERE packet_id = :packetId 
        AND (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        """,
    )
    suspend fun findPacketsWithId(myNodeNum: Int, packetId: Int): List<Packet>

    @Transaction
    @Query(
        """
        SELECT * FROM packet 
        WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND substr(sfpp_hash, 1, 8) = substr(:hash, 1, 8)
        """,
    )
    suspend fun findPacketBySfppHash(myNodeNum: Int, hash: ByteString): Packet?

    // Fetches all DataPackets for the current node, ordered by time.
    // Callers should filter by status in Kotlin (avoids SQLite json_extract dependency).
    @Query(
        """
    SELECT data FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
    ORDER BY received_time ASC
    """,
    )
    suspend fun getAllDataPackets(myNodeNum: Int): List<DataPacket>

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND port_num = 8
    ORDER BY received_time ASC
    """,
    )
    suspend fun getAllWaypoints(myNodeNum: Int): List<Packet>

    @Transaction
    suspend fun deleteWaypoint(myNodeNum: Int, id: Int) {
        val uuidList = getAllWaypoints(myNodeNum).filter { it.data.waypoint?.id == id }.map { it.uuid }
        deleteMessages(myNodeNum, uuidList)
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
        AND (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        """,
    )
    suspend fun findReactionsWithId(myNodeNum: Int, packetId: Int): List<ReactionEntity>

    @Query(
        """
        SELECT * FROM reactions 
        WHERE packet_id = :packetId 
        AND (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        LIMIT 1
        """,
    )
    suspend fun getReactionByPacketId(myNodeNum: Int, packetId: Int): ReactionEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM reactions 
        WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
        AND substr(sfpp_hash, 1, 8) = substr(:hash, 1, 8)
        """,
    )
    suspend fun findReactionBySfppHash(myNodeNum: Int, hash: ByteString): ReactionEntity?

    @Query(
        """
        SELECT COUNT(*) FROM packet
        WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
            AND port_num = 1 AND contact_key = :contact AND filtered = 1
        """,
    )
    suspend fun getFilteredCount(myNodeNum: Int, contact: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM packet
        WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
            AND port_num = 1 AND contact_key = :contact AND filtered = 1
        """,
    )
    fun getFilteredCountFlow(myNodeNum: Int, contact: String): Flow<Int>

    @Transaction
    @Query(
        """
        SELECT * FROM packet
        WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum)
            AND port_num = 1 AND contact_key = :contact
            AND (filtered = 0 OR :includeFiltered = 1)
        ORDER BY received_time DESC
        """,
    )
    fun getMessagesFromPaged(myNodeNum: Int, contact: String, includeFiltered: Boolean): PagingSource<Int, PacketEntity>

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
        "UPDATE packet SET filtered = :filtered WHERE (myNodeNum = 0 OR myNodeNum = :myNodeNum) AND data LIKE :senderIdPattern",
    )
    suspend fun updateFilteredBySender(myNodeNum: Int, senderIdPattern: String, filtered: Boolean)
}
