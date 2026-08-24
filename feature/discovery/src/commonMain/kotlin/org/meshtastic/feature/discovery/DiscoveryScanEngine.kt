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
@file:Suppress("TooManyFunctions", "MagicNumber")

package org.meshtastic.feature.discovery

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.util.latLongToMeter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.core.model.util.snrOrNull
import org.meshtastic.core.repository.DiscoveryPacketCollector
import org.meshtastic.core.repository.DiscoveryPacketCollectorRegistry
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.discovery.ai.DiscoverySummaryAiProvider
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
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
@Suppress("LargeClass", "LongParameterList")
class DiscoveryScanEngine(
    private val radioController: RadioController,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val collectorRegistry: DiscoveryPacketCollectorRegistry,
    private val discoveryDao: DiscoveryDao,
    private val aiProvider: DiscoverySummaryAiProvider,
    private val applicationScope: ApplicationCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val meshPrefs: MeshPrefs,
) : DiscoveryPacketCollector {

    // region Public state

    private val _scanState = MutableStateFlow<DiscoveryScanState>(DiscoveryScanState.Idle)
    val scanState: StateFlow<DiscoveryScanState> = _scanState.asStateFlow()

    private val _currentSession = MutableStateFlow<DiscoverySessionEntity?>(null)
    val currentSession: StateFlow<DiscoverySessionEntity?> = _currentSession.asStateFlow()

    override val isActive: Boolean
        get() =
            _scanState.value !is DiscoveryScanState.Idle &&
                _scanState.value !is DiscoveryScanState.Complete &&
                _scanState.value !is DiscoveryScanState.Failed

    // endregion

    // region Internal scan state

    private val mutex = Mutex()
    private val homeRestorer =
        DiscoveryHomeRestorer(radioController, serviceRepository, discoveryDao, applicationScope, meshPrefs)
    private val terminalCoordinator =
        DiscoveryTerminalCoordinator(
            discoveryDao = discoveryDao,
            homeRestorer = homeRestorer,
            applicationScope = applicationScope,
            onSessionUpdated = { updated -> _currentSession.value = updated },
            onTerminalCompleted = { outcome -> _scanState.value = DiscoveryScanState.Complete(outcome) },
            cancelScan = { mutex.withLock { cancelScanInternal() } },
        )
    private val interruptedSessionRecovery =
        DiscoveryInterruptedSessionRecovery(
            serviceRepository = serviceRepository,
            discoveryDao = discoveryDao,
            meshPrefs = meshPrefs,
            isScanActive = { mutex.withLock { isActive } },
            scheduleRestoreIfIdle = { session ->
                mutex.withLock {
                    if (isActive || meshPrefs.deviceAddress.value != session.deviceAddress) {
                        null
                    } else {
                        homeRestorer.schedulePersistedSession(session)
                    }
                }
            },
        )
    private var scanScope: CoroutineScope? = null
    private var dwellJob: Job? = null
    private var originalLoRaConfig: Config.LoRaConfig? = null

    /** The radio's primary channel at scan start; restored after a custom-channel target overwrites it (FR-005). */
    private var originalPrimaryChannel: ChannelSettings? = null

    /** True once a custom-channel target has retuned the primary channel, so restore only writes when needed. */
    private var tunedPrimaryChannel: Boolean = false
    private var sessionId: Long = 0

    /** Nodes collected for the current preset dwell. Keyed by nodeNum. */
    private val collectedNodes = mutableMapOf<Long, CollectedNodeData>()

    /** DeviceMetrics entries per node for the 2-packet rule. Keyed by nodeNum. */
    private val deviceMetricsLog = mutableMapOf<Long, MutableList<DeviceMetricsEntry>>()

    private var currentPresetName: String = ""
    private var totalDwellSeconds: Long = 0

    /** Guards exact-once result insertion when terminal cleanup races normal dwell completion. Protected by [mutex]. */
    private var currentDwellPersisted: Boolean = false
    private var lastLocalStats: org.meshtastic.proto.LocalStats? = null

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
        /** Null until a packet reports one, so an absent reading stays distinct from a valid 0 dBm. */
        var rssi: Int? = null,
        var hopCount: Int = 0,
        var messageCount: Int = 0,
        var sensorPacketCount: Int = 0,
        var isInfrastructure: Boolean = false,
    )

    private data class DeviceMetricsEntry(val timestamp: Long, val channelUtil: Double, val airUtilTx: Double)

    // endregion

    // region Public API

    /** Convenience overload: scan a list of public presets (each becomes a preset [ScanTarget] labelled by name). */
    suspend fun startScan(presets: List<ChannelOption>, dwellDurationSeconds: Long) =
        startScanTargets(presets.map { ScanTarget(preset = it, label = it.name) }, dwellDurationSeconds)

    /**
     * Starts a discovery scan across the given [targets] — public presets and/or beacon-advertised custom channels.
     * (Distinct name from the [ChannelOption] overload: both would erase to the same JVM signature otherwise.)
     *
     * @param targets The scan queue ([ScanTarget]).
     * @param dwellDurationSeconds How long to listen on each target.
     */
    suspend fun startScanTargets(targets: List<ScanTarget>, dwellDurationSeconds: Long) {
        require(targets.isNotEmpty()) { "At least one scan target is required" }
        require(dwellDurationSeconds > 0) { "Dwell duration must be positive" }

        if (!prepareScanStart(meshPrefs.deviceAddress.value)) return

        // These repository flows may suspend while their initial snapshots load. Keep that wait outside the engine
        // mutex so stop/reset and packet collection remain responsive, then revalidate device and restore ownership
        // under the mutex before publishing any scan state. Capture the baseline after admission waits so a transport
        // restart during those waits is not misreported as a change that happened during snapshot preparation.
        val deviceAddress = meshPrefs.deviceAddress.value
        val sessionGeneration = radioController.sessionGeneration.value
        val snapshot = readInitialConfigSnapshot()
        if (snapshot == null) {
            refuseScan("Radio configuration snapshot is not available")
            return
        }
        val (homeLora, initialPrimaryChannel) = snapshot
        mutex.withLock {
            if (isActive) {
                Logger.w { "DiscoveryScanEngine: scan already active, ignoring startScan" }
            } else if (!isScanPreparationCurrent(deviceAddress, sessionGeneration)) {
                _scanState.value = DiscoveryScanState.Failed("Selected radio changed while preparing the scan")
            } else if (homeRestorer.hasUnsatisfiedRestoreFor(meshPrefs.deviceAddress.value)) {
                // Interrupted-session recovery registers its restore under this mutex. Re-check here so a restore that
                // won the race after the first wait cannot retune the radio underneath this scan.
                _scanState.value = DiscoveryScanState.Failed("Home configuration restoration is incomplete")
            } else {
                _scanState.value = DiscoveryScanState.Preparing
                val requiresPrimaryRestore = targets.any { it.channel != null }
                when {
                    homeLora == null -> refuseUnrestorableScanLocked("Home LoRa configuration is not available")

                    initialPrimaryChannel == null && requiresPrimaryRestore ->
                        refuseUnrestorableScanLocked("Primary channel is not available for a custom-channel scan")

                    else ->
                        prepareScanLocked(
                            targets = targets,
                            dwellDurationSeconds = dwellDurationSeconds,
                            initialLoraConfig = homeLora,
                            initialPrimaryChannel = initialPrimaryChannel,
                            requiresPrimaryRestore = requiresPrimaryRestore,
                            deviceAddress = deviceAddress,
                            sessionGeneration = sessionGeneration,
                        )
                }
            }
        }
    }

    private suspend fun prepareScanStart(deviceAddress: String?): Boolean = when {
        deviceAddress == null -> {
            refuseScan("Selected radio is not available")
            false
        }

        !terminalCoordinator.resetForScan() -> {
            refuseScan("Previous discovery scan cleanup is still running")
            false
        }

        !homeRestorer.awaitBeforeScan(deviceAddress) -> {
            refuseScan("Home configuration restoration is incomplete")
            false
        }

        else -> true
    }

    private suspend fun readInitialConfigSnapshot(): Pair<Config.LoRaConfig?, ChannelSettings?>? =
        withTimeoutOrNull(CONFIG_SNAPSHOT_TIMEOUT_MS) {
            radioConfigRepository.localConfigFlow.first().lora to
                radioConfigRepository.channelSetFlow.first().settings.firstOrNull()
        }

    private suspend fun refuseScan(reason: String) {
        mutex.withLock { if (!isActive) _scanState.value = DiscoveryScanState.Failed(reason) }
    }

    private fun refuseUnrestorableScanLocked(reason: String) {
        Logger.w { "DiscoveryScanEngine: refusing unrestorable scan: $reason" }
        _scanState.value = DiscoveryScanState.Failed(reason)
    }

    private suspend fun prepareScanLocked(
        targets: List<ScanTarget>,
        dwellDurationSeconds: Long,
        initialLoraConfig: Config.LoRaConfig,
        initialPrimaryChannel: ChannelSettings?,
        requiresPrimaryRestore: Boolean,
        deviceAddress: String?,
        sessionGeneration: Long,
    ) {
        originalLoRaConfig = initialLoraConfig
        originalPrimaryChannel = initialPrimaryChannel
        tunedPrimaryChannel = false
        val homePresetStr =
            if (initialLoraConfig.use_preset) {
                ChannelOption.from(initialLoraConfig.modem_preset)?.name ?: ChannelOption.DEFAULT.name
            } else {
                "CUSTOM"
            }
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum
        val myPosition = myNodeNum?.let { nodeRepository.nodeDBbyNum.value[it]?.position }
        val session =
            DiscoverySessionEntity(
                timestamp = nowMillis,
                presetsScanned = targets.joinToString(",") { it.label },
                homePreset = homePresetStr,
                completionStatus = DiscoverySessionStatus.IN_PROGRESS,
                userLatitude = (myPosition?.latitude_i ?: 0).toDouble() / POSITION_DIVISOR,
                userLongitude = (myPosition?.longitude_i ?: 0).toDouble() / POSITION_DIVISOR,
                deviceAddress = deviceAddress,
                homeLoraConfig = initialLoraConfig,
                homePrimaryChannel = initialPrimaryChannel.takeIf { requiresPrimaryRestore },
            )
        val insertedSessionId = discoveryDao.insertSession(session)
        if (!isScanPreparationCurrent(deviceAddress, sessionGeneration)) {
            discoveryDao.deleteSession(insertedSessionId)
            sessionId = 0L
            originalLoRaConfig = null
            originalPrimaryChannel = null
            _scanState.value = DiscoveryScanState.Failed("Selected radio changed while preparing the scan")
            return
        }
        sessionId = insertedSessionId
        _currentSession.value = session.copy(id = sessionId)
        collectorRegistry.collector = this
        _scanState.value = DiscoveryScanState.Shifting(targets.first().label)
        currentPresetName = targets.first().label
        totalDwellSeconds = dwellDurationSeconds
        currentDwellPersisted = false
        CoroutineScope(dispatchers.io + SupervisorJob()).also { scope ->
            scanScope = scope
            scope.launch { runScanLoop(targets, dwellDurationSeconds) }
        }
    }

    private fun isScanPreparationCurrent(deviceAddress: String?, sessionGeneration: Long): Boolean =
        meshPrefs.deviceAddress.value == deviceAddress &&
            radioController.sessionGeneration.value == sessionGeneration &&
            serviceRepository.connectionState.value is ConnectionState.Connected

    /** Stops the active scan and restores the home preset. */
    suspend fun stopScan() {
        val request =
            mutex.withLock {
                if (!isActive) {
                    null
                } else if (_scanState.value is DiscoveryScanState.Analysis) {
                    Logger.i { "DiscoveryScanEngine: ignoring stop after terminal analysis has started" }
                    null
                } else {
                    if (_scanState.value !is DiscoveryScanState.Cancelling) {
                        Logger.i { "DiscoveryScanEngine: stopping scan" }
                        _scanState.value = DiscoveryScanState.Cancelling
                    }
                    // Freeze the scan generation before snapshotting its restore plan.
                    // No later target shift may race this terminal request.
                    cancelScanInternal()
                    terminalRequestLocked(
                        pendingStatus = DiscoverySessionStatus.RESTORE_PENDING_STOPPED,
                        outcome = DiscoveryScanState.CompletionOutcome.Cancelled,
                        awaitRestore = false,
                        generateAi = false,
                    )
                }
            }
        if (request != null) {
            terminalCoordinator.complete(request = request, beforeFinalize = ::persistCurrentDwellResults)
        }
    }

    /** Resets engine state after the UI has acknowledged completion. */
    fun reset() {
        if (!mutex.tryLock()) {
            Logger.w { "DiscoveryScanEngine: ignoring reset while scan state is changing" }
            return
        }
        try {
            if (isActive) {
                Logger.w { "DiscoveryScanEngine: ignoring reset while scan cleanup is still active" }
                return
            }
            _currentSession.value = null
            _scanState.value = DiscoveryScanState.Idle
        } finally {
            mutex.unlock()
        }
    }

    // endregion

    // region DiscoveryPacketCollector

    override suspend fun onPacketReceived(meshPacket: MeshPacket, dataPacket: DataPacket) {
        if (_scanState.value !is DiscoveryScanState.Dwell) return
        val fromNum = meshPacket.from.toLong()
        val portNum = meshPacket.decoded?.portnum ?: return

        mutex.withLock {
            val node = collectedNodes.getOrPut(fromNum) { CollectedNodeData(nodeNum = fromNum) }
            // Update signal info from the direct packet
            // Explicit presence: record a reported 0 dB/0 dBm, skip only a genuinely absent one.
            meshPacket.snrOrNull()?.let { node.snr = it }
            meshPacket.rx_rssi?.let { node.rssi = it }
            node.hopCount = dataPacket.hopsAway.coerceAtLeast(0)

            when (portNum) {
                PortNum.TEXT_MESSAGE_APP -> node.messageCount++
                PortNum.POSITION_APP -> handlePosition(meshPacket, node)
                PortNum.TELEMETRY_APP -> handleTelemetry(meshPacket, node, fromNum)
                PortNum.NEIGHBORINFO_APP -> handleNeighborInfo(meshPacket)
                else -> Unit
            }

            // Enrich the sending node from the local NodeDB (names/position fallback)
            enrichNodeFromDb(node)
        }
    }

    /** Backfills name, position, and infrastructure role from the local NodeDB when not yet received over-the-air. */
    private fun enrichNodeFromDb(node: CollectedNodeData) {
        val dbNode = nodeRepository.nodeDBbyNum.value[node.nodeNum.toInt()] ?: return
        if (node.shortName == null || node.longName == null) {
            node.shortName = dbNode.user.short_name.ifBlank { null }
            node.longName = dbNode.user.long_name.ifBlank { null }
        }
        if (!hasValidCoordinates(node.latitude, node.longitude)) {
            val dbLat = dbNode.position.latitude_i
            val dbLon = dbNode.position.longitude_i
            if (dbLat != null && dbLat != 0) node.latitude = dbLat.toDouble() / POSITION_DIVISOR
            if (dbLon != null && dbLon != 0) node.longitude = dbLon.toDouble() / POSITION_DIVISOR
        }
        node.isInfrastructure = dbNode.user.role in INFRASTRUCTURE_ROLES
    }

    // endregion

    // region Scan loop

    @Suppress("ReturnCount")
    private suspend fun runScanLoop(targets: List<ScanTarget>, dwellDurationSeconds: Long) {
        for (target in targets) {
            if (!isActive) return

            val shouldShift =
                mutex.withLock {
                    if (!canAdvanceScanLocked()) {
                        false
                    } else {
                        currentPresetName = target.label
                        totalDwellSeconds = dwellDurationSeconds
                        currentDwellPersisted = false
                        collectedNodes.clear()
                        deviceMetricsLog.clear()
                        lastLocalStats = null
                        _scanState.value = DiscoveryScanState.Shifting(target.label)
                        true
                    }
                }
            if (!shouldShift) return

            // Shift to the new target (preset, plus a custom primary channel for beacon-channel targets)
            try {
                shiftTarget(target)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.e(e) { "DiscoveryScanEngine: target shift failed; aborting scan and restoring home config" }
                pauseAndAbort()
                return
            }

            // Wait for reconnection
            if (!transitionScanState(DiscoveryScanState.Reconnecting(target.label))) return
            if (!waitForConnection()) {
                pauseAndAbort(persistPartialDwell = true)
                return
            }

            // Request neighbor info at dwell start to seed mesh topology data (D020)
            requestNeighborInfoAtDwellBoundary()

            // Dwell
            if (!runDwell(target.label, dwellDurationSeconds)) {
                pauseAndAbort(persistPartialDwell = true)
                return
            }
            if (!isActive) return

            // Persist this target's results
            persistCurrentDwellResults()
        }

        // Elect normal completion under the same mutex used by stopScan so a late stop cannot replace its outcome.
        val request =
            mutex.withLock {
                collectorRegistry.collector = null
                _scanState.value = DiscoveryScanState.Analysis
                terminalRequestLocked(
                    pendingStatus = DiscoverySessionStatus.RESTORE_PENDING_COMPLETE,
                    outcome = DiscoveryScanState.CompletionOutcome.Success,
                    awaitRestore = true,
                    generateAi = true,
                )
            }
        // complete() cancels scanScope before terminal cleanup finishes. This scan coroutine ends inside the call;
        // follow-up work belongs in terminal-coordinator callbacks, not after this invocation.
        terminalCoordinator.complete(request = request, generateAi = ::generateAiSummaries)
    }

    /** Common cleanup path when a scan step fails mid-loop. */
    private suspend fun pauseAndAbort(persistPartialDwell: Boolean = false) {
        val request =
            mutex.withLock {
                if (_scanState.value is DiscoveryScanState.Cancelling) {
                    null
                } else {
                    _scanState.value = DiscoveryScanState.Analysis
                    terminalRequestLocked(
                        pendingStatus = DiscoverySessionStatus.RESTORE_PENDING_FAILED,
                        outcome = DiscoveryScanState.CompletionOutcome.Failed,
                        awaitRestore = false,
                        generateAi = false,
                    )
                }
            }
        if (request == null) return
        if (persistPartialDwell) {
            terminalCoordinator.complete(request = request, beforeFinalize = ::persistCurrentDwellResults)
        } else {
            terminalCoordinator.complete(request = request)
        }
    }

    private suspend fun shiftTarget(target: ScanTarget) {
        // Start from the captured original config so unrelated fields (hop_limit, tx_power, tx_enabled, …) are carried
        // over instead of zeroed by a fresh LoRaConfig — a from-scratch config can e.g. break the dwell-boundary
        // NeighborInfo request. Only the preset (and, for custom channels, region + channel_num) is overridden.
        val base = originalLoRaConfig ?: Config.LoRaConfig()
        if (target.channel == null) {
            // Public-preset target — dwell on the preset using the radio's existing primary channel (unchanged).
            radioController.setLocalConfig(
                Config(lora = base.copy(use_preset = true, modem_preset = target.preset.modemPreset)),
            )
            Logger.i { "DiscoveryScanEngine: shifted to ${target.label} (use_preset=true)" }
        } else {
            // Beacon custom-channel target: apply the offered preset+region, reset channel_num so firmware derives the
            // frequency from the new name, then tune the primary channel to the offered name+PSK so nodes on that mesh
            // are heard. The original primary channel is restored after the scan.
            radioController.setLocalConfig(
                Config(
                    lora =
                    base.copy(
                        use_preset = true,
                        modem_preset = target.preset.modemPreset,
                        region = target.region ?: base.region,
                        channel_num = 0,
                    ),
                ),
            )
            currentCoroutineContext().ensureActive()
            mutex.withLock { tunedPrimaryChannel = true }
            radioController.setLocalChannel(Channel(index = 0, role = Channel.Role.PRIMARY, settings = target.channel))
            Logger.i { "DiscoveryScanEngine: shifted to ${target.label} (custom channel)" }
        }
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

    /**
     * Requests NeighborInfo from the local node at each dwell boundary to seed mesh topology data. The response arrives
     * via the normal packet pipeline → [handleNeighborInfo].
     */
    private suspend fun requestNeighborInfoAtDwellBoundary() {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        val packetId = radioController.generatePacketId()
        try {
            radioController.requestNeighborInfo(packetId, myNodeNum)
            Logger.d { "DiscoveryScanEngine: requested NeighborInfo from local node $myNodeNum (packetId=$packetId)" }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "DiscoveryScanEngine: NeighborInfo request failed; continuing the dwell" }
        }
    }

    private suspend fun runDwell(presetName: String, durationSeconds: Long): Boolean {
        var remaining = durationSeconds
        var canContinue = true
        while (remaining > 0 && canContinue) {
            val isConnected = serviceRepository.connectionState.value is ConnectionState.Connected
            if (!isConnected) {
                canContinue = transitionScanState(DiscoveryScanState.Reconnecting(presetName)) && waitForConnection()
            } else {
                val dwellState =
                    DiscoveryScanState.Dwell(
                        presetName = presetName,
                        remainingSeconds = remaining,
                        totalSeconds = durationSeconds,
                    )
                canContinue = transitionScanState(dwellState)
                if (canContinue) {
                    delay(TICK_INTERVAL_MS)
                    remaining--
                }
            }
        }
        return canContinue
    }

    /** Caller must hold [mutex]. Terminal states are intentionally excluded even though Cancelling is active. */
    private fun canAdvanceScanLocked(): Boolean = isActive &&
        _scanState.value !is DiscoveryScanState.Cancelling &&
        _scanState.value !is DiscoveryScanState.Analysis

    private suspend fun transitionScanState(state: DiscoveryScanState): Boolean = mutex.withLock {
        if (!canAdvanceScanLocked()) return@withLock false
        _scanState.value = state
        true
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

        if (telemetry.local_stats != null) {
            lastLocalStats = telemetry.local_stats
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
            if (node.snr == 0f && node.rssi == null) {
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
            if (currentDwellPersisted) return@withLock
            if (collectedNodes.isEmpty()) {
                persistEmptyPresetResult()
                currentDwellPersisted = true
            } else {
                val presetResultId = persistPresetResult()
                persistDiscoveredNodes(presetResultId)
                currentDwellPersisted = true
            }
        }
    }

    private suspend fun persistEmptyPresetResult() {
        val emptyResult =
            DiscoveryPresetResultEntity(
                sessionId = sessionId,
                presetName = currentPresetName,
                dwellDurationSeconds = totalDwellSeconds,
            )
        discoveryDao.insertPresetResult(emptyResult)
    }

    private suspend fun persistPresetResult(): Long {
        val (avgChannelUtil, avgAirUtil) = computeAverageMetrics()
        val directCount = collectedNodes.values.count { it.neighborType == "direct" }
        val meshCount = collectedNodes.values.count { it.neighborType == "mesh" }
        val infraCount = collectedNodes.values.count { it.isInfrastructure }

        val packetsRx = lastLocalStats?.num_packets_rx ?: 0
        val packetsRxBad = lastLocalStats?.num_packets_rx_bad ?: 0
        val (successRate, failureRate) = computePacketRates(packetsRx, packetsRxBad)

        val presetResult =
            DiscoveryPresetResultEntity(
                sessionId = sessionId,
                presetName = currentPresetName,
                dwellDurationSeconds = totalDwellSeconds,
                uniqueNodes = collectedNodes.size,
                directNeighborCount = directCount,
                meshNeighborCount = meshCount,
                infrastructureNodeCount = infraCount,
                messageCount = collectedNodes.values.sumOf { it.messageCount },
                sensorPacketCount = collectedNodes.values.sumOf { it.sensorPacketCount },
                avgChannelUtilization = avgChannelUtil,
                avgAirtimeRate = avgAirUtil,
                packetSuccessRate = successRate,
                packetFailureRate = failureRate,
                numPacketsTx = lastLocalStats?.num_packets_tx ?: 0,
                numPacketsRx = packetsRx,
                numPacketsRxBad = packetsRxBad,
                numRxDupe = lastLocalStats?.num_rx_dupe ?: 0,
                numTxRelay = lastLocalStats?.num_tx_relay ?: 0,
                numTxRelayCanceled = lastLocalStats?.num_tx_relay_canceled ?: 0,
                numOnlineNodes = lastLocalStats?.num_online_nodes ?: 0,
                numTotalNodes = lastLocalStats?.num_total_nodes ?: 0,
                uptimeSeconds = lastLocalStats?.uptime_seconds ?: 0,
            )
        return discoveryDao.insertPresetResult(presetResult)
    }

    /**
     * Computes packet success and failure rates as percentages (0–100) from LocalStats counters. Returns (successRate,
     * failureRate). Both are 0.0 if no packets were received.
     */
    private fun computePacketRates(packetsRx: Int, packetsRxBad: Int): Pair<Double, Double> {
        if (packetsRx <= 0) return 0.0 to 0.0
        val failureRate = (packetsRxBad.toDouble() / packetsRx) * PERCENT_MULTIPLIER
        val successRate = PERCENT_MULTIPLIER - failureRate
        return successRate to failureRate
    }

    private suspend fun persistDiscoveredNodes(presetResultId: Long) {
        val session = discoveryDao.getSession(sessionId)
        val userLat = session?.userLatitude ?: 0.0
        val userLon = session?.userLongitude ?: 0.0

        val nodeEntities = collectedNodes.values.map { data -> data.toEntity(presetResultId, userLat, userLon) }
        discoveryDao.insertDiscoveredNodes(nodeEntities)
    }

    private fun CollectedNodeData.toEntity(
        presetResultId: Long,
        userLat: Double,
        userLon: Double,
    ): DiscoveredNodeEntity {
        val distance =
            if (hasValidCoordinates(latitude, longitude) && hasValidCoordinates(userLat, userLon)) {
                latLongToMeter(userLat, userLon, latitude!!, longitude!!)
            } else {
                null
            }
        return DiscoveredNodeEntity(
            presetResultId = presetResultId,
            nodeNum = nodeNum,
            shortName = shortName,
            longName = longName,
            neighborType = neighborType,
            latitude = latitude,
            longitude = longitude,
            distanceFromUser = distance,
            hopCount = hopCount,
            snr = snr,
            rssi = rssi,
            messageCount = messageCount,
            sensorPacketCount = sensorPacketCount,
            isInfrastructure = isInfrastructure,
        )
    }

    /** Returns true if both [lat] and [lon] are non-null and non-zero (i.e. a valid GPS fix). */
    private fun hasValidCoordinates(lat: Double?, lon: Double?): Boolean =
        lat != null && lon != null && lat != 0.0 && lon != 0.0

    /**
     * Computes average channel utilization and airtime from DeviceMetrics, applying the 2-packet rule (only nodes with
     * ≥2 reports count).
     */
    private fun computeAverageMetrics(): Pair<Double, Double> {
        val qualifiedEntries = deviceMetricsLog.values.filter { it.size >= MIN_DEVICE_METRICS_PACKETS }
        if (qualifiedEntries.isEmpty()) return 0.0 to 0.0

        val avgChannel = qualifiedEntries.map { entries -> entries.map { it.channelUtil }.average() }.average()

        // Compute Airtime Rate as (delta air_util_tx / elapsed_time_hours) to match Apple spec FR-008
        val avgAirRate =
            qualifiedEntries
                .mapNotNull { entries ->
                    val first = entries.first()
                    val last = entries.last()
                    val deltaAir = last.airUtilTx - first.airUtilTx
                    val deltaTimeMs = last.timestamp - first.timestamp
                    if (deltaTimeMs > 0) {
                        deltaAir / (deltaTimeMs / 3600000.0)
                    } else {
                        null
                    }
                }
                .average()
                .takeIf { !it.isNaN() } ?: 0.0

        return avgChannel to avgAirRate
    }

    /** Builds one immutable terminal snapshot. Caller must hold [mutex]. */
    private fun terminalRequestLocked(
        pendingStatus: String,
        outcome: DiscoveryScanState.CompletionOutcome,
        awaitRestore: Boolean,
        generateAi: Boolean,
    ): DiscoveryTerminalRequest {
        val session = _currentSession.value
        val finalStatus = finalStatusForPendingStatus(pendingStatus)
        val config = session?.homeLoraConfig ?: originalLoRaConfig
        val primaryChannel = session?.homePrimaryChannel ?: originalPrimaryChannel
        if (tunedPrimaryChannel && primaryChannel == null) {
            Logger.e {
                "DiscoveryScanEngine: primary channel was retuned without a captured home channel; " +
                    "restoring LoRa config only for session ${session?.id ?: sessionId}"
            }
        }
        val restorePlan =
            if (session == null || config == null) {
                null
            } else {
                DiscoveryHomeRestorePlan(
                    sessionId = session.id,
                    deviceAddress = session.deviceAddress,
                    loraConfig = config,
                    primaryChannel = primaryChannel,
                    restorePrimaryChannel = tunedPrimaryChannel && primaryChannel != null,
                    finalStatus = finalStatus,
                )
            }
        return DiscoveryTerminalRequest(
            sessionId = session?.id ?: sessionId,
            restorePlan = restorePlan,
            pendingStatus = pendingStatus,
            outcome = outcome,
            awaitRestore = awaitRestore,
            shouldGenerateAi = generateAi,
        )
    }

    private fun finalStatusForPendingStatus(pendingStatus: String): String =
        finalStatusForPendingRestore(pendingStatus, default = DiscoverySessionStatus.FAILED)

    // endregion

    // region Interrupted-session restoration

    /**
     * Watches reconnects for persisted discovery sessions left without a confirmed home-radio restore. Recovery is
     * device-bound and skipped while this engine owns an active scan.
     */
    suspend fun restoreInterruptedSessionsOnReconnect(onRestored: suspend (homePreset: String) -> Unit = {}): Unit =
        interruptedSessionRecovery.watch(onRestored)

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
        private const val CONFIG_SNAPSHOT_TIMEOUT_MS = 5_000L
        private const val RECONNECT_TIMEOUT_MS = 60_000L
        private const val TICK_INTERVAL_MS = 1_000L
        private const val POSITION_DIVISOR = 1e7
        private const val MIN_DEVICE_METRICS_PACKETS = 2
        private const val PERCENT_MULTIPLIER = 100.0

        /** Node roles that indicate infrastructure (Router, RouterLate, ClientBase). */
        private val INFRASTRUCTURE_ROLES =
            setOf(
                Config.DeviceConfig.Role.ROUTER,
                Config.DeviceConfig.Role.ROUTER_LATE,
                Config.DeviceConfig.Role.CLIENT_BASE,
            )
    }
}
