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
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.ConnectionEpochs
import org.meshtastic.core.model.ConnectionLifecycle
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Reaction
import org.meshtastic.core.repository.AwaitedSendStatus
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PacketHandlerImplTest {

    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val meshLogRepository: MeshLogRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val connectionLifecycleFlow =
        connectionStateFlow
            .map { state -> ConnectionLifecycle(state = state, epochs = ConnectionEpochs(departures = 7)) }
            .stateIn(
                scope = testScope,
                started = SharingStarted.Eagerly,
                initialValue = ConnectionLifecycle(epochs = ConnectionEpochs(departures = 7)),
            )

    private lateinit var handler: PacketHandlerImpl

    private val responseTimeoutCrossingMillis = PacketHandlerImpl.RESPONSE_TIMEOUT.inWholeMilliseconds + 1

    private fun handlerWithScope(scope: TestScope) = PacketHandlerImpl(
        lazy { packetRepository },
        radioInterfaceService,
        lazy { meshLogRepository },
        serviceRepository,
        scope.asServiceScope(),
    )

    @BeforeTest
    fun setUp() {
        every { serviceRepository.connectionState } returns connectionStateFlow
        every { serviceRepository.connectionLifecycle } returns connectionLifecycleFlow
        every { radioInterfaceService.trySendToRadio(any()) } returns true

        handler = handlerWithScope(testScope)
    }

    @Test
    fun testInitialization() {
        assertNotNull(handler)
    }

    @Test
    fun `sendToRadio with ToRadio sends immediately`() {
        val toRadio = ToRadio(packet = MeshPacket(id = 123))

        handler.sendToRadio(toRadio)

        verify { radioInterfaceService.trySendToRadio(any()) }
    }

    @Test
    fun `sendToRadio with MeshPacket queues and sends when connected`() = runTest(testDispatcher) {
        val packet = MeshPacket(id = 456)
        connectionStateFlow.value = ConnectionState.Connected

        handler.sendToRadio(packet)
        testScheduler.runCurrent()

        verify { radioInterfaceService.trySendToRadio(any()) }
    }

    @Test
    fun `awaited send rejects when service scope is already stopped`() = runTest(testDispatcher) {
        val stoppedScope = TestScope(StandardTestDispatcher(testScheduler))
        stoppedScope.cancel()
        val stoppedHandler = handlerWithScope(stoppedScope)

        val result = stoppedHandler.sendToRadioAndAwaitResult(MeshPacket(id = 457))

        assertEquals(AwaitedSendStatus.TRANSPORT_STOPPED, result.status)
        assertFalse(result.dispatched)
    }

    @Test
    fun `plain send rejects when service scope is already stopped`() = runTest(testDispatcher) {
        val stoppedScope = TestScope(StandardTestDispatcher(testScheduler))
        stoppedScope.cancel()
        val stoppedHandler = handlerWithScope(stoppedScope)

        val accepted = stoppedHandler.sendToRadio(MeshPacket(id = 459))

        assertFalse(accepted)
        verify(exactly(0)) { radioInterfaceService.trySendToRadio(any()) }
    }

    @Test
    fun `awaited send is released when service scope stops before worker starts`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val serviceScope = TestScope(StandardTestDispatcher(testScheduler))
        val stoppedHandler = handlerWithScope(serviceScope)

        val result =
            async(start = CoroutineStart.UNDISPATCHED) {
                stoppedHandler.sendToRadioAndAwaitResult(MeshPacket(id = 458))
            }
        serviceScope.cancel()
        testScheduler.runCurrent()
        val completed = result.await()

        assertEquals(AwaitedSendStatus.TRANSPORT_STOPPED, completed.status)
        assertFalse(completed.dispatched)
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

        val result = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 791)) }
        testScheduler.runCurrent()

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 791, res = 33, free = 16))
        testScheduler.runCurrent()

        val rejected = result.await()
        assertEquals(AwaitedSendStatus.RADIO_REJECTED, rejected.status)
        assertTrue(rejected.dispatched)
        assertEquals(7L, rejected.departureEpochAtDispatch)
    }

    @Test
    fun `await response timeout starts after earlier queued packets`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        handler.sendToRadio(MeshPacket(id = 800))
        handler.sendToRadio(MeshPacket(id = 801))
        val result = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 802)) }
        testScheduler.runCurrent()

        // Let both earlier packets consume their full response windows. The awaited packet has not timed out
        // because
        // it has not reached the head of the queue yet.
        testScheduler.advanceTimeBy(responseTimeoutCrossingMillis)
        testScheduler.runCurrent()
        assertFalse(result.isCompleted)
        testScheduler.advanceTimeBy(responseTimeoutCrossingMillis)
        testScheduler.runCurrent()
        assertFalse(result.isCompleted)

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 802, res = 0, free = 16))
        testScheduler.runCurrent()

        assertEquals(AwaitedSendStatus.ACCEPTED, result.await().status)
    }

    @Test
    fun `stopping the queue completes an awaiting packet still behind the backlog`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        // Packet 803 owns the queue head, so 804 is stopped before the transport receives it.
        val storedPacket =
            DataPacket(
                bytes = null,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 804,
                status = MessageStatus.QUEUED,
            )
        everySuspend { packetRepository.getPacketByPacketId(804) } returns storedPacket
        everySuspend { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) } returns Unit
        handler.sendToRadio(MeshPacket(id = 803))
        val result = async {
            handler.sendToRadioAndAwaitResult(
                MeshPacket(id = 804, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP)),
            )
        }
        testScheduler.runCurrent()

        handler.stopPacketQueue()
        testScheduler.runCurrent()

        val stopped = result.await()
        assertEquals(AwaitedSendStatus.TRANSPORT_STOPPED, stopped.status)
        assertFalse(stopped.dispatched)
        verifySuspend { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) }
    }

    @Test
    fun `queue stop skips persistence lookup for a non-persisted packet`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        handler.sendToRadio(MeshPacket(id = 820))
        val queued = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 821)) }
        testScheduler.runCurrent()

        handler.stopPacketQueue()
        testScheduler.runCurrent()

        assertEquals(AwaitedSendStatus.TRANSPORT_STOPPED, queued.await().status)
        verifySuspend(exactly(0)) { packetRepository.getPacketByPacketId(821) }
    }

    @Test
    fun `text emoji without reply id remains a persisted app packet`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val storedPacket =
            DataPacket(
                bytes = null,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 822,
                status = MessageStatus.QUEUED,
            )
        var lookups = 0
        everySuspend { packetRepository.getPacketByPacketId(822) } calls
            {
                lookups++
                if (lookups == 1) null else storedPacket
            }
        everySuspend { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) } returns Unit

        handler.sendToRadio(MeshPacket(id = 820))
        val queued = async {
            handler.sendToRadioAndAwaitResult(
                MeshPacket(id = 822, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, emoji = 1)),
            )
        }
        testScheduler.runCurrent()

        handler.stopPacketQueue()
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(PacketHandlerImpl.PERSISTED_STATUS_RETRY_DELAY.inWholeMilliseconds)
        testScheduler.runCurrent()

        assertEquals(AwaitedSendStatus.TRANSPORT_STOPPED, queued.await().status)
        assertEquals(2, lookups)
        verifySuspend { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) }
        verifySuspend(exactly(0)) { packetRepository.getReactionByPacketId(any()) }
    }

    @Test
    fun `queue stop waits briefly for a persisted reaction row`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val storedReaction =
            Reaction(
                replyId = 1,
                user = User(id = "!00000001"),
                emoji = "👍",
                timestamp = 0,
                snr = null,
                rssi = null,
                hopsAway = 0,
                packetId = 825,
                status = MessageStatus.QUEUED,
            )
        val failedReaction = storedReaction.copy(status = MessageStatus.ERROR)
        var lookups = 0
        everySuspend { packetRepository.getReactionByPacketId(825) } calls
            {
                lookups++
                if (lookups == 1) null else storedReaction
            }
        everySuspend { packetRepository.updateReaction(failedReaction) } returns Unit

        handler.sendToRadio(MeshPacket(id = 820))
        val queued = async {
            handler.sendToRadioAndAwaitResult(
                MeshPacket(id = 825, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, reply_id = 1, emoji = 1)),
            )
        }
        testScheduler.runCurrent()
        handler.stopPacketQueue()
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(PacketHandlerImpl.PERSISTED_STATUS_RETRY_DELAY.inWholeMilliseconds)
        testScheduler.runCurrent()

        assertEquals(AwaitedSendStatus.TRANSPORT_STOPPED, queued.await().status)
        assertEquals(2, lookups)
        verifySuspend { packetRepository.updateReaction(failedReaction) }
    }

    @Test
    fun `dispatch status does not overwrite a terminal routing result`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val storedPacket =
            DataPacket(
                bytes = null,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 823,
                status = MessageStatus.DELIVERED,
            )
        everySuspend { packetRepository.getPacketByPacketId(823) } returns storedPacket

        handler.sendToRadio(MeshPacket(id = 823, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP)))
        testScheduler.runCurrent()
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 823, res = 0, free = 16))
        testScheduler.runCurrent()

        verifySuspend(exactly(0)) { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ENROUTE) }
    }

    @Test
    fun `queue stop does not overwrite a terminal routing result`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val storedPacket =
            DataPacket(
                bytes = null,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 824,
                status = MessageStatus.DELIVERED,
            )
        everySuspend { packetRepository.getPacketByPacketId(824) } returns storedPacket

        handler.sendToRadio(MeshPacket(id = 820))
        val queued = async {
            handler.sendToRadioAndAwaitResult(
                MeshPacket(id = 824, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP)),
            )
        }
        testScheduler.runCurrent()
        handler.stopPacketQueue()
        testScheduler.runCurrent()

        assertEquals(AwaitedSendStatus.TRANSPORT_STOPPED, queued.await().status)
        verifySuspend(exactly(0)) { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) }
    }

    @Test
    fun `stopping the queue reports an awaited packet that was already dispatched`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        // With no backlog, packet 807 reaches the transport before stopPacketQueue() drains its response.
        val storedPacket =
            DataPacket(
                bytes = null,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 807,
                status = MessageStatus.ENROUTE,
            )
        everySuspend { packetRepository.getPacketByPacketId(807) } returns storedPacket
        everySuspend { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) } returns Unit
        val result = async {
            handler.sendToRadioAndAwaitResult(
                MeshPacket(id = 807, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP)),
            )
        }
        testScheduler.runCurrent()

        handler.stopPacketQueue()
        testScheduler.runCurrent()

        val stopped = result.await()
        assertEquals(AwaitedSendStatus.TRANSPORT_STOPPED, stopped.status)
        assertTrue(stopped.dispatched)
        verifySuspend { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) }
    }

    @Test
    fun `queue stop does not overwrite an accepted packet while its reservation is still present`() =
        runTest(testDispatcher) {
            connectionStateFlow.value = ConnectionState.Connected
            val storedPacket =
                DataPacket(
                    bytes = null,
                    dataType = PortNum.TEXT_MESSAGE_APP.value,
                    id = 817,
                    status = MessageStatus.ENROUTE,
                )
            everySuspend { packetRepository.getPacketByPacketId(817) } returns storedPacket
            everySuspend { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) } returns Unit
            val result = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 817)) }
            testScheduler.runCurrent()

            // Queue the response callback first, then queue shutdown before either scheduled launch runs. The response
            // completion resumes the worker behind the already-scheduled shutdown, reproducing the completed-but-still-
            // reserved window that must not be reclassified as an error.
            handler.handleQueueStatus(QueueStatus(mesh_packet_id = 817, res = 0, free = 16))
            handler.stopPacketQueue()
            testScheduler.runCurrent()

            val accepted = result.await()
            assertEquals(AwaitedSendStatus.ACCEPTED, accepted.status)
            assertTrue(accepted.dispatched)
            verifySuspend(exactly(0)) { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) }
        }

    @Test
    fun `disconnect drains queued responses without restarting the processor`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val first = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 813)) }
        val queued = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 814)) }
        testScheduler.runCurrent()

        connectionStateFlow.value = ConnectionState.Disconnected
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 813, res = 0, free = 16))
        testScheduler.runCurrent()

        assertEquals(AwaitedSendStatus.ACCEPTED, first.await().status)
        val stopped = queued.await()
        assertEquals(AwaitedSendStatus.TRANSPORT_STOPPED, stopped.status)
        assertFalse(stopped.dispatched)
        verify(exactly(1)) { radioInterfaceService.trySendToRadio(any()) }
    }

    @Test
    fun `queue response timeout completes awaiting caller with failure`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val result = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 805)) }
        testScheduler.runCurrent()

        testScheduler.advanceTimeBy(responseTimeoutCrossingMillis)
        testScheduler.runCurrent()

        val timedOut = result.await()
        assertEquals(AwaitedSendStatus.TIMED_OUT, timedOut.status)
        assertTrue(timedOut.dispatched)
    }

    @Test
    fun `disconnected queue admission reports transport stopped`() = runTest(testDispatcher) {
        val result = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 805)) }
        testScheduler.runCurrent()

        val stopped = result.await()
        assertEquals(AwaitedSendStatus.TRANSPORT_STOPPED, stopped.status)
        assertFalse(stopped.dispatched)
        verify(exactly(0)) { radioInterfaceService.trySendToRadio(any()) }
    }

    @Test
    fun `disconnected fire and forget admission is rejected`() = runTest(testDispatcher) {
        assertFalse(handler.sendToRadio(MeshPacket(id = 806)))

        verify(exactly(0)) { radioInterfaceService.trySendToRadio(any()) }
    }

    @Test
    fun `transport refusing dispatch completes awaiting caller with failure`() = runTest(testDispatcher) {
        every { radioInterfaceService.trySendToRadio(any()) } returns false
        connectionStateFlow.value = ConnectionState.Connected
        val storedPacket =
            DataPacket(
                bytes = null,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 808,
                status = MessageStatus.QUEUED,
            )
        everySuspend { packetRepository.getPacketByPacketId(808) } returns storedPacket
        everySuspend { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) } returns Unit

        val result = async {
            handler.sendToRadioAndAwaitResult(
                MeshPacket(id = 808, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP)),
            )
        }
        testScheduler.runCurrent()

        val failed = result.await()
        assertEquals(AwaitedSendStatus.SEND_FAILED, failed.status)
        assertFalse(failed.dispatched)
        verifySuspend { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) }
    }

    @Test
    fun `queue send failure completes awaiting caller with failure`() = runTest(testDispatcher) {
        every { radioInterfaceService.trySendToRadio(any()) } calls { error("test send failure") }
        connectionStateFlow.value = ConnectionState.Connected

        val result = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 806)) }
        testScheduler.runCurrent()

        val failed = result.await()
        assertEquals(AwaitedSendStatus.SEND_FAILED, failed.status)
        assertFalse(failed.dispatched)
    }

    @Test
    fun `queue status without a packet id completes the oldest dispatched response`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val first = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 820)) }
        val second = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 821)) }
        testScheduler.runCurrent()

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 0, res = 0, free = 16))
        testScheduler.runCurrent()

        val firstResult = first.await()
        assertEquals(AwaitedSendStatus.ACCEPTED, firstResult.status)
        assertTrue(firstResult.dispatched)
        assertFalse(second.isCompleted)

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 0, res = 0, free = 16))
        testScheduler.runCurrent()
        val secondResult = second.await()
        assertEquals(AwaitedSendStatus.ACCEPTED, secondResult.status)
        assertTrue(secondResult.dispatched)
    }

    @Test
    fun `queue resumes after a stop and a later send`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        handler.stopPacketQueue()
        testScheduler.runCurrent()

        assertTrue(handler.sendToRadio(MeshPacket(id = 822)))
        testScheduler.runCurrent()

        verify(exactly(1)) { radioInterfaceService.trySendToRadio(any()) }
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 822, res = 0, free = 16))
        testScheduler.runCurrent()
    }

    @Test
    fun `cancelled queue handoff releases its reservation before restarting queued work`() = runTest(testDispatcher) {
        val storedPacket =
            DataPacket(
                bytes = null,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 815,
                status = MessageStatus.QUEUED,
            )
        everySuspend { packetRepository.getPacketByPacketId(815) } returns storedPacket
        everySuspend { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) } returns Unit
        var sendAttempts = 0
        every { radioInterfaceService.trySendToRadio(any()) } calls
            {
                sendAttempts++
                if (sendAttempts == 1) throw CancellationException("transport stopped")
                true
            }
        connectionStateFlow.value = ConnectionState.Connected

        val interrupted = async {
            handler.sendToRadioAndAwaitResult(
                MeshPacket(id = 815, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP)),
            )
        }
        val queued = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 816)) }
        testScheduler.runCurrent()

        val stopped = interrupted.await()
        assertEquals(AwaitedSendStatus.TRANSPORT_STOPPED, stopped.status)
        assertFalse(stopped.dispatched)
        verifySuspend { packetRepository.updateMessageStatus(storedPacket, MessageStatus.ERROR) }

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 816, res = 0, free = 16))
        testScheduler.runCurrent()
        val accepted = queued.await()
        assertEquals(AwaitedSendStatus.ACCEPTED, accepted.status)
        assertTrue(accepted.dispatched)

        assertTrue(handler.sendToRadio(MeshPacket(id = 815)))
        testScheduler.runCurrent()
        verify(exactly(3)) { radioInterfaceService.trySendToRadio(any()) }
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 815, res = 0, free = 16))
        testScheduler.runCurrent()
    }

    @Test
    fun `awaited packet without an id is rejected before dispatch`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected

        val result = handler.sendToRadioAndAwaitResult(MeshPacket())

        assertEquals(AwaitedSendStatus.REJECTED, result.status)
        assertFalse(result.dispatched)
        assertFalse(handler.sendToRadioAndAwait(MeshPacket()), "the Boolean compatibility API must map rejection")
        verify(exactly(0)) { radioInterfaceService.trySendToRadio(any()) }
    }

    @Test
    fun `duplicate awaited packet id is rejected without replacing the original waiter`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val original = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 809)) }
        testScheduler.runCurrent()

        val duplicate = handler.sendToRadioAndAwaitResult(MeshPacket(id = 809))

        assertEquals(AwaitedSendStatus.REJECTED, duplicate.status)
        assertFalse(duplicate.dispatched)
        assertFalse(original.isCompleted)

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 809, res = 0, free = 16))
        testScheduler.runCurrent()

        val accepted = original.await()
        assertEquals(AwaitedSendStatus.ACCEPTED, accepted.status)
        assertTrue(accepted.dispatched)
    }

    @Test
    fun `response received before dispatch is ignored and retains the queued id`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        handler.sendToRadio(MeshPacket(id = 816))
        val queued = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 817)) }
        testScheduler.runCurrent()

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 817, res = 0, free = 16))
        handler.completeDispatchedResponse(dataRequestId = 817, complete = true)
        testScheduler.runCurrent()

        assertFalse(queued.isCompleted)

        val duplicate = handler.sendToRadioAndAwaitResult(MeshPacket(id = 817))
        assertEquals(AwaitedSendStatus.REJECTED, duplicate.status)

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 816, res = 0, free = 16))
        testScheduler.runCurrent()

        verify(exactly(2)) { radioInterfaceService.trySendToRadio(any()) }
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 817, res = 0, free = 16))
        testScheduler.runCurrent()

        val accepted = queued.await()
        assertEquals(AwaitedSendStatus.ACCEPTED, accepted.status)
        assertTrue(accepted.dispatched)
        assertTrue(handler.sendToRadio(MeshPacket(id = 817)))
        testScheduler.runCurrent()
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 817, res = 0, free = 16))
        testScheduler.runCurrent()
    }

    @Test
    fun `routing rejection after dispatch completes awaited response as radio rejected`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val result = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 818)) }
        testScheduler.runCurrent()

        handler.completeDispatchedResponse(dataRequestId = 818, complete = false)
        testScheduler.runCurrent()

        val rejected = result.await()
        assertEquals(AwaitedSendStatus.RADIO_REJECTED, rejected.status)
        assertTrue(rejected.dispatched)
    }

    @Test
    fun `late queue status cannot replace an already completed response`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val result = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 819)) }
        testScheduler.runCurrent()

        handler.completeDispatchedResponse(dataRequestId = 819, complete = true)
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 819, res = 33, free = 16))
        testScheduler.runCurrent()

        val accepted = result.await()
        assertEquals(AwaitedSendStatus.ACCEPTED, accepted.status)
        assertTrue(accepted.dispatched)
    }

    @Test
    fun `cancelling an awaiting caller does not release its queued packet id`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        handler.sendToRadio(MeshPacket(id = 811))
        val awaiting = async { handler.sendToRadioAndAwaitResult(MeshPacket(id = 812)) }
        testScheduler.runCurrent()

        awaiting.cancelAndJoin()
        val duplicate = handler.sendToRadioAndAwaitResult(MeshPacket(id = 812))

        assertEquals(AwaitedSendStatus.REJECTED, duplicate.status)
        assertFalse(duplicate.dispatched)

        handler.stopPacketQueue()
        testScheduler.runCurrent()
    }

    @Test
    fun `fire and forget rejects invalid packet ids without throwing or replacing queued work`() =
        runTest(testDispatcher) {
            connectionStateFlow.value = ConnectionState.Connected
            assertTrue(handler.sendToRadio(MeshPacket(id = 810)))
            testScheduler.runCurrent()

            assertFalse(handler.sendToRadio(MeshPacket(id = 810)))
            assertFalse(handler.sendToRadio(MeshPacket()))
            testScheduler.runCurrent()

            verify(exactly(1)) { radioInterfaceService.trySendToRadio(any()) }

            handler.stopPacketQueue()
            testScheduler.runCurrent()
        }

    @Test
    fun `completed packet id can be reused by a later retry`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        assertTrue(handler.sendToRadio(MeshPacket(id = 810)))
        testScheduler.runCurrent()

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 810, res = 0, free = 16))
        testScheduler.runCurrent()

        assertTrue(handler.sendToRadio(MeshPacket(id = 810)))
        testScheduler.runCurrent()

        verify(exactly(2)) { radioInterfaceService.trySendToRadio(any()) }

        handler.stopPacketQueue()
        testScheduler.runCurrent()
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
