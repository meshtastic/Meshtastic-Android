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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.SessionManager
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.ToRadio
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Suppress("LongParameterList", "TooManyFunctions")
@Single
class MeshConnectionManagerImpl(
    private val radioInterfaceService: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
    private val serviceBroadcasts: ServiceBroadcasts,
    private val serviceNotifications: MeshServiceNotifications,
    private val uiPrefs: UiPrefs,
    private val packetHandler: PacketHandler,
    private val nodeRepository: NodeRepository,
    private val locationManager: MeshLocationManager,
    private val mqttManager: MqttManager,
    private val historyManager: HistoryManager,
    private val radioConfigRepository: RadioConfigRepository,
    private val commandSender: CommandSender,
    private val sessionManager: SessionManager,
    private val nodeManager: NodeManager,
    private val analytics: PlatformAnalytics,
    private val packetRepository: PacketRepository,
    private val workerManager: MeshWorkerManager,
    private val appWidgetUpdater: AppWidgetUpdater,
    private val heartbeatSender: DataLayerHeartbeatSender,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : MeshConnectionManager {
    /**
     * Serializes [onConnectionChanged] to prevent TOCTOU races when multiple coroutines emit state transitions
     * concurrently (e.g. flow collector vs. sleep-timeout coroutine).
     */
    private val connectionMutex = Mutex()

    private var preHandshakeJob: Job? = null
    private var sleepTimeout: Job? = null
    private var locationRequestsJob: Job? = null
    private var handshakeTimeout: Job? = null
    private var connectTimeMsec = 0L
    private var connectionRestored = false

    init {
        // Bridge transport-level state into the canonical app-level state.
        // This is the ONLY consumer of RadioInterfaceService.connectionState — it applies
        // light-sleep policy and handshake awareness before writing to ServiceRepository.
        radioInterfaceService.connectionState.onEach(::onRadioConnectionState).launchIn(scope)

        // Ensure notification title and content stay in sync with state changes
        serviceRepository.connectionState.onEach { updateStatusNotification() }.launchIn(scope)

        scope.launch {
            try {
                appWidgetUpdater.updateAll()
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.e(e) { "Failed to kickstart LocalStatsWidget" }
            }
        }

        nodeRepository.myNodeInfo
            .onEach { myNodeEntity ->
                locationRequestsJob?.cancel()
                if (myNodeEntity != null) {
                    locationRequestsJob =
                        uiPrefs
                            .shouldProvideNodeLocation(myNodeEntity.myNodeNum)
                            .onEach { shouldProvide ->
                                if (shouldProvide) {
                                    locationManager.start(scope) { pos -> commandSender.sendPosition(pos) }
                                } else {
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

    private suspend fun onConnectionChanged(c: ConnectionState) = connectionMutex.withLock {
        val current = serviceRepository.connectionState.value
        if (current == c) return@withLock

        // If the transport reports 'Connected', but we are already in the middle of a handshake (Connecting)
        if (c is ConnectionState.Connected && current is ConnectionState.Connecting) {
            Logger.d { "Ignoring redundant transport connection signal while handshake is in progress" }
            return@withLock
        }

        Logger.i { "onConnectionChanged: $current -> $c" }

        sleepTimeout?.cancel()
        sleepTimeout = null
        preHandshakeJob?.cancel()
        preHandshakeJob = null
        handshakeTimeout?.cancel()
        handshakeTimeout = null

        when (c) {
            is ConnectionState.Connecting -> serviceRepository.setConnectionState(ConnectionState.Connecting)
            is ConnectionState.Connected -> handleConnected()
            is ConnectionState.DeviceSleep -> handleDeviceSleep()
            is ConnectionState.Disconnected -> handleDisconnected()
        }
    }

    private fun handleConnected() {
        // Track whether this connection was restored from device sleep (vs. a fresh connect),
        // matching Apple's "connectionRestored" attribute for cross-platform DataDog parity.
        connectionRestored = serviceRepository.connectionState.value is ConnectionState.DeviceSleep
        // The service state remains 'Connecting' until config is fully loaded
        if (serviceRepository.connectionState.value != ConnectionState.Connected) {
            serviceRepository.setConnectionState(ConnectionState.Connecting)
        }
        serviceBroadcasts.broadcastConnection()
        connectTimeMsec = nowMillis

        // Send a wake-up heartbeat before the config request. The firmware may be in a
        // power-saving state where the NimBLE callback context needs warming up. The 100ms
        // delay ensures the heartbeat BLE write is enqueued before the want_config_id
        // (sendToRadio is fire-and-forget through async coroutine launches).
        preHandshakeJob =
            scope.handledLaunch {
                heartbeatSender.sendHeartbeat("pre-handshake")
                delay(PRE_HANDSHAKE_SETTLE_MS)
                Logger.i { "Starting mesh handshake (Stage 1)" }
                startConfigOnly()
            }
    }

    private fun startHandshakeStallGuard(stage: Int, timeout: Duration, action: () -> Unit) {
        handshakeTimeout?.cancel()
        handshakeTimeout =
            scope.handledLaunch {
                delay(timeout)
                if (serviceRepository.connectionState.value is ConnectionState.Connecting) {
                    // Attempt one retry. Note: the firmware silently drops identical consecutive
                    // writes (per-connection dedup). If the first want_config_id was received and
                    // the stall is on our side, the retry will be dropped and the reconnect below
                    // will trigger instead — which is the right recovery in that case.
                    Logger.w {
                        "Handshake stall detected at Stage $stage — retrying, then reconnecting if still stalled"
                    }
                    action()
                    delay(HANDSHAKE_RETRY_TIMEOUT)
                    if (serviceRepository.connectionState.value is ConnectionState.Connecting) {
                        Logger.e { "Handshake still stalled after retry, forcing reconnect" }
                        onConnectionChanged(ConnectionState.Disconnected)
                    }
                }
            }
    }

    private fun tearDownConnection() {
        packetHandler.stopPacketQueue()
        sessionManager.clearAll() // Prevent stale per-node passkeys on reconnect.
        locationManager.stop()
        mqttManager.stop()
    }

    private fun handleDeviceSleep() {
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

        sleepTimeout =
            scope.handledLaunch {
                try {
                    val localConfig = radioConfigRepository.localConfigFlow.first()
                    val rawTimeout = (localConfig.power?.ls_secs ?: 0) + DEVICE_SLEEP_TIMEOUT_SECONDS
                    // Cap the timeout so routers or power-saving configs (ls_secs=3600) don't
                    // leave the UI stuck in DeviceSleep for over an hour.
                    val timeout = rawTimeout.coerceAtMost(MAX_SLEEP_TIMEOUT_SECONDS)
                    Logger.d { "Waiting for sleeping device, timeout=$timeout secs (raw=$rawTimeout)" }
                    delay(timeout.seconds)
                    Logger.w { "Device timed out, setting disconnected" }
                    onConnectionChanged(ConnectionState.Disconnected)
                } catch (_: CancellationException) {
                    Logger.d { "device sleep timeout cancelled" }
                }
            }

        serviceBroadcasts.broadcastConnection()
    }

    private fun handleDisconnected() {
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        tearDownConnection()

        analytics.track(
            EVENT_MESH_DISCONNECT,
            DataPair(KEY_NUM_NODES, nodeManager.nodeDBbyNodeNum.size),
            DataPair(KEY_NUM_ONLINE, nodeManager.nodeDBbyNodeNum.values.count { it.isOnline }),
        )
        analytics.track(EVENT_NUM_NODES, DataPair(KEY_NUM_NODES, nodeManager.nodeDBbyNodeNum.size))

        serviceBroadcasts.broadcastConnection()
    }

    override fun startConfigOnly() {
        val action = { packetHandler.sendToRadio(ToRadio(want_config_id = HandshakeConstants.CONFIG_NONCE)) }
        startHandshakeStallGuard(1, HANDSHAKE_TIMEOUT_STAGE1, action)
        action()
    }

    override fun startNodeInfoOnly() {
        val action = { packetHandler.sendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE)) }
        startHandshakeStallGuard(2, HANDSHAKE_TIMEOUT_STAGE2, action)
        action()
    }

    override fun onRadioConfigLoaded() {
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

    override fun onNodeDbReady() {
        handshakeTimeout?.cancel()
        handshakeTimeout = null

        val myNodeNum = nodeManager.myNodeNum.value ?: 0

        // Set device time now that the full node picture is ready. Sending this during Stage 1
        // (onRadioConfigLoaded) introduced GATT write contention with the Stage 2 node-info burst.
        commandSender.sendAdmin(myNodeNum) { AdminMessage(set_time_only = nowSeconds.toInt()) }

        // Proactively seed the session passkey. The firmware embeds session_passkey in every
        // admin *response* (wantResponse=true), but set_time_only has no response. A get_owner
        // request is the lightest way to trigger a response and populate the passkey cache so
        // that subsequent write operations don't fail with ADMIN_BAD_SESSION_KEY.
        commandSender.sendAdmin(myNodeNum, wantResponse = true) { AdminMessage(get_owner_request = true) }

        // Start MQTT if enabled
        scope.handledLaunch {
            val moduleConfig = radioConfigRepository.moduleConfigFlow.first()
            mqttManager.startProxy(
                moduleConfig.mqtt?.enabled == true,
                moduleConfig.mqtt?.proxy_to_client_enabled == true,
            )
        }

        reportConnection()

        // Request history
        scope.handledLaunch {
            val moduleConfig = radioConfigRepository.moduleConfigFlow.first()
            moduleConfig.store_forward?.let {
                historyManager.requestHistoryReplay("onNodeDbReady", myNodeNum, it, "Unknown")
            }
        }

        // Request immediate LocalStats and DeviceMetrics update on connection with proper request IDs
        commandSender.requestTelemetry(commandSender.generatePacketId(), myNodeNum, TelemetryType.LOCAL_STATS.ordinal)
        commandSender.requestTelemetry(commandSender.generatePacketId(), myNodeNum, TelemetryType.DEVICE.ordinal)
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
        val transportType = radioInterfaceService.getDeviceAddress()?.let { DeviceType.fromAddress(it)?.name }
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

    override fun updateStatusNotification(telemetry: Telemetry?) {
        serviceNotifications.updateServiceStateNotification(
            serviceRepository.connectionState.value,
            telemetry = telemetry,
        )
    }

    companion object {
        private const val DEVICE_SLEEP_TIMEOUT_SECONDS = 30

        // Maximum time (in seconds) to wait for a sleeping device before declaring it
        // disconnected, regardless of the device's ls_secs configuration. Without this
        // cap, routers (ls_secs=3600) leave the UI in DeviceSleep for over an hour.
        private const val MAX_SLEEP_TIMEOUT_SECONDS = 300

        /**
         * Delay between the pre-handshake heartbeat and the want_config_id send.
         *
         * Ensures the heartbeat BLE write completes and the firmware's NimBLE callback context is warmed up before the
         * config request arrives. 100ms is well above observed ESP32 task scheduling latency (~10–50ms) while adding
         * negligible connection latency.
         */
        private const val PRE_HANDSHAKE_SETTLE_MS = 100L

        private val HANDSHAKE_TIMEOUT_STAGE1 = 30.seconds

        /**
         * Stage 2 drains the full node database, which can be significantly larger than Stage 1 config on big meshes.
         * 60 s matches the meshtastic-client SDK timeout and avoids premature stall-guard triggers on meshes with 50+
         * nodes.
         */
        private val HANDSHAKE_TIMEOUT_STAGE2 = 60.seconds

        // Shorter window for the retry attempt: if the device genuinely didn't receive the
        // first want_config_id the retry completes within a few seconds. Waiting another 30s
        // before reconnecting just delays recovery unnecessarily.
        private val HANDSHAKE_RETRY_TIMEOUT = 15.seconds

        private const val EVENT_CONNECTED_SECONDS = "connected_seconds"
        private const val EVENT_MESH_DISCONNECT = "mesh_disconnect"
        private const val EVENT_NUM_NODES = "num_nodes"
        private const val EVENT_MESH_CONNECT = "mesh_connect"

        private const val KEY_NUM_NODES = "num_nodes"
        private const val KEY_NUM_ONLINE = "num_online"
        private const val KEY_RADIO_MODEL = "radio_model"
    }
}
