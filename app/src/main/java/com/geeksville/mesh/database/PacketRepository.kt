package com.geeksville.mesh.database

import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.Portnums.PortNum
import com.geeksville.mesh.database.dao.PacketDao
import com.geeksville.mesh.database.entity.ContactSettings
import com.geeksville.mesh.database.entity.Packet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PacketRepository @Inject constructor(private val packetDaoLazy: dagger.Lazy<PacketDao>) {
    private val packetDao by lazy {
        packetDaoLazy.get()
    }

    fun getWaypoints(): Flow<List<Packet>> = packetDao.getAllPackets(PortNum.WAYPOINT_APP_VALUE)

    fun getContacts(): Flow<Map<String, Packet>> = packetDao.getContactKeys()

    suspend fun getMessageCount(contact: String): Int = withContext(Dispatchers.IO) {
        packetDao.getMessageCount(contact)
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

    suspend fun deleteMessages(uuidList: List<Long>) = withContext(Dispatchers.IO) {
        for (chunk in uuidList.chunked(500)) { // limit number of UUIDs per query
            packetDao.deleteMessages(chunk)
        }
    }

    suspend fun deleteContacts(contactList: List<String>) = withContext(Dispatchers.IO) {
        packetDao.deleteContacts(contactList)
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

    fun getContactSettings(): Flow<Map<String, ContactSettings>> = packetDao.getContactSettings()

    suspend fun getContactSettings(contact: String) = withContext(Dispatchers.IO) {
        packetDao.getContactSettings(contact) ?: ContactSettings(contact)
    }

    suspend fun setMuteUntil(contacts: List<String>, until: Long) = withContext(Dispatchers.IO) {
        packetDao.setMuteUntil(contacts, until)
    }
}
