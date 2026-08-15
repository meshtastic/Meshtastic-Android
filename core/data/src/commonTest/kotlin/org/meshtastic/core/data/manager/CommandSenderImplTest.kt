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
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.capture.capture
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.AwaitedSendResult
import org.meshtastic.core.repository.AwaitedSendStatus
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.SessionManager
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Suppress("LargeClass")
class CommandSenderImplTest {
    private val packetHandler = mock<PacketHandler>(MockMode.autofill)
    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
    private val tracerouteHandler = mock<TracerouteHandler>(MockMode.autofill)
    private val neighborInfoHandler = mock<NeighborInfoHandler>(MockMode.autofill)
    private val sessionManager = mock<SessionManager>(MockMode.autofill)

    private lateinit var commandSender: CommandSenderImpl

    @BeforeTest
    fun setup() {
        every { radioConfigRepository.localConfigFlow } returns flowOf(LocalConfig())
        every { radioConfigRepository.channelSetFlow } returns flowOf(ChannelSet())
        every { nodeManager.myNodeNum } returns MutableStateFlow(MY_NODE_NUM)
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        every { sessionManager.getPasskey(any()) } returns ByteString.EMPTY

        commandSender =
            CommandSenderImpl(
                packetHandler = packetHandler,
                nodeManager = nodeManager,
                radioConfigRepository = radioConfigRepository,
                tracerouteHandler = tracerouteHandler,
                neighborInfoHandler = neighborInfoHandler,
                sessionManager = sessionManager,
                scope = TestScope().asServiceScope(),
            )
    }

    // --- generatePacketId ---

    @Test
    fun generatePacketId_returnsNonZero() {
        val id = commandSender.generatePacketId()
        assertNotEquals(0, id)
    }

    @Test
    fun generatePacketId_isIncrementing() {
        val first = commandSender.generatePacketId()
        val second = commandSender.generatePacketId()
        assertNotEquals(first, second)
    }

    @Test
    fun generatePacketId_staysNonZeroOverManyIterations() {
        repeat(100) { assertNotEquals(0, commandSender.generatePacketId()) }
    }

    // --- resolveNodeNum ---

    @Test
    fun resolveNodeNum_broadcast_returnsNodeNumBroadcast() {
        val result = commandSender.resolveNodeNum(NodeAddress.Broadcast)
        assertEquals(NodeAddress.NODENUM_BROADCAST, result)
    }

    @Test
    fun resolveNodeNum_local_returnsMyNodeNum() {
        val result = commandSender.resolveNodeNum(NodeAddress.Local)
        assertEquals(MY_NODE_NUM, result)
    }

    @Test
    fun resolveNodeNum_local_returnsZeroWhenMyNodeNumNull() {
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)
        commandSender =
            CommandSenderImpl(
                packetHandler = packetHandler,
                nodeManager = nodeManager,
                radioConfigRepository = radioConfigRepository,
                tracerouteHandler = tracerouteHandler,
                neighborInfoHandler = neighborInfoHandler,
                sessionManager = sessionManager,
                scope = TestScope().asServiceScope(),
            )
        assertEquals(0, commandSender.resolveNodeNum(NodeAddress.Local))
    }

    @Test
    fun resolveNodeNum_byNum_returnsPassthrough() {
        assertEquals(42, commandSender.resolveNodeNum(NodeAddress.ByNum(42)))
    }

    @Test
    fun resolveNodeNum_byId_looksUpAndReturns() {
        val node = Node(num = 99, user = User(id = "!deadbeef"))
        every { nodeManager.getNodeById("!deadbeef") } returns node
        assertEquals(99, commandSender.resolveNodeNum(NodeAddress.ById("!deadbeef")))
    }

    @Test
    fun resolveNodeNum_byId_throwsForUnknown() {
        every { nodeManager.getNodeById("!unknown") } returns null
        assertFailsWith<IllegalArgumentException> { commandSender.resolveNodeNum(NodeAddress.ById("!unknown")) }
    }

    // --- sendData ---

    @Test
    fun sendData_setsIdWhenZero() = runTest {
        val packet = DataPacket(to = "^all", bytes = "hi".encodeUtf8(), dataType = PortNum.TEXT_MESSAGE_APP.value)
        packet.id = 0
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns true

        commandSender.sendData(packet)
        assertNotEquals(0, packet.id)
    }

    @Test
    fun sendData_setsStatusQueued() = runTest {
        val packet =
            DataPacket(to = "^all", bytes = "hello".encodeUtf8(), dataType = PortNum.TEXT_MESSAGE_APP.value, time = 0)
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns true

        commandSender.sendData(packet)
        assertEquals(MessageStatus.QUEUED, packet.status)
        assertTrue(packet.time > 0, "an admitted packet should receive a send timestamp")
    }

    @Test
    fun sendData_marksPacketErrorAndThrowsWhenQueueRejectsIt() = runTest {
        val packet =
            DataPacket(to = "^all", bytes = "hello".encodeUtf8(), dataType = PortNum.TEXT_MESSAGE_APP.value, time = 0)
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns false

        assertFailsWith<PacketQueueRejectedException> { commandSender.sendData(packet) }

        assertEquals(MessageStatus.ERROR, packet.status)
        assertEquals(0L, packet.time, "a rejected packet must not receive a send timestamp")
    }

    @Test
    fun sendData_rejectsOversizedPayload() = runTest {
        val oversizedBytes = ByteString.of(*ByteArray(300) { 0x42 })
        val packet = DataPacket(to = "^all", bytes = oversizedBytes, dataType = PortNum.TEXT_MESSAGE_APP.value)

        val ex = assertFailsWith<IllegalStateException> { commandSender.sendData(packet) }
        assertTrue(ex.message!!.contains("Message too long"))
        assertEquals(MessageStatus.ERROR, packet.status)
    }

    @Test
    fun sendData_requiresNonZeroDataType() = runTest {
        val packet = DataPacket(to = "^all", bytes = "test".encodeUtf8(), dataType = 0)
        assertFailsWith<IllegalArgumentException> { commandSender.sendData(packet) }
    }

    // --- sendAdmin ---

    @Test
    fun sendAdmin_injectsSessionPasskey() = runTest {
        val passkey = "secret".encodeUtf8()
        every { sessionManager.getPasskey(DEST_NODE) } returns passkey
        val packets = mutableListOf<MeshPacket>()
        everySuspend { packetHandler.sendToRadio(capture(packets)) } returns true

        commandSender.sendAdmin(DEST_NODE) { AdminMessage(get_owner_request = true) }

        val adminMessage = AdminMessage.ADAPTER.decode(requireNotNull(packets.single().decoded).payload)
        assertEquals(passkey, adminMessage.session_passkey)
    }

    @Test
    fun sendAdmin_generatesNonZeroIdWhenCallerSuppliesZero() = runTest {
        val packets = mutableListOf<MeshPacket>()
        everySuspend { packetHandler.sendToRadio(capture(packets)) } returns true

        commandSender.sendAdmin(DEST_NODE, requestId = 0) { AdminMessage(get_owner_request = true) }

        assertNotEquals(0, packets.single().id)
    }

    @Test
    fun sendAdminSurfacesQueueRejection() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns false

        assertFailsWith<PacketQueueRejectedException> { commandSender.sendAdmin(DEST_NODE) { AdminMessage() } }
    }

    @Test
    fun sendAdminForConnectionForwardsLifecycleOwnershipToAdmission() = runTest {
        val packets = mutableListOf<MeshPacket>()
        everySuspend { packetHandler.sendToRadioForConnection(capture(packets), 17L) } returns true

        commandSender.sendAdminForConnection(DEST_NODE, expectedConnectionVersion = 17L) { AdminMessage() }

        assertEquals(1, packets.size)
        verifySuspend { packetHandler.sendToRadioForConnection(any<MeshPacket>(), 17L) }
    }

    @Test
    fun sendAdminAwaitResult_generatesNonZeroIdWhenCallerSuppliesZero() = runTest {
        val packets = mutableListOf<MeshPacket>()
        everySuspend { packetHandler.sendToRadioAndAwaitResult(capture(packets)) } returns
            AwaitedSendResult(AwaitedSendStatus.ACCEPTED, departureEpochAtDispatch = 0)

        commandSender.sendAdminAwaitResult(DEST_NODE, requestId = 0) { AdminMessage(get_owner_request = true) }

        assertNotEquals(0, packets.single().id)
    }

    @Test
    fun sendAdminAwaitResult_preservesNonAcceptedStatus() = runTest {
        val expected = AwaitedSendResult(AwaitedSendStatus.REJECTED)
        everySuspend { packetHandler.sendToRadioAndAwaitResult(any<MeshPacket>()) } returns expected

        val result = commandSender.sendAdminAwaitResult(DEST_NODE) { AdminMessage(get_owner_request = true) }

        assertEquals(expected, result)
        assertFalse(result.accepted)
    }

    // --- sendAdminImmediate ---

    @Test
    fun sendAdminImmediate_dispatchesDirectToRadioWithPasskeyAndNoResponse() {
        val passkey = "secret".encodeUtf8()
        every { sessionManager.getPasskey(DEST_NODE) } returns passkey
        every { packetHandler.sendToRadio(any<ToRadio>()) } returns Unit

        commandSender.sendAdminImmediate(DEST_NODE) { AdminMessage(set_time_only = 12345) }

        // Direct ToRadio dispatch (not the Connected-gated MeshPacket queue), correct destination,
        // no want_response, and the session passkey injected into the admin payload.
        verify {
            packetHandler.sendToRadio(
                matches<ToRadio> { toRadio ->
                    val packet = toRadio.packet ?: return@matches false
                    val decoded = packet.decoded ?: return@matches false
                    val admin = AdminMessage.ADAPTER.decode(decoded.payload)
                    packet.to == DEST_NODE &&
                        decoded.portnum == PortNum.ADMIN_APP &&
                        !decoded.want_response &&
                        admin.set_time_only == 12345 &&
                        admin.session_passkey == passkey
                },
            )
        }
    }

    // --- requestTraceroute ---

    @Test
    fun requestTraceroute_recordsStartTime() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns true

        commandSender.requestTraceroute(requestId = 42, destNum = DEST_NODE)

        verify { tracerouteHandler.recordStartTime(42) }
    }

    @Test
    fun requestTraceroute_doesNotStartTimerWhenQueueRejectsPacket() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns false

        assertFailsWith<PacketQueueRejectedException> {
            commandSender.requestTraceroute(requestId = 42, destNum = DEST_NODE)
        }

        verify(exactly(0)) { tracerouteHandler.recordStartTime(any()) }
    }

    @Test
    fun requestTelemetry_surfacesQueueRejection() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns false

        assertFailsWith<PacketQueueRejectedException> {
            commandSender.requestTelemetry(requestId = 42, destNum = DEST_NODE, typeValue = 0)
        }
    }

    @Test
    fun requestTelemetryForConnectionForwardsLifecycleOwnershipToAdmission() = runTest {
        everySuspend { packetHandler.sendToRadioForConnection(any<MeshPacket>(), 23L) } returns true

        commandSender.requestTelemetryForConnection(
            requestId = 42,
            destNum = DEST_NODE,
            typeValue = 0,
            expectedConnectionVersion = 23L,
        )

        verifySuspend { packetHandler.sendToRadioForConnection(any<MeshPacket>(), 23L) }
    }

    @Test
    fun requestTraceroute_generatesNonZeroIdWhenCallerSuppliesZero() = runTest {
        val packets = mutableListOf<MeshPacket>()
        everySuspend { packetHandler.sendToRadio(capture(packets)) } returns true

        commandSender.requestTraceroute(requestId = 0, destNum = DEST_NODE)

        val generatedId = packets.single().id
        assertNotEquals(0, generatedId)
        verify { tracerouteHandler.recordStartTime(generatedId) }
    }

    // --- requestNeighborInfo ---

    @Test
    fun requestNeighborInfo_generatesNonZeroIdWhenCallerSuppliesZero() = runTest {
        val packets = mutableListOf<MeshPacket>()
        everySuspend { packetHandler.sendToRadio(capture(packets)) } returns true

        commandSender.requestNeighborInfo(requestId = 0, destNum = DEST_NODE)

        val generatedId = packets.single().id
        assertNotEquals(0, generatedId)
        verify { neighborInfoHandler.recordStartTime(generatedId) }
    }

    @Test
    fun requestNeighborInfo_localNode_usesCachedNeighborInfo() = runTest {
        val cached = NeighborInfo(node_id = MY_NODE_NUM, last_sent_by_id = MY_NODE_NUM)
        every { neighborInfoHandler.lastNeighborInfo } returns cached
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns true

        commandSender.requestNeighborInfo(requestId = 1, destNum = MY_NODE_NUM)

        verifySuspend { packetHandler.sendToRadio(any<MeshPacket>()) }
        verify { neighborInfoHandler.recordStartTime(1) }
    }

    @Test
    fun requestNeighborInfo_localNode_generatesDummyWhenNoCached() = runTest {
        every { neighborInfoHandler.lastNeighborInfo } returns null
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns true

        commandSender.requestNeighborInfo(requestId = 1, destNum = MY_NODE_NUM)

        verifySuspend { packetHandler.sendToRadio(any<MeshPacket>()) }
        verify { neighborInfoHandler.recordStartTime(1) }
    }

    @Test
    fun requestNeighborInfo_remoteNode_sendsRequest() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns true

        commandSender.requestNeighborInfo(requestId = 1, destNum = DEST_NODE)

        verifySuspend { packetHandler.sendToRadio(any<MeshPacket>()) }
        verify { neighborInfoHandler.recordStartTime(1) }
    }

    @Test
    fun requestNeighborInfo_localNode_doesNotStartTimerWhenQueueRejectsPacket() = runTest {
        every { neighborInfoHandler.lastNeighborInfo } returns null
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns false

        assertFailsWith<PacketQueueRejectedException> {
            commandSender.requestNeighborInfo(requestId = 1, destNum = MY_NODE_NUM)
        }

        verify(exactly(0)) { neighborInfoHandler.recordStartTime(any()) }
    }

    @Test
    fun requestNeighborInfo_remoteNode_doesNotStartTimerWhenQueueRejectsPacket() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns false

        assertFailsWith<PacketQueueRejectedException> {
            commandSender.requestNeighborInfo(requestId = 1, destNum = DEST_NODE)
        }

        verify(exactly(0)) { neighborInfoHandler.recordStartTime(any()) }
    }

    // --- sendPosition ---

    @Test
    fun sendPosition_updatesLocalPositionWhenNotFixed() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns true

        val pos = org.meshtastic.proto.Position(latitude_i = 10000000, longitude_i = 20000000)
        commandSender.sendPosition(pos)

        verify { nodeManager.handleReceivedPosition(MY_NODE_NUM, MY_NODE_NUM, any(), any()) }
    }

    @Test
    fun sendPosition_doesNotUpdateLocalPositionWhenQueueRejectsPacket() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns false
        val pos = org.meshtastic.proto.Position(latitude_i = 10000000, longitude_i = 20000000)

        assertFailsWith<PacketQueueRejectedException> { commandSender.sendPosition(pos) }

        verify(exactly(0)) { nodeManager.handleReceivedPosition(any(), any(), any(), any()) }
    }

    @Test
    fun sendPosition_rejectsWhenLocalNodeIdentityIsUnavailable() = runTest {
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)

        assertFailsWith<LocalNodeUnavailableException> { commandSender.sendPosition(org.meshtastic.proto.Position()) }

        verifySuspend(exactly(0)) { packetHandler.sendToRadio(any<MeshPacket>()) }
    }

    @Test
    fun requestUserInfo_rejectsWhenLocalNodeRecordIsUnavailable() = runTest {
        assertFailsWith<LocalNodeUnavailableException> { commandSender.requestUserInfo(DEST_NODE) }

        verifySuspend(exactly(0)) { packetHandler.sendToRadio(any<MeshPacket>()) }
    }

    @Test
    fun setFixedPosition_rejectsBeforeDeviceMutationWhenLocalNodeIdentityIsUnavailable() = runTest {
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)

        assertFailsWith<LocalNodeUnavailableException> {
            commandSender.setFixedPosition(DEST_NODE, Position(latitude = 1.0, longitude = 2.0, altitude = 3))
        }

        verifySuspend(exactly(0)) { packetHandler.sendToRadio(any<MeshPacket>()) }
        verify(exactly(0)) { nodeManager.handleReceivedPosition(any(), any(), any(), any()) }
    }

    @Test
    fun setFixedPosition_doesNotUpdateLocalPositionWhenQueueRejectsPacket() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns false

        assertFailsWith<PacketQueueRejectedException> {
            commandSender.setFixedPosition(DEST_NODE, Position(latitude = 1.0, longitude = 2.0, altitude = 3))
        }

        verify(exactly(0)) { nodeManager.handleReceivedPosition(any(), any(), any(), any()) }
    }

    @Test
    fun setFixedPosition_doesNotProjectZeroPositionWhenRemovingIt() = runTest {
        val packets = mutableListOf<MeshPacket>()
        everySuspend { packetHandler.sendToRadio(capture(packets)) } returns true

        commandSender.setFixedPosition(
            DEST_NODE,
            Position(latitude = 0.0, longitude = 0.0, altitude = 0, time = 1, satellitesInView = 9),
        )

        val adminMessage = AdminMessage.ADAPTER.decode(requireNotNull(packets.single().decoded).payload)
        assertEquals(true, adminMessage.remove_fixed_position)
        verify(exactly(0)) { nodeManager.handleReceivedPosition(any(), any(), any(), any()) }
    }

    @Test
    fun sendPosition_skipsLocalUpdateWhenFixedPosition() = runTest {
        // Use MutableStateFlow so the init launchIn picks it up immediately in TestScope
        val configFlow =
            MutableStateFlow(LocalConfig(position = org.meshtastic.proto.Config.PositionConfig(fixed_position = true)))
        every { radioConfigRepository.localConfigFlow } returns configFlow
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        val testScope = TestScope()
        val fixedSender =
            CommandSenderImpl(
                packetHandler = packetHandler,
                nodeManager = nodeManager,
                radioConfigRepository = radioConfigRepository,
                tracerouteHandler = tracerouteHandler,
                neighborInfoHandler = neighborInfoHandler,
                sessionManager = sessionManager,
                scope = testScope.asServiceScope(),
            )
        testScope.testScheduler.advanceUntilIdle()
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns true

        val pos = org.meshtastic.proto.Position(latitude_i = 10000000, longitude_i = 20000000)
        fixedSender.sendPosition(pos)

        verify(mode = dev.mokkery.verify.VerifyMode.not) {
            nodeManager.handleReceivedPosition(any(), any(), any(), any())
        }
    }

    companion object {
        private const val MY_NODE_NUM = 100
        private const val DEST_NODE = 200
    }
}
