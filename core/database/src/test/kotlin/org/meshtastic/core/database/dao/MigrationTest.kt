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
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.channelSettings

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
        // PSK "AQ==" is base64 for single byte 0x01
        val pskBytes = ByteString.copyFrom(byteArrayOf(0x01))

        // Create packets for Channel 0
        insertPacket(channel = 0, text = "Message Ch0")

        // Old settings: Channel 0 has PSK_A
        val oldSettings =
            listOf(
                channelSettings {
                    psk = pskBytes
                    name = "LongFast"
                },
            )

        // New settings: Channel 0 has PSK_A, Channel 1 has PSK_A
        val newSettings =
            listOf(
                channelSettings {
                    psk = pskBytes
                    name = "LongFast"
                },
                channelSettings {
                    psk = pskBytes
                    name = "NewChan"
                },
            )

        // Perform migration
        packetDao.migrateChannelsByPSK(oldSettings, newSettings)

        // Check packet channel
        val p = getFirstPacket()
        assertEquals("Packet should remain on channel 0", 0, p.data.channel)
    }

    @Test
    fun testMigrateChannelsByPSK_reorder() = runBlocking {
        val pskA = ByteString.copyFrom(byteArrayOf(0x01))
        val pskB = ByteString.copyFrom(byteArrayOf(0x02))

        insertPacket(channel = 0, text = "Msg A")
        insertPacket(channel = 1, text = "Msg B")

        val oldSettings =
            listOf(
                channelSettings {
                    psk = pskA
                    name = "A"
                },
                channelSettings {
                    psk = pskB
                    name = "B"
                },
            )

        val newSettings =
            listOf(
                channelSettings {
                    psk = pskB
                    name = "B"
                },
                channelSettings {
                    psk = pskA
                    name = "A"
                },
            )

        packetDao.migrateChannelsByPSK(oldSettings, newSettings)

        val packets = getAllPackets()
        assertEquals(1, packets.find { it.data.text == "Msg A" }?.data?.channel)
        assertEquals(0, packets.find { it.data.text == "Msg B" }?.data?.channel)
    }

    @Test
    fun testMigrateChannelsByPSK_disambiguateByName() = runBlocking {
        val pskA = ByteString.copyFrom(byteArrayOf(0x01))

        insertPacket(channel = 0, text = "Msg A1")
        insertPacket(channel = 1, text = "Msg A2")

        val oldSettings =
            listOf(
                channelSettings {
                    psk = pskA
                    name = "A1"
                },
                channelSettings {
                    psk = pskA
                    name = "A2"
                },
            )

        // Swap positions but keep names and PSKs
        val newSettings =
            listOf(
                channelSettings {
                    psk = pskA
                    name = "A2"
                },
                channelSettings {
                    psk = pskA
                    name = "A1"
                },
            )

        packetDao.migrateChannelsByPSK(oldSettings, newSettings)

        val packets = getAllPackets()
        assertEquals("Msg A1 should move to index 1", 1, packets.find { it.data.text == "Msg A1" }?.data?.channel)
        assertEquals("Msg A2 should move to index 0", 0, packets.find { it.data.text == "Msg A2" }?.data?.channel)
    }

    @Test
    fun testMigrateChannelsByPSK_preferSameIndexIfStillAmbiguous() = runBlocking {
        val pskA = ByteString.copyFrom(byteArrayOf(0x01))

        insertPacket(channel = 0, text = "Msg A")

        val oldSettings =
            listOf(
                channelSettings {
                    psk = pskA
                    name = "A"
                },
            )

        // New settings has two identical channels (same PSK, same Name)
        val newSettings =
            listOf(
                channelSettings {
                    psk = pskA
                    name = "A"
                },
                channelSettings {
                    psk = pskA
                    name = "A"
                },
            )

        packetDao.migrateChannelsByPSK(oldSettings, newSettings)

        val p = getFirstPacket()
        assertEquals("Should prefer keeping same index 0", 0, p.data.channel)
    }

    private suspend fun insertPacket(channel: Int, text: String) {
        packetDao.insert(
            Packet(
                uuid = 0L,
                myNodeNum = 42424242,
                port_num = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                contact_key = "$channel!broadcast",
                received_time = System.currentTimeMillis(),
                read = false,
                data = DataPacket(to = DataPacket.ID_BROADCAST, channel = channel, text = text),
            ),
        )
    }

    private suspend fun getAllPackets() = packetDao.getAllPackets(Portnums.PortNum.TEXT_MESSAGE_APP_VALUE).first()

    private suspend fun getFirstPacket() = getAllPackets().first()
}
