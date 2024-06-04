package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Update
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.database.entity.ContactSettings
import com.geeksville.mesh.database.entity.Packet
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {

    @Query("SELECT * FROM packet WHERE port_num = :portNum ORDER BY received_time ASC")
    fun getAllPackets(portNum: Int): Flow<List<Packet>>

    @Query("Select * from packet where port_num = 1 order by received_time desc")
    fun getContactKeys(): Flow<Map<@MapColumn(columnName = "contact_key") String, Packet>>

    @Query("SELECT COUNT(*) FROM packet WHERE port_num = 1 AND contact_key = :contact")
    suspend fun getMessageCount(contact: String): Int

    @Insert
    fun insert(packet: Packet)

    @Query("Select * from packet where port_num = 1 and contact_key = :contact order by received_time asc")
    fun getMessagesFrom(contact: String): Flow<List<Packet>>

    @Query("Select * from packet where data = :data")
    fun findDataPacket(data: DataPacket): Packet?

    @Query("Delete from packet where uuid in (:uuidList)")
    fun deleteMessages(uuidList: List<Long>)

    @Query("DELETE FROM packet WHERE contact_key IN (:contactList)")
    fun deleteContacts(contactList: List<String>)

    @Query("Delete from packet where uuid=:uuid")
    fun _delete(uuid: Long)

    @Transaction
    fun delete(packet: Packet) {
        _delete(packet.uuid)
    }

    @Update
    fun update(packet: Packet)

    @Transaction
    fun updateMessageStatus(data: DataPacket, m: MessageStatus) {
        val new = data.copy(status = m)
        findDataPacket(data)?.let { update(it.copy(data = new)) }
    }

    @Transaction
    fun updateMessageId(data: DataPacket, id: Int) {
        val new = data.copy(id = id)
        findDataPacket(data)?.let { update(it.copy(data = new)) }
    }

    @Query("Select data from packet order by received_time asc")
    fun getDataPackets(): List<DataPacket>

    @Transaction
    fun getDataPacketById(requestId: Int): DataPacket? {
        return getDataPackets().lastOrNull { it.id == requestId }
    }

    @Transaction
    fun getQueuedPackets(): List<DataPacket>? =
        getDataPackets().filter { it.status == MessageStatus.QUEUED }

    @Query("Select * from packet where port_num = 8 order by received_time asc")
    fun getAllWaypoints(): List<Packet>

    @Transaction
    fun deleteWaypoint(id: Int) {
        val uuidList = getAllWaypoints().filter { it.data.waypoint?.id == id }.map { it.uuid }
        deleteMessages(uuidList)
    }

    @Query("SELECT * FROM contact_settings")
    fun getContactSettings(): Flow<Map<@MapColumn(columnName = "contact_key") String, ContactSettings>>

    @Query("SELECT * FROM contact_settings WHERE contact_key = :contact")
    suspend fun getContactSettings(contact:String): ContactSettings?

    @Upsert
    fun upsertContactSettings(contacts: List<ContactSettings>)

    @Transaction
    suspend fun setMuteUntil(contacts: List<String>, until: Long) {
        val contactList = contacts.map { contact ->
            getContactSettings(contact)?.copy(muteUntil = until)
                ?: ContactSettings(contact_key = contact, muteUntil = until)
        }
        upsertContactSettings(contactList)
    }
}
