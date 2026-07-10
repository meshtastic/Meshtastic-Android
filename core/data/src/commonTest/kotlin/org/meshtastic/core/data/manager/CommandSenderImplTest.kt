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
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.SessionManager
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.PortNum
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
                scope = TestScope(),
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
                scope = TestScope(),
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
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns Unit

        commandSender.sendData(packet)
        assertNotEquals(0, packet.id)
    }

    @Test
    fun sendData_setsStatusQueued() = runTest {
        val packet = DataPacket(to = "^all", bytes = "hello".encodeUtf8(), dataType = PortNum.TEXT_MESSAGE_APP.value)
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns Unit

        commandSender.sendData(packet)
        assertEquals(MessageStatus.QUEUED, packet.status)
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
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns Unit

        commandSender.sendAdmin(DEST_NODE) { org.meshtastic.proto.AdminMessage(get_owner_request = true) }

        verifySuspend { packetHandler.sendToRadio(any<MeshPacket>()) }
    }

    @Test
    fun sendAdmin_licensedLocalWithKeys_usesSignedPlaintextPacket() = runTest {
        val packet = sendAdminAndCapture(localLicensed = true, localHasKey = true, destinationHasKey = true)

        assertPlaintextAdminPacket(packet, expectedDestination = DEST_NODE)
    }

    @Test
    fun sendAdmin_licensedMutation_usesPlaintextAndIncludesSessionPasskey() = runTest {
        val passkey = "session-passkey".encodeUtf8()
        every { sessionManager.getPasskey(DEST_NODE) } returns passkey

        val packet =
            sendAdminAndCapture(localLicensed = true, localHasKey = true, destinationHasKey = true) {
                AdminMessage(reboot_seconds = 5)
            }

        assertPlaintextAdminPacket(packet, expectedDestination = DEST_NODE)
        val adminMessage = AdminMessage.ADAPTER.decode(requireNotNull(packet.decoded).payload)
        assertEquals(5, adminMessage.reboot_seconds)
        assertEquals(passkey, adminMessage.session_passkey)
    }

    @Test
    fun sendAdmin_normalLocalWithKeys_preservesPkiPacket() = runTest {
        val packet = sendAdminAndCapture(localLicensed = false, localHasKey = true, destinationHasKey = true)

        assertEquals(0, packet.channel)
        assertTrue(packet.pki_encrypted)
        assertEquals(DESTINATION_PUBLIC_KEY, packet.public_key)
        assertAdminPacket(packet, expectedDestination = DEST_NODE)
    }

    @Test
    fun sendAdmin_self_preservesPlaintextChannelZero() = runTest {
        val nodes = mapOf(MY_NODE_NUM to node(MY_NODE_NUM, licensed = false, publicKey = LOCAL_PUBLIC_KEY))
        val packet = sendAdminAndCapture(destination = MY_NODE_NUM, nodes = nodes)

        assertPlaintextAdminPacket(packet, expectedDestination = MY_NODE_NUM)
    }

    @Test
    fun sendAdmin_unknownLocalMode_preservesAdminChannelFallback() = runTest {
        val nodes = mapOf(DEST_NODE to node(DEST_NODE, licensed = true))
        val packet = sendAdminAndCapture(nodes = nodes, channelSet = adminChannelSet())

        assertEquals(1, packet.channel)
        assertFalse(packet.pki_encrypted)
        assertEquals(ByteString.EMPTY, packet.public_key)
        assertAdminPacket(packet, expectedDestination = DEST_NODE)
    }

    @Test
    fun sendAdmin_destinationLicensed_doesNotChangeNormalPkiSelection() = runTest {
        val nodes =
            mapOf(
                MY_NODE_NUM to node(MY_NODE_NUM, licensed = false, publicKey = LOCAL_PUBLIC_KEY),
                DEST_NODE to node(DEST_NODE, licensed = true, publicKey = DESTINATION_PUBLIC_KEY),
            )
        val packet = sendAdminAndCapture(nodes = nodes)

        assertTrue(packet.pki_encrypted)
        assertEquals(DESTINATION_PUBLIC_KEY, packet.public_key)
    }

    @Test
    fun sendAdminAwait_licensedSessionRequest_usesSignedPlaintextPacket() = runTest {
        val nodes =
            mapOf(
                MY_NODE_NUM to node(MY_NODE_NUM, licensed = true, publicKey = LOCAL_PUBLIC_KEY),
                DEST_NODE to node(DEST_NODE, licensed = false, publicKey = DESTINATION_PUBLIC_KEY),
            )
        configureNodes(nodes)
        var capturedPacket: MeshPacket? = null
        everySuspend { packetHandler.sendToRadioAndAwait(any<MeshPacket>()) } calls
            { call ->
                capturedPacket = call.args[0] as MeshPacket
                true
            }

        val accepted =
            commandSender.sendAdminAwait(DEST_NODE, wantResponse = true) {
                AdminMessage(get_device_metadata_request = true)
            }

        assertTrue(accepted)
        assertPlaintextAdminPacket(requireNotNull(capturedPacket), expectedDestination = DEST_NODE)
        assertTrue(requireNotNull(capturedPacket).decoded?.want_response == true)
    }

    // --- requestTraceroute ---

    @Test
    fun requestTraceroute_recordsStartTime() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns Unit

        commandSender.requestTraceroute(requestId = 42, destNum = DEST_NODE)

        verify { tracerouteHandler.recordStartTime(42) }
    }

    // --- requestNeighborInfo ---

    @Test
    fun requestNeighborInfo_localNode_usesCachedNeighborInfo() = runTest {
        val cached = NeighborInfo(node_id = MY_NODE_NUM, last_sent_by_id = MY_NODE_NUM)
        every { neighborInfoHandler.lastNeighborInfo } returns cached
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns Unit

        commandSender.requestNeighborInfo(requestId = 1, destNum = MY_NODE_NUM)

        verifySuspend { packetHandler.sendToRadio(any<MeshPacket>()) }
    }

    @Test
    fun requestNeighborInfo_localNode_generatesDummyWhenNoCached() = runTest {
        every { neighborInfoHandler.lastNeighborInfo } returns null
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns Unit

        commandSender.requestNeighborInfo(requestId = 1, destNum = MY_NODE_NUM)

        verifySuspend { packetHandler.sendToRadio(any<MeshPacket>()) }
    }

    @Test
    fun requestNeighborInfo_remoteNode_sendsRequest() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns Unit

        commandSender.requestNeighborInfo(requestId = 1, destNum = DEST_NODE)

        verifySuspend { packetHandler.sendToRadio(any<MeshPacket>()) }
    }

    // --- sendPosition ---

    @Test
    fun sendPosition_updatesLocalPositionWhenNotFixed() = runTest {
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns Unit

        val pos = org.meshtastic.proto.Position(latitude_i = 10000000, longitude_i = 20000000)
        commandSender.sendPosition(pos)

        verify { nodeManager.handleReceivedPosition(MY_NODE_NUM, MY_NODE_NUM, any(), any()) }
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
                scope = testScope,
            )
        testScope.testScheduler.advanceUntilIdle()
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } returns Unit

        val pos = org.meshtastic.proto.Position(latitude_i = 10000000, longitude_i = 20000000)
        fixedSender.sendPosition(pos)

        verify(mode = dev.mokkery.verify.VerifyMode.not) {
            nodeManager.handleReceivedPosition(any(), any(), any(), any())
        }
    }

    private suspend fun sendAdminAndCapture(
        destination: Int = DEST_NODE,
        localLicensed: Boolean = false,
        localHasKey: Boolean = false,
        destinationHasKey: Boolean = false,
        nodes: Map<Int, Node> =
            mapOf(
                MY_NODE_NUM to
                    node(MY_NODE_NUM, licensed = localLicensed, publicKey = LOCAL_PUBLIC_KEY.takeIf { localHasKey }),
                DEST_NODE to node(DEST_NODE, publicKey = DESTINATION_PUBLIC_KEY.takeIf { destinationHasKey }),
            ),
        channelSet: ChannelSet = ChannelSet(),
        adminMessage: () -> AdminMessage = { AdminMessage(get_owner_request = true) },
    ): MeshPacket {
        configureNodes(nodes, channelSet)
        var capturedPacket: MeshPacket? = null
        everySuspend { packetHandler.sendToRadio(any<MeshPacket>()) } calls
            { call ->
                capturedPacket = call.args[0] as MeshPacket
            }

        commandSender.sendAdmin(destination, wantResponse = true, initFn = adminMessage)

        return requireNotNull(capturedPacket)
    }

    private fun configureNodes(nodes: Map<Int, Node>, channelSet: ChannelSet = ChannelSet()) {
        every { nodeManager.nodeDBbyNodeNum } returns nodes
        every { radioConfigRepository.channelSetFlow } returns flowOf(channelSet)
        val testScope = TestScope()
        commandSender =
            CommandSenderImpl(
                packetHandler = packetHandler,
                nodeManager = nodeManager,
                radioConfigRepository = radioConfigRepository,
                tracerouteHandler = tracerouteHandler,
                neighborInfoHandler = neighborInfoHandler,
                sessionManager = sessionManager,
                scope = testScope,
            )
        testScope.testScheduler.runCurrent()
    }

    private fun node(num: Int, licensed: Boolean = false, publicKey: ByteString? = null) =
        Node(num = num, user = User(is_licensed = licensed), publicKey = publicKey)

    private fun adminChannelSet() = ChannelSet(
        settings =
        listOf(
            org.meshtastic.proto.ChannelSettings(name = "primary"),
            org.meshtastic.proto.ChannelSettings(name = "admin"),
        ),
    )

    private fun assertPlaintextAdminPacket(packet: MeshPacket, expectedDestination: Int) {
        assertEquals(0, packet.channel)
        assertFalse(packet.pki_encrypted)
        assertEquals(ByteString.EMPTY, packet.public_key)
        assertAdminPacket(packet, expectedDestination)
    }

    private fun assertAdminPacket(packet: MeshPacket, expectedDestination: Int) {
        assertEquals(expectedDestination, packet.to)
        assertTrue(packet.want_ack)
        assertEquals(MeshPacket.Priority.RELIABLE, packet.priority)
        assertEquals(PortNum.ADMIN_APP, packet.decoded?.portnum)
    }

    companion object {
        private const val MY_NODE_NUM = 100
        private const val DEST_NODE = 200
        private val LOCAL_PUBLIC_KEY = "local-public-key".encodeUtf8()
        private val DESTINATION_PUBLIC_KEY = "destination-public-key".encodeUtf8()
    }
}
