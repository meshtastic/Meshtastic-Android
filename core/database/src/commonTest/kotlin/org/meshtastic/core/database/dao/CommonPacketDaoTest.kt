/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import org.meshtastic.core.database.entity.MyNodeEntity
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
    private lateinit var nodeInfoDao: NodeInfoDao
    private lateinit var packetDao: PacketDao

    private val myNodeInfo: MyNodeEntity =
        MyNodeEntity(
            myNodeNum = 42424242,
            model = null,
            firmwareVersion = null,
            couldUpdate = false,
            shouldUpdate = false,
            currentPacketId = 1L,
            messageTimeoutMsec = 5 * 60 * 1000,
            minAppVersion = 1,
            maxChannels = 8,
            hasWifi = false,
        )

    private val myNodeNum: Int
        get() = myNodeInfo.myNodeNum

    private val testContactKeys = listOf("0${DataPacket.ID_BROADCAST}", "1!test1234")

    private fun generateTestPackets(myNodeNum: Int) = testContactKeys.flatMap { contactKey ->
        List(SAMPLE_SIZE) {
            Packet(
                uuid = 0L,
                myNodeNum = myNodeNum,
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
        nodeInfoDao = database.nodeInfoDao().apply { setMyNodeInfo(myNodeInfo) }

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
        val messages = packetDao.getMessagesFrom(contactKey).first()
        assertEquals(SAMPLE_SIZE, messages.size)
        assertTrue(messages.all { it.packet.myNodeNum == myNodeNum })
        assertTrue(messages.all { it.packet.contact_key == contactKey })
    }

    @Test
    fun testGetMessageCount() = runTest {
        val contactKey = testContactKeys.first()
        assertEquals(SAMPLE_SIZE, packetDao.getMessageCount(contactKey))
    }

    @Test
    fun testGetUnreadCount() = runTest {
        val contactKey = testContactKeys.first()
        assertEquals(SAMPLE_SIZE, packetDao.getUnreadCount(contactKey))
    }

    @Test
    fun testClearUnreadCount() = runTest {
        val contactKey = testContactKeys.first()
        packetDao.clearUnreadCount(contactKey, nowMillis + SAMPLE_SIZE)
        assertEquals(0, packetDao.getUnreadCount(contactKey))
    }

    @Test
    fun testClearAllUnreadCounts() = runTest {
        packetDao.clearAllUnreadCounts()
        testContactKeys.forEach { assertEquals(0, packetDao.getUnreadCount(it)) }
    }

    @Test
    fun testUpdateMessageStatus() = runTest {
        val contactKey = testContactKeys.first()
        val messages = packetDao.getMessagesFrom(contactKey).first()
        val packet = messages.first().packet.data
        val originalStatus = packet.status

        // Ensure packet has a valid ID for updating
        val packetWithId = packet.copy(id = 999, from = "!$myNodeNum")
        val updatedRoomPacket = messages.first().packet.copy(data = packetWithId, packetId = 999)
        packetDao.update(updatedRoomPacket)

        packetDao.updateMessageStatus(packetWithId, MessageStatus.DELIVERED)
        val updatedMessages = packetDao.getMessagesFrom(contactKey).first()
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
        val queued = packetDao.getQueuedPackets()
        assertNotNull(queued)
        assertEquals(1, queued.size)
        assertEquals("Queued", queued.first().text)
    }

    @Test
    fun testDeleteMessages() = runTest {
        val contactKey = testContactKeys.first()
        packetDao.deleteContacts(listOf(contactKey))
        assertEquals(0, packetDao.getMessageCount(contactKey))
    }

    @Test
    fun testGetContactKeys() = runTest {
        val contacts = packetDao.getContactKeys().first()
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
        val waypoints = packetDao.getAllWaypoints()
        assertEquals(1, waypoints.size)
        // Waypoints aren't text messages, so they don't resolve a string text.
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
            val packet =
                Packet(
                    uuid = 0L,
                    myNodeNum = myNodeNum,
                    port_num = PortNum.TEXT_MESSAGE_APP.value,
                    contact_key = contactKey,
                    received_time = nowMillis + index,
                    read = false,
                    data =
                    DataPacket(
                        to = DataPacket.ID_BROADCAST,
                        bytes = text.encodeToByteArray().toByteString(),
                        dataType = PortNum.TEXT_MESSAGE_APP.value,
                    ),
                    filtered = false,
                )
            packetDao.insert(packet)
        }

        filteredMessages.forEachIndexed { index, text ->
            val packet =
                Packet(
                    uuid = 0L,
                    myNodeNum = myNodeNum,
                    port_num = PortNum.TEXT_MESSAGE_APP.value,
                    contact_key = contactKey,
                    received_time = nowMillis + normalMessages.size + index,
                    read = true,
                    data =
                    DataPacket(
                        to = DataPacket.ID_BROADCAST,
                        bytes = text.encodeToByteArray().toByteString(),
                        dataType = PortNum.TEXT_MESSAGE_APP.value,
                    ),
                    filtered = true,
                )
            packetDao.insert(packet)
        }

        val allMessages = packetDao.getMessagesFrom(contactKey).first()
        assertEquals(normalMessages.size + filteredMessages.size, allMessages.size)

        val includingFiltered = packetDao.getMessagesFrom(contactKey, includeFiltered = true).first()
        assertEquals(normalMessages.size + filteredMessages.size, includingFiltered.size)

        val excludingFiltered = packetDao.getMessagesFrom(contactKey, includeFiltered = false).first()
        assertEquals(normalMessages.size, excludingFiltered.size)
        assertFalse(excludingFiltered.any { it.packet.filtered })
    }

    @Test
    fun testGetPacketsByPacketIdsChunked() = runTest {
        // Regression test for SQLITE_MAX_VARIABLE_NUMBER (999) limit. Inserting >999 packets and
        // looking them up by id must not throw; callers are expected to chunk, and each chunk
        // must return the correct rows.
        val totalPackets = 2000
        val chunkSize = NodeInfoDao.MAX_BIND_PARAMS
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
                    data =
                    DataPacket(
                        to = DataPacket.ID_BROADCAST,
                        bytes = "Chunk $id".encodeToByteArray().toByteString(),
                        dataType = PortNum.TEXT_MESSAGE_APP.value,
                    ),
                    packetId = id,
                ),
            )
        }

        val fetched = packetIds.chunked(chunkSize).flatMap { packetDao.getPacketsByPacketIds(it) }
        assertEquals(totalPackets, fetched.size)
        assertEquals(packetIds.toSet(), fetched.map { it.packet.packetId }.toSet())
    }

    companion object {
        private const val SAMPLE_SIZE = 10
    }
}
