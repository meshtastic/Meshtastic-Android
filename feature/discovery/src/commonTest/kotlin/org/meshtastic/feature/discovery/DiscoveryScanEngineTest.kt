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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.discovery

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.DiscoveryPacketCollector
import org.meshtastic.core.repository.DiscoveryPacketCollectorRegistry
import org.meshtastic.core.testing.FakeMeshPrefs
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

    override suspend fun getAllSessionsSnapshot(): List<DiscoverySessionEntity> = sessions.values.toList()

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

    override suspend fun getMaxDistance(sessionId: Long): Double? = presetResults.values
        .filter { it.sessionId == sessionId }
        .flatMap { pr -> discoveredNodes.values.filter { it.presetResultId == pr.id } }
        .mapNotNull { it.distanceFromUser }
        .maxOrNull()

    override suspend fun getSessionWithResults(sessionId: Long): DiscoverySessionEntity? = sessions[sessionId]

    override suspend fun markInterruptedSessions() {
        sessions.keys.toList().forEach { key ->
            val session = sessions[key]!!
            if (session.completionStatus == "in_progress") {
                sessions[key] = session.copy(completionStatus = "interrupted")
            }
        }
    }

    override suspend fun getInterruptedSession(deviceAddress: String): DiscoverySessionEntity? = sessions.values
        .filter { it.deviceAddress == deviceAddress && it.completionStatus in setOf("in_progress", "interrupted") }
        .maxByOrNull { it.timestamp }
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
                LocalConfig(
                    lora = Config.LoRaConfig(use_preset = true, modem_preset = ChannelOption.LONG_FAST.modemPreset),
                ),
            )
        }
    private val collectorRegistry = FakeCollectorRegistry()
    private val discoveryDao = FakeDiscoveryDao()
    private val aiProvider = FakeAiProvider()
    private val meshPrefs = FakeMeshPrefs()

    /** Creates a [DiscoveryScanEngine] wired to test dispatchers sharing the given [testScope]'s scheduler. */
    private fun createEngine(testScope: TestScope): DiscoveryScanEngine {
        val testDispatcher = UnconfinedTestDispatcher(testScope.testScheduler)
        val dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher)
        val appScope =
            object : ApplicationCoroutineScope {
                override val coroutineContext = testDispatcher + SupervisorJob()
            }
        return DiscoveryScanEngine(
            radioController = radioController,
            serviceRepository = serviceRepository,
            nodeRepository = nodeRepository,
            radioConfigRepository = radioConfigRepository,
            collectorRegistry = collectorRegistry,
            discoveryDao = discoveryDao,
            aiProvider = aiProvider,
            applicationScope = appScope,
            dispatchers = dispatchers,
            meshPrefs = meshPrefs,
        )
    }

    private val testPresets = listOf(ChannelOption.LONG_FAST)

    /**
     * After [DiscoveryScanEngine.startScan], the state is set to [DiscoveryScanState.Shifting] synchronously. This
     * helper asserts that the engine is active — no real-time wait needed.
     */
    private fun assertScanActive(engine: DiscoveryScanEngine) {
        assertTrue(engine.isActive, "Engine should be active after startScan")
    }

    /**
     * Waits briefly for the scan loop (running on test dispatcher) to complete its per-preset initialization
     * (collection clearing). Call before sending packets to avoid a race where the scan loop's `collectedNodes.clear()`
     * wipes out test-injected data.
     */
    @Suppress("MagicNumber")
    private suspend fun awaitScanLoopInit() {
        delay(100)
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
        to = NodeAddress.ID_BROADCAST,
        bytes = ByteString.EMPTY,
        dataType = PortNum.POSITION_APP.value,
        from = "!${from.toString(16)}",
        hopStart = 3,
        hopLimit = 3,
    )

    // endregion

    @Test
    fun startScanCreatesSessionAndRegistersCollector() = runTest {
        val engine = createEngine(this)
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
        assertScanActive(engine)
        engine.stopScan()
    }

    @Test
    fun stopScanPersistsResultsAndTransitionsToIdle() = runTest {
        val engine = createEngine(this)
        engine.startScan(testPresets, dwellDurationSeconds = 60)
        assertScanActive(engine)

        // Verify scan is active
        assertTrue(engine.isActive)

        engine.stopScan()

        // State should be Complete(Cancelled)
        assertTrue(engine.scanState.value is DiscoveryScanState.Complete)
        val completeState = engine.scanState.value as DiscoveryScanState.Complete
        assertEquals(DiscoveryScanState.CompletionOutcome.Cancelled, completeState.outcome)
        assertFalse(engine.isActive)

        // Collector should be unregistered
        assertNull(collectorRegistry.collector)

        // Session should be finalized with "stopped" status
        val session = discoveryDao.sessions.values.first()
        assertEquals("stopped", session.completionStatus)
    }

    @Test
    fun completeScanCreatesSessionWithInProgressStatus() = runTest {
        val engine = createEngine(this)
        engine.startScan(testPresets, dwellDurationSeconds = 5)

        // Immediately after startScan, the session should exist with "in_progress"
        val session = discoveryDao.sessions.values.first()
        assertEquals("in_progress", session.completionStatus)

        // Wait for the scan loop to start, then verify active
        assertScanActive(engine)
        assertTrue(engine.isActive)

        engine.stopScan()
    }

    @Test
    fun emptyPresetDwellPersistsZeroResultEntry() = runTest {
        val engine = createEngine(this)
        engine.startScan(testPresets, dwellDurationSeconds = 10)
        assertScanActive(engine)

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
        val engine = createEngine(this)
        val myNodeNum = 1000
        nodeRepository.setMyNodeInfo(createMyNodeInfo(myNodeNum))
        nodeRepository.setNodes(listOf(createNodeWithPosition(num = myNodeNum, latI = 377749000, lonI = -1224194000)))

        engine.startScan(testPresets, dwellDurationSeconds = 60)
        assertScanActive(engine)

        // Wait for Dwell state
        while (engine.scanState.value !is DiscoveryScanState.Dwell) {
            delay(100)
        }

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
        val engine = createEngine(this)
        nodeRepository.setMyNodeInfo(createMyNodeInfo())

        engine.startScan(testPresets, dwellDurationSeconds = 60)
        assertScanActive(engine)

        // Wait for Dwell state and ensure sessionId is set
        while (engine.scanState.value !is DiscoveryScanState.Dwell || engine.currentSession.value == null) {
            delay(100)
        }

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
        assertTrue(presetResults.isNotEmpty(), "Expected a preset result")

        val result = presetResults.first()
        assertEquals(100, result.numPacketsTx, "numPacketsTx should be 100")
        assertEquals(200, result.numPacketsRx, "numPacketsRx should be 200")
        assertEquals(5, result.numPacketsRxBad, "numPacketsRxBad should be 5")
        assertEquals(10, result.numRxDupe, "numRxDupe should be 10")
        assertEquals(15, result.numTxRelay, "numTxRelay should be 15")
        assertEquals(2, result.numTxRelayCanceled, "numTxRelayCanceled should be 2")
        assertEquals(3, result.numOnlineNodes, "numOnlineNodes should be 3")
        assertEquals(10, result.numTotalNodes, "numTotalNodes should be 10")
        assertEquals(3600, result.uptimeSeconds, "uptimeSeconds should be 3600")

        // Packet success/failure rates should be computed
        // success = (200 - 5) / 200 * 100 = 97.5
        // failure = 5 / 200 * 100 = 2.5
        assertTrue(result.packetSuccessRate > 97.0, "Success rate should be ~97.5%")
        assertTrue(result.packetFailureRate > 2.0, "Failure rate should be ~2.5%")
    }

    @Test
    fun userPositionCapturedAtScanStart() = runTest {
        val engine = createEngine(this)
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
        val engine = createEngine(this)
        val myNodeNum = 1000
        nodeRepository.setMyNodeInfo(createMyNodeInfo(myNodeNum))
        // User at San Francisco (37.7749, -122.4194)
        nodeRepository.setNodes(listOf(createNodeWithPosition(num = myNodeNum, latI = 377749000, lonI = -1224194000)))

        engine.startScan(testPresets, dwellDurationSeconds = 60)
        assertScanActive(engine)

        // Wait for Dwell state
        while (engine.scanState.value !is DiscoveryScanState.Dwell) {
            delay(100)
        }

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

    // region Home-preset restoration (the one config-mutating, safety-critical behavior of a scan)

    @Test
    fun reconnectTimeoutAbortsRestoresHomePresetAndFinalizesSession() = runTest {
        val engine = createEngine(this)
        // Scan a preset different from the seeded home (LONG_FAST) so a restore-to-home is observable.
        engine.startScan(listOf(ChannelOption.SHORT_FAST), dwellDurationSeconds = 60)
        assertScanActive(engine)

        // The radio fails to come back after the preset shift (firmware reboots on a LoRa config change).
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        advanceUntilIdle()

        // The abort path must reach a terminal Failed state, finalize the session, and restore the home preset —
        // none of which happened before the fix, because cancelScanInternal() cancelled this coroutine first.
        val state = engine.scanState.value
        assertTrue(state is DiscoveryScanState.Complete, "expected Complete, was $state")
        assertEquals(DiscoveryScanState.CompletionOutcome.Failed, (state as DiscoveryScanState.Complete).outcome)
        assertFalse(engine.isActive)
        assertNull(collectorRegistry.collector, "collector should be unregistered")

        assertEquals("failed", discoveryDao.sessions.values.first().completionStatus)
        // The last LoRa config applied is the home preset (LONG_FAST), not the scan preset (SHORT_FAST).
        assertEquals(ChannelOption.LONG_FAST.modemPreset, radioController.lastLocalConfig?.lora?.modem_preset)
    }

    @Test
    fun stopScanRestoresHomePreset() = runTest {
        val engine = createEngine(this)
        engine.startScan(listOf(ChannelOption.SHORT_FAST), dwellDurationSeconds = 60)
        assertScanActive(engine)

        engine.stopScan()
        advanceUntilIdle() // let the applicationScope restore complete

        assertEquals(ChannelOption.LONG_FAST.modemPreset, radioController.lastLocalConfig?.lora?.modem_preset)
    }

    @Test
    fun normalCompletionRestoresHomePreset() = runTest {
        val engine = createEngine(this)
        engine.startScan(listOf(ChannelOption.SHORT_FAST), dwellDurationSeconds = 1)
        advanceUntilIdle() // connection stays Connected, so the scan runs to completion

        val state = engine.scanState.value
        assertTrue(state is DiscoveryScanState.Complete, "expected Complete, was $state")
        assertEquals(DiscoveryScanState.CompletionOutcome.Success, (state as DiscoveryScanState.Complete).outcome)
        assertEquals("complete", discoveryDao.sessions.values.first().completionStatus)
        assertEquals(ChannelOption.LONG_FAST.modemPreset, radioController.lastLocalConfig?.lora?.modem_preset)
    }

    // endregion

    // region Interrupted-session detection and restore (crash / BLE-loss recovery)

    /** Seeds a prior-process interrupted session directly into the fake DAO (keyed by its own id). */
    private fun seedInterruptedSession(
        id: Long = 1L,
        deviceAddress: String,
        homePreset: String = "LONG_FAST",
        completionStatus: String = "in_progress",
        homeLoraConfig: Config.LoRaConfig? =
            Config.LoRaConfig(use_preset = true, modem_preset = ChannelOption.LONG_FAST.modemPreset),
    ) {
        discoveryDao.sessions[id] =
            DiscoverySessionEntity(
                id = id,
                timestamp = 1L,
                presetsScanned = "SHORT_FAST",
                homePreset = homePreset,
                completionStatus = completionStatus,
                deviceAddress = deviceAddress,
                homeLoraConfig = homeLoraConfig,
            )
    }

    @Test
    fun startScanCapturesDeviceAddressAndHomeConfigForLaterRestore() = runTest {
        meshPrefs.setDeviceAddress("x:AA:BB:CC:DD:EE:FF")
        val engine = createEngine(this)
        engine.startScan(testPresets, dwellDurationSeconds = 10)

        val session = discoveryDao.sessions.values.first()
        assertEquals("x:AA:BB:CC:DD:EE:FF", session.deviceAddress)
        assertEquals(ChannelOption.LONG_FAST.modemPreset, session.homeLoraConfig?.modem_preset)

        engine.stopScan()
    }

    @Test
    fun restoreInterruptedSessionsOnReconnectRestoresMatchingDevice() = runTest {
        val address = "x:AA:BB:CC:DD:EE:FF"
        meshPrefs.setDeviceAddress(address)
        // A previous process's scan died mid-flight and never reached restoreHomePreset/finalizeSession.
        seedInterruptedSession(deviceAddress = address)

        val restored = mutableListOf<String>()
        val engine = createEngine(this)
        val watcherJob = launch { engine.restoreInterruptedSessionsOnReconnect { restored += it } }
        advanceUntilIdle() // serviceRepository is already Connected, so the watcher's first collection fires now

        assertEquals(ChannelOption.LONG_FAST.modemPreset, radioController.lastLocalConfig?.lora?.modem_preset)
        assertEquals("restored", discoveryDao.sessions.getValue(1).completionStatus)
        assertEquals(listOf("LONG_FAST"), restored, "Caller should be notified with the restored home preset")

        watcherJob.cancel()
    }

    @Test
    fun restoreInterruptedSessionsOnReconnectIgnoresDifferentDevice() = runTest {
        meshPrefs.setDeviceAddress("x:CURRENT")
        seedInterruptedSession(deviceAddress = "x:OTHER-DEVICE")

        val restored = mutableListOf<String>()
        val engine = createEngine(this)
        val watcherJob = launch { engine.restoreInterruptedSessionsOnReconnect { restored += it } }
        advanceUntilIdle()

        assertNull(radioController.lastLocalConfig, "Should not touch the radio for a session from another device")
        assertEquals("in_progress", discoveryDao.sessions.getValue(1).completionStatus)
        assertTrue(restored.isEmpty(), "No notification for a session from another device")

        watcherJob.cancel()
    }

    @Test
    fun restoreInterruptedSessionsOnReconnectSkipsWhileScanActive() = runTest {
        val address = "x:AA:BB:CC:DD:EE:FF"
        meshPrefs.setDeviceAddress(address)

        val engine = createEngine(this)
        engine.startScan(testPresets, dwellDurationSeconds = 60)
        assertScanActive(engine)

        // A stale session from a *different*, already-dead process — seeded at a key the active scan's own
        // insertSession call (id=1) won't collide with, so this row's fate isolates what the watcher itself does.
        seedInterruptedSession(id = 999, deviceAddress = address)

        // The connection is already Connected, so the watcher fires on its very first emission — while this engine's
        // own scan is still active. runCurrent (not advanceUntilIdle) drains that emission WITHOUT advancing virtual
        // time, so the scan's dwell delays never elapse and it stays active for the duration of the check.
        val restored = mutableListOf<String>()
        val watcherJob = launch { engine.restoreInterruptedSessionsOnReconnect { restored += it } }
        runCurrent()

        assertEquals(
            "in_progress",
            discoveryDao.sessions.getValue(999).completionStatus,
            "Stale row must not be touched while this engine's own scan is active",
        )
        assertTrue(restored.isEmpty(), "No restore notification while this engine's own scan is active")

        watcherJob.cancel()
        engine.stopScan()
    }

    @Test
    fun restoreWatcherSurvivesWriteFailureAndRetriesOnNextReconnect() = runTest {
        val address = "x:AA:BB:CC:DD:EE:FF"
        meshPrefs.setDeviceAddress(address)
        seedInterruptedSession(deviceAddress = address)

        // The link drops right after Connected, so the restore's config write fails.
        radioController.throwOnSetLocalConfig = true
        val restored = mutableListOf<String>()
        val engine = createEngine(this)
        val watcherJob = launch { engine.restoreInterruptedSessionsOnReconnect { restored += it } }
        advanceUntilIdle()

        assertEquals("in_progress", discoveryDao.sessions.getValue(1).completionStatus, "Failed restore must not mark")
        assertTrue(restored.isEmpty(), "No notification when the restore write failed")

        // Next reconnect succeeds — the watcher must still be alive to retry. A real reconnect always passes through
        // Disconnected first; the intermediate advanceUntilIdle lets the watcher observe the drop, so the return to
        // Connected is a genuine transition rather than a conflated no-op the StateFlow would dedupe away.
        radioController.throwOnSetLocalConfig = false
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        advanceUntilIdle()
        serviceRepository.setConnectionState(ConnectionState.Connected)
        advanceUntilIdle()

        assertEquals("restored", discoveryDao.sessions.getValue(1).completionStatus)
        assertEquals(ChannelOption.LONG_FAST.modemPreset, radioController.lastLocalConfig?.lora?.modem_preset)
        assertEquals(listOf("LONG_FAST"), restored, "Caller notified once the retry succeeded")

        watcherJob.cancel()
    }

    @Test
    fun restoreInterruptedSessionsOnReconnectTerminalizesUnrestorableSession() = runTest {
        val address = "x:AA:BB:CC:DD:EE:FF"
        meshPrefs.setDeviceAddress(address)
        // Config was null at scan start (nothing to restore), so this session can never be recovered.
        seedInterruptedSession(deviceAddress = address, homePreset = "CUSTOM", homeLoraConfig = null)

        val restored = mutableListOf<String>()
        val engine = createEngine(this)
        val watcherJob = launch { engine.restoreInterruptedSessionsOnReconnect { restored += it } }
        advanceUntilIdle()

        // Marked terminal so it stops re-matching getInterruptedSession forever; radio untouched, no notification.
        assertEquals("unrestorable", discoveryDao.sessions.getValue(1).completionStatus)
        assertNull(radioController.lastLocalConfig, "Nothing to write to the radio without a captured config")
        assertTrue(restored.isEmpty(), "No notification for an unrestorable session")

        watcherJob.cancel()
    }

    // endregion
}
