/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.DiscoveryPacketCollector
import org.meshtastic.core.repository.DiscoveryPacketCollectorRegistry
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.feature.discovery.ai.DiscoverySummaryAiProvider
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// region Inline fakes

/** In-memory fake of [DiscoveryDao] for unit tests. */
private class FakeDiscoveryDao : DiscoveryDao {
    private var nextSessionId = 1L
    private var nextPresetResultId = 1L
    private var nextNodeId = 1L

    val sessions = mutableMapOf<Long, DiscoverySessionEntity>()
    val presetResults = mutableMapOf<Long, DiscoveryPresetResultEntity>()
    val discoveredNodes = mutableMapOf<Long, DiscoveredNodeEntity>()

    override suspend fun insertSession(session: DiscoverySessionEntity): Long {
        val id = nextSessionId++
        sessions[id] = session.copy(id = id)
        return id
    }

    override suspend fun updateSession(session: DiscoverySessionEntity) {
        sessions[session.id] = session
    }

    override fun getAllSessions(): Flow<List<DiscoverySessionEntity>> =
        flowOf(sessions.values.sortedByDescending { it.timestamp })

    override suspend fun getSession(sessionId: Long): DiscoverySessionEntity? = sessions[sessionId]

    override fun getSessionFlow(sessionId: Long): Flow<DiscoverySessionEntity?> = MutableStateFlow(sessions[sessionId])

    override suspend fun deleteSession(sessionId: Long) {
        sessions.remove(sessionId)
        val resultIds = presetResults.values.filter { it.sessionId == sessionId }.map { it.id }
        resultIds.forEach { resultId ->
            discoveredNodes.entries.removeAll { it.value.presetResultId == resultId }
            presetResults.remove(resultId)
        }
    }

    override suspend fun insertPresetResult(result: DiscoveryPresetResultEntity): Long {
        val id = nextPresetResultId++
        presetResults[id] = result.copy(id = id)
        return id
    }

    override suspend fun updatePresetResult(result: DiscoveryPresetResultEntity) {
        presetResults[result.id] = result
    }

    override suspend fun getPresetResults(sessionId: Long): List<DiscoveryPresetResultEntity> =
        presetResults.values.filter { it.sessionId == sessionId }

    override fun getPresetResultsFlow(sessionId: Long): Flow<List<DiscoveryPresetResultEntity>> =
        flowOf(getPresetResultsSynchronous(sessionId))

    private fun getPresetResultsSynchronous(sessionId: Long): List<DiscoveryPresetResultEntity> =
        presetResults.values.filter { it.sessionId == sessionId }

    override suspend fun insertDiscoveredNode(node: DiscoveredNodeEntity): Long {
        val id = nextNodeId++
        discoveredNodes[id] = node.copy(id = id)
        return id
    }

    override suspend fun insertDiscoveredNodes(nodes: List<DiscoveredNodeEntity>) {
        nodes.forEach { insertDiscoveredNode(it) }
    }

    override suspend fun updateDiscoveredNode(node: DiscoveredNodeEntity) {
        discoveredNodes[node.id] = node
    }

    override suspend fun getDiscoveredNodes(presetResultId: Long): List<DiscoveredNodeEntity> =
        discoveredNodes.values.filter { it.presetResultId == presetResultId }

    override fun getDiscoveredNodesFlow(presetResultId: Long): Flow<List<DiscoveredNodeEntity>> =
        flowOf(discoveredNodes.values.filter { it.presetResultId == presetResultId })

    override suspend fun getUniqueNodeNums(sessionId: Long): List<Long> = presetResults.values
        .filter { it.sessionId == sessionId }
        .flatMap { pr -> discoveredNodes.values.filter { it.presetResultId == pr.id } }
        .map { it.nodeNum }
        .distinct()

    override suspend fun getUniqueNodeCount(sessionId: Long): Int = getUniqueNodeNums(sessionId).size

    override suspend fun getSessionWithResults(sessionId: Long): DiscoverySessionEntity? = sessions[sessionId]
}

/** Simple fake collector registry that tracks registration. */
private class FakeCollectorRegistry : DiscoveryPacketCollectorRegistry {
    override var collector: DiscoveryPacketCollector? = null
}

/** AI provider that is never available (no AI in tests). */
private class FakeAiProvider : DiscoverySummaryAiProvider {
    override val isAvailable: Boolean = false

    override suspend fun generateSessionSummary(
        session: DiscoverySessionEntity,
        presetResults: List<DiscoveryPresetResultEntity>,
    ): String? = null

    override suspend fun generatePresetSummary(result: DiscoveryPresetResultEntity): String? = null
}

// endregion

class DiscoveryScanEngineTest {

    private val radioController = FakeRadioController()
    private val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
    private val nodeRepository = FakeNodeRepository()
    private val radioConfigRepository =
        FakeRadioConfigRepository().apply {
            setLocalConfigDirect(
                LocalConfig(lora = Config.LoRaConfig(modem_preset = ChannelOption.LONG_FAST.modemPreset)),
            )
        }
    private val collectorRegistry = FakeCollectorRegistry()
    private val discoveryDao = FakeDiscoveryDao()
    private val aiProvider = FakeAiProvider()

    private val engine =
        DiscoveryScanEngine(
            radioController = radioController,
            serviceRepository = serviceRepository,
            nodeRepository = nodeRepository,
            radioConfigRepository = radioConfigRepository,
            collectorRegistry = collectorRegistry,
            discoveryDao = discoveryDao,
            aiProvider = aiProvider,
        )

    private val testPresets = listOf(ChannelOption.LONG_FAST)

    /**
     * After [DiscoveryScanEngine.startScan], the state is set to [DiscoveryScanState.Shifting] synchronously. This
     * helper asserts that the engine is active — no real-time wait needed.
     */
    private fun assertScanActive() {
        assertTrue(engine.isActive, "Engine should be active after startScan")
    }

    /**
     * Waits briefly for the scan loop (running on [ioDispatcher]) to complete its per-preset initialization (collection
     * clearing). Call before sending packets to avoid a race where the scan loop's `collectedNodes.clear()` wipes out
     * test-injected data.
     */
    @Suppress("MagicNumber")
    private fun awaitScanLoopInit() {
        Thread.sleep(500)
    }

    // region Helper factories

    private fun createMyNodeInfo(nodeNum: Int = 1000) = MyNodeInfo(
        myNodeNum = nodeNum,
        hasGPS = true,
        model = "TestModel",
        firmwareVersion = "2.0.0",
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 1L,
        messageTimeoutMsec = 5000,
        minAppVersion = 1,
        maxChannels = 8,
        hasWifi = false,
        channelUtilization = 0f,
        airUtilTx = 0f,
        deviceId = "test-device",
    )

    private fun createNodeWithPosition(num: Int, latI: Int = 0, lonI: Int = 0) = Node(
        num = num,
        user = User(id = "!${num.toString(16)}", short_name = "T$num", long_name = "Test Node $num"),
        position = Position(latitude_i = latI, longitude_i = lonI),
    )

    private fun createPositionMeshPacket(
        from: Int,
        latI: Int,
        lonI: Int,
        snr: Float = 5.5f,
        rssi: Int = -70,
    ): MeshPacket {
        val posPayload = Position.ADAPTER.encode(Position(latitude_i = latI, longitude_i = lonI)).toByteString()
        val data = Data(portnum = PortNum.POSITION_APP, payload = posPayload)
        return MeshPacket(from = from, decoded = data, rx_snr = snr, rx_rssi = rssi)
    }

    private fun createTelemetryWithLocalStats(from: Int, localStats: LocalStats): MeshPacket {
        val telPayload = Telemetry.ADAPTER.encode(Telemetry(local_stats = localStats)).toByteString()
        val data = Data(portnum = PortNum.TELEMETRY_APP, payload = telPayload)
        return MeshPacket(from = from, decoded = data)
    }

    private fun createDataPacket(from: Int): DataPacket = DataPacket(
        to = DataPacket.ID_BROADCAST,
        bytes = ByteString.EMPTY,
        dataType = PortNum.POSITION_APP.value,
        from = "!${from.toString(16)}",
        hopStart = 3,
        hopLimit = 3,
    )

    // endregion

    @Test
    fun startScanCreatesSessionAndRegistersCollector() = runTest {
        engine.startScan(testPresets, dwellDurationSeconds = 10)

        // Session should be persisted (happens synchronously inside startScan)
        assertEquals(1, discoveryDao.sessions.size)
        val session = discoveryDao.sessions.values.first()
        assertEquals("in_progress", session.completionStatus)
        assertEquals("LONG_FAST", session.presetsScanned)
        assertEquals("LONG_FAST", session.homePreset)

        // Collector should be registered (synchronous inside startScan)
        assertNotNull(collectorRegistry.collector)
        assertTrue(collectorRegistry.collector === engine)

        // currentSession should be populated
        val currentSession = engine.currentSession.value
        assertNotNull(currentSession)
        assertEquals(session.id, currentSession.id)

        // Wait for scan loop to start then clean up
        assertScanActive()
        engine.stopScan()
    }

    @Test
    fun stopScanPersistsResultsAndTransitionsToIdle() = runTest {
        engine.startScan(testPresets, dwellDurationSeconds = 60)
        assertScanActive()

        // Verify scan is active
        assertTrue(engine.isActive)

        engine.stopScan()

        // State should be Idle
        assertTrue(engine.scanState.value is DiscoveryScanState.Idle)
        assertFalse(engine.isActive)

        // Collector should be unregistered
        assertNull(collectorRegistry.collector)

        // Session should be finalized with "stopped" status
        val session = discoveryDao.sessions.values.first()
        assertEquals("stopped", session.completionStatus)
    }

    @Test
    fun completeScanCreatesSessionWithInProgressStatus() = runTest {
        engine.startScan(testPresets, dwellDurationSeconds = 5)

        // Immediately after startScan, the session should exist with "in_progress"
        val session = discoveryDao.sessions.values.first()
        assertEquals("in_progress", session.completionStatus)

        // Wait for the scan loop to start, then verify active
        assertScanActive()
        assertTrue(engine.isActive)

        engine.stopScan()
    }

    @Test
    fun emptyPresetDwellPersistsZeroResultEntry() = runTest {
        engine.startScan(testPresets, dwellDurationSeconds = 10)
        assertScanActive()

        // Stop without receiving any packets — forces persistCurrentDwellResults
        engine.stopScan()

        // Should have a preset result with zero unique nodes
        val presetResults = discoveryDao.presetResults.values.toList()
        assertTrue(presetResults.isNotEmpty(), "Expected at least one preset result")

        val result = presetResults.first()
        assertEquals("LONG_FAST", result.presetName)
        assertEquals(0, result.uniqueNodes)
        assertEquals(0, result.messageCount)

        // No discovered nodes
        assertTrue(discoveryDao.discoveredNodes.isEmpty())
    }

    @Test
    fun packetCollectionPopulatesNodeData() = runTest {
        nodeRepository.setMyNodeInfo(createMyNodeInfo())

        engine.startScan(testPresets, dwellDurationSeconds = 60)
        assertScanActive()
        awaitScanLoopInit()

        // Simulate receiving a position packet
        val meshPacket =
            createPositionMeshPacket(from = 12345, latI = 377749300, lonI = -1224194200, snr = 5.5f, rssi = -70)
        val dataPacket = createDataPacket(from = 12345)

        engine.onPacketReceived(meshPacket, dataPacket)

        // Stop scan to persist results
        engine.stopScan()

        // Should have one discovered node with lat/lon
        val nodes = discoveryDao.discoveredNodes.values.toList()
        assertEquals(1, nodes.size)

        val node = nodes.first()
        assertEquals(12345L, node.nodeNum)
        assertNotNull(node.latitude, "Node should have latitude")
        assertNotNull(node.longitude, "Node should have longitude")
        // latitude_i = 377749300 → 37.77493
        assertTrue(node.latitude!! > 37.7 && node.latitude!! < 37.8, "Latitude should be ~37.77")
        // longitude_i = -1224194200 → -122.41942
        assertTrue(node.longitude!! < -122.4 && node.longitude!! > -122.5, "Longitude should be ~-122.42")
        assertEquals(5.5f, node.snr)
        assertEquals(-70, node.rssi)
    }

    @Test
    fun telemetryWithLocalStatsPopulatesRfHealth() = runTest {
        nodeRepository.setMyNodeInfo(createMyNodeInfo())

        engine.startScan(testPresets, dwellDurationSeconds = 60)
        assertScanActive()
        awaitScanLoopInit()

        // Send a telemetry packet with local_stats
        val localStats =
            LocalStats(
                num_packets_tx = 100,
                num_packets_rx = 200,
                num_packets_rx_bad = 5,
                num_rx_dupe = 10,
                num_tx_relay = 15,
                num_tx_relay_canceled = 2,
                num_online_nodes = 3,
                num_total_nodes = 10,
                uptime_seconds = 3600,
            )
        val meshPacket = createTelemetryWithLocalStats(from = 12345, localStats = localStats)
        val dataPacket = createDataPacket(from = 12345)

        engine.onPacketReceived(meshPacket, dataPacket)

        // Stop to persist
        engine.stopScan()

        // The preset result should have RF health fields from local_stats
        val presetResults = discoveryDao.presetResults.values.toList()
        assertTrue(presetResults.isNotEmpty())

        val result = presetResults.first()
        assertEquals(100, result.numPacketsTx)
        assertEquals(200, result.numPacketsRx)
        assertEquals(5, result.numPacketsRxBad)
        assertEquals(10, result.numRxDupe)
        assertEquals(15, result.numTxRelay)
        assertEquals(2, result.numTxRelayCanceled)
        assertEquals(3, result.numOnlineNodes)
        assertEquals(10, result.numTotalNodes)
        assertEquals(3600, result.uptimeSeconds)

        // Packet success/failure rates should be computed
        // success = (200 - 5) / 200 * 100 = 97.5
        // failure = 5 / 200 * 100 = 2.5
        assertTrue(result.packetSuccessRate > 97.0, "Success rate should be ~97.5%")
        assertTrue(result.packetFailureRate > 2.0, "Failure rate should be ~2.5%")
    }

    @Test
    fun userPositionCapturedAtScanStart() = runTest {
        val myNodeNum = 1000
        nodeRepository.setMyNodeInfo(createMyNodeInfo(myNodeNum))
        nodeRepository.setNodes(listOf(createNodeWithPosition(num = myNodeNum, latI = 377749300, lonI = -1224194200)))

        engine.startScan(testPresets, dwellDurationSeconds = 10)

        val session = discoveryDao.sessions.values.first()
        // User position should be captured from the own node
        // latitude_i = 377749300 → 37.77493
        assertTrue(session.userLatitude > 37.7 && session.userLatitude < 37.8, "User lat should be ~37.77")
        assertTrue(session.userLongitude < -122.4 && session.userLongitude > -122.5, "User lon should be ~-122.42")

        engine.stopScan()
    }

    @Test
    fun distanceFromUserCalculatedForDiscoveredNodes() = runTest {
        val myNodeNum = 1000
        nodeRepository.setMyNodeInfo(createMyNodeInfo(myNodeNum))
        // User at San Francisco (37.7749, -122.4194)
        nodeRepository.setNodes(listOf(createNodeWithPosition(num = myNodeNum, latI = 377749000, lonI = -1224194000)))

        engine.startScan(testPresets, dwellDurationSeconds = 60)
        assertScanActive()
        awaitScanLoopInit()

        // Discovered node at Oakland (37.8044, -122.2712) — roughly 15 km away
        val meshPacket = createPositionMeshPacket(from = 54321, latI = 378044000, lonI = -1222712000)
        val dataPacket = createDataPacket(from = 54321)

        engine.onPacketReceived(meshPacket, dataPacket)
        engine.stopScan()

        val nodes = discoveryDao.discoveredNodes.values.toList()
        assertEquals(1, nodes.size)

        val node = nodes.first()
        assertNotNull(node.distanceFromUser, "Distance from user should be computed")
        // SF to Oakland is roughly 13–17 km
        assertTrue(
            node.distanceFromUser!! > 10_000 && node.distanceFromUser!! < 25_000,
            "Distance should be between 10km and 25km, was ${node.distanceFromUser}m",
        )
    }
}
