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
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.ContactSettings
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.MeshDataMapper
import org.meshtastic.core.repository.AdminPacketHandler
import org.meshtastic.core.repository.MeshBeaconPrefs
import org.meshtastic.core.repository.MeshBeaconRepository
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MessageFilter
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionLease
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.StoreForwardPacketHandler
import org.meshtastic.core.repository.TelemetryPacketHandler
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshBeacon
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.Routing
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import org.meshtastic.proto.Waypoint
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class MeshDataHandlerTest {

    private lateinit var handler: MeshDataHandlerImpl
    private val nodeManager: NodeManager = mock(MockMode.autofill)
    private val packetHandler: PacketHandler = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val notificationManager: NotificationManager = mock(MockMode.autofill)
    private val serviceNotifications: MeshNotificationManager = mock(MockMode.autofill)
    private val analytics: PlatformAnalytics = mock(MockMode.autofill)
    private val dataMapper: MeshDataMapper = mock(MockMode.autofill)
    private val tracerouteHandler: TracerouteHandler = mock(MockMode.autofill)
    private val neighborInfoHandler: NeighborInfoHandler = mock(MockMode.autofill)
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val messageFilter: MessageFilter = mock(MockMode.autofill)
    private val storeForwardHandler: StoreForwardPacketHandler = mock(MockMode.autofill)
    private val telemetryHandler: TelemetryPacketHandler = mock(MockMode.autofill)
    private val adminPacketHandler: AdminPacketHandler = mock(MockMode.autofill)
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val session = RadioSessionContext(generation = 7L, address = "tcp:test")

    private fun testLease(session: RadioSessionContext): RadioSessionLease = object : RadioSessionLease {
        override val session: RadioSessionContext = session

        override fun isCurrent(): Boolean = true
    }

    private fun MeshDataHandlerImpl.handleReceivedData(packet: MeshPacket, myNodeNum: Int) {
        handleReceivedData(packet, myNodeNum, session)
    }

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Separate scope for the real GeofenceMonitor: its serial worker is a never-completing coroutine, so it must NOT
    // live in the runTest scope (that would trip UncompletedCoroutinesError). Shares the dispatcher so advanceUntilIdle
    // still drives it; cancelled in tearDown.
    private val geofenceScope = CoroutineScope(testDispatcher)

    // Real repository over an in-memory prefs fake — its persistence write-through is exercised without a DataStore.
    private val fakeBeaconPrefs =
        object : MeshBeaconPrefs {
            private val flow = MutableStateFlow<List<String>>(emptyList())
            override val storedBeacons: StateFlow<List<String>> = flow

            override fun setStoredBeacons(records: List<String>) {
                flow.value = records
            }
        }
    private val meshBeaconRepository = MeshBeaconRepository(fakeBeaconPrefs, geofenceScope)

    @AfterTest
    fun tearDown() {
        geofenceScope.cancel()
    }

    @BeforeTest
    fun setUp() {
        handler =
            MeshDataHandlerImpl(
                nodeManager = nodeManager,
                packetHandler = packetHandler,
                serviceStateWriter = serviceRepository,
                packetRepository = lazy { packetRepository },
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
                collectorRegistry = mock(MockMode.autofill),
                // GeofenceMonitor is a final @Single (mokkery can't mock it) — use a real one over mocked
                // collaborators. With no geofence-bearing waypoints emitted, onPositionReceived is a no-op.
                geofenceMonitor =
                GeofenceMonitor(
                    packetRepository = lazy { packetRepository },
                    nodeManager = nodeManager,
                    serviceNotifications = serviceNotifications,
                    crossingStore = GeofenceCrossingStore(),
                    notificationPrefs = FakeNotificationPrefs(),
                    radioInterfaceService = radioInterfaceService,
                    scope = geofenceScope,
                ),
                meshBeaconRepository = meshBeaconRepository,
                radioInterfaceService = radioInterfaceService,
                scope = testScope,
            )

        everySuspend { radioInterfaceService.runWithSessionLease(any(), any()) } calls
            {
                val requestedSession = it.args[0] as RadioSessionContext

                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as (suspend (RadioSessionLease) -> Unit)
                block(testLease(requestedSession))
                true
            }
        // Default: mapper returns null for empty packets, which is the safe default
        every { dataMapper.toDataPacket(any()) } returns null
        // Stub commonly accessed properties to avoid NPE from autofill
        every { nodeManager.getNodeById(any()) } returns null
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        // GeofenceMonitor collects this on init; stub it so the launched collector doesn't NPE on the test scope.
        every { packetRepository.getWaypoints() } returns emptyFlow()
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
                from = NodeAddress.numToDefaultId(myNodeNum),
                to = NodeAddress.ID_BROADCAST,
                bytes = position.encode().toByteString(),
                dataType = PortNum.POSITION_APP.value,
                time = 1000L,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)

        // Position from local node — no further action expected
    }

    @Test
    fun `handleReceivedData broadcasts for remote packets`() {
        val myNodeNum = 123
        val remoteNum = 456
        val packet = MeshPacket(from = remoteNum, decoded = Data(portnum = PortNum.PRIVATE_APP))
        val dataPacket =
            DataPacket(
                from = NodeAddress.numToDefaultId(remoteNum),
                to = NodeAddress.ID_BROADCAST,
                bytes = null,
                dataType = PortNum.PRIVATE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)
    }

    @Test
    fun `handleReceivedData tracks analytics`() {
        val packet = MeshPacket(from = 456, decoded = Data(portnum = PortNum.PRIVATE_APP))
        val dataPacket =
            DataPacket(
                from = "!other",
                to = NodeAddress.ID_BROADCAST,
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
                to = NodeAddress.ID_BROADCAST,
                bytes = position.encode().toByteString(),
                dataType = PortNum.POSITION_APP.value,
                time = 1000L,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)

        verify { nodeManager.handleReceivedPosition(remoteNum, myNodeNum, any(), 1000L, session) }
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
                to = NodeAddress.ID_BROADCAST,
                bytes = user.encode().toByteString(),
                dataType = PortNum.NODEINFO_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)

        verify { nodeManager.handleReceivedUser(remoteNum, any(), any(), any(), session) }
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
                to = NodeAddress.ID_BROADCAST,
                bytes = user.encode().toByteString(),
                dataType = PortNum.NODEINFO_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)

        verify(mode = dev.mokkery.verify.VerifyMode.not) {
            nodeManager.handleReceivedUser(any(), any(), any(), any(), any())
        }
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
                to = NodeAddress.ID_BROADCAST,
                bytes = pax.encode().toByteString(),
                dataType = PortNum.PAXCOUNTER_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { nodeManager.handleReceivedPaxcounter(remoteNum, any(), session) }
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

        verify { tracerouteHandler.handleTraceroute(packet, any(), any(), session) }
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
                to = NodeAddress.ID_BROADCAST,
                bytes = ni.encode().toByteString(),
                dataType = PortNum.NEIGHBORINFO_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { neighborInfoHandler.handleNeighborInfo(packet) }
    }

    // --- Mesh Beacon handling ---

    @Test
    fun `mesh beacon with a join offer is recorded as an invitation`() {
        val beacon = MeshBeacon(message = "Join us", offer_channel = ChannelSettings(name = "PartyNet"))
        val packet =
            MeshPacket(
                from = 456,
                decoded = Data(portnum = PortNum.MESH_BEACON_APP, payload = beacon.encode().toByteString()),
            )
        every { dataMapper.toDataPacket(packet) } returns
            DataPacket(
                from = "!remote",
                bytes = beacon.encode().toByteString(),
                dataType = PortNum.MESH_BEACON_APP.value,
            )

        handler.handleReceivedData(packet, 123)

        val offers = meshBeaconRepository.offers.value
        assertEquals(1, offers.size)
        assertEquals("Join us", offers.first().message)
        assertEquals("PartyNet", offers.first().channelName)
    }

    @Test
    fun `mesh beacon without a join offer is ignored`() {
        val beacon = MeshBeacon(message = "Just saying hi") // no offer_channel → not actionable
        val packet =
            MeshPacket(
                from = 456,
                decoded = Data(portnum = PortNum.MESH_BEACON_APP, payload = beacon.encode().toByteString()),
            )
        every { dataMapper.toDataPacket(packet) } returns
            DataPacket(
                from = "!remote",
                bytes = beacon.encode().toByteString(),
                dataType = PortNum.MESH_BEACON_APP.value,
            )

        handler.handleReceivedData(packet, 123)

        assertEquals(0, meshBeaconRepository.offers.value.size)
    }

    @Test
    fun `our own mesh beacon is ignored`() {
        // Spec FR-001: ignore beacons from the scanning node itself (else a listen+broadcast node self-notifies).
        val beacon = MeshBeacon(message = "Join us", offer_channel = ChannelSettings(name = "PartyNet"))
        val packet =
            MeshPacket(
                from = 123, // == myNodeNum below
                decoded = Data(portnum = PortNum.MESH_BEACON_APP, payload = beacon.encode().toByteString()),
            )
        every { dataMapper.toDataPacket(packet) } returns
            DataPacket(from = "!self", bytes = beacon.encode().toByteString(), dataType = PortNum.MESH_BEACON_APP.value)

        handler.handleReceivedData(packet, 123)

        assertEquals(0, meshBeaconRepository.offers.value.size)
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
                to = NodeAddress.ID_BROADCAST,
                bytes = byteArrayOf().toByteString(),
                dataType = PortNum.STORE_FORWARD_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { storeForwardHandler.handleStoreAndForward(packet, any(), 123, session) }
    }

    // --- Routing/ACK-NAK handling ---

    @Test
    fun `routing packet with successful ack broadcasts and removes response`() = testScope.runTest {
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
                to = NodeAddress.ID_BROADCAST,
                bytes = routing.encode().toByteString(),
                dataType = PortNum.ROUTING_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        every { nodeManager.toNodeID(456) } returns "!remote"

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend { packetHandler.removeResponse(99, complete = true) }
    }

    @Test
    fun `routing ack from a retired generation cannot update the replacement database`() = testScope.runTest {
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
                to = NodeAddress.ID_BROADCAST,
                bytes = routing.encode().toByteString(),
                dataType = PortNum.ROUTING_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        every { nodeManager.toNodeID(456) } returns "!remote"
        everySuspend { radioInterfaceService.runWithSessionLease(session, any()) } returns false

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend(exactly(0)) { packetRepository.getPacketByPacketId(any()) }
        verifySuspend(exactly(0)) { packetRepository.update(any(), any()) }
        verifySuspend(exactly(0)) { packetHandler.removeResponse(any(), any()) }
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
                to = NodeAddress.ID_BROADCAST,
                bytes = routing.encode().toByteString(),
                dataType = PortNum.ROUTING_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        every { nodeManager.toNodeID(456) } returns "!remote"

        handler.handleReceivedData(packet, 123)
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
                to = NodeAddress.ID_BROADCAST,
                bytes = telemetry.encode().toByteString(),
                dataType = PortNum.TELEMETRY_APP.value,
                time = 2000000L,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { telemetryHandler.handleTelemetry(packet, any(), 123, session) }
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
                to = NodeAddress.ID_BROADCAST,
                bytes = telemetry.encode().toByteString(),
                dataType = PortNum.TELEMETRY_APP.value,
                time = 2000000L,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, myNodeNum)

        verify { telemetryHandler.handleTelemetry(packet, any(), myNodeNum, session) }
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
                to = NodeAddress.ID_BROADCAST,
                bytes = "hello".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        everySuspend { packetRepository.findPacketsWithId(42) } returns emptyList()
        everySuspend { packetRepository.getContactSettings(any()) } returns ContactSettings(contactKey = "test")
        every { messageFilter.shouldFilter(any(), any()) } returns false
        // Provide sender node so getSenderName() doesn't fall back to getString (requires Skiko)
        every { nodeManager.getNodeById("!remote") } returns
            Node(num = 456, user = User(id = "!remote", long_name = "Remote User", short_name = "RU"))

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend { packetRepository.insert(any(), 123, any(), any(), any(), any()) }
    }

    @Test
    fun `text persistence from a retired session is rejected before database access`() = testScope.runTest {
        val packet =
            MeshPacket(
                id = 45,
                from = 456,
                decoded =
                Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = "late".encodeToByteArray().toByteString()),
            )
        val dataPacket =
            DataPacket(
                id = 45,
                from = "!remote",
                to = NodeAddress.ID_BROADCAST,
                bytes = "late".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        everySuspend { radioInterfaceService.runWithSessionLease(session, any()) } returns false

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend(exactly(0)) { packetRepository.findPacketsWithId(any()) }
        verifySuspend(exactly(0)) { packetRepository.insert(any(), any(), any(), any(), any(), any()) }
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
                to = NodeAddress.ID_BROADCAST,
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
                to = NodeAddress.ID_BROADCAST,
                bytes = "test".encodeToByteArray().toByteString(),
                dataType = PortNum.RANGE_TEST_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        everySuspend { packetRepository.findPacketsWithId(55) } returns emptyList()
        everySuspend { packetRepository.getContactSettings(any()) } returns ContactSettings(contactKey = "test")
        every { messageFilter.shouldFilter(any(), any()) } returns false
        every { nodeManager.getNodeById("!remote") } returns
            Node(num = 456, user = User(id = "!remote", long_name = "Remote User", short_name = "RU"))

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
                to = NodeAddress.ID_BROADCAST,
                bytes = admin.encode().toByteString(),
                dataType = PortNum.ADMIN_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket

        handler.handleReceivedData(packet, 123)

        verify { adminPacketHandler.handleAdminMessage(packet, 123, session) }
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
                to = NodeAddress.ID_BROADCAST,
                bytes = "spam content".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        everySuspend { packetRepository.findPacketsWithId(77) } returns emptyList()
        every { nodeManager.getNodeById(any()) } returns null
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
                to = NodeAddress.ID_BROADCAST,
                bytes = "hello".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        everySuspend { packetRepository.findPacketsWithId(88) } returns emptyList()
        every { nodeManager.getNodeById("!remote") } returns
            Node(num = 456, user = User(id = "!remote"), isIgnored = true)
        everySuspend { packetRepository.getContactSettings(any()) } returns ContactSettings(contactKey = "test")

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend { packetRepository.insert(any(), 123, any(), any(), any(), filtered = true) }
    }

    // --- Mention / mute interaction (meshtastic/design#21) ---

    private val myId = "!abcd1234"

    private fun mentionPacket() = MeshPacket(
        id = 101,
        from = 456,
        decoded =
        Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = "hey @$myId".encodeToByteArray().toByteString()),
    )

    private fun mentionDataPacket() = DataPacket(
        id = 101,
        from = "!remote",
        to = NodeAddress.ID_BROADCAST,
        bytes = "hey @$myId".encodeToByteArray().toByteString(),
        dataType = PortNum.TEXT_MESSAGE_APP.value,
    )

    @Test
    fun `mention from a muted node does not notify`() = testScope.runTest {
        val packet = mentionPacket()
        every { dataMapper.toDataPacket(packet) } returns mentionDataPacket()
        everySuspend { packetRepository.findPacketsWithId(101) } returns emptyList()
        everySuspend { packetRepository.getContactSettings(any()) } returns ContactSettings(contactKey = "test")
        every { messageFilter.shouldFilter(any(), any()) } returns false
        every { nodeManager.getMyId() } returns myId
        // Node mute is authoritative: a mention must NOT break through it.
        every { nodeManager.getNodeById("!remote") } returns
            Node(num = 456, user = User(id = "!remote", long_name = "Remote User"), isMuted = true)

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend(mode = dev.mokkery.verify.VerifyMode.not) {
            serviceNotifications.updateMessageNotification(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `mention from an unmuted node in a muted channel still notifies`() = testScope.runTest {
        val packet = mentionPacket()
        every { dataMapper.toDataPacket(packet) } returns mentionDataPacket()
        everySuspend { packetRepository.findPacketsWithId(101) } returns emptyList()
        // Channel/conversation muted — a mention breaks through it (design#21).
        everySuspend { packetRepository.getContactSettings(any()) } returns
            ContactSettings(contactKey = "test", isMuted = true)
        every { messageFilter.shouldFilter(any(), any()) } returns false
        every { nodeManager.getMyId() } returns myId
        every { nodeManager.getNodeById("!remote") } returns
            Node(num = 456, user = User(id = "!remote", long_name = "Remote User"))

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend {
            serviceNotifications.updateMessageNotification(any(), any(), any(), any(), any(), isSilent = false)
        }
    }

    // --- Waypoint persisted-owner enforcement ---
    //
    // A locked waypoint (locked_to != 0) may only be modified by the node it is locked to. The inbound-payload check
    // alone (locked_to == from) cannot enforce this: a non-owner can still replay an existing id with locked_to = 0
    // (unlock) or their own num (takeover). handleWaypoint additionally consults the currently-stored owner.

    private fun waypointPacket(txId: Int, from: Int, waypoint: Waypoint): MeshPacket {
        val payload = waypoint.encode().toByteString()
        val packet =
            MeshPacket(id = txId, from = from, decoded = Data(portnum = PortNum.WAYPOINT_APP, payload = payload))
        val dataPacket =
            DataPacket(
                id = txId,
                from = NodeAddress.numToDefaultId(from),
                to = NodeAddress.ID_BROADCAST,
                bytes = payload,
                dataType = PortNum.WAYPOINT_APP.value,
            )
        every { dataMapper.toDataPacket(packet) } returns dataPacket
        return packet
    }

    /** Persist a single stored waypoint (via the getWaypoints firehose) so handleWaypoint can read its owner. */
    private fun storeWaypoint(id: Int, lockedTo: Int) {
        val stored =
            DataPacket(to = NodeAddress.ID_BROADCAST, channel = 0, waypoint = Waypoint(id = id, locked_to = lockedTo))
        every { packetRepository.getWaypoints() } returns flowOf(listOf(stored))
    }

    private fun stubWaypointPersistDependencies(txId: Int) {
        everySuspend { packetRepository.findPacketsWithId(txId) } returns emptyList()
        everySuspend { packetRepository.getContactSettings(any()) } returns ContactSettings(contactKey = "test")
        every { messageFilter.shouldFilter(any(), any()) } returns false
    }

    @Test
    fun `non-owner unlock replay of a locked waypoint is dropped`() = testScope.runTest {
        storeWaypoint(id = 42, lockedTo = 111)
        // Mallory (999) replays waypoint 42 with locked_to = 0 (unlock). The inbound check passes, so only the
        // stored-owner check can drop it.
        val packet = waypointPacket(txId = 500, from = 999, waypoint = Waypoint(id = 42, locked_to = 0))
        stubWaypointPersistDependencies(500)

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend(exactly(0)) { packetRepository.insert(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `non-owner takeover of a locked waypoint is dropped`() = testScope.runTest {
        storeWaypoint(id = 42, lockedTo = 111)
        // Mallory (999) locks waypoint 42 to herself. The inbound check passes (locked_to == from), so only the
        // stored-owner check can catch this.
        val packet = waypointPacket(txId = 501, from = 999, waypoint = Waypoint(id = 42, locked_to = 999))
        stubWaypointPersistDependencies(501)

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend(exactly(0)) { packetRepository.insert(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `owner unlock of their own locked waypoint is accepted`() = testScope.runTest {
        storeWaypoint(id = 42, lockedTo = 111)
        val packet = waypointPacket(txId = 502, from = 111, waypoint = Waypoint(id = 42, locked_to = 0))
        stubWaypointPersistDependencies(502)

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend { packetRepository.insert(any(), 123, any(), any(), any(), any()) }
    }

    @Test
    fun `owner edit of their own locked waypoint is accepted`() = testScope.runTest {
        storeWaypoint(id = 42, lockedTo = 111)
        val packet = waypointPacket(txId = 503, from = 111, waypoint = Waypoint(id = 42, locked_to = 111))
        stubWaypointPersistDependencies(503)

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend { packetRepository.insert(any(), 123, any(), any(), any(), any()) }
    }

    @Test
    fun `new waypoint from a non-owner is accepted when none is stored`() = testScope.runTest {
        // Nothing persisted yet: getWaypoints() emits an empty list (as Room does). A creation, not a hijack.
        every { packetRepository.getWaypoints() } returns flowOf(emptyList())
        val packet = waypointPacket(txId = 504, from = 999, waypoint = Waypoint(id = 42, locked_to = 0))
        stubWaypointPersistDependencies(504)

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend { packetRepository.insert(any(), 123, any(), any(), any(), any()) }
    }

    @Test
    fun `non-owner update to an unlocked waypoint is accepted`() = testScope.runTest {
        storeWaypoint(id = 42, lockedTo = 0)
        val packet = waypointPacket(txId = 505, from = 999, waypoint = Waypoint(id = 42, locked_to = 0))
        stubWaypointPersistDependencies(505)

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend { packetRepository.insert(any(), 123, any(), any(), any(), any()) }
    }

    @Test
    fun `waypoint locked to someone other than the sender is dropped`() = testScope.runTest {
        // Pre-existing inbound-payload rule: a node can only lock a waypoint to itself. Nothing stored here — the
        // payload itself is invalid, so it is rejected before any repository read.
        val packet = waypointPacket(txId = 506, from = 999, waypoint = Waypoint(id = 42, locked_to = 111))
        stubWaypointPersistDependencies(506)

        handler.handleReceivedData(packet, 123)
        advanceUntilIdle()

        verifySuspend(exactly(0)) { packetRepository.insert(any(), any(), any(), any(), any(), any()) }
    }
}
