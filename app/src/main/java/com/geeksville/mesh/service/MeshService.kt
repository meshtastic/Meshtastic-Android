/*
 * Copyright (c) 2025 Meshtastic LLC
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
import org.meshtastic.core.analytics.DataPair
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.common.hasLocationPermission
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.Position
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.SERVICE_NOTIFY_ID
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.ChannelProtos
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.ModuleConfigProtos
import org.meshtastic.proto.Portnums
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MeshService : Service() {

    @Inject lateinit var radioInterfaceService: RadioInterfaceService

    @Inject lateinit var serviceRepository: ServiceRepository

    @Inject lateinit var databaseManager: DatabaseManager

    @Inject lateinit var meshPrefs: MeshPrefs

    @Inject lateinit var connectionStateHolder: ConnectionStateHandler

    @Inject lateinit var packetHandler: PacketHandler

    @Inject lateinit var serviceBroadcasts: MeshServiceBroadcasts

    @Inject lateinit var nodeManager: MeshNodeManager

    @Inject lateinit var messageProcessor: MeshMessageProcessor

    @Inject lateinit var commandSender: MeshCommandSender

    @Inject lateinit var locationManager: MeshLocationManager

    @Inject lateinit var connectionManager: MeshConnectionManager

    @Inject lateinit var serviceNotifications: MeshServiceNotifications

    @Inject lateinit var dataHandler: MeshDataHandler

    @Inject lateinit var analytics: PlatformAnalytics

    @Inject lateinit var radioConfigRepository: RadioConfigRepository

    @Inject lateinit var router: MeshRouter

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val myNodeNum: Int
        get() = nodeManager.myNodeNum ?: throw RadioNotConnectedException()

    companion object {
        const val ACTION_NODE_CHANGE = "com.geeksville.mesh.NODE_CHANGE"
        const val ACTION_MESH_CONNECTED = "com.geeksville.mesh.MESH_CONNECTED"
        const val ACTION_MESSAGE_STATUS = "com.geeksville.mesh.MESSAGE_STATUS"

        private const val PREFIX = "com.geeksville.mesh"

        fun actionReceived(portNum: String) = "$PREFIX.RECEIVED.$portNum"

        fun actionReceived(portNum: Int): String {
            val portType = Portnums.PortNum.forNumber(portNum)
            val portStr = portType?.toString() ?: portNum.toString()
            return actionReceived(portStr)
        }

        fun createIntent(context: Context) = Intent(context, MeshService::class.java)

        fun changeDeviceAddress(context: Context, service: IMeshService, address: String?) {
            service.setDeviceAddress(address)
            val intent = Intent(context, MeshService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        val minDeviceVersion = DeviceVersion(BuildConfig.MIN_FW_VERSION)
        val absoluteMinDeviceVersion = DeviceVersion(BuildConfig.ABS_MIN_FW_VERSION)

        class NoDeviceConfigException(message: String = "No radio settings received (is our app too old?)") :
            RadioNotConnectedException(message)
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("Creating mesh service")
        serviceNotifications.initChannels()

        connectionManager.init()
        messageProcessor.start()

        serviceScope.handledLaunch { radioInterfaceService.connect() }

        radioInterfaceService.receivedData
            .onEach { bytes -> messageProcessor.handleFromRadio(bytes, nodeManager.myNodeNum) }
            .launchIn(serviceScope)

        serviceRepository.serviceAction.onEach(router.actionHandler::onServiceAction).launchIn(serviceScope)

        nodeManager.loadCachedNodeDB()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val a = radioInterfaceService.getBondedDeviceAddress()
        val wantForeground = a != null && a != NO_DEVICE_SELECTED

        val notification = connectionManager.updateStatusNotification()

        try {
            ServiceCompat.startForeground(
                this,
                SERVICE_NOTIFY_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (hasLocationPermission()) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    }
                } else {
                    0
                },
            )
        } catch (ex: Exception) {
            Timber.e(ex, "Error starting foreground service")
            return START_NOT_STICKY
        }
        return if (!wantForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            START_NOT_STICKY
        } else {
            START_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Timber.i("Destroying mesh service")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        serviceJob.cancel()
        super.onDestroy()
    }

    private val binder =
        object : IMeshService.Stub() {
            override fun setDeviceAddress(deviceAddr: String?) = toRemoteExceptions {
                updateLastAddress(deviceAddr)
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

            override fun setRemoteOwner(id: Int, payload: ByteArray) = toRemoteExceptions {
                val u = MeshProtos.User.parseFrom(payload)
                commandSender.sendAdmin(myNodeNum, id) { setOwner = u }
            }

            override fun getRemoteOwner(id: Int, destNum: Int) = toRemoteExceptions {
                commandSender.sendAdmin(destNum, id, wantResponse = true) { getOwnerRequest = true }
            }

            override fun send(p: DataPacket) = toRemoteExceptions {
                commandSender.sendData(p)
                serviceBroadcasts.broadcastMessageStatus(p)
                dataHandler.rememberDataPacket(p, myNodeNum, false)
                val bytes = p.bytes ?: ByteArray(0)
                analytics.track("data_send", DataPair("num_bytes", bytes.size), DataPair("type", p.dataType))
            }

            override fun getConfig(): ByteArray = toRemoteExceptions {
                runBlocking {
                    radioConfigRepository.localConfigFlow.first().toByteArray() ?: throw NoDeviceConfigException()
                }
            }

            override fun setConfig(payload: ByteArray) = toRemoteExceptions {
                val c = ConfigProtos.Config.parseFrom(payload)
                commandSender.sendAdmin(myNodeNum) { setConfig = c }
            }

            override fun setRemoteConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
                val c = ConfigProtos.Config.parseFrom(payload)
                commandSender.sendAdmin(num, id) { setConfig = c }
            }

            override fun getRemoteConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
                commandSender.sendAdmin(destNum, id, wantResponse = true) {
                    if (config == AdminProtos.AdminMessage.ConfigType.SESSIONKEY_CONFIG_VALUE) {
                        getDeviceMetadataRequest = true
                    } else {
                        getConfigRequestValue = config
                    }
                }
            }

            override fun setModuleConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
                val c = ModuleConfigProtos.ModuleConfig.parseFrom(payload)
                commandSender.sendAdmin(num, id) { setModuleConfig = c }
            }

            override fun getModuleConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
                commandSender.sendAdmin(destNum, id, wantResponse = true) { getModuleConfigRequestValue = config }
            }

            override fun setRingtone(destNum: Int, ringtone: String) = toRemoteExceptions {
                commandSender.sendAdmin(destNum) { setRingtoneMessage = ringtone }
            }

            override fun getRingtone(id: Int, destNum: Int) = toRemoteExceptions {
                commandSender.sendAdmin(destNum, id, wantResponse = true) { getRingtoneRequest = true }
            }

            override fun setCannedMessages(destNum: Int, messages: String) = toRemoteExceptions {
                commandSender.sendAdmin(destNum) { setCannedMessageModuleMessages = messages }
            }

            override fun getCannedMessages(id: Int, destNum: Int) = toRemoteExceptions {
                commandSender.sendAdmin(destNum, id, wantResponse = true) {
                    getCannedMessageModuleMessagesRequest = true
                }
            }

            override fun setChannel(payload: ByteArray?) = toRemoteExceptions {
                if (payload != null) {
                    val c = ChannelProtos.Channel.parseFrom(payload)
                    commandSender.sendAdmin(myNodeNum) { setChannel = c }
                }
            }

            override fun setRemoteChannel(id: Int, num: Int, payload: ByteArray?) = toRemoteExceptions {
                if (payload != null) {
                    val c = ChannelProtos.Channel.parseFrom(payload)
                    commandSender.sendAdmin(num, id) { setChannel = c }
                }
            }

            override fun getRemoteChannel(id: Int, destNum: Int, index: Int) = toRemoteExceptions {
                commandSender.sendAdmin(destNum, id, wantResponse = true) { getChannelRequest = index + 1 }
            }

            override fun beginEditSettings() = toRemoteExceptions {
                commandSender.sendAdmin(myNodeNum) { beginEditSettings = true }
            }

            override fun commitEditSettings() = toRemoteExceptions {
                commandSender.sendAdmin(myNodeNum) { commitEditSettings = true }
            }

            override fun getChannelSet(): ByteArray = toRemoteExceptions {
                runBlocking { radioConfigRepository.channelSetFlow.first().toByteArray() }
            }

            override fun getNodes(): List<NodeInfo> = nodeManager.getNodes()

            override fun connectionState(): String = connectionStateHolder.connectionState.value.toString()

            override fun startProvideLocation() {
                locationManager.start { commandSender.sendPosition(it) }
            }

            override fun stopProvideLocation() {
                locationManager.stop()
            }

            override fun removeByNodenum(requestId: Int, nodeNum: Int) = toRemoteExceptions {
                nodeManager.removeByNodenum(nodeNum)
                commandSender.sendAdmin(myNodeNum, requestId) { removeByNodenum = nodeNum }
            }

            override fun requestUserInfo(destNum: Int) = toRemoteExceptions {
                if (destNum != myNodeNum) {
                    commandSender.requestUserInfo(destNum)
                }
            }

            override fun requestPosition(destNum: Int, position: Position) = toRemoteExceptions {
                if (destNum != myNodeNum) {
                    val provideLocation = meshPrefs.shouldProvideNodeLocation(myNodeNum)
                    val currentPosition =
                        when {
                            provideLocation && position.isValid() -> position
                            else ->
                                nodeManager.nodeDBbyNodeNum[myNodeNum]
                                    ?.position
                                    ?.let { Position(it) }
                                    ?.takeIf { it.isValid() }
                        }
                    currentPosition?.let { commandSender.requestPosition(destNum, it) }
                }
            }

            override fun setFixedPosition(destNum: Int, position: Position) = toRemoteExceptions {
                commandSender.setFixedPosition(destNum, position)
            }

            override fun requestTraceroute(requestId: Int, destNum: Int) = toRemoteExceptions {
                commandSender.requestTraceroute(requestId, destNum)
            }

            override fun requestShutdown(requestId: Int, destNum: Int) = toRemoteExceptions {
                commandSender.sendAdmin(destNum, requestId) { shutdownSeconds = 5 }
            }

            override fun requestReboot(requestId: Int, destNum: Int) = toRemoteExceptions {
                commandSender.sendAdmin(destNum, requestId) { rebootSeconds = 5 }
            }

            override fun rebootToDfu() = toRemoteExceptions {
                commandSender.sendAdmin(myNodeNum) { enterDfuModeRequest = true }
            }

            override fun requestFactoryReset(requestId: Int, destNum: Int) = toRemoteExceptions {
                commandSender.sendAdmin(destNum, requestId) { factoryResetDevice = 1 }
            }

            override fun requestNodedbReset(requestId: Int, destNum: Int, preserveFavorites: Boolean) =
                toRemoteExceptions {
                    commandSender.sendAdmin(destNum, requestId) { nodedbReset = preserveFavorites }
                }

            override fun getDeviceConnectionStatus(requestId: Int, destNum: Int) = toRemoteExceptions {
                commandSender.sendAdmin(destNum, requestId, wantResponse = true) {
                    getDeviceConnectionStatusRequest = true
                }
            }
        }

    private fun updateLastAddress(deviceAddr: String?) {
        val currentAddr = meshPrefs.deviceAddress
        if (deviceAddr != currentAddr) {
            meshPrefs.deviceAddress = deviceAddr
            serviceScope.handledLaunch {
                nodeManager.clear()
                databaseManager.switchActiveDatabase(deviceAddr)
                serviceNotifications.clearNotifications()
                nodeManager.loadCachedNodeDB()
            }
        }
    }
}
