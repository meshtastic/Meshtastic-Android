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

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.PortNum

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var packetDao: PacketDao
    private lateinit var nodeInfoDao: NodeInfoDao

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

    @Before
    fun createDb(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MeshtasticDatabase::class.java).build()
        nodeInfoDao = database.nodeInfoDao().apply { setMyNodeInfo(myNodeInfo) }
        packetDao = database.packetDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun testMigrateChannelsByPSK_duplicatePSK() = runBlocking {
        // PSK \"AQ==\" is base64 for single byte 0x01
        val pskBytes = byteArrayOf(0x01).toByteString()

        // Create packets for Channel 0
        insertPacket(channel = 0, text = "Message Ch0")

        // Old settings: Channel 0 has PSK_A
        val oldSettings = listOf(ChannelSettings(psk = pskBytes, name = "LongFast"))

        // New settings: Channel 0 has PSK_A, Channel 1 has PSK_A
        val newSettings =
            listOf(
                ChannelSettings(psk = pskBytes, name = "LongFast"),
                ChannelSettings(psk = pskBytes, name = "NewChan"),
            )

        // Perform migration
        packetDao.migrateChannelsByPSK(oldSettings, newSettings)

        // Check packet channel
        val p = getFirstPacket()
        assertEquals("Packet should remain on channel 0", 0, p.data.channel)
    }

    @Test
    fun testMigrateChannelsByPSK_reorder() = runBlocking {
        val pskA = byteArrayOf(0x01).toByteString()
        val pskB = byteArrayOf(0x02).toByteString()

        insertPacket(channel = 0, text = "Msg A")
        insertPacket(channel = 1, text = "Msg B")

        val oldSettings = listOf(ChannelSettings(psk = pskA, name = "A"), ChannelSettings(psk = pskB, name = "B"))

        val newSettings = listOf(ChannelSettings(psk = pskB, name = "B"), ChannelSettings(psk = pskA, name = "A"))

        packetDao.migrateChannelsByPSK(oldSettings, newSettings)

        val packets = getAllPackets()
        assertEquals(1, packets.find { it.data.text == "Msg A" }?.data?.channel)
        assertEquals(0, packets.find { it.data.text == "Msg B" }?.data?.channel)
    }

    @Test
    fun testMigrateChannelsByPSK_disambiguateByName() = runBlocking {
        val pskA = byteArrayOf(0x01).toByteString()

        insertPacket(channel = 0, text = "Msg A1")
        insertPacket(channel = 1, text = "Msg A2")

        val oldSettings = listOf(ChannelSettings(psk = pskA, name = "A1"), ChannelSettings(psk = pskA, name = "A2"))

        // Swap positions but keep names and PSKs
        val newSettings = listOf(ChannelSettings(psk = pskA, name = "A2"), ChannelSettings(psk = pskA, name = "A1"))

        packetDao.migrateChannelsByPSK(oldSettings, newSettings)

        val packets = getAllPackets()
        assertEquals("Msg A1 should move to index 1", 1, packets.find { it.data.text == "Msg A1" }?.data?.channel)
        assertEquals("Msg A2 should move to index 0", 0, packets.find { it.data.text == "Msg A2" }?.data?.channel)
    }

    @Test
    fun testMigrateChannelsByPSK_preferSameIndexIfStillAmbiguous() = runBlocking {
        val pskA = byteArrayOf(0x01).toByteString()

        insertPacket(channel = 0, text = "Msg A")

        val oldSettings = listOf(ChannelSettings(psk = pskA, name = "A"))

        // New settings has two identical channels (same PSK, same Name)
        val newSettings = listOf(ChannelSettings(psk = pskA, name = "A"), ChannelSettings(psk = pskA, name = "A"))

        packetDao.migrateChannelsByPSK(oldSettings, newSettings)

        val p = getFirstPacket()
        assertEquals("Should prefer keeping same index 0", 0, p.data.channel)
    }

    private suspend fun insertPacket(channel: Int, text: String) {
        packetDao.insert(
            Packet(
                uuid = 0L,
                myNodeNum = 42424242,
                port_num = PortNum.TEXT_MESSAGE_APP.value,
                contact_key = "$channel!broadcast",
                received_time = nowMillis,
                read = false,
                data = DataPacket(to = DataPacket.ID_BROADCAST, channel = channel, text = text),
            ),
        )
    }

    private suspend fun getAllPackets() = packetDao.getAllPackets(PortNum.TEXT_MESSAGE_APP.value).first()

    private suspend fun getFirstPacket() = getAllPackets().first()
}
