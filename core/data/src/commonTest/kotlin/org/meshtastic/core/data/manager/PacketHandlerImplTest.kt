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

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PacketHandlerImplTest {

    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val meshLogRepository: MeshLogRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)

    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var handler: PacketHandlerImpl

    @BeforeTest
    fun setUp() {
        every { serviceRepository.connectionState } returns connectionStateFlow

        handler =
            PacketHandlerImpl(
                lazy { packetRepository },
                radioInterfaceService,
                lazy { meshLogRepository },
                serviceRepository,
                testScope,
            )
    }

    @Test
    fun testInitialization() {
        assertNotNull(handler)
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
    fun `handleQueueStatus treats ERRNO_SHOULD_RELEASE as success`() = runTest(testDispatcher) {
        // Firmware 2.8+ returns ErrorCode 35 (ERRNO_SHOULD_RELEASE) for self-addressed packets delivered
        // through the synchronous local loopback — a success, not a queue failure.
        connectionStateFlow.value = ConnectionState.Connected

        val result = async { handler.sendToRadioAndAwait(MeshPacket(id = 790)) }
        testScheduler.runCurrent()

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 790, res = 35, free = 16))
        testScheduler.runCurrent()

        assertTrue(result.await())
    }

    @Test
    fun `handleQueueStatus completes ERRNO_SHOULD_RELEASE even when queue is full`() = runTest(testDispatcher) {
        // Regression: a self-addressed local-loopback delivery (res=35) can coincide with a full TX queue (free=0).
        // The success+full early return must not swallow it, or the response hangs until TIMEOUT (the very stall
        // this fix targets). Only the plain res=0 "accepted, now full" echo should be skipped.
        connectionStateFlow.value = ConnectionState.Connected

        val result = async { handler.sendToRadioAndAwait(MeshPacket(id = 792)) }
        testScheduler.runCurrent()

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 792, res = 35, free = 0))
        testScheduler.runCurrent()

        assertTrue(result.await())
    }

    @Test
    fun `handleQueueStatus treats other nonzero res as failure`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected

        val result = async { handler.sendToRadioAndAwait(MeshPacket(id = 791)) }
        testScheduler.runCurrent()

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 791, res = 33, free = 16))
        testScheduler.runCurrent()

        assertFalse(result.await())
    }

    @Test
    fun `handleQueueStatus property test`() = runTest(testDispatcher) {
        checkAll(Arb.int(0, 10), Arb.int(0, 32), Arb.int(0, 100000)) { res, free, packetId ->
            val status = QueueStatus(res = res, free = free, mesh_packet_id = packetId)

            // Ensure it doesn't crash on any input
            handler.handleQueueStatus(status)
            testScheduler.runCurrent()
        }
    }

    @Test
    fun `outgoing packets are logged with NODE_NUM_LOCAL`() = runTest(testDispatcher) {
        val packet = MeshPacket(id = 123, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP))
        val toRadio = ToRadio(packet = packet)

        handler.sendToRadio(toRadio)
        testScheduler.runCurrent()

        verifySuspend { meshLogRepository.insert(any()) }
    }
}
