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
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.PortNum
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacketTranslationDaoTest {

    private lateinit var database: MeshtasticDatabase
    private lateinit var packetDao: PacketDao

    private val myNodeInfo =
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

    private val contactKey = "0${NodeAddress.ID_BROADCAST}"

    @BeforeTest
    fun setUp() {
        database = getInMemoryDatabaseBuilder().build()
        packetDao = database.packetDao()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private suspend fun seedTextPacket(): Long {
        database.nodeInfoDao().setMyNodeInfo(myNodeInfo)
        packetDao.insert(
            Packet(
                uuid = 0L,
                myNodeNum = myNodeInfo.myNodeNum,
                port_num = PortNum.TEXT_MESSAGE_APP.value,
                contact_key = contactKey,
                received_time = 1000L,
                read = true,
                data =
                DataPacket(
                    to = NodeAddress.ID_BROADCAST,
                    bytes = "Hola, ¿cómo estás?".encodeToByteArray().toByteString(),
                    dataType = PortNum.TEXT_MESSAGE_APP.value,
                ),
            ),
        )
        return packet().uuid
    }

    private suspend fun packet(): Packet = packetDao.getMessagesFrom(contactKey).first().single().packet

    @Test
    fun setTranslationPersistsTextAndShowsIt() = runTest {
        val uuid = seedTextPacket()
        assertNull(packet().translatedText)
        assertFalse(packet().showTranslated)

        packetDao.setTranslation(uuid, "Hello, how are you?")

        val updated = packet()
        assertEquals("Hello, how are you?", updated.translatedText)
        assertTrue(updated.showTranslated)
    }

    @Test
    fun setShowTranslatedFlipsOnlyTheFlag() = runTest {
        val uuid = seedTextPacket()
        packetDao.setTranslation(uuid, "Hello, how are you?")

        packetDao.setShowTranslated(uuid, false)
        val original = packet()
        assertEquals("Hello, how are you?", original.translatedText)
        assertFalse(original.showTranslated)

        packetDao.setShowTranslated(uuid, true)
        val translated = packet()
        assertEquals("Hello, how are you?", translated.translatedText)
        assertTrue(translated.showTranslated)
    }
}
