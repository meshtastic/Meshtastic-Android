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
package org.meshtastic.core.service

import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRequest
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeUiPrefs
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.proto.Telemetry
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.meshtastic.proto.Position as ProtoPosition

class QueryControllerImplTest {
    /** Records the requests left to the app sender. */
    private class RecordingCommandSender : CommandSender {
        val localStats = mutableListOf<Int>()

        override suspend fun requestTraceroute(destNum: Int): NodeRequest = NodeRequest.NONE

        override suspend fun requestNeighborInfo(destNum: Int): NodeRequest = NodeRequest.NONE

        override suspend fun requestLocalStats(destNum: Int): NodeRequest {
            localStats += destNum
            return NodeRequest.NONE
        }
    }

    private class Fixture(
        val transport: FakeRadioTransport,
        val client: RadioClient,
        val radio: FakeRadioInterfaceService,
        val uiPrefs: FakeUiPrefs,
        val commandSender: RecordingCommandSender,
        val controller: QueryControllerImpl,
    ) {
        fun sent(port: PortNum): MeshPacket = transport.outboundPackets().single { it.decoded?.portnum == port }
    }

    private suspend fun TestScope.fixture(): Fixture {
        val transport = FakeRadioTransport(TransportIdentity("fake:query"), autoHandshake = true, nodeNum = LOCAL)
        val client =
            RadioClient.Builder()
                .transport(transport)
                .storage(InMemoryStorageProvider())
                .coroutineContext(backgroundScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .build()
        client.connect()
        val radio =
            FakeRadioInterfaceService(backgroundScope).apply {
                setDeviceAddress("tselected")
                connect()
                this.client = client
            }
        val nodeManager =
            mock<NodeManager>().also {
                every { it.myNodeNum } returns MutableStateFlow(LOCAL)
                every { it.nodeDBbyNodeNum } returns emptyMap()
            }
        val uiPrefs = FakeUiPrefs()
        val commandSender = RecordingCommandSender()
        val controller = QueryControllerImpl(commandSender, radio, nodeManager, uiPrefs, backgroundScope)
        return Fixture(transport, client, radio, uiPrefs, commandSender, controller)
    }

    @Test
    fun `a telemetry request names its variant and the mesh's NAK fails that request`() = runTest {
        val fixture = fixture()

        val request = fixture.controller.requestTelemetry(PEER, TelemetryType.ENVIRONMENT)
        runCurrent()
        val sent = fixture.sent(PortNum.TELEMETRY_APP)
        fixture.transport.injectRoutingError(sent.id, Routing.Error.NO_RESPONSE, fromNode = PEER)
        runCurrent()

        assertEquals(PEER, sent.decoded?.dest)
        assertNotNull(Telemetry.ADAPTER.decode(checkNotNull(sent.decoded).payload).environment_metrics)
        assertEquals(Routing.Error.NO_RESPONSE.value, request.awaitRoutingError())
        fixture.client.disconnect()
    }

    @Test
    fun `a position request shares our fix when we have one and location sharing is on`() = runTest {
        val fixture = fixture()
        fixture.uiPrefs.setShouldProvideNodeLocation(LOCAL, true)

        fixture.controller.requestPosition(PEER, Position(latitude = 51.5, longitude = -0.125, altitude = 20))
        runCurrent()

        val shared = ProtoPosition.ADAPTER.decode(checkNotNull(fixture.sent(PortNum.POSITION_APP).decoded).payload)
        assertEquals(515_000_000, shared.latitude_i)
        assertEquals(-1_250_000, shared.longitude_i)
        assertEquals(20, shared.altitude)
        assertTrue(shared.time > 0)
        fixture.client.disconnect()
    }

    @Test
    fun `a position request without location sharing carries only the time`() = runTest {
        val fixture = fixture()
        fixture.uiPrefs.setShouldProvideNodeLocation(LOCAL, false)

        fixture.controller.requestPosition(PEER, Position(latitude = 51.5, longitude = -0.125, altitude = 20))
        runCurrent()

        val sent = fixture.sent(PortNum.POSITION_APP)
        val shared = ProtoPosition.ADAPTER.decode(checkNotNull(sent.decoded).payload)
        assertNull(shared.latitude_i)
        assertNull(shared.longitude_i)
        assertTrue(shared.time > 0)
        assertEquals(true, sent.decoded?.want_response)
        fixture.client.disconnect()
    }

    @Test
    fun `local stats of a remote node go through the app sender and ours through the SDK`() = runTest {
        val fixture = fixture()

        fixture.controller.requestTelemetry(PEER, TelemetryType.LOCAL_STATS)
        fixture.controller.requestTelemetry(LOCAL, TelemetryType.LOCAL_STATS)
        runCurrent()

        assertEquals(listOf(PEER), fixture.commandSender.localStats)
        val sent = fixture.sent(PortNum.TELEMETRY_APP)
        assertEquals(LOCAL, sent.to)
        assertNotNull(Telemetry.ADAPTER.decode(checkNotNull(sent.decoded).payload).local_stats)
        fixture.client.disconnect()
    }

    @Test
    fun `a request without a radio session is refused`() = runTest {
        val fixture = fixture()
        fixture.radio.disconnect()

        assertFailsWith<PacketQueueRejectedException> { fixture.controller.requestUserInfo(PEER) }
        assertFailsWith<PacketQueueRejectedException> {
            fixture.controller.requestTelemetry(PEER, TelemetryType.DEVICE)
        }
        fixture.client.disconnect()
    }

    private companion object {
        const val LOCAL = 100
        const val PEER = 200
    }
}
