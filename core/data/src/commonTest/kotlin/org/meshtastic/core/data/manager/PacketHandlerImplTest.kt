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
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PersistedPacket
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.Routing
import org.meshtastic.proto.User
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.PacedMessageHandle
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.toFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PacketHandlerImplTest {
    @Test
    fun `SDK retains the wire ID and paces packets while admitting the next caller`() = runTest {
        val fixture = fixture()
        assertTrue(fixture.handler.sendToRadio(packet(101)))
        assertTrue(fixture.handler.sendToRadio(packet(102)))
        assertFalse(fixture.handler.sendToRadio(packet(102)))
        assertEquals(listOf(101), fixture.sentTextIds())

        fixture.queueStatus(101, result = 0)
        runCurrent()
        assertEquals(listOf(101, 102), fixture.sentTextIds())
        verifySuspend { fixture.repository.applyOutgoingQueueStatus(packet(101), MessageStatus.ENROUTE) }
        verifySuspend { fixture.repository.applyOutgoingQueueStatus(packet(102), MessageStatus.ENROUTE) }
        fixture.shutdown()
    }

    @Test
    fun `firmware local delivery settles the durable row as delivered`() = runTest {
        val fixture = fixture()
        val outgoing = packet(301, destination = LOCAL_NODE)
        assertTrue(fixture.handler.sendToRadio(outgoing))
        runCurrent()
        verifySuspend(exactly(0)) { fixture.repository.applyOutgoingQueueStatus(outgoing, MessageStatus.DELIVERED) }
        fixture.queueStatus(301, result = 35)
        runCurrent()

        verifySuspend { fixture.repository.applyOutgoingQueueStatus(outgoing, MessageStatus.DELIVERED) }
        fixture.shutdown()
    }

    @Test
    fun `a late queue rejection still settles the durable outgoing row`() = runTest {
        val fixture = fixture()
        val outgoing = packet(551)
        assertTrue(fixture.handler.sendToRadio(outgoing))
        advanceTimeBy(6.seconds)
        runCurrent()
        verifySuspend(exactly(0)) { fixture.repository.applyOutgoingQueueStatus(outgoing, MessageStatus.ERROR) }

        fixture.queueStatus(551, result = 32)
        runCurrent()
        verifySuspend { fixture.repository.applyOutgoingQueueStatus(outgoing, MessageStatus.ERROR) }
        fixture.shutdown()
    }

    @Test
    fun `a stale send receipt cannot write to the replacement radio database`() = runTest {
        val fixture = fixture()
        val outgoing = packet(601)
        assertTrue(fixture.handler.sendToRadio(outgoing))
        runCurrent()
        fixture.radio.disconnect()
        fixture.radio.setDeviceAddress("treplacement")
        fixture.radio.connect()
        fixture.databases.switchToNewDatabase()
        fixture.queueStatus(601, result = 32)
        runCurrent()

        verifySuspend(exactly(0)) { fixture.repository.applyOutgoingQueueStatus(outgoing, MessageStatus.ERROR) }
        fixture.shutdown()
    }

    @Test
    fun `an ENROUTE row still times out after its session ended with no reconnect`() = runTest {
        // Regression: a timer fenced on the session lease leaves the row ENROUTE forever once the session is gone.
        val fixture = fixture()
        assertTrue(fixture.handler.sendToRadio(packet(701)))
        runCurrent()
        fixture.queueStatus(701, result = 0)
        runCurrent()
        fixture.radio.disconnect()
        fixture.client.disconnect()
        advanceTimeBy(PacketHandlerImpl.SEND_ACK_TIMEOUT)
        runCurrent()
        verifySuspend { fixture.repository.timeOutEnroutePacket(PERSISTED_ID, Routing.Error.TIMEOUT.value) }
        fixture.shutdown()
    }

    @Test
    fun `a timeout armed before a disconnect does not write after the database switches`() = runTest {
        // Regression: a timer that writes whatever database is current would hit the replacement radio's database.
        val fixture = fixture()
        assertTrue(fixture.handler.sendToRadio(packet(711)))
        runCurrent()
        fixture.queueStatus(711, result = 0)
        runCurrent()
        fixture.radio.disconnect()
        fixture.databases.switchToNewDatabase()
        advanceTimeBy(PacketHandlerImpl.SEND_ACK_TIMEOUT)
        runCurrent()
        verifySuspend(exactly(0)) { fixture.repository.timeOutEnroutePacket(PERSISTED_ID, Routing.Error.TIMEOUT.value) }
        fixture.shutdown()
    }

    @Test
    fun `a timeout still writes when the database instance is republished while the session is active`() = runTest {
        // Regression: a database-only fence drops this write on association merge or wedge reopen; main wrote it.
        val fixture = fixture()
        assertTrue(fixture.handler.sendToRadio(packet(721)))
        runCurrent()
        fixture.queueStatus(721, result = 0)
        runCurrent()
        fixture.databases.switchToNewDatabase()
        advanceTimeBy(PacketHandlerImpl.SEND_ACK_TIMEOUT)
        runCurrent()
        verifySuspend { fixture.repository.timeOutEnroutePacket(PERSISTED_ID, Routing.Error.TIMEOUT.value) }
        fixture.shutdown()
    }

    @Test
    fun `a dispatched send that fails as disconnected is marked ERROR after its session is gone`() = runTest {
        // Regression: Unconfirmed plus Disconnected fell through and the write was refused by a lease already gone.
        val fixture = fixture()
        val outgoing = packet(801)
        assertTrue(fixture.handler.sendToRadio(outgoing))
        runCurrent()
        fixture.radio.disconnect()
        fixture.client.disconnect()
        runCurrent()
        verifySuspend { fixture.repository.applyOutgoingQueueStatus(outgoing, MessageStatus.ERROR) }
        fixture.shutdown()
    }

    @Test
    fun `a send whose ACK wait times out becomes TIMEOUT without any app timer`() = runTest {
        // Regression: AckTimeout not projected, so the row waits for a re-arm that never comes while connected.
        val fixture = fixture()
        val outgoing = packet(901)
        assertTrue(fixture.handler.sendToRadio(outgoing))
        runCurrent()
        advanceTimeBy(6.seconds)
        advanceTimeBy(PacketHandlerImpl.SEND_ACK_TIMEOUT)
        runCurrent()
        verifySuspend { fixture.repository.timeOutEnroutePacket(PERSISTED_ID, Routing.Error.TIMEOUT.value) }
        verifySuspend(exactly(0)) { fixture.repository.applyOutgoingQueueStatus(outgoing, MessageStatus.ERROR) }
        fixture.shutdown()
    }

    @Test
    fun `a status write still lands when the database is republished while the session is active`() = runTest {
        // Regression: gating the status write on the selected database alone drops it after an in-session republish.
        val fixture = fixture()
        val outgoing = packet(902)
        val stored = PersistedPacket(PERSISTED_ID, DataPacket(bytes = null, dataType = PortNum.TEXT_MESSAGE_APP.value))
        var attempts = 0
        everySuspend { fixture.repository.applyOutgoingQueueStatus(outgoing, MessageStatus.ENROUTE) } calls
            {
                if (attempts++ == 0) null else stored
            }
        assertTrue(fixture.handler.sendToRadio(outgoing))
        runCurrent()
        fixture.databases.switchToNewDatabase()
        advanceTimeBy(PacketHandlerImpl.PERSISTED_STATUS_RETRY_DELAY)
        runCurrent()
        verifySuspend(exactly(2)) { fixture.repository.applyOutgoingQueueStatus(outgoing, MessageStatus.ENROUTE) }
        fixture.shutdown()
    }

    @Test
    fun `a status waits for a row the sender writes seconds after queueing`() = runTest {
        // Regression: a 1 s wait dropped ENROUTE for a row a busy database wrote late, and the next connect re-sent it.
        val fixture = fixture()
        val outgoing = packet(903)
        val stored = PersistedPacket(PERSISTED_ID, DataPacket(bytes = null, dataType = PortNum.TEXT_MESSAGE_APP.value))
        var rowWritten = false
        var statusesWritten = 0
        everySuspend { fixture.repository.applyOutgoingQueueStatus(outgoing, MessageStatus.ENROUTE) } calls
            {
                if (rowWritten) stored.also { statusesWritten++ } else null
            }

        assertTrue(fixture.handler.sendToRadio(outgoing))
        runCurrent()
        advanceTimeBy(5.seconds)
        rowWritten = true
        advanceTimeBy(PacketHandlerImpl.PERSISTED_STATUS_RETRY_DELAY)
        runCurrent()

        assertEquals(1, statusesWritten)
        fixture.shutdown()
    }

    @Test
    fun `reconnect re-arms a persisted timeout for the selected database`() = runTest {
        val fixture = fixture()
        val persisted =
            PersistedPacket(
                PERSISTED_ID,
                DataPacket(
                    bytes = null,
                    dataType = PortNum.TEXT_MESSAGE_APP.value,
                    status = MessageStatus.ENROUTE,
                    time = 0,
                ),
            )
        everySuspend { fixture.repository.getEnroutePackets() } returns listOf(persisted)
        fixture.handler.rearmSendAckTimeouts()
        advanceTimeBy(PacketHandlerImpl.REARM_GRACE)
        runCurrent()
        verifySuspend { fixture.repository.timeOutEnroutePacket(PERSISTED_ID, Routing.Error.TIMEOUT.value) }
        fixture.shutdown()
    }

    @Test
    fun `a data packet goes out under its persisted ID with its reply and emoji`() = runTest {
        val fixture = fixture()
        val packet =
            DataPacket(
                to = NodeAddress.numToDefaultId(PEER),
                bytes = "👍".encodeUtf8(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                channel = 1,
                replyId = 77,
                wantAck = true,
                emoji = 1,
            )
                .also { it.id = 901 }

        fixture.handler.sendData(packet)
        runCurrent()

        val sent = fixture.transport.outboundPackets().single { it.decoded?.portnum == PortNum.TEXT_MESSAGE_APP }
        assertEquals(901, sent.id)
        assertEquals(LOCAL_NODE, sent.from)
        assertEquals(PEER, sent.to)
        assertEquals(1, sent.channel)
        assertTrue(sent.want_ack)
        assertEquals(77, sent.decoded?.reply_id)
        assertEquals(1, sent.decoded?.emoji)
        assertEquals(MessageStatus.QUEUED, packet.status)
        assertTrue(packet.time > 0)
        fixture.shutdown()
    }

    @Test
    fun `a data packet without its own hop limit goes out with firmware's default rather than zero`() = runTest {
        val fixture = fixture()
        val packet =
            DataPacket(to = NodeAddress.ID_BROADCAST, bytes = "x".encodeUtf8(), dataType = PortNum.ATAK_PLUGIN.value)

        fixture.handler.sendData(packet)
        runCurrent()

        val sent = fixture.transport.outboundPackets().single { it.decoded?.portnum == PortNum.ATAK_PLUGIN }
        assertEquals(NodeAddress.NODENUM_BROADCAST, sent.to)
        assertEquals(packet.id, sent.id)
        // HOP_RELIABLE in firmware's MeshTypes.h; the handshake here carries no LoRa config.
        assertEquals(3, sent.hop_limit)
        fixture.shutdown()
    }

    @Test
    fun `a data packet without an ID takes the next ID from the SDK client's counter`() = runTest {
        val fixture = fixture()
        val packet =
            DataPacket(to = NodeAddress.ID_BROADCAST, bytes = "x".encodeUtf8(), dataType = PortNum.ATAK_PLUGIN.value)

        fixture.handler.sendData(packet)

        // RadioClient.nextPacketId steps its counter by one, so the SDK's own next send cannot reuse the row's ID.
        assertEquals(packet.id + 1, fixture.client.nextPacketId().raw)
        fixture.shutdown()
    }

    @Test
    fun `a direct message on the PKI channel is encrypted to the stored key on channel 0`() = runTest {
        val fixture = fixture()
        val packet =
            DataPacket(
                to = NodeAddress.numToDefaultId(PEER),
                bytes = "hi".encodeUtf8(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                channel = NodeAddress.PKC_CHANNEL_INDEX,
            )

        fixture.handler.sendData(packet)
        runCurrent()

        val sent = fixture.transport.outboundPackets().single { it.decoded?.portnum == PortNum.TEXT_MESSAGE_APP }
        assertTrue(sent.pki_encrypted)
        assertEquals(PEER_KEY, sent.public_key)
        assertEquals(0, sent.channel)
        fixture.shutdown()
    }

    @Test
    fun `a data packet the session refuses is marked failed and throws`() = runTest {
        val fixture = fixture()
        fixture.radio.disconnect()
        val packet = DataPacket(to = NodeAddress.ID_BROADCAST, bytes = "x".encodeUtf8(), dataType = 1, time = 0)

        assertFailsWith<PacketQueueRejectedException> { fixture.handler.sendData(packet) }

        assertEquals(MessageStatus.ERROR, packet.status)
        assertEquals(0L, packet.time)
        fixture.shutdown()
    }

    @Test
    fun `a data packet over the encoded Data limit is refused before it is queued`() = runTest {
        val fixture = fixture()
        val packet =
            DataPacket(
                to = NodeAddress.ID_BROADCAST,
                bytes = ByteString.of(*ByteArray(300) { 0x42 }),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
            )

        val failure = assertFailsWith<IllegalStateException> { fixture.handler.sendData(packet) }

        assertTrue(failure.message.orEmpty().contains("Message too long"))
        assertEquals(MessageStatus.ERROR, packet.status)
        assertTrue(fixture.sentTextIds().isEmpty())
        fixture.shutdown()
    }

    private suspend fun TestScope.fixture(): Fixture {
        val transport =
            FakeRadioTransport(TransportIdentity("fake:outgoing"), autoHandshake = true, nodeNum = LOCAL_NODE)
        val client =
            RadioClient.Builder()
                .transport(transport)
                .storage(InMemoryStorageProvider())
                .coroutineContext(backgroundScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .sendAckTimeout(PacketHandlerImpl.SEND_ACK_TIMEOUT)
                .build()
        client.connect()
        val radio =
            FakeRadioInterfaceService(backgroundScope).apply {
                setDeviceAddress("tselected")
                connect()
                this.client = client
            }
        val service = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val radioService =
            object : RadioInterfaceService by radio {
                override fun sendPaced(session: RadioSessionContext, packet: MeshPacket): PacedMessageHandle? =
                    if (radio.isSessionActive(session)) client.sendPaced(packet) else null
            }
        val repository = mock<PacketRepository>(MockMode.autofill)
        everySuspend { repository.getEnroutePackets() } returns emptyList()
        everySuspend { repository.getEnrouteReactions() } returns emptyList()
        // Like PacketDao's outgoing match, a packet from another sender finds no stored row.
        everySuspend { repository.applyOutgoingQueueStatus(any(), any()) } calls
            { args ->
                PersistedPacket(PERSISTED_ID, DataPacket(bytes = null, dataType = PortNum.TEXT_MESSAGE_APP.value))
                    .takeIf { args.arg<MeshPacket>(0).from == LOCAL_NODE }
            }
        val databases = FakeDatabaseProvider()
        val handler =
            PacketHandlerImpl(
                lazy { repository },
                databases,
                radioService,
                service,
                nodeManager(),
                backgroundScope.asServiceScope(),
            )
        return Fixture(handler, repository, databases, service, radio, transport, client)
    }

    private class Fixture(
        val handler: PacketHandlerImpl,
        val repository: PacketRepository,
        val databases: FakeDatabaseProvider,
        val service: FakeServiceRepository,
        val radio: FakeRadioInterfaceService,
        val transport: FakeRadioTransport,
        val client: RadioClient,
    ) {
        suspend fun shutdown() {
            client.disconnect()
            databases.close()
        }

        fun queueStatus(id: Int, result: Int) {
            val status =
                QueueStatus.Builder()
                    .also {
                        it.mesh_packet_id = id
                        it.res = result
                    }
                    .build()
            transport.injectFrame(FromRadio.Builder().also { it.queueStatus = status }.build().toFrame())
        }

        fun sentTextIds(): List<Int> =
            transport.outboundPackets().filter { it.decoded?.portnum == PortNum.TEXT_MESSAGE_APP }.map { it.id }
    }

    private fun nodeManager(): NodeManager = mock<NodeManager>().also {
        dev.mokkery.every { it.myNodeNum } returns MutableStateFlow(LOCAL_NODE)
        dev.mokkery.every { it.nodeDBbyNodeNum } returns
            mapOf(PEER to Node(num = PEER, user = User.Builder().also { u -> u.public_key = PEER_KEY }.build()))
    }

    private fun packet(id: Int, destination: Int = PEER): MeshPacket = MeshPacket.Builder()
        .also {
            it.id = id
            it.from = LOCAL_NODE
            it.to = destination
            it.want_ack = true
            it.decoded = Data.Builder().also { data -> data.portnum = PortNum.TEXT_MESSAGE_APP }.build()
        }
        .build()

    private companion object {
        const val LOCAL_NODE = 123
        const val PEER = 456
        val PEER_KEY = ByteArray(32) { 0x2B.toByte() }.toByteString()
        val PERSISTED_ID = PersistedPacketId(myNodeNum = LOCAL_NODE, uuid = 456L)
    }
}
