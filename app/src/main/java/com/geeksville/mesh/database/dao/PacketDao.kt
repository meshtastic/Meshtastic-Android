package com.geeksville.mesh.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.geeksville.mesh.database.entity.Packet
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {

    @Query("Select * from packet order by received_date desc limit 0,:maxItem")
    fun getAllPacket(maxItem: Int): LiveData<List<Packet>>

    @Query("Select * from packet order by received_date asc limit 0,:maxItem")
    fun getAllPacketsInReceiveOrder(maxItem: Int): Flow<List<Packet>>

    @Insert
    fun insert(packet: Packet)

    @Query("DELETE from packet")
    fun deleteAll()

}