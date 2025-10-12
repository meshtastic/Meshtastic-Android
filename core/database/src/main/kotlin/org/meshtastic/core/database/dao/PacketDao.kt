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

package org.meshtastic.core.database.dao

import androidx.room.Dao
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.ContactSettings
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.PacketEntity
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus

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
        AND port_num = 1
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
        AND port_num = 1 AND contact_key = :contact AND read = 0
    """,
    )
    suspend fun getUnreadCount(contact: String): Int

    @Query(
        """
    SELECT COUNT(*) FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND read = 0
    """,
    )
    fun getUnreadCountTotal(): Flow<Int>

    @Query(
        """
    UPDATE packet
    SET read = 1
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM my_node))
        AND port_num = 1 AND contact_key = :contact AND read = 0 AND received_time <= :timestamp
    """,
    )
    suspend fun clearUnreadCount(contact: String, timestamp: Long)

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

    @Query("DELETE FROM reactions WHERE reply_id IN (:packetIds)")
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
        findDataPacket(data)?.let { update(it.copy(data = new)) }
    }

    @Transaction
    suspend fun updateMessageId(data: DataPacket, id: Int) {
        val new = data.copy(id = id)
        findDataPacket(data)?.let { update(it.copy(data = new)) }
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
    @Query("SELECT * FROM packet WHERE packet_id = :packetId LIMIT 1")
    suspend fun getPacketByPacketId(packetId: Int): PacketEntity?

    @Transaction
    suspend fun getQueuedPackets(): List<DataPacket>? = getDataPackets().filter { it.status == MessageStatus.QUEUED }

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

    @Transaction
    suspend fun setMuteUntil(contacts: List<String>, until: Long) {
        val contactList =
            contacts.map { contact ->
                getContactSettings(contact)?.copy(muteUntil = until)
                    ?: ContactSettings(contact_key = contact, muteUntil = until)
            }
        upsertContactSettings(contactList)
    }

    @Upsert suspend fun insert(reaction: ReactionEntity)

    @Query("DELETE FROM packet")
    suspend fun deleteAll()
}
