package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Query
import androidx.room.Transaction
import com.geeksville.mesh.database.entity.Packet
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {

    @Query("Select * from packet order by received_time asc")
    fun getAllPackets(): Flow<List<Packet>>

    @Insert
    fun insert(packet: Packet)

    @Query("Delete from packet")
    fun deleteAll()

    @Query("Delete from packet where uuid=:uuid")
    fun _delete(uuid: Long)

    @Transaction
    fun delete(packet: Packet) {
        _delete(packet.uuid)
    }

    @Update
    fun update(packet: Packet)

}
