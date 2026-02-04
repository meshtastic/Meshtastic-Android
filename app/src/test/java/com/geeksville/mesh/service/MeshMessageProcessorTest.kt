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
package com.geeksville.mesh.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum

class MeshMessageProcessorTest {

    private val nodeManager: MeshNodeManager = mockk(relaxed = true)
    private val serviceRepository: ServiceRepository = mockk(relaxed = true)
    private val meshLogRepository: MeshLogRepository = mockk(relaxed = true)
    private val router: MeshRouter = mockk(relaxed = true)
    private val fromRadioDispatcher: FromRadioPacketHandler = mockk(relaxed = true)
    private val meshLogRepositoryLazy = dagger.Lazy { meshLogRepository }
    private val dataHandler: MeshDataHandler = mockk(relaxed = true)

    private val isNodeDbReady = MutableStateFlow(false)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var processor: MeshMessageProcessor

    @Before
    fun setUp() {
        every { nodeManager.isNodeDbReady } returns isNodeDbReady
        every { router.dataHandler } returns dataHandler
        processor =
            MeshMessageProcessor(nodeManager, serviceRepository, meshLogRepositoryLazy, router, fromRadioDispatcher)
        processor.start(testScope)
    }

    @Test
    fun `early packets are buffered and flushed when DB is ready`() = runTest(testDispatcher) {
        val packet = MeshPacket(id = 123, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP))

        // 1. Database is NOT ready
        isNodeDbReady.value = false
        testScheduler.runCurrent() // trigger start() onEach

        processor.handleReceivedMeshPacket(packet, 999)

        // Verify that handleReceivedData has NOT been called yet
        verify(exactly = 0) { dataHandler.handleReceivedData(any(), any(), any(), any()) }

        // 2. Database becomes ready
        isNodeDbReady.value = true
        testScheduler.runCurrent() // trigger onEach(true)

        // Verify that handleReceivedData is now called with the buffered packet
        verify(exactly = 1) { dataHandler.handleReceivedData(match { it.id == 123 }, any(), any(), any()) }
    }

    @Test
    fun `packets are processed immediately if DB is already ready`() = runTest(testDispatcher) {
        val packet = MeshPacket(id = 456, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP))

        isNodeDbReady.value = true
        testScheduler.runCurrent()

        processor.handleReceivedMeshPacket(packet, 999)

        verify(exactly = 1) { dataHandler.handleReceivedData(match { it.id == 456 }, any(), any(), any()) }
    }
}
