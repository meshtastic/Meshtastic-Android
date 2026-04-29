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
@file:Suppress("TooManyFunctions", "MagicNumber")

package org.meshtastic.feature.discovery

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.core.repository.DiscoveryPacketCollector
import org.meshtastic.core.repository.DiscoveryPacketCollectorRegistry
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.discovery.ai.DiscoverySummaryAiProvider
import org.meshtastic.proto.Config
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry

/**
 * Core scan engine for Local Mesh Discovery.
 *
 * Cycles through a queue of LoRa presets, dwells on each for a configured duration while collecting packets, then
 * persists aggregated results via [DiscoveryDao].
 */
@Single
@Suppress("LongParameterList")
class DiscoveryScanEngine(
    private val radioController: RadioController,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val collectorRegistry: DiscoveryPacketCollectorRegistry,
    private val discoveryDao: DiscoveryDao,
    private val aiProvider: DiscoverySummaryAiProvider,
) : DiscoveryPacketCollector {

    // region Public state

    private val _scanState = MutableStateFlow<DiscoveryScanState>(DiscoveryScanState.Idle)
    val scanState: StateFlow<DiscoveryScanState> = _scanState.asStateFlow()

    private val _currentSession = MutableStateFlow<DiscoverySessionEntity?>(null)
    val currentSession: StateFlow<DiscoverySessionEntity?> = _currentSession.asStateFlow()

    override val isActive: Boolean
        get() = _scanState.value !is DiscoveryScanState.Idle && _scanState.value !is DiscoveryScanState.Complete

    // endregion

    // region Internal scan state

    private val mutex = Mutex()
    private var scanScope: CoroutineScope? = null
    private var dwellJob: Job? = null
    private var homePreset: ChannelOption? = null
    private var sessionId: Long = 0

    /** Nodes collected for the current preset dwell. Keyed by nodeNum. */
    private val collectedNodes = mutableMapOf<Long, CollectedNodeData>()

    /** DeviceMetrics entries per node for the 2-packet rule. Keyed by nodeNum. */
    private val deviceMetricsLog = mutableMapOf<Long, MutableList<DeviceMetricsEntry>>()

    private var currentPresetName: String = ""
    private var totalDwellSeconds: Long = 0

    // endregion

    // region Internal data classes

    private data class CollectedNodeData(
        var nodeNum: Long,
        var shortName: String? = null,
        var longName: String? = null,
        var neighborType: String = "direct",
        var latitude: Double? = null,
        var longitude: Double? = null,
        var snr: Float = 0f,
        var rssi: Int = 0,
        var hopCount: Int = 0,
        var messageCount: Int = 0,
        var sensorPacketCount: Int = 0,
    )

    private data class DeviceMetricsEntry(val timestamp: Long, val channelUtil: Double, val airUtilTx: Double)

    // endregion

    // region Public API

    /**
     * Starts a discovery scan across the given [presets].
     *
     * @param presets The LoRa presets to cycle through.
     * @param dwellDurationSeconds How long to listen on each preset.
     */
    suspend fun startScan(presets: List<ChannelOption>, dwellDurationSeconds: Long) {
        require(presets.isNotEmpty()) { "At least one preset is required" }
        require(dwellDurationSeconds > 0) { "Dwell duration must be positive" }

        mutex.withLock {
            if (isActive) {
                Logger.w { "DiscoveryScanEngine: scan already active, ignoring startScan" }
                return
            }

            // Capture the current LoRa preset as "home"
            homePreset =
                radioConfigRepository.localConfigFlow.first().lora?.modem_preset?.let { modemPreset ->
                    ChannelOption.entries.firstOrNull { it.modemPreset == modemPreset }
                } ?: ChannelOption.DEFAULT

            val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum
            val myPosition = myNodeNum?.let { nodeRepository.nodeDBbyNum.value[it]?.position }
            val latDouble = (myPosition?.latitude_i ?: 0).toDouble() / POSITION_DIVISOR
            val lonDouble = (myPosition?.longitude_i ?: 0).toDouble() / POSITION_DIVISOR

            // Create the DB session
            val session =
                DiscoverySessionEntity(
                    timestamp = nowMillis,
                    presetsScanned = presets.joinToString(",") { it.name },
                    homePreset = homePreset?.name ?: ChannelOption.DEFAULT.name,
                    completionStatus = "in_progress",
                    userLatitude = latDouble,
                    userLongitude = lonDouble,
                )
            sessionId = discoveryDao.insertSession(session)
            _currentSession.value = session.copy(id = sessionId)

            // Register as packet collector
            collectorRegistry.collector = this

            // Set initial state so the scan loop's isActive guard succeeds
            _scanState.value = DiscoveryScanState.Shifting(presets.first().name)
            currentPresetName = presets.first().name
            totalDwellSeconds = dwellDurationSeconds

            // Launch scan coroutine
            val scope = CoroutineScope(ioDispatcher + SupervisorJob())
            scanScope = scope
            scope.launch { runScanLoop(presets, dwellDurationSeconds) }
        }
    }

    /** Stops the active scan and restores the home preset. */
    suspend fun stopScan() {
        mutex.withLock {
            if (!isActive) return
            Logger.i { "DiscoveryScanEngine: stopping scan" }
            cancelScanInternal()
        }
        persistCurrentDwellResults()
        finalizeSession("stopped")
        _scanState.value = DiscoveryScanState.Idle

        // Restore home preset in the background so we don't block the UI with the connection wait
        CoroutineScope(Dispatchers.Default).launch { restoreHomePreset() }
    }

    /** Resets engine state after the UI has acknowledged completion. */
    fun reset() {
        _scanState.value = DiscoveryScanState.Idle
        _currentSession.value = null
    }

    // endregion

    // region DiscoveryPacketCollector

    @Suppress("CyclomaticComplexMethod", "ComplexCondition")
    override suspend fun onPacketReceived(meshPacket: MeshPacket, dataPacket: DataPacket) {
        if (_scanState.value !is DiscoveryScanState.Dwell) return
        val fromNum = meshPacket.from.toLong()
        val portNum = meshPacket.decoded?.portnum ?: return

        mutex.withLock {
            val node = collectedNodes.getOrPut(fromNum) { CollectedNodeData(nodeNum = fromNum) }
            // Update signal info from the direct packet
            if (meshPacket.rx_snr != 0f) node.snr = meshPacket.rx_snr
            if (meshPacket.rx_rssi != 0) node.rssi = meshPacket.rx_rssi
            node.hopCount = dataPacket.hopsAway.coerceAtLeast(0)

            when (portNum) {
                PortNum.TEXT_MESSAGE_APP -> node.messageCount++
                PortNum.POSITION_APP -> handlePosition(meshPacket, node)
                PortNum.TELEMETRY_APP -> handleTelemetry(meshPacket, node, fromNum)
                PortNum.NEIGHBORINFO_APP -> handleNeighborInfo(meshPacket)
                else -> {
                    /* Other portnums don't need special handling */
                }
            }

            // Ensure all nodes in the collection have names and position if available in the NodeDB
            collectedNodes.values.forEach { n ->
                val dbNode = nodeRepository.nodeDBbyNum.value[n.nodeNum.toInt()]
                if (dbNode != null) {
                    if (n.shortName == null || n.longName == null) {
                        n.shortName = dbNode.user.short_name.ifBlank { null }
                        n.longName = dbNode.user.long_name.ifBlank { null }
                    }
                    if (n.latitude == null || n.longitude == null || (n.latitude == 0.0 && n.longitude == 0.0)) {
                        val dbLat = dbNode.position.latitude_i
                        val dbLon = dbNode.position.longitude_i
                        if (dbLat != null && dbLat != 0) n.latitude = dbLat.toDouble() / POSITION_DIVISOR
                        if (dbLon != null && dbLon != 0) n.longitude = dbLon.toDouble() / POSITION_DIVISOR
                    }
                }
            }
        }
    }

    // endregion

    // region Scan loop

    @Suppress("ReturnCount")
    private suspend fun runScanLoop(presets: List<ChannelOption>, dwellDurationSeconds: Long) {
        for (preset in presets) {
            if (!isActive) return

            currentPresetName = preset.name
            mutex.withLock {
                collectedNodes.clear()
                deviceMetricsLog.clear()
            }
            totalDwellSeconds = dwellDurationSeconds

            // Shift to the new preset
            _scanState.value = DiscoveryScanState.Shifting(preset.name)
            shiftPreset(preset)

            // Wait for reconnection
            _scanState.value = DiscoveryScanState.Reconnecting(preset.name)
            val reconnected = waitForConnection()
            if (!reconnected) {
                cancelScanInternal()
                restoreHomePreset()
                finalizeSession("paused")
                _scanState.value = DiscoveryScanState.Idle
                return
            }

            // Dwell
            val dwellCompleted = runDwell(preset.name, dwellDurationSeconds)
            if (!dwellCompleted) {
                cancelScanInternal()
                restoreHomePreset()
                finalizeSession("paused")
                _scanState.value = DiscoveryScanState.Idle
                return
            }
            if (!isActive) return

            // Persist this preset's results
            persistCurrentDwellResults()
        }

        // All presets scanned
        _scanState.value = DiscoveryScanState.Analysis
        restoreHomePreset()
        generateAiSummaries()
        finalizeSession("complete")
        _scanState.value = DiscoveryScanState.Complete
    }

    private suspend fun shiftPreset(preset: ChannelOption) {
        val loraConfig = Config.LoRaConfig(use_preset = true, modem_preset = preset.modemPreset)
        val config = Config(lora = loraConfig)
        radioController.setLocalConfig(config)
        Logger.i { "DiscoveryScanEngine: shifted to ${preset.name} (use_preset=true)" }
        // The firmware often restarts the radio or reboots after a LoRa config change.
        // Wait a short moment to ensure we don't consider it 'connected' right before it drops.
        delay(3000)
    }

    private suspend fun waitForConnection(): Boolean {
        val result =
            withTimeoutOrNull(RECONNECT_TIMEOUT_MS) {
                serviceRepository.connectionState.first { it is ConnectionState.Connected }
            }
        return result != null
    }

    private suspend fun runDwell(presetName: String, durationSeconds: Long): Boolean {
        var remaining = durationSeconds
        while (remaining > 0 && isActive) {
            val isConnected = serviceRepository.connectionState.value is ConnectionState.Connected
            if (!isConnected) {
                _scanState.value = DiscoveryScanState.Reconnecting(presetName)
                val reconnected = waitForConnection()
                if (!reconnected) return false
                continue
            }

            _scanState.value =
                DiscoveryScanState.Dwell(
                    presetName = presetName,
                    remainingSeconds = remaining,
                    totalSeconds = durationSeconds,
                )
            delay(TICK_INTERVAL_MS)
            remaining--
        }
        return true
    }

    // endregion

    // region Packet handlers

    private fun handlePosition(meshPacket: MeshPacket, node: CollectedNodeData) {
        val payload = meshPacket.decoded?.payload ?: return
        val pos = Position.ADAPTER.decodeOrNull(payload, Logger) ?: return
        val lat = pos.latitude_i
        val lon = pos.longitude_i
        if (lat != null && lat != 0) node.latitude = lat / POSITION_DIVISOR
        if (lon != null && lon != 0) node.longitude = lon / POSITION_DIVISOR
    }

    private fun handleTelemetry(meshPacket: MeshPacket, node: CollectedNodeData, fromNum: Long) {
        val payload = meshPacket.decoded?.payload ?: return
        val telemetry = Telemetry.ADAPTER.decodeOrNull(payload, Logger) ?: return

        val deviceMetrics = telemetry.device_metrics
        if (deviceMetrics != null) {
            val entries = deviceMetricsLog.getOrPut(fromNum) { mutableListOf() }
            entries.add(
                DeviceMetricsEntry(
                    timestamp = nowMillis,
                    channelUtil = deviceMetrics.channel_utilization?.toDouble() ?: 0.0,
                    airUtilTx = deviceMetrics.air_util_tx?.toDouble() ?: 0.0,
                ),
            )
        }

        if (telemetry.environment_metrics != null) {
            node.sensorPacketCount++
        }
    }

    private fun handleNeighborInfo(meshPacket: MeshPacket) {
        val payload = meshPacket.decoded?.payload ?: return
        val ni = NeighborInfo.ADAPTER.decodeOrNull(payload, Logger) ?: return
        for (neighbor in ni.neighbors) {
            val neighborNum = neighbor.node_id.toLong()
            val node =
                collectedNodes.getOrPut(neighborNum) { CollectedNodeData(nodeNum = neighborNum, neighborType = "mesh") }
            // Only mark as mesh if not already seen directly
            if (node.snr == 0f && node.rssi == 0) {
                node.neighborType = "mesh"
            }
        }
    }

    // endregion

    // region Persistence

    @Suppress("ReturnCount")
    private suspend fun generateAiSummaries() {
        if (sessionId == 0L || !aiProvider.isAvailable) return
        val session = discoveryDao.getSession(sessionId) ?: return
        val presetResults = discoveryDao.getPresetResults(sessionId)
        if (presetResults.isEmpty()) return

        // Generate per-preset AI summaries
        for (result in presetResults) {
            val presetSummary = aiProvider.generatePresetSummary(result)
            if (presetSummary != null) {
                discoveryDao.updatePresetResult(result.copy(aiSummary = presetSummary))
            }
        }

        // Generate session-level AI summary
        val sessionSummary = aiProvider.generateSessionSummary(session, presetResults)
        if (sessionSummary != null) {
            discoveryDao.updateSession(session.copy(aiSummary = sessionSummary))
        }
    }

    private suspend fun persistCurrentDwellResults() {
        if (sessionId == 0L) return
        mutex.withLock {
            if (collectedNodes.isEmpty()) {
                // Persist a zero-result entry so the preset appears in reports
                val emptyResult =
                    DiscoveryPresetResultEntity(
                        sessionId = sessionId,
                        presetName = currentPresetName,
                        dwellDurationSeconds = totalDwellSeconds,
                    )
                discoveryDao.insertPresetResult(emptyResult)
                return
            }

            val (avgChannelUtil, avgAirUtil) = computeAverageMetrics()
            val directCount = collectedNodes.values.count { it.neighborType == "direct" }
            val meshCount = collectedNodes.values.count { it.neighborType == "mesh" }

            val presetResult =
                DiscoveryPresetResultEntity(
                    sessionId = sessionId,
                    presetName = currentPresetName,
                    dwellDurationSeconds = totalDwellSeconds,
                    uniqueNodes = collectedNodes.size,
                    directNeighborCount = directCount,
                    meshNeighborCount = meshCount,
                    messageCount = collectedNodes.values.sumOf { it.messageCount },
                    sensorPacketCount = collectedNodes.values.sumOf { it.sensorPacketCount },
                    avgChannelUtilization = avgChannelUtil,
                    avgAirtimeRate = avgAirUtil,
                )
            val presetResultId = discoveryDao.insertPresetResult(presetResult)

            val nodeEntities =
                collectedNodes.values.map { data ->
                    DiscoveredNodeEntity(
                        presetResultId = presetResultId,
                        nodeNum = data.nodeNum,
                        shortName = data.shortName,
                        longName = data.longName,
                        neighborType = data.neighborType,
                        latitude = data.latitude,
                        longitude = data.longitude,
                        hopCount = data.hopCount,
                        snr = data.snr,
                        rssi = data.rssi,
                        messageCount = data.messageCount,
                        sensorPacketCount = data.sensorPacketCount,
                    )
                }
            discoveryDao.insertDiscoveredNodes(nodeEntities)
        }
    }

    /**
     * Computes average channel utilization and airtime from DeviceMetrics, applying the 2-packet rule (only nodes with
     * ≥2 reports count).
     */
    private fun computeAverageMetrics(): Pair<Double, Double> {
        val qualifiedEntries = deviceMetricsLog.values.filter { it.size >= MIN_DEVICE_METRICS_PACKETS }
        if (qualifiedEntries.isEmpty()) return 0.0 to 0.0

        val avgChannel = qualifiedEntries.map { entries -> entries.map { it.channelUtil }.average() }.average()
        val avgAir = qualifiedEntries.map { entries -> entries.map { it.airUtilTx }.average() }.average()
        return avgChannel to avgAir
    }

    private suspend fun finalizeSession(status: String) {
        if (sessionId == 0L) return
        val uniqueCount = discoveryDao.getUniqueNodeCount(sessionId)
        val presetResults = discoveryDao.getPresetResults(sessionId)
        val session = discoveryDao.getSession(sessionId) ?: return
        val totalDwell = presetResults.sumOf { it.dwellDurationSeconds }
        val totalMsgs = presetResults.sumOf { it.messageCount }
        val totalSensor = presetResults.sumOf { it.sensorPacketCount }
        val avgChanUtil =
            presetResults
                .filter { it.uniqueNodes > 0 }
                .map { it.avgChannelUtilization }
                .average()
                .takeIf { !it.isNaN() } ?: 0.0
        discoveryDao.updateSession(
            session.copy(
                totalUniqueNodes = uniqueCount,
                totalDwellSeconds = totalDwell,
                totalMessages = totalMsgs,
                totalSensorPackets = totalSensor,
                avgChannelUtilization = avgChanUtil,
                completionStatus = status,
            ),
        )
        _currentSession.value = discoveryDao.getSession(sessionId)
    }

    // endregion

    // region Home preset restoration

    private suspend fun restoreHomePreset() {
        val preset = homePreset ?: return
        shiftPreset(preset)
        // Wait briefly for reconnection after restoring
        waitForConnection()
    }

    // endregion

    // region Lifecycle helpers

    private fun cancelScanInternal() {
        collectorRegistry.collector = null
        dwellJob?.cancel()
        dwellJob = null
        scanScope?.cancel()
        scanScope = null
    }

    // endregion

    companion object {
        private const val RECONNECT_TIMEOUT_MS = 60_000L
        private const val TICK_INTERVAL_MS = 1_000L
        private const val POSITION_DIVISOR = 1e7
        private const val MIN_DEVICE_METRICS_PACKETS = 2
    }
}
