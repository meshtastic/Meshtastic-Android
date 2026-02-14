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
import co.touchlab.kermit.Logger
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.meshtastic.core.strings.getString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.analytics.DataPair
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.model.util.nowSeconds
import org.meshtastic.core.prefs.ui.UiPrefs
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.connected_count
import org.meshtastic.core.strings.connecting
import org.meshtastic.core.strings.device_sleeping
import org.meshtastic.core.strings.disconnected
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
) {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sleepTimeout: Job? = null
    private var locationRequestsJob: Job? = null
    private var connectTimeMsec = 0L

    fun start(scope: CoroutineScope) {
        this.scope = scope
        radioInterfaceService.connectionState.onEach(::onRadioConnectionState).launchIn(scope)

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
        if (connectionStateHolder.connectionState.value == c && c !is ConnectionState.Connected) return
        Logger.d { "onConnectionChanged: ${connectionStateHolder.connectionState.value} -> $c" }

        sleepTimeout?.cancel()
        sleepTimeout = null

        when (c) {
            is ConnectionState.Connecting -> connectionStateHolder.setState(ConnectionState.Connecting)
            is ConnectionState.Connected -> handleConnected()
            is ConnectionState.DeviceSleep -> handleDeviceSleep()
            is ConnectionState.Disconnected -> handleDisconnected()
        }
        updateStatusNotification()
    }

    private fun handleConnected() {
        // The service state remains 'Connecting' until config is fully loaded
        if (connectionStateHolder.connectionState.value == ConnectionState.Disconnected) {
            connectionStateHolder.setState(ConnectionState.Connecting)
        }
        serviceBroadcasts.broadcastConnection()
        Logger.d { "Starting connect" }
        connectTimeMsec = nowMillis
        scope.handledLaunch { nodeRepository.clearMyNodeInfo() }
        startConfigOnly()
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
        commandSender.processQueuedPackets()

        val myNodeNum = nodeManager.myNodeNum ?: 0
        // Set time
        commandSender.sendAdmin(myNodeNum) { AdminMessage(set_time_only = nowSeconds.toInt()) }
    }

    fun onNodeDbReady() {
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

        updateStatusNotification()
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
        updateStatusNotification(telemetry)
    }

    fun updateStatusNotification(telemetry: Telemetry? = null): Notification {
        val summary =
            when (connectionStateHolder.connectionState.value) {
                is ConnectionState.Connected ->
                    getString(Res.string.connected_count)
                        .format(nodeManager.nodeDBbyNodeNum.values.count { it.isOnline })
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

        private const val EVENT_CONNECTED_SECONDS = "connected_seconds"
        private const val EVENT_MESH_DISCONNECT = "mesh_disconnect"
        private const val EVENT_NUM_NODES = "num_nodes"
        private const val EVENT_MESH_CONNECT = "mesh_connect"

        private const val KEY_NUM_NODES = "num_nodes"
        private const val KEY_NUM_ONLINE = "num_online"
        private const val KEY_RADIO_MODEL = "radio_model"
    }
}
