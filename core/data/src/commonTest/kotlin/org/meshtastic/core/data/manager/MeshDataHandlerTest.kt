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
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.ContactSettings
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.MeshDataMapper
import org.meshtastic.core.repository.AdminPacketHandler
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.MessageFilter
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.StoreForwardPacketHandler
import org.meshtastic.core.repository.TelemetryPacketHandler
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.Routing
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class MeshDataHandlerTest {

    private lateinit var handler: MeshDataHandlerImpl
    private val nodeManager: NodeManager = mock(MockMode.autofill)
    private val packetHandler: PacketHandler = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val serviceBroadcasts: ServiceBroadcasts = mock(MockMode.autofill)
    private val notificationManager: NotificationManager = mock(MockMode.autofill)
    private val serviceNotifications: MeshServiceNotifications = mock(MockMode.autofill)
    private val analytics: PlatformAnalytics = mock(MockMode.autofill)
    private val dataMapper: MeshDataMapper = mock(MockMode.autofill)
    private val tracerouteHandler: TracerouteHandler = mock(MockMode.autofill)
    private val neighborInfoHandler: NeighborInfoHandler = mock(MockMode.autofill)
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val messageFilter: MessageFilter = mock(MockMode.autofill)
    private val storeForwardHandler: StoreForwardPacketHandler = mock(MockMode.autofill)
    private val telemetryHandler: TelemetryPacketHandler = mock(MockMode.autofill)
    private val adminPacketHandler: AdminPacketHandler = mock(MockMode.autofill)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setUp() {
        handler =
            MeshDataHandlerImpl(
                nodeManager = nodeManager,
                packetHandler = packetHandler,
                serviceRepository = serviceRepository,
                packetRepository = lazy { packetRepository },
                serviceBroadcasts = serviceBroadcasts,
                notificationManager = notificationManager,
                serviceNotifications = serviceNotifications,
                analytics = analytics,
                dataMapper = dataMapper,
                tracerouteHandler = tracerouteHandler,
                neighborInfoHandler = neighborInfoHandler,
                radioConfigRepository = radioConfigRepository,
                messageFilter = messageFilter,
                storeForwardHandler = storeForwardHandler,
                telemetryHandler = telemetryHandler,
                adminPacketHandler = adminPacketHandler,
                scope = testScope,
            )

        // Default: mapper returns null for empty packets, which is the safe default
        every { dataMapper.toDataPacket(any()) } returns null
        // Stub commonly accessed properties to avoid NPE from autofill
        every { nodeManager.nodeDBbyID } returns emptyMap()
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
    }

    @Test
    fun testInitialization() {
        assertNotNull(handler)
    }

    @Test
    fun `handleReceivedData returns early when dataMapper returns null`() {
        val packet = MeshPacket()
        every { dataMapper.toDataPacket(packet) } returns null

        handler.handleReceivedData(packet, 123)

        // Should not broadcast if dataMapper returns null
        verify(mode = dev.mokkery.verify.VerifyMode.not) { serviceBroadcasts.broadcastReceivedData(any()) }
    }

    @Test
    fun `handleReceivedData does not broadcast for position from local node`() {
        val myNodeNum = 123
        val position = Position(latitude_i = 450000000, longitude_i = 900000000)
        val packet =
            MeshPacket(
                from = myNodeNum,
                decoded = Data(portnum = PortNum.POSITION_APP, payload = position.encode().toByteString()),
            )
        val dataPacket =
            DataPacket(
                from = DataPacket.nodeNumToDefaultId(myNodeNum),
                to = DataPacket.ID_BROADCAST,
                bytes = position.encode().toByteString(),
                dataType = PortNum.POSITION_APP.value,
                time = 1000L,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)

        // Position from local node: shouldBroadcast stays as !fromUs = false
        verify(mode = dev.mokkery.verify.VerifyMode.not) { serviceBroadcasts.broadcastReceivedData(any()) }
    }

    @Test
    fun `handleReceivedData broadcasts for remote packets`() {
        val myNodeNum = 123
        val remoteNum = 456
        val packet = MeshPacket(from = remoteNum, decoded = Data(portnum = PortNum.PRIVATE_APP))
        val dataPacket =
            DataPacket(
                from = DataPacket.nodeNumToDefaultId(remoteNum),
                to = DataPacket.ID_BROADCAST,
                bytes = null,
                dataType = PortNum.PRIVATE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)

        verify { serviceBroadcasts.broadcastReceivedData(any()) }
    }

    @Test
    fun `handleReceivedData tracks analytics`() {
        val packet = MeshPacket(from = 456, decoded = Data(portnum = PortNum.PRIVATE_APP))
        val dataPacket =
            DataPacket(
                from = "!other",
                to = DataPacket.ID_BROADCAST,
                bytes = null,
                dataType = PortNum.PRIVATE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { analytics.track("num_data_receive", any()) }
    }

    // --- Position handling ---

    @Test
    fun `position packet delegates to nodeManager`() {
        val myNodeNum = 123
        val remoteNum = 456
        val position = Position(latitude_i = 450000000, longitude_i = 900000000)
        val packet =
            MeshPacket(
                from = remoteNum,
                decoded = Data(portnum = PortNum.POSITION_APP, payload = position.encode().toByteString()),
            )
        val dataPacket =
            DataPacket(
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = position.encode().toByteString(),
                dataType = PortNum.POSITION_APP.value,
                time = 1000L,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)

        verify { nodeManager.handleReceivedPosition(remoteNum, myNodeNum, any(), 1000L) }
    }

    // --- NodeInfo handling ---

    @Test
    fun `nodeinfo packet from remote delegates to handleReceivedUser`() {
        val myNodeNum = 123
        val remoteNum = 456
        val user = User(id = "!remote", long_name = "Remote", short_name = "R")
        val packet =
            MeshPacket(
                from = remoteNum,
                decoded = Data(portnum = PortNum.NODEINFO_APP, payload = user.encode().toByteString()),
            )
        val dataPacket =
            DataPacket(
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = user.encode().toByteString(),
                dataType = PortNum.NODEINFO_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)

        verify { nodeManager.handleReceivedUser(remoteNum, any(), any(), any()) }
    }

    @Test
    fun `nodeinfo packet from local node is ignored`() {
        val myNodeNum = 123
        val user = User(id = "!local", long_name = "Local", short_name = "L")
        val packet =
            MeshPacket(
                from = myNodeNum,
                decoded = Data(portnum = PortNum.NODEINFO_APP, payload = user.encode().toByteString()),
            )
        val dataPacket =
            DataPacket(
                from = "!local",
                to = DataPacket.ID_BROADCAST,
                bytes = user.encode().toByteString(),
                dataType = PortNum.NODEINFO_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)

        verify(mode = dev.mokkery.verify.VerifyMode.not) { nodeManager.handleReceivedUser(any(), any(), any(), any()) }
    }

    // --- Paxcounter handling ---

    @Test
    fun `paxcounter packet delegates to nodeManager`() {
        val remoteNum = 456
        val pax = Paxcount(wifi = 10, ble = 5, uptime = 1000)
        val packet =
            MeshPacket(
                from = remoteNum,
                decoded = Data(portnum = PortNum.PAXCOUNTER_APP, payload = pax.encode().toByteString()),
            )
        val dataPacket =
            DataPacket(
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = pax.encode().toByteString(),
                dataType = PortNum.PAXCOUNTER_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { nodeManager.handleReceivedPaxcounter(remoteNum, any()) }
    }

    // --- Traceroute handling ---

    @Test
    fun `traceroute packet delegates to tracerouteHandler and suppresses broadcast`() {
        val packet =
            MeshPacket(
                from = 456,
                decoded = Data(portnum = PortNum.TRACEROUTE_APP, payload = byteArrayOf().toByteString()),
            )
        val dataPacket =
            DataPacket(
                from = "!remote",
                to = "!local",
                bytes = byteArrayOf().toByteString(),
                dataType = PortNum.TRACEROUTE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { tracerouteHandler.handleTraceroute(packet, any(), any()) }
        verify(mode = dev.mokkery.verify.VerifyMode.not) { serviceBroadcasts.broadcastReceivedData(any()) }
    }

    // --- NeighborInfo handling ---

    @Test
    fun `neighborinfo packet delegates to neighborInfoHandler and broadcasts`() {
        val ni = NeighborInfo(node_id = 456)
        val packet =
            MeshPacket(
                from = 456,
                decoded = Data(portnum = PortNum.NEIGHBORINFO_APP, payload = ni.encode().toByteString()),
            )
        val dataPacket =
            DataPacket(
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = ni.encode().toByteString(),
                dataType = PortNum.NEIGHBORINFO_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { neighborInfoHandler.handleNeighborInfo(packet) }
        verify { serviceBroadcasts.broadcastReceivedData(any()) }
    }

    // --- Store-and-Forward handling ---

    @Test
    fun `store forward packet delegates to storeForwardHandler`() {
        val packet =
            MeshPacket(
                from = 456,
                decoded = Data(portnum = PortNum.STORE_FORWARD_APP, payload = byteArrayOf().toByteString()),
            )
        val dataPacket =
            DataPacket(
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = byteArrayOf().toByteString(),
                dataType = PortNum.STORE_FORWARD_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { storeForwardHandler.handleStoreAndForward(packet, any(), 123) }
    }

    // --- Routing/ACK-NAK handling ---

    @Test
    fun `routing packet with successful ack broadcasts and removes response`() {
        val routing = Routing(error_reason = Routing.Error.NONE)
        val packet =
            MeshPacket(
                from = 456,
                decoded =
                Data(portnum = PortNum.ROUTING_APP, payload = routing.encode().toByteString(), request_id = 99),
            )
        val dataPacket =
            DataPacket(
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = routing.encode().toByteString(),
                dataType = PortNum.ROUTING_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        every { nodeManager.toNodeID(456) } returns "!remote"

        handler.handleReceivedData(packet, 123)

        verify { packetHandler.removeResponse(99, complete = true) }
    }

    @Test
    fun `routing packet always broadcasts`() {
        val routing = Routing(error_reason = Routing.Error.NONE)
        val packet =
            MeshPacket(
                from = 456,
                decoded =
                Data(portnum = PortNum.ROUTING_APP, payload = routing.encode().toByteString(), request_id = 99),
            )
        val dataPacket =
            DataPacket(
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = routing.encode().toByteString(),
                dataType = PortNum.ROUTING_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        every { nodeManager.toNodeID(456) } returns "!remote"

        handler.handleReceivedData(packet, 123)

        verify { serviceBroadcasts.broadcastReceivedData(any()) }
    }

    // --- Telemetry handling ---

    @Test
    fun `telemetry packet delegates to telemetryHandler`() {
        val telemetry =
            Telemetry(
                time = 2000,
                device_metrics = org.meshtastic.proto.DeviceMetrics(battery_level = 80, voltage = 4.0f),
            )
        val packet =
            MeshPacket(
                from = 456,
                decoded = Data(portnum = PortNum.TELEMETRY_APP, payload = telemetry.encode().toByteString()),
            )
        val dataPacket =
            DataPacket(
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = telemetry.encode().toByteString(),
                dataType = PortNum.TELEMETRY_APP.value,
                time = 2000000L,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { telemetryHandler.handleTelemetry(packet, any(), 123) }
    }

    @Test
    fun `telemetry from local node delegates to telemetryHandler`() {
        val myNodeNum = 123
        val telemetry =
            Telemetry(
                time = 2000,
                device_metrics = org.meshtastic.proto.DeviceMetrics(battery_level = 80, voltage = 4.0f),
            )
        val packet =
            MeshPacket(
                from = myNodeNum,
                decoded = Data(portnum = PortNum.TELEMETRY_APP, payload = telemetry.encode().toByteString()),
            )
        val dataPacket =
            DataPacket(
                from = "!local",
                to = DataPacket.ID_BROADCAST,
                bytes = telemetry.encode().toByteString(),
                dataType = PortNum.TELEMETRY_APP.value,
                time = 2000000L,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)

        verify { telemetryHandler.handleTelemetry(packet, any(), myNodeNum) }
    }

    // --- Text message handling ---

    @Test
    fun `text message is persisted via rememberDataPacket`() = testScope.runTest {
        val packet =
            MeshPacket(
                id = 42,
                from = 456,
                decoded =
                Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = "hello".encodeToByteArray().toByteString()),
            )
        val dataPacket =
            DataPacket(
                id = 42,
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = "hello".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        everySuspend { packetRepository.findPacketsWithId(42) } returns emptyList()
        everySuspend { packetRepository.getContactSettings(any()) } returns ContactSettings(contactKey = "test")
        every { messageFilter.shouldFilter(any(), any()) } returns false
        // Provide sender node so getSenderName() doesn't fall back to getString (requires Skiko)
        every { nodeManager.nodeDBbyID } returns
            mapOf(
                "!remote" to
                    Node(num = 456, user = User(id = "!remote", long_name = "Remote User", short_name = "RU")),
            )

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend { packetRepository.insert(any(), 123, any(), any(), any(), any()) }
    }

    @Test
    fun `duplicate text message is not inserted again`() = testScope.runTest {
        val packet =
            MeshPacket(
                id = 42,
                from = 456,
                decoded =
                Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = "hello".encodeToByteArray().toByteString()),
            )
        val dataPacket =
            DataPacket(
                id = 42,
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = "hello".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        // Return existing packet on duplicate check
        everySuspend { packetRepository.findPacketsWithId(42) } returns listOf(dataPacket)

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend(mode = dev.mokkery.verify.VerifyMode.not) {
            packetRepository.insert(any(), any(), any(), any(), any(), any())
        }
    }

    // --- Reaction handling ---

    @Test
    fun `text with reply_id and emoji is treated as reaction`() = testScope.runTest {
        val emojiBytes = "👍".encodeToByteArray()
        val packet =
            MeshPacket(
                id = 99,
                from = 456,
                to = 123,
                decoded =
                Data(
                    portnum = PortNum.TEXT_MESSAGE_APP,
                    payload = emojiBytes.toByteString(),
                    reply_id = 42,
                    emoji = 1,
                ),
            )
        val dataPacket =
            DataPacket(
                id = 99,
                from = "!remote",
                to = "!local",
                bytes = emojiBytes.toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        every { nodeManager.nodeDBbyNodeNum } returns
            mapOf(
                456 to Node(num = 456, user = User(id = "!remote")),
                123 to Node(num = 123, user = User(id = "!local")),
            )
        everySuspend { packetRepository.findReactionsWithId(99) } returns emptyList()
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        everySuspend { packetRepository.getPacketByPacketId(42) } returns null

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend { packetRepository.insertReaction(any(), 123) }
    }

    // --- Range test / detection sensor handling ---

    @Test
    fun `range test packet is remembered as text message type`() = testScope.runTest {
        val packet =
            MeshPacket(
                id = 55,
                from = 456,
                decoded =
                Data(portnum = PortNum.RANGE_TEST_APP, payload = "test".encodeToByteArray().toByteString()),
            )
        val dataPacket =
            DataPacket(
                id = 55,
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = "test".encodeToByteArray().toByteString(),
                dataType = PortNum.RANGE_TEST_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        everySuspend { packetRepository.findPacketsWithId(55) } returns emptyList()
        everySuspend { packetRepository.getContactSettings(any()) } returns ContactSettings(contactKey = "test")
        every { messageFilter.shouldFilter(any(), any()) } returns false
        every { nodeManager.nodeDBbyID } returns
            mapOf(
                "!remote" to
                    Node(num = 456, user = User(id = "!remote", long_name = "Remote User", short_name = "RU")),
            )

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        // Range test should be remembered with TEXT_MESSAGE_APP dataType
        verifySuspend { packetRepository.insert(any(), 123, any(), any(), any(), any()) }
    }

    // --- Admin message handling ---

    @Test
    fun `admin message delegates to adminPacketHandler`() {
        val admin = org.meshtastic.proto.AdminMessage(session_passkey = okio.ByteString.of(1, 2, 3))
        val packet =
            MeshPacket(from = 123, decoded = Data(portnum = PortNum.ADMIN_APP, payload = admin.encode().toByteString()))
        val dataPacket =
            DataPacket(
                from = "!local",
                to = DataPacket.ID_BROADCAST,
                bytes = admin.encode().toByteString(),
                dataType = PortNum.ADMIN_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { adminPacketHandler.handleAdminMessage(packet, 123) }
    }

    // --- Message filtering ---

    @Test
    fun `filtered message is inserted with filtered flag`() = testScope.runTest {
        val packet =
            MeshPacket(
                id = 77,
                from = 456,
                decoded =
                Data(
                    portnum = PortNum.TEXT_MESSAGE_APP,
                    payload = "spam content".encodeToByteArray().toByteString(),
                ),
            )
        val dataPacket =
            DataPacket(
                id = 77,
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = "spam content".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        everySuspend { packetRepository.findPacketsWithId(77) } returns emptyList()
        every { nodeManager.nodeDBbyID } returns emptyMap()
        everySuspend { packetRepository.getContactSettings(any()) } returns ContactSettings(contactKey = "test")
        every { messageFilter.shouldFilter("spam content", false) } returns true

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        // Verify insert was called with filtered = true (6th param)
        verifySuspend { packetRepository.insert(any(), 123, any(), any(), any(), filtered = true) }
    }

    @Test
    fun `message from ignored node is filtered`() = testScope.runTest {
        val packet =
            MeshPacket(
                id = 88,
                from = 456,
                decoded =
                Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = "hello".encodeToByteArray().toByteString()),
            )
        val dataPacket =
            DataPacket(
                id = 88,
                from = "!remote",
                to = DataPacket.ID_BROADCAST,
                bytes = "hello".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        everySuspend { packetRepository.findPacketsWithId(88) } returns emptyList()
        every { nodeManager.nodeDBbyID } returns
            mapOf("!remote" to Node(num = 456, user = User(id = "!remote"), isIgnored = true))
        everySuspend { packetRepository.getContactSettings(any()) } returns ContactSettings(contactKey = "test")

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend { packetRepository.insert(any(), 123, any(), any(), any(), filtered = true) }
    }
}
