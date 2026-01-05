/*
 * Copyright (c) 2025 Meshtastic LLC
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

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.proto.Portnums

@RunWith(AndroidJUnit4::class)
class PacketDaoTest {
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
                port_num = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                contact_key = contactKey,
                received_time = System.currentTimeMillis(),
                read = false,
                DataPacket(DataPacket.ID_BROADCAST, 0, "Message $it!"),
            )
        }
    }

    @Before
    fun createDb(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MeshtasticDatabase::class.java).build()

        nodeInfoDao = database.nodeInfoDao().apply { setMyNodeInfo(myNodeInfo) }

        packetDao =
            database.packetDao().apply {
                generateTestPackets(42424243).forEach { insert(it) }
                generateTestPackets(myNodeNum).forEach { insert(it) }
            }
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun test_myNodeNum() = runBlocking {
        val myNodeInfo = nodeInfoDao.getMyNodeInfo().first()
        assertEquals(myNodeNum, myNodeInfo?.myNodeNum)
    }

    @Test
    fun test_getAllPackets() = runBlocking {
        val packets = packetDao.getAllPackets(Portnums.PortNum.TEXT_MESSAGE_APP_VALUE).first()
        assertEquals(testContactKeys.size * SAMPLE_SIZE, packets.size)

        val onlyMyNodeNum = packets.all { it.myNodeNum == myNodeNum }
        assertTrue(onlyMyNodeNum)
    }

    @Test
    fun test_getContactKeys() = runBlocking {
        val contactKeys = packetDao.getContactKeys().first()
        assertEquals(testContactKeys.size, contactKeys.size)

        val onlyMyNodeNum = contactKeys.values.all { it.myNodeNum == myNodeNum }
        assertTrue(onlyMyNodeNum)
    }

    @Test
    fun test_getMessageCount() = runBlocking {
        testContactKeys.forEach { contactKey ->
            val messageCount = packetDao.getMessageCount(contactKey)
            assertEquals(SAMPLE_SIZE, messageCount)
        }
    }

    @Test
    fun test_getMessagesFrom() = runBlocking {
        testContactKeys.forEach { contactKey ->
            val messages = packetDao.getMessagesFrom(contactKey).first()
            assertEquals(SAMPLE_SIZE, messages.size)

            val onlyFromContactKey = messages.all { it.packet.contact_key == contactKey }
            assertTrue(onlyFromContactKey)

            val onlyMyNodeNum = messages.all { it.packet.myNodeNum == myNodeNum }
            assertTrue(onlyMyNodeNum)
        }
    }

    @Test
    fun test_getUnreadCount() = runBlocking {
        testContactKeys.forEach { contactKey ->
            val unreadCount = packetDao.getUnreadCount(contactKey)
            assertEquals(SAMPLE_SIZE, unreadCount)
        }
    }

    @Test
    fun test_clearUnreadCount() = runBlocking {
        val timestamp = System.currentTimeMillis()
        testContactKeys.forEach { contactKey ->
            packetDao.clearUnreadCount(contactKey, timestamp)
            val unreadCount = packetDao.getUnreadCount(contactKey)
            assertEquals(0, unreadCount)
        }
    }

    @Test
    fun test_deleteContacts() = runBlocking {
        packetDao.deleteContacts(testContactKeys)

        testContactKeys.forEach { contactKey ->
            val messages = packetDao.getMessagesFrom(contactKey).first()
            assertTrue(messages.isEmpty())
        }
    }

    @Test
    fun test_findPacketsWithId() = runBlocking {
        val packetId = 12345
        val packet =
            Packet(
                uuid = 0L,
                myNodeNum = myNodeNum,
                port_num = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                contact_key = "test",
                received_time = System.currentTimeMillis(),
                read = true,
                data = DataPacket(to = DataPacket.ID_BROADCAST, channel = 0, text = "Test").copy(id = packetId),
                packetId = packetId,
            )

        packetDao.insert(packet)

        val found = packetDao.findPacketsWithId(packetId)
        assertEquals(1, found.size)
        assertEquals(packetId, found[0].packetId)
    }

    @Test
    fun test_sfppHashPersistence() = runBlocking {
        val hash = byteArrayOf(1, 2, 3, 4)
        val packet =
            Packet(
                uuid = 0L,
                myNodeNum = myNodeNum,
                port_num = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                contact_key = "test",
                received_time = System.currentTimeMillis(),
                read = true,
                data = DataPacket(to = DataPacket.ID_BROADCAST, channel = 0, text = "Test"),
                sfpp_hash = hash,
            )

        packetDao.insert(packet)

        val retrieved = packetDao.getAllPackets(Portnums.PortNum.TEXT_MESSAGE_APP_VALUE).first().find { it.sfpp_hash?.contentEquals(hash) == true }
        assertNotNull(retrieved)
        assertTrue(retrieved?.sfpp_hash?.contentEquals(hash) == true)
    }

    @Test
    fun test_updateSFPPStatus_logic() = runBlocking {
        val packetId = 999
        val fromNum = 123
        val toNum = 456
        val hash = byteArrayOf(9, 8, 7, 6)

        val fromId = DataPacket.nodeNumToDefaultId(fromNum)
        val toId = DataPacket.nodeNumToDefaultId(toNum)

        val packet =
            Packet(
                uuid = 0L,
                myNodeNum = myNodeNum,
                port_num = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                contact_key = "test",
                received_time = System.currentTimeMillis(),
                read = true,
                data = DataPacket(to = toId, channel = 0, text = "Match me").copy(from = fromId, id = packetId),
                packetId = packetId,
            )

        packetDao.insert(packet)

        // Verifying the logic used in PacketRepository
        val found = packetDao.findPacketsWithId(packetId)
        found.forEach { p ->
            if (p.data.from == fromId && p.data.to == toId) {
                packetDao.update(
                    p.copy(
                        data = p.data.copy(status = MessageStatus.SFPP_CONFIRMED, sfppHash = hash),
                        sfpp_hash = hash,
                    ),
                )
            }
        }

        val updated = packetDao.findPacketsWithId(packetId)[0]
        assertEquals(MessageStatus.SFPP_CONFIRMED, updated.data.status)
        assertTrue(updated.data.sfppHash?.contentEquals(hash) == true)
        assertTrue(updated.sfpp_hash?.contentEquals(hash) == true)
    }

    companion object {
        private const val SAMPLE_SIZE = 10
    }
}
