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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Neighbor
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for edge cases in packet collection: duplicate packets, nodes without positions, and neighbor-info-only
 * sightings (D023).
 */
class DiscoveryPacketCollectionTest {

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
    private val collectorRegistry = PacketTestCollectorRegistry()
    private val discoveryDao = InMemoryDiscoveryDao()
    private val aiProvider = PacketTestAiProvider()
    private val meshPrefs = FakeMeshPrefs()

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

    private suspend fun awaitDwell(engine: DiscoveryScanEngine) {
        while (engine.scanState.value !is DiscoveryScanState.Dwell) {
            delay(50)
        }
    }

    // region Duplicate packets

    @Test
    fun duplicatePacketsFromSameNodeDeduplicateByNodeNum() = runTest {
        val engine = createEngine(this)
        nodeRepository.setMyNodeInfo(createMyNodeInfo())
        engine.startScan(testPresets, dwellDurationSeconds = 60)
        awaitDwell(engine)

        // Send two position packets from the same node
        val meshPacket1 = positionPacket(from = 1111, latI = 377749000, lonI = -1224194000, snr = 5.0f, rssi = -70)
        val meshPacket2 = positionPacket(from = 1111, latI = 377750000, lonI = -1224195000, snr = 8.0f, rssi = -55)
        engine.onPacketReceived(meshPacket1, dataPacket(from = 1111))
        engine.onPacketReceived(meshPacket2, dataPacket(from = 1111))

        engine.stopScan()

        // Only one discovered node for nodeNum=1111
        val nodes = discoveryDao.discoveredNodes.values.toList()
        assertEquals(1, nodes.size, "Duplicate packets should map to a single node entry")
        assertEquals(1111L, nodes[0].nodeNum)
        // Second packet's SNR/RSSI should overwrite first
        assertEquals(8.0f, nodes[0].snr, "Later SNR should overwrite")
        assertEquals(-55, nodes[0].rssi, "Later RSSI should overwrite")
    }

    @Test
    fun duplicatePacketsCountMessagesAccumulatively() = runTest {
        val engine = createEngine(this)
        nodeRepository.setMyNodeInfo(createMyNodeInfo())
        engine.startScan(testPresets, dwellDurationSeconds = 60)
        awaitDwell(engine)

        // Send 3 text messages from same node
        repeat(3) { engine.onPacketReceived(textMessagePacket(from = 2222), dataPacket(from = 2222)) }

        engine.stopScan()

        val nodes = discoveryDao.discoveredNodes.values.toList()
        assertEquals(1, nodes.size)
        assertEquals(3, nodes[0].messageCount, "Message count should accumulate across duplicate packets")
    }

    // endregion

    // region Nodes without positions

    @Test
    fun nodeWithoutPositionHasNullLatLon() = runTest {
        val engine = createEngine(this)
        nodeRepository.setMyNodeInfo(createMyNodeInfo())
        engine.startScan(testPresets, dwellDurationSeconds = 60)
        awaitDwell(engine)

        // Send a text message with no position data
        engine.onPacketReceived(textMessagePacket(from = 3333), dataPacket(from = 3333))

        engine.stopScan()

        val nodes = discoveryDao.discoveredNodes.values.toList()
        assertEquals(1, nodes.size)
        assertNull(nodes[0].latitude, "Node without position should have null latitude")
        assertNull(nodes[0].longitude, "Node without position should have null longitude")
        assertNull(nodes[0].distanceFromUser, "Node without position should have null distance")
    }

    @Test
    fun nodeWithZeroPositionTreatedAsNoPosition() = runTest {
        val engine = createEngine(this)
        nodeRepository.setMyNodeInfo(createMyNodeInfo())
        engine.startScan(testPresets, dwellDurationSeconds = 60)
        awaitDwell(engine)

        // Position of 0,0 is treated as invalid/no fix
        val packet = positionPacket(from = 4444, latI = 0, lonI = 0)
        engine.onPacketReceived(packet, dataPacket(from = 4444))

        engine.stopScan()

        val nodes = discoveryDao.discoveredNodes.values.toList()
        assertEquals(1, nodes.size)
        assertNull(nodes[0].distanceFromUser, "Zero-position node should have null distance")
    }

    // endregion

    // region Neighbor-info-only sightings

    @Test
    fun neighborInfoOnlyNodeIsMarkedAsMesh() = runTest {
        val engine = createEngine(this)
        nodeRepository.setMyNodeInfo(createMyNodeInfo())
        engine.startScan(testPresets, dwellDurationSeconds = 60)
        awaitDwell(engine)

        // Send a neighbor info packet that references node 5555 as a mesh neighbor
        val niPacket = neighborInfoPacket(from = 9999, neighborNodeIds = listOf(5555))
        engine.onPacketReceived(niPacket, dataPacket(from = 9999))

        engine.stopScan()

        // Node 5555 should appear as a mesh neighbor even though we never received a direct packet from it
        val nodes = discoveryDao.discoveredNodes.values.toList()
        val meshNode = nodes.find { it.nodeNum == 5555L }
        assertTrue(meshNode != null, "Neighbor-info-only node should be persisted")
        assertEquals("mesh", meshNode.neighborType, "Neighbor-info-only node should have 'mesh' type")
    }

    @Test
    fun neighborInfoDoesNotOverrideDirectType() = runTest {
        val engine = createEngine(this)
        nodeRepository.setMyNodeInfo(createMyNodeInfo())
        engine.startScan(testPresets, dwellDurationSeconds = 60)
        awaitDwell(engine)

        // First: receive a direct packet from node 6666
        engine.onPacketReceived(
            positionPacket(from = 6666, latI = 377749000, lonI = -1224194000, snr = 10f, rssi = -40),
            dataPacket(from = 6666),
        )

        // Then: receive neighbor info that also references 6666
        val niPacket = neighborInfoPacket(from = 8888, neighborNodeIds = listOf(6666))
        engine.onPacketReceived(niPacket, dataPacket(from = 8888))

        engine.stopScan()

        val nodes = discoveryDao.discoveredNodes.values.toList()
        val directNode = nodes.find { it.nodeNum == 6666L }
        assertTrue(directNode != null, "Node should be persisted")
        assertEquals("direct", directNode.neighborType, "Direct type should not be overridden by neighbor-info")
        assertEquals(10f, directNode.snr, "SNR from direct packet should be preserved")
    }

    @Test
    fun neighborInfoMultipleNeighborsAllRecorded() = runTest {
        val engine = createEngine(this)
        nodeRepository.setMyNodeInfo(createMyNodeInfo())
        engine.startScan(testPresets, dwellDurationSeconds = 60)
        awaitDwell(engine)

        val niPacket = neighborInfoPacket(from = 7777, neighborNodeIds = listOf(101, 102, 103))
        engine.onPacketReceived(niPacket, dataPacket(from = 7777))

        engine.stopScan()

        val nodes = discoveryDao.discoveredNodes.values.toList()
        // Node 7777 (the sender) + 3 mesh neighbors
        val meshNodes = nodes.filter { it.neighborType == "mesh" }
        assertEquals(3, meshNodes.size, "All neighbor IDs from NeighborInfo should be recorded")
        assertTrue(meshNodes.map { it.nodeNum }.containsAll(listOf(101L, 102L, 103L)))
    }

    // endregion

    // region Helpers

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

    private fun positionPacket(from: Int, latI: Int, lonI: Int, snr: Float = 5.5f, rssi: Int = -70): MeshPacket {
        val posPayload = Position.ADAPTER.encode(Position(latitude_i = latI, longitude_i = lonI)).toByteString()
        val data = Data(portnum = PortNum.POSITION_APP, payload = posPayload)
        return MeshPacket(from = from, decoded = data, rx_snr = snr, rx_rssi = rssi)
    }

    private fun textMessagePacket(from: Int): MeshPacket {
        val data = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = "hello".encodeToByteArray().toByteString())
        return MeshPacket(from = from, decoded = data, rx_snr = 3.0f, rx_rssi = -80)
    }

    private fun neighborInfoPacket(from: Int, neighborNodeIds: List<Int>): MeshPacket {
        val neighbors = neighborNodeIds.map { Neighbor(node_id = it) }
        val ni = NeighborInfo(node_id = from, neighbors = neighbors)
        val payload = NeighborInfo.ADAPTER.encode(ni).toByteString()
        val data = Data(portnum = PortNum.NEIGHBORINFO_APP, payload = payload)
        return MeshPacket(from = from, decoded = data)
    }

    private fun dataPacket(from: Int) = DataPacket(
        to = NodeAddress.ID_BROADCAST,
        bytes = ByteString.EMPTY,
        dataType = PortNum.POSITION_APP.value,
        from = "!${from.toString(16)}",
        hopStart = 3,
        hopLimit = 3,
    )

    // endregion
}

// region Inline test doubles

private class PacketTestCollectorRegistry : DiscoveryPacketCollectorRegistry {
    override var collector: DiscoveryPacketCollector? = null
}

private class PacketTestAiProvider : DiscoverySummaryAiProvider {
    override val isAvailable: Boolean = false

    override suspend fun generateSessionSummary(
        session: DiscoverySessionEntity,
        presetResults: List<DiscoveryPresetResultEntity>,
    ): String? = null

    override suspend fun generatePresetSummary(result: DiscoveryPresetResultEntity): String? = null
}

private class InMemoryDiscoveryDao : DiscoveryDao {
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
        resultIds.forEach { rid ->
            discoveredNodes.entries.removeAll { it.value.presetResultId == rid }
            presetResults.remove(rid)
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

    override suspend fun getPresetResults(sessionId: Long) = presetResults.values.filter { it.sessionId == sessionId }

    override fun getPresetResultsFlow(sessionId: Long) =
        flowOf(presetResults.values.filter { it.sessionId == sessionId })

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

    override suspend fun getDiscoveredNodes(presetResultId: Long) =
        discoveredNodes.values.filter { it.presetResultId == presetResultId }

    override fun getDiscoveredNodesFlow(presetResultId: Long) =
        flowOf(discoveredNodes.values.filter { it.presetResultId == presetResultId })

    override suspend fun getUniqueNodeNums(sessionId: Long) = presetResults.values
        .filter { it.sessionId == sessionId }
        .flatMap { pr -> discoveredNodes.values.filter { it.presetResultId == pr.id } }
        .map { it.nodeNum }
        .distinct()

    override suspend fun getUniqueNodeCount(sessionId: Long) = getUniqueNodeNums(sessionId).size

    override suspend fun getMaxDistance(sessionId: Long) = presetResults.values
        .filter { it.sessionId == sessionId }
        .flatMap { pr -> discoveredNodes.values.filter { it.presetResultId == pr.id } }
        .mapNotNull { it.distanceFromUser }
        .maxOrNull()

    override suspend fun getSessionWithResults(sessionId: Long) = sessions[sessionId]

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
