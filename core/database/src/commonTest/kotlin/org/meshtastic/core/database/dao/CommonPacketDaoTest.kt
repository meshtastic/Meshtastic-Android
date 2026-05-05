/*
 * Copyright (c) 2026 Meshtastic LLC
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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.proto.PortNum
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class CommonPacketDaoTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var packetDao: PacketDao

    private val myNodeNum = 42424242

    private val testContactKeys = listOf("0${DataPacket.ID_BROADCAST}", "1!test1234")

    private fun generateTestPackets(nodeNum: Int) = testContactKeys.flatMap { contactKey ->
        List(SAMPLE_SIZE) {
            Packet(
                uuid = 0L,
                myNodeNum = nodeNum,
                port_num = PortNum.TEXT_MESSAGE_APP.value,
                contact_key = contactKey,
                received_time = nowMillis + it,
                read = false,
                data =
                DataPacket(
                    to = DataPacket.ID_BROADCAST,
                    bytes = "Message $it!".encodeToByteArray().toByteString(),
                    dataType = PortNum.TEXT_MESSAGE_APP.value,
                ),
            )
        }
    }

    suspend fun createDb() {
        database = getInMemoryDatabaseBuilder().build()
        packetDao =
            database.packetDao().apply {
                generateTestPackets(42424243).forEach { insert(it) }
                generateTestPackets(myNodeNum).forEach { insert(it) }
            }
    }

    @AfterTest
    fun closeDb() {
        database.close()
    }

    @Test
    fun testGetMessagesFrom() = runTest {
        val contactKey = testContactKeys.first()
        val messages = packetDao.getMessagesFrom(myNodeNum, contactKey).first()
        assertEquals(SAMPLE_SIZE, messages.size)
        assertTrue(messages.all { it.packet.myNodeNum == myNodeNum })
        assertTrue(messages.all { it.packet.contact_key == contactKey })
    }

    @Test
    fun testGetMessageCount() = runTest {
        val contactKey = testContactKeys.first()
        assertEquals(SAMPLE_SIZE, packetDao.getMessageCount(myNodeNum, contactKey))
    }

    @Test
    fun testGetUnreadCount() = runTest {
        val contactKey = testContactKeys.first()
        assertEquals(SAMPLE_SIZE, packetDao.getUnreadCount(myNodeNum, contactKey))
    }

    @Test
    fun testClearUnreadCount() = runTest {
        val contactKey = testContactKeys.first()
        packetDao.clearUnreadCount(myNodeNum, contactKey, nowMillis + SAMPLE_SIZE)
        assertEquals(0, packetDao.getUnreadCount(myNodeNum, contactKey))
    }

    @Test
    fun testClearAllUnreadCounts() = runTest {
        packetDao.clearAllUnreadCounts(myNodeNum)
        testContactKeys.forEach { assertEquals(0, packetDao.getUnreadCount(myNodeNum, it)) }
    }

    @Test
    fun testUpdateMessageStatus() = runTest {
        val contactKey = testContactKeys.first()
        val messages = packetDao.getMessagesFrom(myNodeNum, contactKey).first()
        val packet = messages.first().packet.data

        val packetWithId = packet.copy(id = 999, from = "!$myNodeNum")
        val updatedRoomPacket = messages.first().packet.copy(data = packetWithId, packetId = 999)
        packetDao.update(updatedRoomPacket)

        packetDao.updateMessageStatus(myNodeNum, packetWithId, MessageStatus.DELIVERED)
        val updatedMessages = packetDao.getMessagesFrom(myNodeNum, contactKey).first()
        assertEquals(MessageStatus.DELIVERED, updatedMessages.first { it.packet.data.id == 999 }.packet.data.status)
    }

    @Test
    fun testGetQueuedPackets() = runTest {
        val queuedPacket =
            Packet(
                uuid = 0L,
                myNodeNum = myNodeNum,
                port_num = PortNum.TEXT_MESSAGE_APP.value,
                contact_key = "queued",
                received_time = nowMillis,
                read = true,
                data =
                DataPacket(
                    to = DataPacket.ID_BROADCAST,
                    bytes = "Queued".encodeToByteArray().toByteString(),
                    dataType = PortNum.TEXT_MESSAGE_APP.value,
                    status = MessageStatus.QUEUED,
                ),
            )
        packetDao.insert(queuedPacket)
        val queued = packetDao.getAllDataPackets(myNodeNum).filter { it.status == MessageStatus.QUEUED }
        assertNotNull(queued)
        assertEquals(1, queued.size)
        assertEquals("Queued", queued.first().text)
    }

    @Test
    fun testDeleteMessages() = runTest {
        val contactKey = testContactKeys.first()
        packetDao.deleteContacts(myNodeNum, listOf(contactKey))
        assertEquals(0, packetDao.getMessageCount(myNodeNum, contactKey))
    }

    @Test
    fun testGetContactKeys() = runTest {
        val contacts = packetDao.getContactKeys(myNodeNum).first()
        assertEquals(testContactKeys.size, contacts.size)
        testContactKeys.forEach { assertTrue(contacts.containsKey(it)) }
    }

    @Test
    fun testGetWaypoints() = runTest {
        val waypointPacket =
            Packet(
                uuid = 0L,
                myNodeNum = myNodeNum,
                port_num = PortNum.WAYPOINT_APP.value,
                contact_key = "0${DataPacket.ID_BROADCAST}",
                received_time = nowMillis,
                read = true,
                data =
                DataPacket(
                    to = DataPacket.ID_BROADCAST,
                    bytes = "Waypoint".encodeToByteArray().toByteString(),
                    dataType = PortNum.WAYPOINT_APP.value,
                ),
            )
        packetDao.insert(waypointPacket)
        val waypoints = packetDao.getAllWaypoints(myNodeNum)
        assertEquals(1, waypoints.size)
    }

    @Test
    fun testUpsertReaction() = runTest {
        val reaction =
            ReactionEntity(myNodeNum = myNodeNum, replyId = 123, userId = "!test", emoji = "👍", timestamp = nowMillis)
        packetDao.insert(reaction)
    }

    @Test
    fun testGetMessagesFromWithIncludeFiltered() = runTest {
        val contactKey = "filter-test"
        val normalMessages = listOf("Msg 1", "Msg 2")
        val filteredMessages = listOf("Filtered 1")

        normalMessages.forEachIndexed { index, text ->
            packetDao.insert(
                Packet(
                    uuid = 0L,
                    myNodeNum = myNodeNum,
                    port_num = PortNum.TEXT_MESSAGE_APP.value,
                    contact_key = contactKey,
                    received_time = nowMillis + index,
                    read = false,
                    data = DataPacket(
                        to = DataPacket.ID_BROADCAST,
                        bytes = text.encodeToByteArray().toByteString(),
                        dataType = PortNum.TEXT_MESSAGE_APP.value,
                    ),
                    filtered = false,
                ),
            )
        }

        filteredMessages.forEachIndexed { index, text ->
            packetDao.insert(
                Packet(
                    uuid = 0L,
                    myNodeNum = myNodeNum,
                    port_num = PortNum.TEXT_MESSAGE_APP.value,
                    contact_key = contactKey,
                    received_time = nowMillis + normalMessages.size + index,
                    read = true,
                    data = DataPacket(
                        to = DataPacket.ID_BROADCAST,
                        bytes = text.encodeToByteArray().toByteString(),
                        dataType = PortNum.TEXT_MESSAGE_APP.value,
                    ),
                    filtered = true,
                ),
            )
        }

        val allMessages = packetDao.getMessagesFrom(myNodeNum, contactKey).first()
        assertEquals(normalMessages.size + filteredMessages.size, allMessages.size)

        val includingFiltered = packetDao.getMessagesFrom(myNodeNum, contactKey, includeFiltered = true).first()
        assertEquals(normalMessages.size + filteredMessages.size, includingFiltered.size)

        val excludingFiltered = packetDao.getMessagesFrom(myNodeNum, contactKey, includeFiltered = false).first()
        assertEquals(normalMessages.size, excludingFiltered.size)
        assertFalse(excludingFiltered.any { it.packet.filtered })
    }

    @Test
    fun testGetPacketsByPacketIdsChunked() = runTest {
        val totalPackets = 2000
        val chunkSize = MAX_SQLITE_BIND_PARAMS
        val contactKey = "chunk-test"
        val baseTime = nowMillis
        val packetIds = (1..totalPackets).toList()

        packetIds.forEach { id ->
            packetDao.insert(
                Packet(
                    uuid = 0L,
                    myNodeNum = myNodeNum,
                    port_num = PortNum.TEXT_MESSAGE_APP.value,
                    contact_key = contactKey,
                    received_time = baseTime + id,
                    read = false,
                    data = DataPacket(
                        to = DataPacket.ID_BROADCAST,
                        bytes = "Chunk $id".encodeToByteArray().toByteString(),
                        dataType = PortNum.TEXT_MESSAGE_APP.value,
                    ),
                    packetId = id,
                ),
            )
        }

        val fetched = packetIds.chunked(chunkSize).flatMap { packetDao.getPacketsByPacketIds(myNodeNum, it) }
        assertEquals(totalPackets, fetched.size)
        assertEquals(packetIds.toSet(), fetched.map { it.packet.packetId }.toSet())
    }

    companion object {
        private const val SAMPLE_SIZE = 10
        private const val MAX_SQLITE_BIND_PARAMS = 999
    }
}
