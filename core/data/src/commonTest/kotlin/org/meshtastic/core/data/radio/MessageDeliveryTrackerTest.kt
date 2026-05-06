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
package org.meshtastic.core.data.radio

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RetryPolicy
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.InMemoryStorageProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MessageDeliveryTrackerTest {

    @Test
    fun `sent acked flow is persisted as delivered`() = runTest {
        val updates = linkedMapOf<Int, MutableList<MessageStatus>>()
        val repository = mockPacketRepository(updates)
        val tracker = buildTracker(repository)
        val transport = fakeTransport("fake:acked")
        val client = buildClient(transport)

        client.connect()
        val handle = client.send(unicastPacket("acked"))
        tracker.track(101, handle, RetryPolicy.None)

        runCurrent()
        transport.injectRoutingAck(transport.lastTextPacketId())
        runCurrent()
        advanceUntilIdle()

        assertEquals(
            listOf(
                MessageStatus.ENROUTE,
                MessageStatus.DELIVERED,
                MessageStatus.DELIVERED,
            ),
            updates.getValue(101),
        )

        client.disconnect()
    }

    @Test
    fun `retry exhaustion ends in error after the final attempt`() = runTest {
        val updates = linkedMapOf<Int, MutableList<MessageStatus>>()
        val repository = mockPacketRepository(updates)
        val tracker = buildTracker(repository)
        val transport = fakeTransport("fake:retry-exhausted")
        val client = buildClient(transport, sendTimeout = 100.milliseconds)

        client.connect()
        val handle = client.send(unicastPacket("retry-exhausted"))
        tracker.track(102, handle, RetryPolicy.Fixed(maxAttempts = 1, delay = 100.milliseconds))

        runCurrent()
        assertEquals(1, transport.sentTextPackets().size)

        advanceTimeBy(100.milliseconds)
        runCurrent()
        advanceTimeBy(100.milliseconds)
        runCurrent()
        assertEquals(2, transport.sentTextPackets().size)

        advanceTimeBy(100.milliseconds)
        runCurrent()
        advanceUntilIdle()

        assertEquals(MessageStatus.ERROR, updates.getValue(102).last())

        client.disconnect()
    }

    @Test
    fun `routing failure transitions from enroute to error`() = runTest {
        val updates = linkedMapOf<Int, MutableList<MessageStatus>>()
        val repository = mockPacketRepository(updates)
        val tracker = buildTracker(repository)
        val transport = fakeTransport("fake:routing-error")
        val client = buildClient(transport)

        client.connect()
        val handle = client.send(unicastPacket("fail"))
        tracker.track(103, handle, RetryPolicy.None)

        runCurrent()
        transport.injectRoutingError(transport.lastTextPacketId(), Routing.Error.NO_ROUTE)
        runCurrent()
        advanceUntilIdle()

        assertEquals(
            listOf(
                MessageStatus.ENROUTE,
                MessageStatus.ENROUTE,
                MessageStatus.ERROR,
            ),
            updates.getValue(103),
        )

        client.disconnect()
    }

    @Test
    fun `retry policy resends after exponential backoff`() = runTest {
        val updates = linkedMapOf<Int, MutableList<MessageStatus>>()
        val repository = mockPacketRepository(updates)
        val tracker = buildTracker(repository)
        val transport = fakeTransport("fake:retry")
        val client = buildClient(transport, sendTimeout = 100.milliseconds)

        client.connect()
        val handle = client.send(unicastPacket("retry"))
        tracker.track(
            104,
            handle,
            RetryPolicy.ExponentialBackoff(
                maxAttempts = 2,
                initialDelay = 1.seconds,
                maxDelay = 2.seconds,
                jitterFactor = 0.0,
            ),
        )

        runCurrent()
        assertEquals(1, transport.sentTextPackets().size)

        advanceTimeBy(100.milliseconds)
        runCurrent()
        assertEquals(1, transport.sentTextPackets().size)

        advanceTimeBy(999.milliseconds)
        runCurrent()
        assertEquals(1, transport.sentTextPackets().size)

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(2, transport.sentTextPackets().size)

        transport.injectRoutingAck(transport.lastTextPacketId())
        runCurrent()
        advanceUntilIdle()

        assertEquals(MessageStatus.DELIVERED, updates.getValue(104).last())

        client.disconnect()
    }

    @Test
    fun `concurrent messages are tracked independently`() = runTest {
        val updates = linkedMapOf<Int, MutableList<MessageStatus>>()
        val repository = mockPacketRepository(updates)
        val tracker = buildTracker(repository)
        val transport = fakeTransport("fake:concurrent")
        val client = buildClient(transport, sendTimeout = 200.milliseconds)

        client.connect()
        val firstHandle = client.send(unicastPacket("first"))
        val secondHandle = client.send(unicastPacket("second"))
        tracker.track(201, firstHandle, RetryPolicy.None)
        tracker.track(202, secondHandle, RetryPolicy.None)

        runCurrent()
        val requestIds = transport.sentTextPackets().takeLast(2).map { it.id }
        transport.injectRoutingAck(requestIds.first())
        runCurrent()

        advanceTimeBy(200.milliseconds)
        runCurrent()
        advanceUntilIdle()

        assertEquals(MessageStatus.DELIVERED, updates.getValue(201).last())
        assertEquals(MessageStatus.ERROR, updates.getValue(202).last())

        client.disconnect()
    }

    @Test
    fun `timeout marks message as error`() = runTest {
        val updates = linkedMapOf<Int, MutableList<MessageStatus>>()
        val repository = mockPacketRepository(updates)
        val tracker = buildTracker(repository)
        val transport = fakeTransport("fake:timeout")
        val client = buildClient(transport, sendTimeout = 150.milliseconds)

        client.connect()
        val handle = client.send(unicastPacket("timeout"))
        tracker.track(203, handle, RetryPolicy.None)

        runCurrent()
        advanceTimeBy(150.milliseconds)
        runCurrent()
        advanceUntilIdle()

        assertEquals(MessageStatus.ERROR, updates.getValue(203).last())

        client.disconnect()
    }

    @Test
    fun `duplicate ack after delivery does not add extra updates`() = runTest {
        val updates = linkedMapOf<Int, MutableList<MessageStatus>>()
        val repository = mockPacketRepository(updates)
        val tracker = buildTracker(repository)
        val transport = fakeTransport("fake:duplicate-ack")
        val client = buildClient(transport)

        client.connect()
        val handle = client.send(unicastPacket("duplicate"))
        tracker.track(204, handle, RetryPolicy.None)

        runCurrent()
        val requestId = transport.lastTextPacketId()
        transport.injectRoutingAck(requestId)
        runCurrent()
        advanceUntilIdle()

        val completedUpdates = updates.getValue(204).toList()

        transport.injectRoutingAck(requestId)
        runCurrent()
        advanceUntilIdle()

        assertEquals(completedUpdates, updates.getValue(204))

        client.disconnect()
    }

    private fun TestScope.buildClient(
        transport: FakeRadioTransport,
        sendTimeout: Duration = 5.seconds,
    ): RadioClient = RadioClient.Builder()
        .transport(transport)
        .storage(InMemoryStorageProvider())
        .coroutineContext(backgroundScope.coroutineContext)
        .sendTimeout(sendTimeout)
        .build()

    private fun TestScope.buildTracker(packetRepository: PacketRepository): MessageDeliveryTracker {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return MessageDeliveryTracker(
            packetRepository = lazyOf(packetRepository),
            dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
        )
    }

    private fun mockPacketRepository(
        updates: MutableMap<Int, MutableList<MessageStatus>>,
    ): PacketRepository {
        val repository = mock<PacketRepository>(MockMode.autofill)

        everySuspend { repository.getPacketByPacketId(any()) } calls { args ->
            DataPacket(bytes = null, dataType = 0, id = args.arg<Int>(0))
        }
        everySuspend { repository.updateMessageStatus(any<Int>(), any<MessageStatus>()) } calls { args ->
            updates.record(args.arg<Int>(0), args.arg<MessageStatus>(1))
        }
        everySuspend { repository.updateMessageStatus(any<DataPacket>(), any<MessageStatus>()) } calls { args ->
            updates.record(args.arg<DataPacket>(0).id, args.arg<MessageStatus>(1))
        }

        return repository
    }

    private fun MutableMap<Int, MutableList<MessageStatus>>.record(packetId: Int, status: MessageStatus) {
        getOrPut(packetId) { mutableListOf() }.add(status)
    }

    private fun fakeTransport(identity: String) = FakeRadioTransport(
        identity = TransportIdentity(identity),
        autoHandshake = true,
    )

    private fun unicastPacket(text: String) = MeshPacket(
        to = 0x12345678,
        channel = 0,
        want_ack = true,
        decoded = Data(
            portnum = PortNum.TEXT_MESSAGE_APP,
            payload = text.encodeToByteArray().toByteString(),
        ),
    )

    private fun FakeRadioTransport.sentTextPackets(): List<MeshPacket> =
        outboundPackets().filter { it.decoded?.portnum == PortNum.TEXT_MESSAGE_APP }

    private fun FakeRadioTransport.lastTextPacketId(): Int = sentTextPackets().last().id
}
