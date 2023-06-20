package com.geeksville.mesh.database

import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
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

    suspend fun getAllPackets(): Flow<List<Packet>> = withContext(Dispatchers.IO) {
        packetDao.getAllPackets()
    }

    suspend fun getQueuedPackets(): List<DataPacket>? = withContext(Dispatchers.IO) {
        packetDao.getQueuedPackets()
    }

    suspend fun insert(packet: Packet) = withContext(Dispatchers.IO) {
        packetDao.insert(packet)
    }

    suspend fun getMessagesFrom(contact: String) = withContext(Dispatchers.IO) {
        packetDao.getMessagesFrom(contact)
    }

    suspend fun updateMessageStatus(d: DataPacket, m: MessageStatus) = withContext(Dispatchers.IO) {
        packetDao.updateMessageStatus(d, m)
    }

    suspend fun updateMessageId(d: DataPacket, id: Int) = withContext(Dispatchers.IO) {
        packetDao.updateMessageId(d, id)
    }

    suspend fun getDataPacketById(requestId: Int) = withContext(Dispatchers.IO) {
        packetDao.getDataPacketById(requestId)
    }

    suspend fun deleteAllMessages() = withContext(Dispatchers.IO) {
        packetDao.deleteAllMessages()
    }

    suspend fun deleteMessages(uuidList: List<Long>) = withContext(Dispatchers.IO) {
        packetDao.deleteMessages(uuidList)
    }

    suspend fun deleteWaypoint(id: Int) = withContext(Dispatchers.IO) {
        packetDao.deleteWaypoint(id)
    }

    suspend fun delete(packet: Packet) = withContext(Dispatchers.IO) {
        packetDao.delete(packet)
    }

    suspend fun update(packet: Packet) = withContext(Dispatchers.IO) {
        packetDao.update(packet)
    }
}
