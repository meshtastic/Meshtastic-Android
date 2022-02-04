package com.geeksville.mesh.database

import androidx.lifecycle.LiveData
import com.geeksville.mesh.database.dao.PacketDao
import com.geeksville.mesh.database.entity.Packet
import kotlinx.coroutines.flow.Flow

class PacketRepository(private val packetDao : PacketDao) {
    val allPackets : LiveData<List<Packet>> = packetDao.getAllPacket(MAX_ITEMS)
    val allPacketsInReceiveOrder : Flow<List<Packet>> = packetDao.getAllPacketsInReceiveOrder(MAX_ITEMS)

    suspend fun insert(packet: Packet) {
        packetDao.insert(packet)
    }

    suspend fun deleteAll() {
        packetDao.deleteAll()
    }

    companion object {
        private const val MAX_ITEMS = 500
    }

}