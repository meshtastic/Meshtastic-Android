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
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommandSenderImplTest {
    private val tracerouteHandler = mock<TracerouteHandler>(MockMode.autofill)
    private val neighborInfoHandler = mock<NeighborInfoHandler>(MockMode.autofill)

    private class Fixture(val sdk: SdkClientFixture, val sender: CommandSenderImpl) {
        fun sent(port: PortNum): MeshPacket = sdk.transport.outboundPackets().single { it.decoded?.portnum == port }
    }

    private suspend fun TestScope.fixture(): Fixture {
        val sdk = sdkClientFixture(MY_NODE_NUM)
        val nodeManager = mock<NodeManager>(MockMode.autofill)
        every { nodeManager.myNodeNum } returns MutableStateFlow(MY_NODE_NUM)
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
        every { radioConfigRepository.localConfigFlow } returns flowOf(LocalConfig.Builder().build())
        val packetHandler =
            PacketHandlerImpl(
                lazy { mock(MockMode.autofill) },
                sdk.radio,
                lazy { mock(MockMode.autofill) },
                FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) },
                nodeManager,
                backgroundScope.asServiceScope(),
            )
        val sender =
            CommandSenderImpl(
                packetHandler,
                nodeManager,
                tracerouteHandler,
                neighborInfoHandler,
                radioConfigRepository,
                backgroundScope.asServiceScope(),
            )
        return Fixture(sdk, sender)
    }

    @Test
    fun `a traceroute goes out on the traceroute port and times the reply by its wire ID`() = runTest {
        val fixture = fixture()

        val request = fixture.sender.requestTraceroute(DEST_NODE)
        runCurrent()

        val sent = fixture.sent(PortNum.TRACEROUTE_APP)
        assertEquals(DEST_NODE, sent.to)
        assertTrue(sent.want_ack)
        assertEquals(true, sent.decoded?.want_response)
        assertEquals(DEST_NODE, sent.decoded?.dest)
        assertEquals(sent.id, request.requestId)
        verify { tracerouteHandler.recordStartTime(sent.id, DEST_NODE) }
        fixture.sdk.client.disconnect()
    }

    @Test
    fun `a traceroute the session refuses throws without starting its timer`() = runTest {
        val fixture = fixture()
        fixture.sdk.radio.disconnect()

        assertFailsWith<PacketQueueRejectedException> { fixture.sender.requestTraceroute(DEST_NODE) }

        verify(exactly(0)) { tracerouteHandler.recordStartTime(any(), any()) }
        fixture.sdk.client.disconnect()
    }

    @Test
    fun `a routing NAK to a traceroute settles its request with that error`() = runTest {
        val fixture = fixture()
        val request = fixture.sender.requestTraceroute(DEST_NODE)
        val id = assertNotNull(request.requestId)
        runCurrent()

        fixture.sdk.transport.injectRoutingError(id, Routing.Error.NO_CHANNEL, fromNode = MY_NODE_NUM)
        runCurrent()

        assertEquals(Routing.Error.NO_CHANNEL.value, request.awaitRoutingError())
        fixture.sdk.client.disconnect()
    }

    @Test
    fun `a neighbor request to our own node carries the dummy table firmware ignores`() = runTest {
        every { neighborInfoHandler.lastNeighborInfo } returns null
        val fixture = fixture()

        val request = fixture.sender.requestNeighborInfo(MY_NODE_NUM)
        runCurrent()

        val sent = fixture.sent(PortNum.NEIGHBORINFO_APP)
        val table = NeighborInfo.ADAPTER.decode(checkNotNull(sent.decoded).payload)
        // NeighborInfoModule::handleReceivedProtobuf skips exactly one neighbor with node_id 0 and snr 0.
        assertEquals(1, table.neighbors.size)
        assertEquals(0, table.neighbors.single().node_id)
        assertEquals(0f, table.neighbors.single().snr)
        assertEquals(3600, table.node_broadcast_interval_secs)
        verify { neighborInfoHandler.recordStartTime(checkNotNull(request.requestId)) }
        fixture.sdk.client.disconnect()
    }

    @Test
    fun `a neighbor request to a remote node names it as the destination`() = runTest {
        val fixture = fixture()

        fixture.sender.requestNeighborInfo(DEST_NODE)
        runCurrent()

        val sent = fixture.sent(PortNum.NEIGHBORINFO_APP)
        assertEquals(DEST_NODE, sent.to)
        assertEquals(DEST_NODE, sent.decoded?.dest)
        assertTrue(sent.want_ack)
        assertEquals(true, sent.decoded?.want_response)
        fixture.sdk.client.disconnect()
    }

    @Test
    fun `local stats can be requested from a remote node`() = runTest {
        val fixture = fixture()

        fixture.sender.requestLocalStats(DEST_NODE)
        runCurrent()

        val sent = fixture.sent(PortNum.TELEMETRY_APP)
        assertEquals(DEST_NODE, sent.to)
        assertEquals(DEST_NODE, sent.decoded?.dest)
        assertEquals(true, sent.decoded?.want_response)
        assertNotNull(Telemetry.ADAPTER.decode(checkNotNull(sent.decoded).payload).local_stats)
        fixture.sdk.client.disconnect()
    }

    private companion object {
        const val MY_NODE_NUM = 100
        const val DEST_NODE = 200
    }
}
