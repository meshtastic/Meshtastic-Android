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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.ConnectionState
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
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.ToRadio
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
    private val nodeManager: NodeManager,
    private val analytics: PlatformAnalytics,
    private val packetRepository: PacketRepository,
    private val workerManager: MeshWorkerManager,
    private val appWidgetUpdater: AppWidgetUpdater,
) : MeshConnectionManager {
    private var scope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())
    private var sleepTimeout: Job? = null
    private var locationRequestsJob: Job? = null
    private var handshakeTimeout: Job? = null
    private var connectTimeMsec = 0L

    @OptIn(FlowPreview::class)
    override fun start(scope: CoroutineScope) {
        this.scope = scope
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

    private fun onRadioConnectionState(newState: ConnectionState) {
        scope.handledLaunch {
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
    }

    private fun onConnectionChanged(c: ConnectionState) {
        val current = serviceRepository.connectionState.value
        if (current == c) return

        // If the transport reports 'Connected', but we are already in the middle of a handshake (Connecting)
        if (c is ConnectionState.Connected && current is ConnectionState.Connecting) {
            Logger.d { "Ignoring redundant transport connection signal while handshake is in progress" }
            return
        }

        Logger.i { "onConnectionChanged: $current -> $c" }

        sleepTimeout?.cancel()
        sleepTimeout = null
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
        // The service state remains 'Connecting' until config is fully loaded
        if (serviceRepository.connectionState.value != ConnectionState.Connected) {
            serviceRepository.setConnectionState(ConnectionState.Connecting)
        }
        serviceBroadcasts.broadcastConnection()
        Logger.i { "Starting mesh handshake (Stage 1)" }
        connectTimeMsec = nowMillis
        startConfigOnly()
    }

    private fun startHandshakeStallGuard(stage: Int, action: () -> Unit) {
        handshakeTimeout?.cancel()
        handshakeTimeout =
            scope.handledLaunch {
                delay(HANDSHAKE_TIMEOUT)
                if (serviceRepository.connectionState.value is ConnectionState.Connecting) {
                    Logger.w { "Handshake stall detected! Retrying Stage $stage." }
                    action()
                    // Recursive timeout for one more try
                    delay(HANDSHAKE_TIMEOUT)
                    if (serviceRepository.connectionState.value is ConnectionState.Connecting) {
                        Logger.e { "Handshake still stalled after retry. Resetting connection." }
                        onConnectionChanged(ConnectionState.Disconnected)
                    }
                }
            }
    }

    private fun handleDeviceSleep() {
        serviceRepository.setConnectionState(ConnectionState.DeviceSleep)
        packetHandler.stopPacketQueue()
        locationManager.stop()
        mqttManager.stop()

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
                    val timeout = (localConfig.power?.ls_secs ?: 0) + DEVICE_SLEEP_TIMEOUT_SECONDS
                    Logger.d { "Waiting for sleeping device, timeout=$timeout secs" }
                    delay(timeout.seconds)
                    Logger.w { "Device timeout out, setting disconnected" }
                    onConnectionChanged(ConnectionState.Disconnected)
                } catch (_: CancellationException) {
                    Logger.d { "device sleep timeout cancelled" }
                }
            }

        serviceBroadcasts.broadcastConnection()
    }

    private fun handleDisconnected() {
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        packetHandler.stopPacketQueue()
        locationManager.stop()
        mqttManager.stop()

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
        startHandshakeStallGuard(1, action)
        action()
    }

    override fun startNodeInfoOnly() {
        val action = { packetHandler.sendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE)) }
        startHandshakeStallGuard(2, action)
        action()
    }

    override fun onRadioConfigLoaded() {
        scope.handledLaunch {
            val queuedPackets = packetRepository.getQueuedPackets() ?: emptyList()
            queuedPackets.forEach { packet ->
                try {
                    workerManager.enqueueSendMessage(packet.id)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.e(e) { "Failed to enqueue queued packet worker" }
                }
            }
        }

        val myNodeNum = nodeManager.myNodeNum ?: 0
        // Set time
        commandSender.sendAdmin(myNodeNum) { AdminMessage(set_time_only = nowSeconds.toInt()) }
    }

    override fun onNodeDbReady() {
        handshakeTimeout?.cancel()
        handshakeTimeout = null

        // Start MQTT if enabled
        scope.handledLaunch {
            val moduleConfig = radioConfigRepository.moduleConfigFlow.first()
            mqttManager.start(
                scope,
                moduleConfig.mqtt?.enabled == true,
                moduleConfig.mqtt?.proxy_to_client_enabled == true,
            )
        }

        reportConnection()

        val myNodeNum = nodeManager.myNodeNum ?: 0
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
    }

    override fun updateTelemetry(t: Telemetry) {
        t.local_stats?.let { nodeRepository.updateLocalStats(it) }
        updateStatusNotification(t)
    }

    override fun updateStatusNotification(telemetry: Telemetry?): Any {
        val summary =
            when (serviceRepository.connectionState.value) {
                is ConnectionState.Connected ->
                    getString(Res.string.meshtastic_app_name) + ": " + getString(Res.string.connected)
                is ConnectionState.Disconnected -> getString(Res.string.disconnected)
                is ConnectionState.DeviceSleep -> getString(Res.string.device_sleeping)
                is ConnectionState.Connecting -> getString(Res.string.connecting)
            }
        return serviceNotifications.updateServiceStateNotification(summary, telemetry = telemetry)
    }

    companion object {
        private const val DEVICE_SLEEP_TIMEOUT_SECONDS = 30
        private val HANDSHAKE_TIMEOUT = 30.seconds

        private const val EVENT_CONNECTED_SECONDS = "connected_seconds"
        private const val EVENT_MESH_DISCONNECT = "mesh_disconnect"
        private const val EVENT_NUM_NODES = "num_nodes"
        private const val EVENT_MESH_CONNECT = "mesh_connect"

        private const val KEY_NUM_NODES = "num_nodes"
        private const val KEY_NUM_ONLINE = "num_online"
        private const val KEY_RADIO_MODEL = "radio_model"
    }
}
