package com.geeksville.mesh.database

import androidx.lifecycle.LiveData
import com.geeksville.mesh.database.dao.PacketDao
import com.geeksville.mesh.database.entity.Packet

class PacketRepository(private val packetDao : PacketDao) {
    val allPackets : LiveData<List<Packet>> = packetDao.getAllPacket(500)

    suspend fun insert(packet: Packet) {
        packetDao.insert(packet)
    }

    suspend fun deleteAll() {
        packetDao.deleteAll()
    }
}