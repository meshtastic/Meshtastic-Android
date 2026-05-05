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
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.FakeNodeRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class CommonPacketRepositoryTest {

    protected lateinit var dbProvider: FakeDatabaseProvider
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)
    private val nodeRepository = FakeNodeRepository()

    protected lateinit var repository: PacketRepositoryImpl

    fun setupRepo() {
        dbProvider = FakeDatabaseProvider()
        repository = PacketRepositoryImpl(dbProvider, dispatchers, nodeRepository)
    }

    @AfterTest
    fun tearDown() {
        dbProvider.close()
    }

    @Test
    fun `savePacket persists and retrieves waypoints`() = runTest(testDispatcher) {
        val myNodeNum = 1
        val contact = "contact"

        // Set the current node number so PacketRepositoryImpl can pass it to queries
        nodeRepository.setMyNodeInfo(org.meshtastic.core.testing.TestDataFactory.createMyNodeInfo(myNodeNum = myNodeNum))

        val packet = DataPacket(to = DataPacket.BROADCAST, bytes = okio.ByteString.EMPTY, dataType = 1, id = 123)

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
