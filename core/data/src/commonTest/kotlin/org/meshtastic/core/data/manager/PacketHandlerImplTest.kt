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
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PersistedPacket
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.Routing
import org.meshtastic.proto.ToRadio
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PacketHandlerImplTest {

    companion object {
        private val PERSISTED_ID = PersistedPacketId(myNodeNum = 123, uuid = 456L)
    }

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
        everySuspend { packetRepository.updateOutgoingMessageStatus(any(), any()) } returns PERSISTED_ID

        handler =
            PacketHandlerImpl(
                lazy { packetRepository },
                radioInterfaceService,
                lazy { meshLogRepository },
                serviceRepository,
                testScope.asServiceScope(),
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
    fun `sendToRadio updates status using the full outgoing packet identity`() = runTest(testDispatcher) {
        val packet =
            MeshPacket(
                from = 0x11111111,
                to = 0x22222222,
                id = 123,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP),
            )

        handler.sendToRadio(ToRadio(packet = packet))
        testScheduler.runCurrent()

        verifySuspend { packetRepository.updateOutgoingMessageStatus(packet, MessageStatus.ENROUTE) }
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
    fun `strict await treats ERRNO_SHOULD_RELEASE as immediate delivery success`() = runTest(testDispatcher) {
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
    fun `strict await completes ERRNO_SHOULD_RELEASE even when queue is full`() = runTest(testDispatcher) {
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
    fun `strict await treats queue rejection as failure`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected

        val result = async { handler.sendToRadioAndAwait(MeshPacket(id = 791)) }
        testScheduler.runCurrent()

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 791, res = 33, free = 16))
        testScheduler.runCurrent()

        assertFalse(result.await())
    }

    @Test
    fun `strict await fails immediately while disconnected`() = runTest(testDispatcher) {
        val result = handler.sendToRadioAndAwait(MeshPacket(id = 796))

        assertFalse(result)
        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun `strict await fails immediately when transport send throws`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        every { radioInterfaceService.sendToRadio(any()) } throws IllegalStateException("transport failed")

        val result = async { handler.sendToRadioAndAwait(MeshPacket(id = 797)) }
        testScheduler.runCurrent()

        assertTrue(result.isCompleted)
        assertFalse(result.await())
    }

    @Test
    fun `strict await does not complete on ordinary queue acceptance`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected

        val result = async { handler.sendToRadioAndAwait(MeshPacket(id = 793)) }
        testScheduler.runCurrent()

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 793, res = 0, free = 16))
        testScheduler.runCurrent()

        assertFalse(result.isCompleted)

        handler.removeResponse(793, complete = true)
        assertTrue(result.await())
    }

    @Test
    fun `strict await succeeds on routing ack`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected

        val result = async { handler.sendToRadioAndAwait(MeshPacket(id = 794)) }
        testScheduler.runCurrent()
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 794, res = 0, free = 16))
        testScheduler.runCurrent()

        assertFalse(result.isCompleted)

        handler.removeResponse(794, complete = true)

        assertTrue(result.await())
    }

    @Test
    fun `zero id queue status completes only its correlated routing response`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected

        val awaitingRoutingAck = async { handler.sendToRadioAndAwait(MeshPacket(id = 798)) }
        testScheduler.runCurrent()
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 798, res = 0, free = 16))
        testScheduler.runCurrent()
        assertFalse(awaitingRoutingAck.isCompleted)

        val synchronousLoopback = async { handler.sendToRadioAndAwait(MeshPacket(id = 799)) }
        testScheduler.runCurrent()
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 0, res = 35, free = 16))
        testScheduler.runCurrent()

        assertTrue(synchronousLoopback.await())
        assertFalse(awaitingRoutingAck.isCompleted)

        handler.removeResponse(798, complete = true)
        assertTrue(awaitingRoutingAck.await())
    }

    @Test
    fun `strict await fails on routing nak`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected

        val result = async { handler.sendToRadioAndAwait(MeshPacket(id = 795)) }
        testScheduler.runCurrent()
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 795, res = 0, free = 16))
        testScheduler.runCurrent()

        handler.removeResponse(795, complete = false)

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

    private fun enrouteDataPacket(id: Int, time: Long = 0L) =
        DataPacket(to = "!12345678", bytes = null, dataType = 1, id = id, time = time, status = MessageStatus.ENROUTE)

    @Test
    fun `unacked ENROUTE send times out to a retryable ERROR TIMEOUT`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected

        handler.sendToRadio(ToRadio(packet = MeshPacket(id = 123)))
        testScheduler.advanceTimeBy(PacketHandlerImpl.SEND_ACK_TIMEOUT + 1.seconds)
        testScheduler.runCurrent()

        verifySuspend { packetRepository.timeOutEnroutePacket(PERSISTED_ID, Routing.Error.TIMEOUT.value) }
    }

    @Test
    fun `the timeout never fires before its deadline`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected

        handler.sendToRadio(ToRadio(packet = MeshPacket(id = 124)))
        testScheduler.advanceTimeBy(PacketHandlerImpl.SEND_ACK_TIMEOUT - 1.seconds)
        testScheduler.runCurrent()

        verifySuspend(exactly(0)) { packetRepository.timeOutEnroutePacket(any(), any()) }
    }

    @Test
    fun `rearm times out a stale persisted ENROUTE packet after the reconnect grace`() = runTest(testDispatcher) {
        val stale = enrouteDataPacket(321, time = 0L)
        everySuspend { packetRepository.getEnroutePackets() } returns listOf(PersistedPacket(PERSISTED_ID, stale))

        handler.rearmSendAckTimeouts()
        testScheduler.advanceTimeBy(PacketHandlerImpl.REARM_GRACE + 1.seconds)
        testScheduler.runCurrent()

        verifySuspend { packetRepository.timeOutEnroutePacket(PERSISTED_ID, Routing.Error.TIMEOUT.value) }
    }

    @Test
    fun `rearm gives a fresh ENROUTE packet its full ack window`() = runTest(testDispatcher) {
        val fresh = enrouteDataPacket(322, time = nowMillis)
        everySuspend { packetRepository.getEnroutePackets() } returns listOf(PersistedPacket(PERSISTED_ID, fresh))

        handler.rearmSendAckTimeouts()
        testScheduler.advanceTimeBy(PacketHandlerImpl.REARM_GRACE + 1.seconds)
        testScheduler.runCurrent()
        verifySuspend(exactly(0)) { packetRepository.timeOutEnroutePacket(any(), any()) }

        testScheduler.advanceTimeBy(PacketHandlerImpl.SEND_ACK_TIMEOUT + 1.seconds)
        testScheduler.runCurrent()
        verifySuspend { packetRepository.timeOutEnroutePacket(PERSISTED_ID, Routing.Error.TIMEOUT.value) }
    }

    @Test
    fun `rearming supersedes the pending timer instead of stacking a second one`() = runTest(testDispatcher) {
        // Repeated reconnects must not accumulate timers for the same send, and the superseded timer must not
        // fire on its own original deadline.
        connectionStateFlow.value = ConnectionState.Connected
        val packet = enrouteDataPacket(325, time = nowMillis)
        everySuspend { packetRepository.getEnroutePackets() } returns listOf(PersistedPacket(PERSISTED_ID, packet))

        handler.sendToRadio(ToRadio(packet = MeshPacket(id = 325)))
        testScheduler.runCurrent()
        repeat(3) {
            handler.rearmSendAckTimeouts()
            testScheduler.runCurrent()
        }

        testScheduler.advanceTimeBy(PacketHandlerImpl.SEND_ACK_TIMEOUT * 2 + 1.seconds)
        testScheduler.runCurrent()

        verifySuspend(exactly(1)) {
            packetRepository.timeOutEnroutePacket(PERSISTED_ID, Routing.Error.TIMEOUT.value)
        }
    }

    @Test
    fun `rearm keeps independent timers for persisted rows sharing one mesh packet id`() = runTest(testDispatcher) {
        val firstId = PersistedPacketId(myNodeNum = 123, uuid = 456L)
        val secondId = PersistedPacketId(myNodeNum = 123, uuid = 789L)
        val first = PersistedPacket(firstId, enrouteDataPacket(id = 326, time = 0L))
        val second = PersistedPacket(secondId, enrouteDataPacket(id = 326, time = 0L))
        everySuspend { packetRepository.getEnroutePackets() } returns listOf(first, second)

        handler.rearmSendAckTimeouts()
        testScheduler.advanceTimeBy(PacketHandlerImpl.REARM_GRACE + 1.seconds)
        testScheduler.runCurrent()

        verifySuspend { packetRepository.timeOutEnroutePacket(firstId, Routing.Error.TIMEOUT.value) }
        verifySuspend { packetRepository.timeOutEnroutePacket(secondId, Routing.Error.TIMEOUT.value) }
    }
}
