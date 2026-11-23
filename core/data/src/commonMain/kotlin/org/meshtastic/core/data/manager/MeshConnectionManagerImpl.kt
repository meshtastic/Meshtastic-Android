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

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.TransportPhase
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NodeRestartTracker
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.send
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Suppress("LongParameterList", "TooManyFunctions")
@Single
class MeshConnectionManagerImpl(
    private val radioInterfaceService: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
    private val serviceNotifications: MeshNotificationManager,
    private val uiPrefs: UiPrefs,
    private val packetHandler: PacketHandler,
    private val nodeRepository: NodeRepository,
    private val locationManager: MeshLocationManager,
    private val mqttManager: MqttManager,
    private val historyManager: HistoryManager,
    private val radioConfigRepository: RadioConfigRepository,
    private val nodeManager: NodeManager,
    private val analytics: PlatformAnalytics,
    private val packetRepository: PacketRepository,
    private val workerManager: MeshWorkerManager,
    private val appWidgetUpdater: AppWidgetUpdater,
    private val scope: ServiceScope,
    private val nodeRestartTracker: NodeRestartTracker,
) : MeshConnectionManager {
    /**
     * Serializes [onConnectionChanged] to prevent TOCTOU races when multiple coroutines emit state transitions
     * concurrently (e.g. flow collector vs. sleep-timeout coroutine).
     */
    private val connectionMutex = Mutex()

    private var sleepTimeout: Job? = null
    private var locationRequestsJob: Job? = null
    private var postHandshakeRequestsJob: Job? = null
    private val locationRejectionLogged = atomic(false)

    private var connectTimeMsec = 0L
    private var connectionRestored = false

    init {
        // Bridge transport-level state into the canonical app-level state.
        // This is the ONLY consumer of RadioInterfaceService.connectionState — it applies
        // light-sleep policy and handshake awareness before writing to ServiceRepository.
        // Guarded per-emission: one uncaught throw here would kill the sole bridge collector and
        // permanently freeze the app-level state (a stuck-"Connected" UI no transport event can fix).
        radioInterfaceService.connectionState
            .onEach { state ->
                safeCatching { onRadioConnectionState(state) }
                    .onFailure { Logger.e(it) { "Connection state bridge failed for $state; collector kept alive" } }
            }
            .launchIn(scope)

        // The SDK reports the link up only at Ready, so its handshake phases present the app state as Connecting.
        // A handshake that ends short of Ready hands the state back to the link, which may not have changed.
        radioInterfaceService.transportStatus
            .map { it?.phase in HANDSHAKE_PHASES }
            .distinctUntilChanged()
            .onEach { handshaking ->
                safeCatching {
                    if (handshaking) {
                        onHandshakeStarted()
                    } else {
                        onRadioConnectionState(radioInterfaceService.connectionState.value)
                    }
                }
                    .onFailure { Logger.e(it) { "Handshake state bridge failed; collector kept alive" } }
            }
            .launchIn(scope)

        // Ensure notification title and content stay in sync with state changes
        serviceRepository.connectionState.onEach { updateStatusNotification() }.launchIn(scope)

        // An expected node restart ends when the post-reboot config handshake completes.
        serviceRepository.connectionState
            .onEach { if (it == ConnectionState.Connected) nodeRestartTracker.onConnected() }
            .launchIn(scope)

        scope.launch {
            try {
                appWidgetUpdater.updateAll()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.e(e) { "Failed to kickstart LocalStatsWidget" }
            }
        }

        nodeRepository.myNodeInfo
            .onEach { myNodeEntity ->
                locationRequestsJob?.cancel()
                locationRejectionLogged.value = false
                if (myNodeEntity != null) {
                    locationRequestsJob =
                        uiPrefs
                            .shouldProvideNodeLocation(myNodeEntity.myNodeNum)
                            .onEach { shouldProvide ->
                                if (shouldProvide) {
                                    locationManager.start(scope) { pos ->
                                        val failure = safeCatching { sendOwnPosition(pos) }.exceptionOrNull()
                                        when (failure) {
                                            null -> locationRejectionLogged.value = false

                                            is PacketQueueRejectedException ->
                                                logLocationSendFailure(
                                                    failure,
                                                    "Location update was rejected by packet queue",
                                                )

                                            is LocalNodeUnavailableException ->
                                                logLocationSendFailure(
                                                    failure,
                                                    "Location update is waiting for local node identity",
                                                )

                                            else ->
                                                Logger.e(failure) {
                                                    "Location update failed unexpectedly; collector kept alive"
                                                }
                                        }
                                        failure == null
                                    }
                                } else {
                                    locationRejectionLogged.value = false
                                    locationManager.stop()
                                }
                            }
                            .launchIn(scope)
                }
            }
            .launchIn(scope)
    }

    /**
     * Bridges a transport-level [ConnectionState] into the canonical app-level state.
     *
     * Applies light-sleep policy (power-saving / router role) to decide whether a [ConnectionState.DeviceSleep] event
     * should be surfaced as sleep or as a full disconnect, then delegates to [onConnectionChanged] for the actual state
     * transition.
     */
    private suspend fun onRadioConnectionState(newState: ConnectionState) {
        val localConfig = radioConfigRepository.localConfigFlow.first()
        val isRouter = localConfig.device?.role == Config.DeviceConfig.Role.ROUTER
        val lsEnabled = localConfig.power?.is_power_saving == true || isRouter

        val effectiveState =
            when (newState) {
                is ConnectionState.Connected -> ConnectionState.Connected

                is ConnectionState.DeviceSleep ->
                    if (lsEnabled) ConnectionState.DeviceSleep else ConnectionState.Disconnected

                is ConnectionState.Connecting -> ConnectionState.Connecting

                is ConnectionState.Disconnected -> ConnectionState.Disconnected
            }
        onConnectionChanged(effectiveState)
    }

    private suspend fun onConnectionChanged(c: ConnectionState, fromState: ConnectionState? = null): Boolean =
        connectionMutex.withLock {
            val current = serviceRepository.connectionState.value
            if (fromState != null && current != fromState) {
                Logger.d { "Skipping connection transition $current -> $c, expected current state $fromState" }
                return@withLock false
            }
            if (current == c) return@withLock false

            sleepTimeout?.cancel()
            sleepTimeout = null

            when (c) {
                is ConnectionState.Connecting -> serviceRepository.setConnectionState(ConnectionState.Connecting)
                is ConnectionState.Connected -> Unit
                is ConnectionState.DeviceSleep -> handleDeviceSleep()
                is ConnectionState.Disconnected -> handleDisconnected()
            }
            true
        }

    /** Shows the handshake as Connecting until the snapshot install publishes Connected. */
    private suspend fun onHandshakeStarted() = connectionMutex.withLock {
        val current = serviceRepository.connectionState.value
        if (current == ConnectionState.Connected || current == ConnectionState.Connecting) return@withLock
        sleepTimeout?.cancel()
        sleepTimeout = null
        serviceRepository.setConnectionState(ConnectionState.Connecting)
    }

    private fun tearDownConnection() {
        postHandshakeRequestsJob?.cancel()
        postHandshakeRequestsJob = null
        locationManager.stop()
        mqttManager.stop()
    }

    private fun handleDeviceSleep() {
        connectionRestored = true
        serviceRepository.setConnectionState(ConnectionState.DeviceSleep)
        tearDownConnection()

        if (connectTimeMsec != 0L) {
            val now = nowMillis
            val duration = now - connectTimeMsec
            connectTimeMsec = 0L
            analytics.track(
                EVENT_CONNECTED_SECONDS,
                DataPair(EVENT_CONNECTED_SECONDS, duration.milliseconds.toDouble(DurationUnit.SECONDS)),
            )
        }

        sleepTimeout = scope.handledLaunch {
            val localConfig = radioConfigRepository.localConfigFlow.first()
            val rawTimeout = (localConfig.power?.ls_secs ?: 0) + DEVICE_SLEEP_TIMEOUT_SECONDS
            // Cap the timeout so routers or power-saving configs (ls_secs=3600) don't
            // leave the UI stuck in DeviceSleep for over an hour.
            val timeout = rawTimeout.coerceAtMost(MAX_SLEEP_TIMEOUT_SECONDS)
            Logger.d { "Waiting for sleeping device, timeout=$timeout secs (raw=$rawTimeout)" }
            delay(timeout.seconds)
            Logger.w { "Device timed out, setting disconnected" }
            onConnectionChanged(ConnectionState.Disconnected, fromState = ConnectionState.DeviceSleep)
        }
    }

    private fun handleDisconnected() {
        connectionRestored = false
        connectTimeMsec = 0L
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        tearDownConnection()
        // A different radio may connect next, and localStats only overwrites on a fresh local_stats telemetry
        // packet - without this, signal-quality rating would silently blend the *previous* radio's noise floor into
        // the new one's readings until it gets around to reporting its own. Clearing back to "no reading yet" makes
        // that gap an explicit SNR-only fallback (design#15) instead of a stale, wrong number.
        nodeRepository.updateLocalStats(LocalStats.Builder().build())

        analytics.track(
            EVENT_MESH_DISCONNECT,
            DataPair(KEY_NUM_NODES, nodeManager.nodeDBbyNodeNum.size),
            DataPair(KEY_NUM_ONLINE, nodeManager.nodeDBbyNodeNum.values.count { it.isOnline }),
        )
        analytics.track(EVENT_NUM_NODES, DataPair(KEY_NUM_NODES, nodeManager.nodeDBbyNodeNum.size))
    }

    override fun clearRadioConfig() {
        scope.handledLaunch {
            radioConfigRepository.clearLocalConfig()
            radioConfigRepository.clearChannelSet()
            radioConfigRepository.clearLocalModuleConfig()
        }
    }

    override fun onRadioConfigLoaded() {
        packetHandler.rearmSendAckTimeouts()
        scope.handledLaunch {
            val queuedPackets = packetRepository.getQueuedPackets()
            queuedPackets.forEach { packet ->
                try {
                    workerManager.enqueueSendMessage(packet.id)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.e(e) { "Failed to enqueue queued packet worker" }
                }
            }
        }
    }

    override suspend fun onNodeDbReady() {
        connectTimeMsec = nowMillis
        schedulePostHandshakeRequests()

        // Start MQTT if enabled
        scope.handledLaunch {
            val moduleConfig = radioConfigRepository.moduleConfigFlow.first()
            mqttManager.startProxy(
                moduleConfig.mqtt?.enabled == true,
                moduleConfig.mqtt?.proxy_to_client_enabled == true,
            )
        }

        reportConnection()
    }

    private suspend fun schedulePostHandshakeRequests() = connectionMutex.withLock {
        postHandshakeRequestsJob?.cancelAndJoin()
        postHandshakeRequestsJob = null
        val myNodeNum = nodeManager.myNodeNum.value
        val connectedLifecycle = serviceRepository.connectionLifecycle.value
        if (myNodeNum == null || connectedLifecycle.state !is ConnectionState.Connected) {
            Logger.w { "Skipping post-handshake requests because the connected local-node state is unavailable" }
            return@withLock
        }
        postHandshakeRequestsJob = scope.handledLaunch {
            // The requests are independent. One unexpected request failure must not cancel the others, and
            // teardown serializes with this job publication through connectionMutex.
            supervisorScope {
                listOf(TelemetryType.LOCAL_STATS, TelemetryType.DEVICE).forEach { type ->
                    launch {
                        retryPostHandshakeRequest(
                            label = "$type telemetry request",
                            myNodeNum = myNodeNum,
                            connectedVersion = connectedLifecycle.version,
                        ) {
                            requestLocalTelemetry(type, myNodeNum, connectedLifecycle.version)
                        }
                    }
                }
                launch {
                    val config = radioConfigRepository.moduleConfigFlow.first().store_forward ?: return@launch
                    retryPostHandshakeRequest(
                        label = "History replay",
                        myNodeNum = myNodeNum,
                        connectedVersion = connectedLifecycle.version,
                    ) {
                        historyManager.requestHistoryReplay(
                            trigger = "onNodeDbReady",
                            myNodeNum = myNodeNum,
                            storeForwardConfig = config,
                            transport = "Unknown",
                            expectedConnectionVersion = connectedLifecycle.version,
                        )
                    }
                }
            }
        }
    }

    private fun logLocationSendFailure(failure: Throwable, warning: String) {
        if (locationRejectionLogged.compareAndSet(expect = false, update = true)) {
            Logger.w(failure) { warning }
        } else {
            Logger.d { "$warning (still pending)" }
        }
    }

    /** The session's client, while [connectedVersion], when given, still owns the connection. */
    private fun activeClient(operation: String, connectedVersion: Long? = null): RadioClient {
        val owned = connectedVersion == null || connectedVersion == serviceRepository.connectionLifecycle.value.version
        return radioInterfaceService.activeSession.value?.takeIf { owned }?.let(radioInterfaceService::clientForSession)
            ?: throw PacketQueueRejectedException(operation)
    }

    /** Sends the phone's fix to our own node with the send builder, so its time and precision survive. */
    private suspend fun sendOwnPosition(pos: Position) {
        val myNum = nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("Position update")
        val client = activeClient("Position update")
        val handle =
            try {
                client.send {
                    data(PortNum.POSITION_APP, pos.encode())
                    to(NodeId(myNum))
                    priority(MeshPacket.Priority.BACKGROUND)
                }
            } catch (e: MeshtasticException) {
                throw PacketQueueRejectedException("Position update").apply { addSuppressed(e) }
            }
        packetHandler.logSent(
            handle.id.raw,
            myNum,
            PortNum.POSITION_APP,
            pos.encode().toByteString(),
            priority = MeshPacket.Priority.BACKGROUND,
        )
        if (radioConfigRepository.localConfigFlow.first().position?.fixed_position != true) {
            nodeManager.handleReceivedPosition(myNum, myNum, pos, nowMillis)
        }
    }

    /** Asks our own node for [type] telemetry, only while [connectedVersion] still owns the connection. */
    private suspend fun requestLocalTelemetry(type: TelemetryType, myNodeNum: Int, connectedVersion: Long) {
        val operation = "$type telemetry request"
        val telemetry = activeClient(operation, connectedVersion).telemetry
        val local = type == TelemetryType.LOCAL_STATS
        val variant =
            Telemetry.Builder()
                .also { wb ->
                    if (local) {
                        wb.local_stats = LocalStats.Builder().build()
                    } else {
                        wb.device_metrics = DeviceMetrics.Builder().build()
                    }
                }
                .build()
        // TelemetryApi does not expose the wire ID it allocated.
        packetHandler.logSent(0, myNodeNum, PortNum.TELEMETRY_APP, variant.encode().toByteString(), true, myNodeNum)
        val result =
            try {
                if (local) telemetry.requestLocalStats() else telemetry.requestDevice(NodeId(myNodeNum))
            } catch (e: MeshtasticException) {
                throw PacketQueueRejectedException(operation).apply { addSuppressed(e) }
            }
        // An unanswered request retries like a refused one, within the same attempt cap.
        if (result !is AdminResult.Success) throw PacketQueueRejectedException(operation)
    }

    private fun ownsPostHandshakeRequests(myNodeNum: Int, connectedVersion: Long): Boolean =
        serviceRepository.connectionLifecycle.value.let { lifecycle ->
            lifecycle.version == connectedVersion &&
                lifecycle.state is ConnectionState.Connected &&
                nodeManager.myNodeNum.value == myNodeNum
        }

    private suspend fun retryPostHandshakeRequest(
        label: String,
        myNodeNum: Int,
        connectedVersion: Long,
        send: suspend () -> Unit,
    ) {
        var rejectionCount = 0
        var rejectionLogged = false
        var complete = false
        while (!complete && ownsPostHandshakeRequests(myNodeNum, connectedVersion)) {
            try {
                send()
                complete = true
            } catch (e: PacketQueueRejectedException) {
                rejectionCount++
                val exhausted = rejectionCount >= MAX_POST_HANDSHAKE_ADMISSION_ATTEMPTS
                if (exhausted) {
                    Logger.w(e) { "$label abandoned after $rejectionCount packet-queue rejections" }
                } else {
                    if (!rejectionLogged) {
                        Logger.w(e) { "$label rejected; waiting for packet-queue admission" }
                        rejectionLogged = true
                    } else {
                        Logger.d { "$label still waiting for packet-queue admission" }
                    }
                    delay(postHandshakeAdmissionRetryDelay(rejectionCount))
                }
                complete = exhausted
            } catch (e: LocalNodeUnavailableException) {
                Logger.w(e) { "$label stopped because the local node became unavailable" }
                complete = true
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.w(e) { "$label failed after the handshake" }
                complete = true
            }
        }
    }

    private fun reportConnection() {
        val myNode = nodeManager.getMyNodeInfo()
        val radioModel = DataPair(KEY_RADIO_MODEL, myNode?.model ?: "unknown")
        analytics.track(
            EVENT_MESH_CONNECT,
            DataPair(KEY_NUM_NODES, nodeManager.nodeDBbyNodeNum.size),
            DataPair(KEY_NUM_ONLINE, nodeManager.nodeDBbyNodeNum.values.count { it.isOnline }),
            radioModel,
        )

        // DataDog RUM custom action matching Apple's "connect" event for cross-platform analytics.
        val transportType = currentDeviceType()?.name ?: currentVirtualInterface()?.name
        analytics.trackConnect(
            firmwareVersion = myNode?.firmwareVersion,
            transportType = transportType,
            hardwareModel = myNode?.model,
            nodes = nodeManager.nodeDBbyNodeNum.size,
            connectionRestored = connectionRestored,
        )
    }

    override fun updateTelemetry(t: Telemetry) {
        t.local_stats?.let { nodeRepository.updateLocalStats(it) }
        updateStatusNotification(t)
    }

    private fun currentDeviceType(): DeviceType? =
        radioInterfaceService.getDeviceAddress()?.let { DeviceType.fromAddress(it) }

    private fun currentVirtualInterface(): InterfaceId? =
        radioInterfaceService.getDeviceAddress()?.firstOrNull()?.let(InterfaceId::forIdChar)?.takeIf { it.isVirtual }

    override fun updateStatusNotification(telemetry: Telemetry?) {
        val state = serviceRepository.connectionState.value
        // During an expected node restart the disconnect is transient; keep the persistent notification on the
        // connecting presentation instead of flashing "disconnected".
        val presented =
            if (state == ConnectionState.Disconnected && nodeRestartTracker.restartExpected.value) {
                ConnectionState.Connecting
            } else {
                state
            }
        serviceNotifications.updateServiceStateNotification(presented, telemetry = telemetry)
    }

    companion object {
        private const val DEVICE_SLEEP_TIMEOUT_SECONDS = 30

        private val HANDSHAKE_PHASES = setOf(TransportPhase.HandshakeConfig, TransportPhase.HandshakeNodes)

        // Maximum time (in seconds) to wait for a sleeping device before declaring it
        // disconnected, regardless of the device's ls_secs configuration. Without this
        // cap, routers (ls_secs=3600) leave the UI in DeviceSleep for over an hour.
        private const val MAX_SLEEP_TIMEOUT_SECONDS = 300

        internal const val MAX_POST_HANDSHAKE_ADMISSION_ATTEMPTS = 8
        private val POST_HANDSHAKE_ADMISSION_INITIAL_RETRY_DELAY = 1.seconds
        private const val POST_HANDSHAKE_ADMISSION_MAX_BACKOFF_EXPONENT = 3

        internal fun postHandshakeAdmissionRetryDelay(rejectionCount: Int): Duration {
            require(rejectionCount > 0) { "rejectionCount must be positive" }
            val exponent = (rejectionCount - 1).coerceAtMost(POST_HANDSHAKE_ADMISSION_MAX_BACKOFF_EXPONENT)
            return POST_HANDSHAKE_ADMISSION_INITIAL_RETRY_DELAY * (1 shl exponent)
        }

        private const val EVENT_CONNECTED_SECONDS = "connected_seconds"
        private const val EVENT_MESH_DISCONNECT = "mesh_disconnect"
        private const val EVENT_NUM_NODES = "num_nodes"
        private const val EVENT_MESH_CONNECT = "mesh_connect"

        private const val KEY_NUM_NODES = "num_nodes"
        private const val KEY_NUM_ONLINE = "num_online"
        private const val KEY_RADIO_MODEL = "radio_model"
    }
}
