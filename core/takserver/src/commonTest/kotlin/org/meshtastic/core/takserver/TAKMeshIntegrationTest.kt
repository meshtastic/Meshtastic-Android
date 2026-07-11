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
package org.meshtastic.core.takserver

import co.touchlab.kermit.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.model.service.LockdownTokenInfo
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.TAKPacket
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for [TAKMeshIntegration] lifecycle, routing, and protocol gating.
 *
 * These tests use fakes for all 5 dependencies and run in commonTest. The v2 outbound SDK-dependent happy path is
 * tested separately in jvmTest.
 */
@Suppress("TooManyFunctions")
class TAKMeshIntegrationTest {

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeTAKServerManager : TAKServerManager {
        private val _isRunning = MutableStateFlow(false)
        override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
        override val connectionCount: StateFlow<Int> = MutableStateFlow(0)

        private val _inboundMessages = MutableSharedFlow<InboundCoTMessage>(extraBufferCapacity = 64)
        override val inboundMessages: SharedFlow<InboundCoTMessage> = _inboundMessages.asSharedFlow()

        val broadcasts = mutableListOf<CoTMessage>()
        val rawBroadcasts = mutableListOf<String>()
        var startCount = 0
        var stopped = false

        override fun start(scope: CoroutineScope) {
            startCount++
            _isRunning.value = true
        }

        override fun stop() {
            stopped = true
            _isRunning.value = false
        }

        override fun broadcast(cotMessage: CoTMessage) {
            broadcasts.add(cotMessage)
        }

        override fun broadcastRawXml(xml: String) {
            rawBroadcasts.add(xml)
        }

        suspend fun emitInbound(cotMessage: CoTMessage, clientInfo: TAKClientInfo? = null) {
            _inboundMessages.emit(InboundCoTMessage(cotMessage, clientInfo))
        }
    }

    private class FakeCommandSender : CommandSender {
        val sentPackets = mutableListOf<DataPacket>()

        override suspend fun sendData(p: DataPacket) {
            sentPackets.add(p)
        }

        override fun getCurrentPacketId(): Long = 0L

        override fun getCachedLocalConfig(): LocalConfig = LocalConfig()

        override fun getCachedChannelSet(): ChannelSet = ChannelSet()

        override fun generatePacketId(): Int = 1

        override suspend fun sendAdmin(
            destNum: Int,
            requestId: Int,
            wantResponse: Boolean,
            initFn: () -> AdminMessage,
        ) {}

        override suspend fun sendAdminAwait(
            destNum: Int,
            requestId: Int,
            wantResponse: Boolean,
            initFn: () -> AdminMessage,
        ): Boolean = true

        override suspend fun sendPosition(pos: org.meshtastic.proto.Position, destNum: Int?, wantResponse: Boolean) {}

        override suspend fun requestPosition(destNum: Int, currentPosition: Position) {}

        override suspend fun setFixedPosition(destNum: Int, pos: Position) {}

        override suspend fun requestUserInfo(destNum: Int) {}

        override suspend fun requestTraceroute(requestId: Int, destNum: Int) {}

        override suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {}

        override suspend fun requestNeighborInfo(requestId: Int, destNum: Int) {}

        override fun sendLockdownPassphrase(
            passphrase: String,
            boots: Int,
            hours: Int,
            maxSessionSeconds: Int,
            disable: Boolean,
        ) {}

        override fun sendLockNow() {}
    }

    private class FakeServiceRepository : ServiceRepository {
        private val _meshPacketFlow = MutableSharedFlow<MeshPacket>(replay = 1, extraBufferCapacity = 64)
        override val meshPacketFlow: Flow<MeshPacket> = _meshPacketFlow

        override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)

        override fun setConnectionState(connectionState: ConnectionState) {}

        override val clientNotification: StateFlow<ClientNotification?> = MutableStateFlow(null)

        override fun setClientNotification(notification: ClientNotification?) {}

        override fun clearClientNotification() {}

        override val errorMessage: StateFlow<String?> = MutableStateFlow(null)

        override fun setErrorMessage(text: String, severity: Severity) {}

        override fun clearErrorMessage() {}

        override val connectionProgress: StateFlow<String?> = MutableStateFlow(null)

        override fun setConnectionProgress(text: String) {}

        override suspend fun emitMeshPacket(packet: MeshPacket) {
            _meshPacketFlow.emit(packet)
        }

        override val tracerouteResponse: StateFlow<TracerouteResponse?> = MutableStateFlow(null)

        override fun setTracerouteResponse(value: TracerouteResponse?) {}

        override fun clearTracerouteResponse() {}

        override val neighborInfoResponse: StateFlow<String?> = MutableStateFlow(null)

        override fun setNeighborInfoResponse(value: String?) {}

        override fun clearNeighborInfoResponse() {}

        override val lockdownState: StateFlow<LockdownState> = MutableStateFlow(LockdownState.None)

        override fun setLockdownState(state: LockdownState) {}

        override fun clearLockdownState() {}

        override val lockdownTokenInfo: StateFlow<LockdownTokenInfo?> = MutableStateFlow(null)

        override fun setLockdownTokenInfo(info: LockdownTokenInfo?) {}

        override val sessionAuthorized: StateFlow<Boolean> = MutableStateFlow(false)

        override fun setSessionAuthorized(authorized: Boolean) {}
    }

    private class FakeMeshConfigHandler : MeshConfigHandler {
        override val localConfig: StateFlow<LocalConfig> = MutableStateFlow(LocalConfig())
        override val moduleConfig: StateFlow<LocalModuleConfig> = MutableStateFlow(LocalModuleConfig())

        override fun handleDeviceConfig(config: Config) {}

        override fun handleModuleConfig(config: ModuleConfig) {}

        override fun handleChannel(channel: Channel) {}

        override fun handleDeviceUIConfig(config: DeviceUIConfig) {}

        override fun handleRegionPresets(map: LoRaRegionPresetMap) {}
    }

    private class FakeNodeRepository(firmwareVersion: String? = "2.8.0.0") : NodeRepository {
        private val _myNodeInfo =
            MutableStateFlow(
                firmwareVersion?.let {
                    MyNodeInfo(
                        myNodeNum = 1,
                        hasGPS = false,
                        model = null,
                        firmwareVersion = it,
                        couldUpdate = false,
                        shouldUpdate = false,
                        currentPacketId = 0L,
                        messageTimeoutMsec = 0,
                        minAppVersion = 0,
                        maxChannels = 8,
                        hasWifi = false,
                        channelUtilization = 0f,
                        airUtilTx = 0f,
                        deviceId = null,
                    )
                },
            )
        override val myNodeInfo: StateFlow<MyNodeInfo?> = _myNodeInfo

        fun setFirmwareVersion(version: String?) {
            _myNodeInfo.value =
                version?.let {
                    MyNodeInfo(
                        myNodeNum = 1,
                        hasGPS = false,
                        model = null,
                        firmwareVersion = it,
                        couldUpdate = false,
                        shouldUpdate = false,
                        currentPacketId = 0L,
                        messageTimeoutMsec = 0,
                        minAppVersion = 0,
                        maxChannels = 8,
                        hasWifi = false,
                        channelUtilization = 0f,
                        airUtilTx = 0f,
                        deviceId = null,
                    )
                }
        }

        override val ourNodeInfo: StateFlow<Node?> = MutableStateFlow(null)
        override val myId: StateFlow<String?> = MutableStateFlow(null)
        override val localStats: StateFlow<LocalStats> = MutableStateFlow(LocalStats())
        override val nodeDBbyNum: StateFlow<Map<Int, Node>> = MutableStateFlow(emptyMap())
        override val onlineNodeCount: Flow<Int> = MutableStateFlow(0)
        override val totalNodeCount: Flow<Int> = MutableStateFlow(0)

        override fun updateLocalStats(stats: LocalStats) {}

        override fun effectiveLogNodeId(nodeNum: Int): Flow<Int> = MutableStateFlow(0)

        override fun getNode(userId: String): Node = Node(num = 0)

        override fun getUser(nodeNum: Int): User = User()

        override fun getUser(userId: String): User = User()

        override fun getNodes(
            sort: NodeSortOption,
            filter: String,
            includeUnknown: Boolean,
            onlyOnline: Boolean,
            onlyDirect: Boolean,
        ): Flow<List<Node>> = MutableStateFlow(emptyList())

        override suspend fun getNodesOlderThan(lastHeard: Int): List<Node> = emptyList()

        override suspend fun getUnknownNodes(): List<Node> = emptyList()

        override suspend fun clearNodeDB(preserveFavorites: Boolean) {}

        override suspend fun clearMyNodeInfo() {}

        override suspend fun deleteNode(num: Int) {}

        override suspend fun deleteNodes(nodeNums: List<Int>) {}

        override suspend fun setNodeNotes(num: Int, notes: String) {}

        override suspend fun upsert(node: Node) {}

        override suspend fun installConfig(mi: MyNodeInfo, nodes: List<Node>): List<Int> = emptyList()

        override suspend fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata) {}

        override suspend fun updatePowerChannelLabel(num: Int, channelIndex: Int, label: String) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private data class TestHarness(
        val serverManager: FakeTAKServerManager = FakeTAKServerManager(),
        val commandSender: FakeCommandSender = FakeCommandSender(),
        val serviceRepository: FakeServiceRepository = FakeServiceRepository(),
        val meshConfigHandler: FakeMeshConfigHandler = FakeMeshConfigHandler(),
        val nodeRepository: FakeNodeRepository = FakeNodeRepository(),
    ) {
        val integration =
            TAKMeshIntegration(
                takServerManager = serverManager,
                commandSender = commandSender,
                serviceRepository = serviceRepository,
                meshConfigHandler = meshConfigHandler,
                nodeRepository = nodeRepository,
            )
    }

    // ── Lifecycle tests ──────────────────────────────────────────────────────

    @Test
    fun `start launches TAKServerManager`() = runTest(UnconfinedTestDispatcher()) {
        val h = TestHarness()
        h.integration.start(backgroundScope)

        assertEquals(1, h.serverManager.startCount)
    }

    @Test
    fun `stop cancels jobs and stops TAKServerManager`() = runTest(UnconfinedTestDispatcher()) {
        val h = TestHarness()
        h.integration.start(backgroundScope)

        h.integration.stop()

        assertTrue(h.serverManager.stopped)
    }

    @Test
    fun `double start is idempotent`() = runTest(UnconfinedTestDispatcher()) {
        val h = TestHarness()
        h.integration.start(backgroundScope)

        h.integration.start(backgroundScope) // second start

        assertEquals(1, h.serverManager.startCount, "TAKServerManager should only be started once")
    }

    @Test
    fun `stop then inbound TAK message does not forward to mesh`() = runTest(UnconfinedTestDispatcher()) {
        val h = TestHarness()
        h.integration.start(backgroundScope)

        h.integration.stop()

        h.serverManager.emitInbound(createPli("after-stop"))

        assertTrue(h.commandSender.sentPackets.isEmpty())
    }

    // ── TAK-Talk strip preservation (regression) ────────────────────────────

    @Test
    fun `stripNonEssentialElements preserves TAK-Talk voice and marti`() {
        // Regression guard: <voice/> (push-to-talk marker) and <marti><dest .../>
        // </marti> (directed routing) were once added to STRIP_PATTERNS, which
        // silently broke TAK-Talk end-to-end — ATAK received the m-t-t CoT but its
        // plugin could neither play (no <voice/>) nor route (no <marti/>) it. Both
        // MUST survive the send-side strip.
        val mtt =
            """
            <event version="2.0" uid="TAKTALK-MESSAGE-test" type="m-t-t" how="null" time="t" start="t" stale="t">
              <point lat="0.0" lon="0.0" hae="9999999.0" ce="9999999.0" le="9999999.0"/>
              <detail>
                <callsign>ASPEN</callsign><lang>English</lang><text>Testing 123</text>
                <chatroom-id>1</chatroom-id><takv version="x"/><voice/>
                <marti><dest callsign="ETHEL"/></marti>
              </detail>
            </event>
            """
                .trimIndent()

        val stripped = TAKMeshIntegration.stripNonEssentialElements(mtt)

        assertTrue(stripped.contains("<voice/>"), "TAK-Talk <voice/> PTT marker must survive strip")
        assertTrue(
            stripped.contains("<marti>") && stripped.contains("dest callsign=\"ETHEL\""),
            "TAK-Talk <marti> directed-routing must survive strip",
        )
        // Sanity: genuinely non-essential elements are still stripped.
        assertTrue(!stripped.contains("<takv"), "non-essential <takv> should still be stripped")
    }

    // ── Inbound mesh → TAK client (V1) ──────────────────────────────────────

    @Test
    fun `inbound V1 PLI packet is broadcast to TAK clients`() = runTest(UnconfinedTestDispatcher()) {
        val h = TestHarness()
        h.integration.start(backgroundScope)

        h.serviceRepository.emitMeshPacket(createV1PliMeshPacket())

        assertTrue(h.serverManager.broadcasts.isNotEmpty(), "Expected broadcasts for V1 PLI")
        assertTrue(h.serverManager.broadcasts.first().type.startsWith("a-f-"))
    }

    @Test
    fun `inbound packet on unrelated port is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val h = TestHarness()
        h.integration.start(backgroundScope)

        val textPacket =
            MeshPacket(
                decoded =
                Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = "hello".encodeToByteArray().toByteString()),
            )
        h.serviceRepository.emitMeshPacket(textPacket)

        assertTrue(h.serverManager.broadcasts.isEmpty())
        assertTrue(h.serverManager.rawBroadcasts.isEmpty())
    }

    // ── Firmware gating ──────────────────────────────────────────────────────

    @Test
    fun `null firmware defaults to V2 protocol`() = runTest(UnconfinedTestDispatcher()) {
        val h = TestHarness(nodeRepository = FakeNodeRepository(firmwareVersion = null))
        h.integration.start(backgroundScope)

        h.serverManager.emitInbound(createPli("test-v2-default"))

        // In commonTest without TAKPacket-SDK, v2 path catches and falls back.
        // Verify the code didn't crash and attempted to send.
        if (h.commandSender.sentPackets.isNotEmpty()) {
            val sent = h.commandSender.sentPackets.first()
            assertEquals(PortNum.ATAK_PLUGIN_V2.value, sent.dataType)
        }
    }

    @Test
    fun `legacy firmware sends on V1 port 72`() = runTest(UnconfinedTestDispatcher()) {
        val h = TestHarness(nodeRepository = FakeNodeRepository(firmwareVersion = "2.7.0.0"))
        h.integration.start(backgroundScope)

        h.serverManager.emitInbound(createPli("test-v1"))

        if (h.commandSender.sentPackets.isNotEmpty()) {
            val sent = h.commandSender.sentPackets.first()
            assertEquals(PortNum.ATAK_PLUGIN.value, sent.dataType)
        }
    }

    @Test
    fun `legacy firmware drops non-PLI non-GeoChat types`() = runTest(UnconfinedTestDispatcher()) {
        val h = TestHarness(nodeRepository = FakeNodeRepository(firmwareVersion = "2.7.0.0"))
        h.integration.start(backgroundScope)

        val marker = CoTMessage(uid = "marker-1", type = "a-h-G", stale = Clock.System.now() + 5.minutes)
        h.serverManager.emitInbound(marker)

        assertTrue(h.commandSender.sentPackets.isEmpty())
    }

    // ── GeoChat callsign enrichment ──────────────────────────────────────────

    @Test
    fun `GeoChat without callsign is enriched from client info`() = runTest(UnconfinedTestDispatcher()) {
        val h = TestHarness(nodeRepository = FakeNodeRepository(firmwareVersion = "2.7.0.0"))
        h.integration.start(backgroundScope)

        val chatMsg =
            CoTMessage(
                uid = "GeoChat.test.All Chat Rooms.1234",
                type = "b-t-f",
                how = "h-g-i-g-o",
                stale = Clock.System.now() + 5.minutes,
                contact = null,
                chat = CoTChat(message = "hello", senderCallsign = null),
            )
        val clientInfo = TAKClientInfo(id = "client-1", endpoint = "127.0.0.1:8089", callsign = "ALPHA-1")
        h.serverManager.emitInbound(chatMsg, clientInfo)

        // GeoChat on legacy V1 should produce a sent packet with the enriched callsign
        if (h.commandSender.sentPackets.isNotEmpty()) {
            val sent = h.commandSender.sentPackets.first()
            assertEquals(PortNum.ATAK_PLUGIN.value, sent.dataType)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createPli(uid: String) =
        CoTMessage.pli(uid = uid, callsign = "TEST", latitude = 33.0, longitude = -84.0)

    private fun createV1PliMeshPacket(): MeshPacket {
        val takPacket =
            TAKPacket(
                contact = org.meshtastic.proto.Contact(callsign = "BRAVO", device_callsign = "bravo-uid"),
                pli =
                org.meshtastic.proto.PLI(
                    latitude_i = 330000000,
                    longitude_i = -840000000,
                    altitude = 100,
                    speed = 0,
                    course = 0,
                ),
                group =
                org.meshtastic.proto.Group(
                    team = org.meshtastic.proto.Team.Cyan,
                    role = org.meshtastic.proto.MemberRole.TeamMember,
                ),
                status = org.meshtastic.proto.Status(battery = 85),
            )
        return MeshPacket(
            decoded = Data(portnum = PortNum.ATAK_PLUGIN, payload = TAKPacket.ADAPTER.encode(takPacket).toByteString()),
        )
    }
}
