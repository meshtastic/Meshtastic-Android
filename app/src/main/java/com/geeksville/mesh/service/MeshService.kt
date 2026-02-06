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

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import co.touchlab.kermit.Logger
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.model.NO_DEVICE_SELECTED
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.util.toRemoteExceptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.meshtastic.core.common.hasLocationPermission
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.Position
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.SERVICE_NOTIFY_ID
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.PortNum
import javax.inject.Inject

@AndroidEntryPoint
@Suppress("TooManyFunctions", "LargeClass")
class MeshService : Service() {

    @Inject lateinit var radioInterfaceService: RadioInterfaceService

    @Inject lateinit var serviceRepository: ServiceRepository

    @Inject lateinit var connectionStateHolder: ConnectionStateHandler

    @Inject lateinit var packetHandler: PacketHandler

    @Inject lateinit var serviceBroadcasts: MeshServiceBroadcasts

    @Inject lateinit var nodeManager: MeshNodeManager

    @Inject lateinit var messageProcessor: MeshMessageProcessor

    @Inject lateinit var commandSender: MeshCommandSender

    @Inject lateinit var locationManager: MeshLocationManager

    @Inject lateinit var connectionManager: MeshConnectionManager

    @Inject lateinit var serviceNotifications: MeshServiceNotifications

    @Inject lateinit var radioConfigRepository: RadioConfigRepository

    @Inject lateinit var router: MeshRouter

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val myNodeNum: Int
        get() = nodeManager.myNodeNum ?: throw RadioNotConnectedException()

    companion object {
        fun actionReceived(portNum: Int): String {
            val portType = PortNum.fromValue(portNum)
            val portStr = portType?.toString() ?: portNum.toString()
            return com.geeksville.mesh.service.actionReceived(portStr)
        }

        fun createIntent(context: Context) = Intent(context, MeshService::class.java)

        fun changeDeviceAddress(context: Context, service: IMeshService, address: String?) {
            service.setDeviceAddress(address)
            startService(context)
        }

        val minDeviceVersion = DeviceVersion(BuildConfig.MIN_FW_VERSION)
        val absoluteMinDeviceVersion = DeviceVersion(BuildConfig.ABS_MIN_FW_VERSION)
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i { "Creating mesh service" }
        serviceNotifications.initChannels()

        packetHandler.start(serviceScope)
        router.start(serviceScope)
        nodeManager.start(serviceScope)
        connectionManager.start(serviceScope)
        messageProcessor.start(serviceScope)
        commandSender.start(serviceScope)

        serviceScope.handledLaunch { radioInterfaceService.connect() }

        radioInterfaceService.receivedData
            .onEach { bytes -> messageProcessor.handleFromRadio(bytes, nodeManager.myNodeNum) }
            .launchIn(serviceScope)

        serviceRepository.serviceAction.onEach(router.actionHandler::onServiceAction).launchIn(serviceScope)

        nodeManager.loadCachedNodeDB()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val a = radioInterfaceService.getDeviceAddress()
        val wantForeground = a != null && a != NO_DEVICE_SELECTED

        val notification = connectionManager.updateStatusNotification()

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (hasLocationPermission()) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            types
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, SERVICE_NOTIFY_ID, notification, foregroundServiceType)
        } catch (ex: SecurityException) {
            // On Android 14+ starting a location FGS from the background can fail with SecurityException
            // if the app is not in an allowed state. Retry without the location type if that was requested.
            val connectedDeviceOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
            if (foregroundServiceType != connectedDeviceOnly) {
                Logger.w(ex) { "Failed to start foreground service with location type, retrying with connectedDevice only" }
                try {
                    ServiceCompat.startForeground(this, SERVICE_NOTIFY_ID, notification, connectedDeviceOnly)
                } catch (retryEx: Exception) {
                    Logger.e(retryEx) { "Failed to start foreground service even after retry" }
                }
            } else {
                Logger.e(ex) { "SecurityException starting foreground service" }
            }
        } catch (ex: Exception) {
            Logger.e(ex) { "Error starting foreground service" }
            return START_NOT_STICKY
        }

        return if (!wantForeground) {
            Logger.i { "Stopping mesh service because no device is selected" }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            START_NOT_STICKY
        } else {
            START_STICKY
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Logger.i { "Mesh service: onTaskRemoved" }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Logger.i { "Destroying mesh service" }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        serviceRepository.cancelPendingRetries()
        serviceJob.cancel()
        super.onDestroy()
    }

    private val binder =
        object : IMeshService.Stub() {
            override fun setDeviceAddress(deviceAddr: String?) = toRemoteExceptions {
                Logger.d { "Passing through device change to radio service: ${deviceAddr?.take(8)}..." }
                router.actionHandler.handleUpdateLastAddress(deviceAddr)
                radioInterfaceService.setDeviceAddress(deviceAddr)
            }

            override fun subscribeReceiver(packageName: String, receiverName: String) {
                serviceBroadcasts.subscribeReceiver(receiverName, packageName)
            }

            override fun getUpdateStatus(): Int = -4

            override fun startFirmwareUpdate() {
                // Not implemented yet
            }

            override fun getMyNodeInfo(): MyNodeInfo? = nodeManager.getMyNodeInfo()

            override fun getMyId(): String = nodeManager.getMyId()

            override fun getPacketId(): Int = commandSender.generatePacketId()

            override fun setOwner(u: MeshUser) = toRemoteExceptions {
                router.actionHandler.handleSetOwner(u, myNodeNum)
            }

            override fun setRemoteOwner(id: Int, destNum: Int, payload: ByteArray) = toRemoteExceptions {
                router.actionHandler.handleSetRemoteOwner(id, destNum, payload)
            }

            override fun getRemoteOwner(id: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleGetRemoteOwner(id, destNum)
            }

            override fun send(p: DataPacket) = toRemoteExceptions { router.actionHandler.handleSend(p, myNodeNum) }

            override fun getConfig(): ByteArray = toRemoteExceptions {
                runBlocking { radioConfigRepository.localConfigFlow.first().encode() }
            }

            override fun setConfig(payload: ByteArray) = toRemoteExceptions {
                router.actionHandler.handleSetConfig(payload, myNodeNum)
            }

            override fun setRemoteConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
                router.actionHandler.handleSetRemoteConfig(id, num, payload)
            }

            override fun getRemoteConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
                router.actionHandler.handleGetRemoteConfig(id, destNum, config)
            }

            override fun setModuleConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
                router.actionHandler.handleSetModuleConfig(id, num, payload)
            }

            override fun getModuleConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
                router.actionHandler.handleGetModuleConfig(id, destNum, config)
            }

            override fun setRingtone(destNum: Int, ringtone: String) = toRemoteExceptions {
                router.actionHandler.handleSetRingtone(destNum, ringtone)
            }

            override fun getRingtone(id: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleGetRingtone(id, destNum)
            }

            override fun setCannedMessages(destNum: Int, messages: String) = toRemoteExceptions {
                router.actionHandler.handleSetCannedMessages(destNum, messages)
            }

            override fun getCannedMessages(id: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleGetCannedMessages(id, destNum)
            }

            override fun setChannel(payload: ByteArray?) = toRemoteExceptions {
                router.actionHandler.handleSetChannel(payload, myNodeNum)
            }

            override fun setRemoteChannel(id: Int, num: Int, payload: ByteArray?) = toRemoteExceptions {
                router.actionHandler.handleSetRemoteChannel(id, num, payload)
            }

            override fun getRemoteChannel(id: Int, destNum: Int, index: Int) = toRemoteExceptions {
                router.actionHandler.handleGetRemoteChannel(id, destNum, index)
            }

            override fun beginEditSettings(destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleBeginEditSettings(destNum)
            }

            override fun commitEditSettings(destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleCommitEditSettings(destNum)
            }

            override fun getChannelSet(): ByteArray = toRemoteExceptions {
                runBlocking { radioConfigRepository.channelSetFlow.first().encode() }
            }

            override fun getNodes(): List<NodeInfo> = nodeManager.getNodes()

            override fun connectionState(): String = connectionStateHolder.connectionState.value.toString()

            override fun startProvideLocation() {
                locationManager.start(serviceScope) { commandSender.sendPosition(it) }
            }

            override fun stopProvideLocation() {
                locationManager.stop()
            }

            override fun removeByNodenum(requestId: Int, nodeNum: Int) = toRemoteExceptions {
                val myNodeNum = nodeManager.myNodeNum
                if (myNodeNum != null) {
                    router.actionHandler.handleRemoveByNodenum(nodeNum, requestId, myNodeNum)
                } else {
                    nodeManager.removeByNodenum(nodeNum)
                }
            }

            override fun requestUserInfo(destNum: Int) = toRemoteExceptions {
                if (destNum != myNodeNum) {
                    commandSender.requestUserInfo(destNum)
                }
            }

            override fun requestPosition(destNum: Int, position: Position) = toRemoteExceptions {
                router.actionHandler.handleRequestPosition(destNum, position, myNodeNum)
            }

            override fun setFixedPosition(destNum: Int, position: Position) = toRemoteExceptions {
                commandSender.setFixedPosition(destNum, position)
            }

            override fun requestTraceroute(requestId: Int, destNum: Int) = toRemoteExceptions {
                commandSender.requestTraceroute(requestId, destNum)
            }

            override fun requestNeighborInfo(requestId: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleRequestNeighborInfo(requestId, destNum)
            }

            override fun requestShutdown(requestId: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleRequestShutdown(requestId, destNum)
            }

            override fun requestReboot(requestId: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleRequestReboot(requestId, destNum)
            }

            override fun rebootToDfu(destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleRebootToDfu(destNum)
            }

            override fun requestFactoryReset(requestId: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleRequestFactoryReset(requestId, destNum)
            }

            override fun requestNodedbReset(requestId: Int, destNum: Int, preserveFavorites: Boolean) =
                toRemoteExceptions {
                    router.actionHandler.handleRequestNodedbReset(requestId, destNum, preserveFavorites)
                }

            override fun getDeviceConnectionStatus(requestId: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleGetDeviceConnectionStatus(requestId, destNum)
            }

            override fun requestTelemetry(requestId: Int, destNum: Int, type: Int) = toRemoteExceptions {
                router.actionHandler.handleRequestTelemetry(requestId, destNum, type)
            }

            override fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) =
                toRemoteExceptions {
                    router.actionHandler.handleRequestRebootOta(requestId, destNum, mode, hash)
                }
        }
}
