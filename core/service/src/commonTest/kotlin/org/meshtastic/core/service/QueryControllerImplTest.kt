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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.core.testing.FakeUiPrefs
import org.meshtastic.proto.Data
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
import org.meshtastic.sdk.ConnectionState as SdkConnectionState

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
        val service: FakeServiceRepository,
        val uiPrefs: FakeUiPrefs,
        val timers: RecordingRequestTimers,
        val controller: QueryControllerImpl,
    ) {
        fun sent(port: PortNum): MeshPacket = transport.outboundPackets().single { it.decoded?.portnum == port }
    }

    private suspend fun TestScope.fixture(
        ownUser: User? = OWN_USER,
        connect: Boolean = true,
        handshake: Boolean = true,
        ownStoredPosition: ProtoPosition = ProtoPosition.Builder().build(),
    ): Fixture {
        val transport = FakeRadioTransport(TransportIdentity("fake:query"), autoHandshake = handshake, nodeNum = LOCAL)
        val client =
            RadioClient.Builder()
                .transport(transport)
                .storage(InMemoryStorageProvider())
                .coroutineContext(backgroundScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .build()
        if (connect) client.connect()
        if (connect && ownUser != null) {
            // Our own node info, as firmware broadcasts it, so the SDK knows the user it sends with a request.
            transport.injectPacket(
                MeshPacket.Builder()
                    .also { wb ->
                        wb.from = LOCAL
                        wb.to = BROADCAST
                        wb.decoded =
                            Data.Builder()
                                .also { db ->
                                    db.portnum = PortNum.NODEINFO_APP
                                    db.payload = User.ADAPTER.encode(ownUser).toByteString()
                                }
                                .build()
                    }
                    .build(),
            )
            runCurrent()
        }
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
                    (
                        ownUser?.let { user ->
                            mapOf(LOCAL to Node(num = LOCAL, user = user, position = ownStoredPosition))
                        } ?: emptyMap()
                        )
            }
        val uiPrefs = FakeUiPrefs()
        val timers = RecordingRequestTimers()
        val service = FakeServiceRepository()
        if (connect && handshake) service.setConnectionState(SdkConnectionState.Connected)
        val controller = QueryControllerImpl(radio, service, timers, timers, nodeManager, uiPrefs, backgroundScope)
        return Fixture(transport, client, radio, service, uiPrefs, timers, controller)
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
        // The setting is ours to share with everyone; the peer's own entry must not decide it.
        fixture.uiPrefs.setShouldProvideNodeLocation(PEER, false)

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
    fun `a position request the destination ACKs and then NAKs as unanswered reports NO_RESPONSE`() = runTest {
        val fixture = fixture()

        val request = fixture.controller.requestPosition(PEER, Position(latitude = 0.0, longitude = 0.0, altitude = 0))
        runCurrent()
        val sent = fixture.sent(PortNum.POSITION_APP)
        // Firmware's ReliableRouter ACKs the request, then MeshModule::callModules NAKs it when no module replied.
        fixture.transport.injectRoutingAck(sent.id, fromNode = PEER)
        runCurrent()
        fixture.transport.injectRoutingError(sent.id, Routing.Error.NO_RESPONSE, fromNode = PEER)
        runCurrent()

        assertEquals(sent.id, request.requestId)
        assertEquals(Routing.Error.NO_RESPONSE.value, request.awaitRoutingError())
        fixture.client.disconnect()
    }

    // Regression: the SDK's null-channel fallback must resolve to the channel the peer was last heard on; a
    // hard-coded 0 or dropped channel would send on the wrong one.
    @Test
    fun `a position request goes out on the channel the peer was last heard on`() = runTest {
        val fixture = fixture()
        fixture.transport.injectPacket(
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = PEER
                    wb.to = 0
                    wb.channel = 2
                    wb.rx_time = 1_700_000_100
                    wb.decoded =
                        Data.Builder()
                            .also { db ->
                                db.portnum = PortNum.NODEINFO_APP
                                db.payload =
                                    User.ADAPTER.encode(User.Builder().also { ub -> ub.long_name = "Peer" }.build())
                                        .toByteString()
                            }
                            .build()
                }
                .build(),
        )
        runCurrent()

        fixture.controller.requestPosition(PEER, Position(0.0, 0.0, 0))
        runCurrent()

        assertEquals(2, fixture.sent(PortNum.POSITION_APP).channel)
        fixture.client.disconnect()
    }

    @Test
    fun `a position request without location sharing carries only the time`() = runTest {
        val fixture = fixture(ownStoredPosition = STORED_POSITION)
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
    fun `a traceroute goes out on the traceroute port under the ID its timer uses`() = runTest {
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
    fun `a traceroute without a radio session starts no timer`() = runTest {
        val fixture = fixture()
        fixture.radio.disconnect()

        assertFailsWith<PacketQueueRejectedException> { fixture.controller.requestTraceroute(PEER) }

        assertTrue(fixture.timers.traceroutes.isEmpty())
        fixture.client.disconnect()
    }

    @Test
    fun `a remote neighbor request names its destination under the ID its timer uses`() = runTest {
        val fixture = fixture()

        fixture.controller.requestNeighborInfo(PEER)
        runCurrent()

        val sent = fixture.sent(PortNum.NEIGHBORINFO_APP)
        assertEquals(PEER, sent.to)
        assertEquals(PEER, sent.decoded?.dest)
        assertTrue(sent.want_ack)
        assertEquals(listOf(sent.id), fixture.timers.neighborInfo)
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

    // Regression: gating on the SDK client state instead of the app's published Connected state.
    @Test
    fun `requests are refused while the app is not Connected even if the SDK client is`() = runTest {
        val fixture = fixture()
        assertEquals(SdkConnectionState.Connected, fixture.client.connection.value)
        fixture.service.setConnectionState(SdkConnectionState.Connecting(attempt = 1))

        assertFailsWith<PacketQueueRejectedException> { fixture.controller.requestTraceroute(PEER) }
        assertTrue(fixture.timers.traceroutes.isEmpty())
        fixture.client.disconnect()
    }

    @Test
    fun `requests are refused until the snapshot install publishes Connected`() = runTest {
        val fixture = fixture(connect = false, handshake = false)
        backgroundScope.launch { fixture.client.connect() }
        runCurrent()
        val state = fixture.client.connection.value
        assertTrue(state is SdkConnectionState.Connecting || state is SdkConnectionState.Configuring, "$state")

        assertFailsWith<PacketQueueRejectedException> { fixture.controller.requestTraceroute(PEER) }
        assertFailsWith<PacketQueueRejectedException> { fixture.controller.requestNeighborInfo(PEER) }
        assertFailsWith<PacketQueueRejectedException> {
            fixture.controller.requestTelemetry(PEER, TelemetryType.DEVICE)
        }

        assertTrue(fixture.timers.traceroutes.isEmpty())
        assertTrue(fixture.timers.neighborInfo.isEmpty())
        fixture.client.disconnect()
    }

    @Test
    fun `a user-info request carries our user`() = runTest {
        val fixture = fixture()

        fixture.controller.requestUserInfo(PEER)
        runCurrent()

        val sent = fixture.sent(PortNum.NODEINFO_APP)
        assertEquals(OWN_USER, User.ADAPTER.decode(checkNotNull(sent.decoded).payload))
        assertEquals(true, sent.decoded?.want_response)
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
        // A traceroute has no expected sender, so the SDK settles on our radio's MAX_RETRANSMIT without holding it.
        advanceTimeBy(RPC_DEADLINE_MS)
        runCurrent()

        assertEquals(Routing.Error.MAX_RETRANSMIT.value, request.awaitRoutingError())
        fixture.client.disconnect()
    }

    @Test
    fun `a peer's GOT_NAK to a paxcount request reports GOT_NAK not NO_ROUTE`() = runTest {
        val fixture = fixture()

        val request = fixture.controller.requestTelemetry(PEER, TelemetryType.PAX)
        runCurrent()
        val sent = fixture.sent(PortNum.PAXCOUNTER_APP)
        fixture.transport.injectRoutingError(sent.id, Routing.Error.GOT_NAK, fromNode = PEER)
        runCurrent()

        assertEquals(Routing.Error.GOT_NAK.value, request.awaitRoutingError())
        fixture.client.disconnect()
    }

    private companion object {
        /** Past both the SDK's default RPC timeout and `REMOTE_READ_TIMEOUT`. */
        const val RPC_DEADLINE_MS = 151_000L

        /** After the SDK's default 30 s RPC timeout, well inside `REMOTE_READ_TIMEOUT`. */
        const val LATE_NAK_MS = 60_000L

        val STORED_POSITION: ProtoPosition =
            ProtoPosition.Builder()
                .also { wb ->
                    wb.latitude_i = 401_000_000
                    wb.longitude_i = -749_000_000
                }
                .build()
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
        const val BROADCAST = -1
    }
}
