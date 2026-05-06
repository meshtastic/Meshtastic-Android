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
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.StoreForwardPlusPlus
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.StoreForwardEvent
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.InMemoryStorageProvider
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SdkPacketBridgeTest {

    @Test
    fun `packet received is emitted to service repository`() = runTest {
        val serviceRepository = FakeServiceRepository()
        val bridge = SdkPacketBridge(serviceRepository, lazyOf(mock(MockMode.autofill)), FakeNodeRepository())
        val (transport, client) = connectedClient()

        bridge.observe(TestRadioClientAccessor(client), backgroundScope)
        client.connect()
        runCurrent()

        val packet = MeshPacket(from = 0x10101010, to = 0, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP))
        val packetAwaiter = backgroundScope.async { serviceRepository.meshPacketFlow.first() }
        runCurrent()

        transport.injectPacket(packet)
        runCurrent()

        assertEquals(packet, packetAwaiter.await())
        client.disconnect()
    }

    @Test
    fun `store forward server list tracks discovered servers`() = runTest {
        val serviceRepository = FakeServiceRepository()
        val bridge = SdkPacketBridge(serviceRepository, lazyOf(mock(MockMode.autofill)), FakeNodeRepository())
        val (transport, client) = connectedClient()

        bridge.observe(TestRadioClientAccessor(client), backgroundScope)
        client.connect()
        runCurrent()

        transport.injectStoreForwardResponse(
            requestId = 0,
            message = org.meshtastic.proto.StoreAndForward(
                rr = org.meshtastic.proto.StoreAndForward.RequestResponse.ROUTER_HEARTBEAT,
                heartbeat = org.meshtastic.proto.StoreAndForward.Heartbeat(period = 300),
            ),
            fromNode = 0x0A0A0A0A,
        )
        transport.injectStoreForwardResponse(
            requestId = 0,
            message = org.meshtastic.proto.StoreAndForward(
                rr = org.meshtastic.proto.StoreAndForward.RequestResponse.ROUTER_HEARTBEAT,
                heartbeat = org.meshtastic.proto.StoreAndForward.Heartbeat(period = 300),
            ),
            fromNode = 0x0B0B0B0B,
        )
        runCurrent()

        assertEquals(listOf(0x0A0A0A0A, 0x0B0B0B0B), serviceRepository.storeForwardServers.value)
        client.disconnect()
    }

    @Test
    fun `sfpp confirmed link updates packet repository`() = runTest {
        val packetRepository = RecordingPacketRepository()
        val nodeRepository = FakeNodeRepository().apply { setMyNodeNum(0x11111111) }
        val bridge = SdkPacketBridge(FakeServiceRepository(), lazyOf(packetRepository), nodeRepository)

        bridge.handleStoreForwardEvent(
            StoreForwardEvent.SfppLinkProvided(
                packetId = 0x1234,
                from = 0x55667788,
                to = 0x01020304,
                messageHash = byteArrayOf(1, 2, 3, 4),
                confirmed = true,
            ),
        )

        val call = packetRepository.statusCalls.single()
        assertEquals(0x1234, call.packetId)
        assertEquals(0x55667788, call.from)
        assertEquals(0x01020304, call.to)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), call.hash)
        assertEquals(MessageStatus.SFPP_CONFIRMED, call.status)
        assertEquals(0x11111111, call.myNodeNum)
    }

    @Test
    fun `sfpp routing link updates packet repository`() = runTest {
        val packetRepository = RecordingPacketRepository()
        val bridge = SdkPacketBridge(FakeServiceRepository(), lazyOf(packetRepository), FakeNodeRepository())

        bridge.handleStoreForwardEvent(
            StoreForwardEvent.SfppLinkProvided(
                packetId = 77,
                from = 0x11112222,
                to = 0x33334444,
                messageHash = byteArrayOf(9, 8, 7),
                confirmed = false,
            ),
        )

        assertEquals(MessageStatus.SFPP_ROUTING, packetRepository.statusCalls.single().status)
    }

    @Test
    fun `sfpp link without hash is ignored`() = runTest {
        val packetRepository = RecordingPacketRepository()
        val bridge = SdkPacketBridge(FakeServiceRepository(), lazyOf(packetRepository), FakeNodeRepository())

        bridge.handleStoreForwardEvent(
            StoreForwardEvent.SfppLinkProvided(
                packetId = 1,
                from = 2,
                to = 3,
                messageHash = null,
                confirmed = true,
            ),
        )

        assertTrue(packetRepository.statusCalls.isEmpty())
        assertTrue(packetRepository.hashCalls.isEmpty())
    }

    @Test
    fun `sfpp canon announce updates packet repository by hash`() = runTest {
        val packetRepository = RecordingPacketRepository()
        val bridge = SdkPacketBridge(FakeServiceRepository(), lazyOf(packetRepository), FakeNodeRepository())

        bridge.handleStoreForwardEvent(
            StoreForwardEvent.SfppCanonAnnounced(
                messageHash = byteArrayOf(7, 6, 5, 4),
                rxTime = 0xFEDCBA98L,
            ),
        )

        val call = packetRepository.hashCalls.single()
        assertContentEquals(byteArrayOf(7, 6, 5, 4), call.hash)
        assertEquals(MessageStatus.SFPP_CONFIRMED, call.status)
        assertEquals(0xFEDCBA98L, call.rxTime)
    }

    @Test
    fun `unknown packet type is handled without crashing`() = runTest {
        val serviceRepository = FakeServiceRepository()
        val packetRepository = RecordingPacketRepository()
        val bridge = SdkPacketBridge(serviceRepository, lazyOf(packetRepository), FakeNodeRepository())
        val (transport, client) = connectedClient()

        bridge.observe(TestRadioClientAccessor(client), backgroundScope)
        client.connect()
        runCurrent()

        transport.injectPacket(
            MeshPacket(
                from = 0x99990000.toInt(),
                to = 0,
                decoded = Data(portnum = PortNum.UNKNOWN_APP, payload = byteArrayOf(0x01, 0x02).toByteString()),
            ),
        )
        runCurrent()

        assertTrue(packetRepository.statusCalls.isEmpty())
        assertTrue(packetRepository.hashCalls.isEmpty())
        assertEquals(emptyList(), serviceRepository.storeForwardServers.value)
        client.disconnect()
    }

    private fun TestScope.connectedClient(): Pair<FakeRadioTransport, RadioClient> {
        val transport = FakeRadioTransport(identity = TransportIdentity("fake:packet-bridge"), autoHandshake = true)
        val client =
            RadioClient.Builder()
                .transport(transport)
                .storage(InMemoryStorageProvider())
                .coroutineContext(backgroundScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .build()
        return transport to client
    }

    private class RecordingPacketRepository(
        private val delegate: PacketRepository = mock(MockMode.autofill),
    ) : PacketRepository by delegate {
        data class StatusCall(
            val packetId: Int,
            val from: Int,
            val to: Int,
            val hash: ByteArray,
            val status: MessageStatus,
            val rxTime: Long,
            val myNodeNum: Int?,
        )

        data class HashCall(
            val hash: ByteArray,
            val status: MessageStatus,
            val rxTime: Long,
        )

        val statusCalls = mutableListOf<StatusCall>()
        val hashCalls = mutableListOf<HashCall>()

        override suspend fun updateSFPPStatus(
            packetId: Int,
            from: Int,
            to: Int,
            hash: ByteArray,
            status: MessageStatus,
            rxTime: Long,
            myNodeNum: Int?,
        ) {
            statusCalls += StatusCall(packetId, from, to, hash.copyOf(), status, rxTime, myNodeNum)
        }

        override suspend fun updateSFPPStatusByHash(hash: ByteArray, status: MessageStatus, rxTime: Long) {
            hashCalls += HashCall(hash.copyOf(), status, rxTime)
        }
    }

    private class TestRadioClientAccessor(client: RadioClient) : RadioClientAccessor {
        override val client = MutableStateFlow<RadioClient?>(client)

        override fun rebuildAndConnectAsync() = Unit

        override fun disconnect() = Unit
    }
}
