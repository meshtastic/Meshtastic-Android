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
package org.meshtastic.core.data.manager

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio

class PacketHandlerImplTest {

    private val packetRepository: PacketRepository = mockk(relaxed = true)
    private val serviceBroadcasts: ServiceBroadcasts = mockk(relaxed = true)
    private val radioInterfaceService: RadioInterfaceService = mockk(relaxed = true)
    private val meshLogRepository: MeshLogRepository = mockk(relaxed = true)
    private val serviceRepository: ServiceRepository = mockk(relaxed = true)
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var handler: PacketHandlerImpl

    @Before
    fun setUp() {
        every { serviceRepository.connectionState } returns connectionStateFlow
        every { serviceRepository.setConnectionState(any()) } answers { connectionStateFlow.value = firstArg() }

        handler =
            PacketHandlerImpl(
                { packetRepository },
                serviceBroadcasts,
                radioInterfaceService,
                { meshLogRepository },
                serviceRepository,
            )
        handler.start(testScope)
    }

    @Test
    fun `sendToRadio with ToRadio sends immediately`() {
        val toRadio = ToRadio(packet = MeshPacket(id = 123))

        handler.sendToRadio(toRadio)

        verify { radioInterfaceService.sendToRadio(any()) }
    }

    @Test
    fun `sendToRadio with MeshPacket queues and sends when connected`() = runTest(testDispatcher) {
        val packet = MeshPacket(id = 456)
        connectionStateFlow.value = ConnectionState.Connected

        handler.sendToRadio(packet)
        testScheduler.runCurrent()

        verify { radioInterfaceService.sendToRadio(any()) }
    }

    @Test
    fun `handleQueueStatus completes deferred`() = runTest(testDispatcher) {
        val packet = MeshPacket(id = 789)
        connectionStateFlow.value = ConnectionState.Connected

        handler.sendToRadio(packet)
        testScheduler.runCurrent()

        val status =
            QueueStatus(
                mesh_packet_id = 789,
                res = 0, // Success
                free = 1,
            )

        handler.handleQueueStatus(status)
        testScheduler.runCurrent()
    }

    @Test
    fun `outgoing packets are logged with NODE_NUM_LOCAL`() = runTest(testDispatcher) {
        val packet = MeshPacket(id = 123, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP))
        val toRadio = ToRadio(packet = packet)

        handler.sendToRadio(toRadio)
        testScheduler.runCurrent()

        coVerify { meshLogRepository.insert(match { log -> log.fromNum == MeshLog.NODE_NUM_LOCAL }) }
    }
}
