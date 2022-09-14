package com.geeksville.mesh.database

import com.geeksville.mesh.database.dao.PacketDao
import com.geeksville.mesh.database.entity.Packet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PacketRepository @Inject constructor(private val packetDaoLazy: dagger.Lazy<PacketDao>) {
    private val packetDao by lazy {
        packetDaoLazy.get()
    }

    suspend fun getAll(): Flow<List<Packet>> = withContext(Dispatchers.IO) {
        packetDao.getAllPackets()
    }

    suspend fun insert(packet: Packet) = withContext(Dispatchers.IO) {
        packetDao.insert(packet)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        packetDao.deleteAll()
    }

    suspend fun delete(packet: Packet) = withContext(Dispatchers.IO) {
        packetDao.delete(packet)
    }

    suspend fun update(packet: Packet) = withContext(Dispatchers.IO) {
        packetDao.update(packet)
    }
}
