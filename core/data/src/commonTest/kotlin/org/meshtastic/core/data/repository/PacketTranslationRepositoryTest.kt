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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.proto.PortNum
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PacketTranslationRepositoryTest {

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var repository: PacketRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private val myNodeNum = 42424242
    private val contactKey = "0${NodeAddress.ID_BROADCAST}"

    @BeforeTest
    fun setUp() {
        dbProvider = FakeDatabaseProvider()
        repository = PacketRepositoryImpl(dbProvider, dispatchers)
    }

    @AfterTest
    fun tearDown() {
        dbProvider.close()
    }

    private suspend fun seedTextPacket(): Long {
        val db = dbProvider.currentDb.value
        db.nodeInfoDao()
            .setMyNodeInfo(
                MyNodeEntity(
                    myNodeNum = myNodeNum,
                    model = null,
                    firmwareVersion = null,
                    couldUpdate = false,
                    shouldUpdate = false,
                    currentPacketId = 1L,
                    messageTimeoutMsec = 5 * 60 * 1000,
                    minAppVersion = 1,
                    maxChannels = 8,
                    hasWifi = false,
                ),
            )
        db.packetDao()
            .insert(
                Packet(
                    uuid = 0L,
                    myNodeNum = myNodeNum,
                    port_num = PortNum.TEXT_MESSAGE_APP.value,
                    contact_key = contactKey,
                    received_time = 1000L,
                    read = true,
                    data =
                    DataPacket(
                        to = NodeAddress.ID_BROADCAST,
                        bytes = "Hola".encodeToByteArray().toByteString(),
                        dataType = PortNum.TEXT_MESSAGE_APP.value,
                    ),
                ),
            )
        return packet().uuid
    }

    private suspend fun packet(): Packet =
        dbProvider.currentDb.value.packetDao().getMessagesFrom(contactKey).first().single().packet

    @Test
    fun setMessageTranslationPersistsAndShowsTranslation() = runTest(testDispatcher) {
        val uuid = seedTextPacket()

        repository.setMessageTranslation(uuid, "Hello")

        val updated = packet()
        assertEquals("Hello", updated.translatedText)
        assertTrue(updated.showTranslated)
    }

    @Test
    fun setShowTranslatedTogglesDisplayState() = runTest(testDispatcher) {
        val uuid = seedTextPacket()
        repository.setMessageTranslation(uuid, "Hello")

        repository.setShowTranslated(uuid, false)
        assertFalse(packet().showTranslated)
        assertEquals("Hello", packet().translatedText)

        repository.setShowTranslated(uuid, true)
        assertTrue(packet().showTranslated)
    }
}
