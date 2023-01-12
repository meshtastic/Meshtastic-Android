package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Query
import androidx.room.Transaction
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.database.entity.Packet
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {

    @Query("Select * from packet order by received_time asc")
    fun getAllPackets(): Flow<List<Packet>>

    @Insert
    fun insert(packet: Packet)

    @Query("Select * from packet where port_num = 1 and contact_key = :contact order by received_time asc")
    fun getMessagesFrom(contact: String): Flow<List<Packet>>

    @Query("Select * from packet where data = :data")
    fun findDataPacket(data: DataPacket): Packet?

    @Query("Delete from packet where port_num = 1")
    fun deleteAllMessages()

    @Query("Delete from packet where uuid in (:uuidList)")
    fun deleteMessages(uuidList: List<Long>)

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

    @Query("Select data from packet order by received_time asc")
    fun getDataPackets(): List<DataPacket>

    @Transaction
    fun getDataPacketById(requestId: Int): DataPacket? {
        return getDataPackets().firstOrNull { it.id == requestId }
    }

    @Transaction
    fun getQueuedPackets(): List<DataPacket>? =
        getDataPackets().filter { it.status in setOf(MessageStatus.ENROUTE, MessageStatus.QUEUED) }
}
