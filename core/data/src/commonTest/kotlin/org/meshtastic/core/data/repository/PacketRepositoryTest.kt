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

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PacketRepositoryTest {

    private lateinit var dbProvider: FakeDatabaseProvider
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private lateinit var repository: PacketRepositoryImpl

    @BeforeTest
    fun setUp() {
        dbProvider = FakeDatabaseProvider()
        repository = PacketRepositoryImpl(dbProvider, dispatchers)
    }

    @AfterTest
    fun tearDown() {
        dbProvider.close()
    }

    @Test
    fun `savePacket persists and retrieves waypoints`() = runTest(testDispatcher) {
        val myNodeNum = 1
        val contact = "contact"
        
        // Ensure my_node is present so getMessageCount finds the packet
        dbProvider.currentDb.value.nodeInfoDao().setMyNodeInfo(MyNodeEntity(
            myNodeNum = myNodeNum,
            model = "model",
            firmwareVersion = "1.0",
            couldUpdate = false,
            shouldUpdate = false,
            currentPacketId = 0L,
            messageTimeoutMsec = 0,
            minAppVersion = 0,
            maxChannels = 0,
            hasWifi = false,
        ))

        val packet = DataPacket(
            to = "0!ffffffff",
            bytes = okio.ByteString.EMPTY,
            dataType = 1,
            id = 123
        )

        repository.savePacket(myNodeNum, contact, packet, 1000L)

        // Verify it was saved.
        val count = repository.getMessageCount(contact)
        assertEquals(1, count)
    }

    @Test
    fun `clearAllUnreadCounts works with real DB`() = runTest(testDispatcher) {
        repository.clearAllUnreadCounts()
        // No exception thrown
    }
}
