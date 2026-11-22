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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.testing.FakePacketHandler
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeUiPrefs
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
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
    /** Records the request timers started, as (request ID, destination). */
    private class RecordingRequestTimers :
        TracerouteHandler,
        NeighborInfoHandler {
        val traceroutes = mutableListOf<Pair<Int, Int>>()
        val neighborInfo = mutableListOf<Int>()

        override fun recordStartTime(requestId: Int, destination: Int) {
            traceroutes += requestId to destination
        }

        override fun recordStartTime(requestId: Int) {
            neighborInfo += requestId
        }

        override fun handleTraceroute(
            packet: MeshPacket,
            logUuid: String?,
            logInsertJob: Job?,
            session: RadioSessionContext,
        ) = Unit

        override fun handleNeighborInfo(packet: MeshPacket) = Unit
    }

    private class Fixture(
        val transport: FakeRadioTransport,
        val client: RadioClient,
        val radio: FakeRadioInterfaceService,
        val uiPrefs: FakeUiPrefs,
        val packetHandler: FakePacketHandler,
        val timers: RecordingRequestTimers,
        val controller: QueryControllerImpl,
    ) {
        fun sent(port: PortNum): MeshPacket = transport.outboundPackets().single { it.decoded?.portnum == port }
    }

    private suspend fun TestScope.fixture(ownUser: User? = OWN_USER, connect: Boolean = true): Fixture {
        val transport = FakeRadioTransport(TransportIdentity("fake:query"), autoHandshake = true, nodeNum = LOCAL)
        val client =
            RadioClient.Builder()
                .transport(transport)
                .storage(InMemoryStorageProvider())
                .coroutineContext(backgroundScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .build()
        if (connect) client.connect()
        val radio =
            FakeRadioInterfaceService(backgroundScope).apply {
                setDeviceAddress("tselected")
                connect()
                this.client = client
            }
        val nodeManager =
            mock<NodeManager>().also {
                every { it.myNodeNum } returns MutableStateFlow(LOCAL)
                every { it.nodeDBbyNodeNum } returns
                    (ownUser?.let { user -> mapOf(LOCAL to Node(num = LOCAL, user = user)) } ?: emptyMap())
            }
        val uiPrefs = FakeUiPrefs()
        val packetHandler = FakePacketHandler()
        val timers = RecordingRequestTimers()
        val controller =
            QueryControllerImpl(radio, packetHandler, timers, timers, nodeManager, uiPrefs, backgroundScope)
        return Fixture(transport, client, radio, uiPrefs, packetHandler, timers, controller)
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
    fun `local stats can be requested from a remote node`() = runTest {
        val fixture = fixture()

        fixture.controller.requestTelemetry(PEER, TelemetryType.LOCAL_STATS)
        runCurrent()

        val sent = fixture.sent(PortNum.TELEMETRY_APP)
        assertEquals(PEER, sent.to)
        assertEquals(PEER, sent.decoded?.dest)
        assertNotNull(Telemetry.ADAPTER.decode(checkNotNull(sent.decoded).payload).local_stats)
        fixture.client.disconnect()
    }

    @Test
    fun `a traceroute goes out on the traceroute port under the ID its timer and log row use`() = runTest {
        val fixture = fixture()

        val request = fixture.controller.requestTraceroute(PEER)
        runCurrent()

        val sent = fixture.sent(PortNum.TRACEROUTE_APP)
        assertEquals(PEER, sent.to)
        assertTrue(sent.want_ack)
        assertEquals(true, sent.decoded?.want_response)
        assertEquals(PEER, sent.decoded?.dest)
        assertEquals(sent.id, request.requestId)
        assertEquals(listOf(sent.id to PEER), fixture.timers.traceroutes)
        val row = fixture.packetHandler.loggedRows.single()
        assertEquals(listOf(sent.id, PEER, PEER), listOf(row.id, row.to, row.dest))
        assertEquals(PortNum.TRACEROUTE_APP, row.port)
        assertTrue(row.wantResponse)
        fixture.client.disconnect()
    }

    @Test
    fun `a routing NAK to a traceroute settles its request with that error`() = runTest {
        val fixture = fixture()
        val request = fixture.controller.requestTraceroute(PEER)
        runCurrent()

        fixture.transport.injectRoutingError(
            checkNotNull(request.requestId),
            Routing.Error.NO_CHANNEL,
            fromNode = LOCAL,
        )
        runCurrent()

        assertEquals(Routing.Error.NO_CHANNEL.value, request.awaitRoutingError())
        fixture.client.disconnect()
    }

    @Test
    fun `a traceroute without a radio session starts no timer and logs nothing`() = runTest {
        val fixture = fixture()
        fixture.radio.disconnect()

        assertFailsWith<PacketQueueRejectedException> { fixture.controller.requestTraceroute(PEER) }

        assertTrue(fixture.timers.traceroutes.isEmpty())
        assertTrue(fixture.packetHandler.loggedRows.isEmpty())
        fixture.client.disconnect()
    }

    @Test
    fun `a remote neighbor request names its destination under the ID its timer and log row use`() = runTest {
        val fixture = fixture()

        fixture.controller.requestNeighborInfo(PEER)
        runCurrent()

        val sent = fixture.sent(PortNum.NEIGHBORINFO_APP)
        assertEquals(PEER, sent.to)
        assertEquals(PEER, sent.decoded?.dest)
        assertTrue(sent.want_ack)
        assertEquals(listOf(sent.id), fixture.timers.neighborInfo)
        val row = fixture.packetHandler.loggedRows.single()
        assertEquals(listOf(sent.id, PEER, PEER), listOf(row.id, row.to, row.dest))
        assertEquals(PortNum.NEIGHBORINFO_APP, row.port)
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

    @Test
    fun `a client that has not finished its handshake refuses requests before starting a timer or log row`() = runTest {
        val fixture = fixture(connect = false)

        assertFailsWith<PacketQueueRejectedException> { fixture.controller.requestTraceroute(PEER) }
        assertFailsWith<PacketQueueRejectedException> { fixture.controller.requestNeighborInfo(PEER) }
        assertFailsWith<PacketQueueRejectedException> {
            fixture.controller.requestTelemetry(PEER, TelemetryType.DEVICE)
        }

        assertTrue(fixture.timers.traceroutes.isEmpty())
        assertTrue(fixture.timers.neighborInfo.isEmpty())
        assertTrue(fixture.packetHandler.loggedRows.isEmpty())
    }

    @Test
    fun `a user-info request carries our user and leaves a request row under its wire ID`() = runTest {
        val fixture = fixture()

        fixture.controller.requestUserInfo(PEER)
        runCurrent()

        val sent = fixture.sent(PortNum.NODEINFO_APP)
        assertEquals(OWN_USER, User.ADAPTER.decode(checkNotNull(sent.decoded).payload))
        assertEquals(true, sent.decoded?.want_response)
        val row = fixture.packetHandler.loggedRows.single()
        assertEquals(listOf(sent.id, PEER), listOf(row.id, row.to))
        assertEquals(PortNum.NODEINFO_APP, row.port)
        fixture.client.disconnect()
    }

    @Test
    fun `a user-info request without our own user is refused instead of sent empty`() = runTest {
        val fixture = fixture(ownUser = null)

        assertFailsWith<LocalNodeUnavailableException> { fixture.controller.requestUserInfo(PEER) }

        assertTrue(fixture.transport.outboundPackets().none { it.decoded?.portnum == PortNum.NODEINFO_APP })
        fixture.client.disconnect()
    }

    @Test
    fun `a remote telemetry NAK after the default RPC timeout still reports`() = runTest {
        val fixture = fixture()
        val request = fixture.controller.requestTelemetry(PEER, TelemetryType.DEVICE)
        runCurrent()

        advanceTimeBy(LATE_NAK_MS)
        fixture.transport.injectRoutingError(fixture.sent(PortNum.TELEMETRY_APP).id, Routing.Error.NO_RESPONSE, PEER)
        runCurrent()

        assertEquals(Routing.Error.NO_RESPONSE.value, request.awaitRoutingError())
        fixture.client.disconnect()
    }

    @Test
    fun `a remote traceroute NAK after the default RPC timeout still reports`() = runTest {
        val fixture = fixture()
        val request = fixture.controller.requestTraceroute(PEER)
        runCurrent()

        advanceTimeBy(LATE_NAK_MS)
        fixture.transport.injectRoutingError(checkNotNull(request.requestId), Routing.Error.NO_RESPONSE, PEER)
        runCurrent()

        assertEquals(Routing.Error.NO_RESPONSE.value, request.awaitRoutingError())
        fixture.client.disconnect()
    }

    @Test
    fun `our radio giving up on a traceroute reports MAX_RETRANSMIT`() = runTest {
        val fixture = fixture()
        val request = fixture.controller.requestTraceroute(PEER)
        runCurrent()

        fixture.transport.injectRoutingError(
            checkNotNull(request.requestId),
            Routing.Error.MAX_RETRANSMIT,
            fromNode = LOCAL,
        )
        // The SDK holds our own radio's MAX_RETRANSMIT for a remote node until the RPC deadline.
        advanceTimeBy(RPC_DEADLINE_MS)
        runCurrent()

        assertEquals(Routing.Error.MAX_RETRANSMIT.value, request.awaitRoutingError())
        fixture.client.disconnect()
    }

    private companion object {
        /** Past both the SDK's default RPC timeout and `REMOTE_READ_TIMEOUT`. */
        const val RPC_DEADLINE_MS = 151_000L

        /** After the SDK's default 30 s RPC timeout, well inside `REMOTE_READ_TIMEOUT`. */
        const val LATE_NAK_MS = 60_000L
        val OWN_USER: User =
            User.Builder()
                .also { wb ->
                    wb.id = "!00000064"
                    wb.long_name = "Local"
                    wb.short_name = "LOC"
                }
                .build()

        const val LOCAL = 100
        const val PEER = 200
    }
}
