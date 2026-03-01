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
package com.geeksville.mesh.service

import android.app.Notification
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import co.touchlab.kermit.Logger
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.widget.LocalStatsWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.meshtastic.core.analytics.DataPair
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.prefs.ui.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.feature.messaging.domain.worker.SendMessageWorker
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.ToRadio
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Suppress("LongParameterList", "TooManyFunctions")
@Singleton
class MeshConnectionManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val radioInterfaceService: RadioInterfaceService,
    private val connectionStateHolder: ConnectionStateHandler,
    private val serviceBroadcasts: MeshServiceBroadcasts,
    private val serviceNotifications: MeshServiceNotifications,
    private val uiPrefs: UiPrefs,
    private val packetHandler: PacketHandler,
    private val nodeRepository: NodeRepository,
    private val locationManager: MeshLocationManager,
    private val mqttManager: MeshMqttManager,
    private val historyManager: MeshHistoryManager,
    private val radioConfigRepository: RadioConfigRepository,
    private val commandSender: MeshCommandSender,
    private val nodeManager: MeshNodeManager,
    private val analytics: PlatformAnalytics,
    private val packetRepository: PacketRepository,
    private val workManager: WorkManager,
) {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sleepTimeout: Job? = null
    private var locationRequestsJob: Job? = null
    private var handshakeTimeout: Job? = null
    private var connectTimeMsec = 0L

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        this.scope = scope
        radioInterfaceService.connectionState.onEach(::onRadioConnectionState).launchIn(scope)

        // Ensure notification title and content stay in sync with state changes
        connectionStateHolder.connectionState.onEach { updateStatusNotification() }.launchIn(scope)

        // Kickstart the widget composition. The widget internally uses collectAsState()
        // and its own sampled StateFlow to drive updates automatically without excessive IPC and recreation.
        scope.launch {
            try {
                LocalStatsWidget().updateAll(context)
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
        val current = connectionStateHolder.connectionState.value
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
            is ConnectionState.Connecting -> connectionStateHolder.setState(ConnectionState.Connecting)
            is ConnectionState.Connected -> handleConnected()
            is ConnectionState.DeviceSleep -> handleDeviceSleep()
            is ConnectionState.Disconnected -> handleDisconnected()
        }
    }

    private fun handleConnected() {
        // The service state remains 'Connecting' until config is fully loaded
        if (connectionStateHolder.connectionState.value != ConnectionState.Connected) {
            connectionStateHolder.setState(ConnectionState.Connecting)
        }
        serviceBroadcasts.broadcastConnection()
        Logger.i { "Starting mesh handshake (Stage 1)" }
        connectTimeMsec = nowMillis
        startConfigOnly()

        // Guard against handshake stalls
        handshakeTimeout =
            scope.handledLaunch {
                delay(HANDSHAKE_TIMEOUT)
                if (connectionStateHolder.connectionState.value is ConnectionState.Connecting) {
                    Logger.w { "Handshake stall detected! Retrying Stage 1." }
                    startConfigOnly()
                    // Recursive timeout for one more try
                    delay(HANDSHAKE_TIMEOUT)
                    if (connectionStateHolder.connectionState.value is ConnectionState.Connecting) {
                        Logger.e { "Handshake still stalled after retry. Resetting connection." }
                        onConnectionChanged(ConnectionState.Disconnected)
                    }
                }
            }
    }

    private fun handleDeviceSleep() {
        connectionStateHolder.setState(ConnectionState.DeviceSleep)
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
        connectionStateHolder.setState(ConnectionState.Disconnected)
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

    fun startConfigOnly() {
        packetHandler.sendToRadio(ToRadio(want_config_id = CONFIG_ONLY_NONCE))
    }

    fun startNodeInfoOnly() {
        packetHandler.sendToRadio(ToRadio(want_config_id = NODE_INFO_NONCE))
    }

    fun onRadioConfigLoaded() {
        scope.handledLaunch {
            val queuedPackets = packetRepository.getQueuedPackets() ?: emptyList()
            queuedPackets.forEach { packet ->
                try {
                    val workRequest = OneTimeWorkRequestBuilder<SendMessageWorker>()
                        .setInputData(workDataOf(SendMessageWorker.KEY_PACKET_ID to packet.id))
                        .build()

                    workManager.enqueueUniqueWork(
                        "${SendMessageWorker.WORK_NAME_PREFIX}${packet.id}",
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to enqueue queued packet worker" }
                }
            }
        }

        val myNodeNum = nodeManager.myNodeNum ?: 0
        // Set time
        commandSender.sendAdmin(myNodeNum) { AdminMessage(set_time_only = nowSeconds.toInt()) }
    }

    fun onNodeDbReady() {
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

    fun updateTelemetry(telemetry: Telemetry) {
        telemetry.local_stats?.let { nodeRepository.updateLocalStats(it) }
        updateStatusNotification(telemetry)
    }

    fun updateStatusNotification(telemetry: Telemetry? = null): Notification {
        val summary =
            when (connectionStateHolder.connectionState.value) {
                is ConnectionState.Connected ->
                    getString(Res.string.meshtastic_app_name) + ": " + getString(Res.string.connected)
                is ConnectionState.Disconnected -> getString(Res.string.disconnected)
                is ConnectionState.DeviceSleep -> getString(Res.string.device_sleeping)
                is ConnectionState.Connecting -> getString(Res.string.connecting)
            }
        return serviceNotifications.updateServiceStateNotification(summary, telemetry = telemetry)
    }

    companion object {
        private const val CONFIG_ONLY_NONCE = 69420
        private const val NODE_INFO_NONCE = 69421
        private const val DEVICE_SLEEP_TIMEOUT_SECONDS = 30
        private val HANDSHAKE_TIMEOUT = 10.seconds

        private const val EVENT_CONNECTED_SECONDS = "connected_seconds"
        private const val EVENT_MESH_DISCONNECT = "mesh_disconnect"
        private const val EVENT_NUM_NODES = "num_nodes"
        private const val EVENT_MESH_CONNECT = "mesh_connect"

        private const val KEY_NUM_NODES = "num_nodes"
        private const val KEY_NUM_ONLINE = "num_online"
        private const val KEY_RADIO_MODEL = "radio_model"
    }
}
