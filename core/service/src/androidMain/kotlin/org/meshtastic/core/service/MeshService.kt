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
package org.meshtastic.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.koin.android.ext.android.inject
import org.meshtastic.core.common.hasLocationPermission
import org.meshtastic.core.common.util.toRemoteExceptions
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.RadioNotConnectedException
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.SERVICE_NOTIFY_ID
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.PortNum

/**
 * Android foreground service that hosts the Meshtastic mesh radio connection.
 *
 * Acts as the lifecycle anchor for the [MeshServiceOrchestrator], which manages all manager initialization and
 * connection state. Exposes an AIDL binder for external client integration via [core:api].
 */
// IMeshService is deprecated but still required for AIDL binding
@Suppress("TooManyFunctions", "LargeClass", "DEPRECATION")
class MeshService : Service() {

    private val radioInterfaceService: RadioInterfaceService by inject()

    private val serviceRepository: ServiceRepository by inject()

    private val serviceBroadcasts: ServiceBroadcasts by inject()

    private val nodeManager: NodeManager by inject()

    private val commandSender: CommandSender by inject()

    private val locationManager: MeshLocationManager by inject()

    private val connectionManager: MeshConnectionManager by inject()

    private val notifications: MeshServiceNotifications by inject()

    /** Android-typed accessor for the foreground service notification. */
    private val androidNotifications: MeshServiceNotificationsImpl
        get() = notifications as MeshServiceNotificationsImpl

    private val orchestrator: MeshServiceOrchestrator by inject()

    private val router: MeshRouter by inject()

    private val dispatchers: CoroutineDispatchers by inject()

    private val serviceJob = Job()
    private val serviceScope by lazy { CoroutineScope(dispatchers.io + serviceJob) }

    private var isServiceInitialized = false

    private val myNodeNum: Int
        get() = nodeManager.myNodeNum.value ?: throw RadioNotConnectedException()

    companion object {
        fun actionReceived(portNum: Int): String {
            val portType = PortNum.fromValue(portNum)
            val portStr = portType?.toString() ?: portNum.toString()
            return actionReceived(portStr)
        }

        fun createIntent(context: Context) = Intent(context, MeshService::class.java)

        fun changeDeviceAddress(context: Context, service: IMeshService, address: String?) {
            service.setDeviceAddress(address)
            startService(context)
        }

        val minDeviceVersion = DeviceVersion(DeviceVersion.MIN_FW_VERSION)
        val absoluteMinDeviceVersion = DeviceVersion(DeviceVersion.ABS_MIN_FW_VERSION)
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i { "Creating mesh service" }

        try {
            orchestrator.start()
            isServiceInitialized = true
        } catch (e: IllegalStateException) {
            // Koin throws IllegalStateException when the DI graph is not yet initialized.
            // This can happen if the system restarts the service (e.g. after a crash or on boot)
            // before Application.onCreate() has finished setting up Koin.
            // In release builds, R8 may merge Koin's InstanceCreationException with unrelated
            // exception classes (observed as io.ktor.http.URLDecodeException), so we cannot rely
            // on the exception type alone. We catch IllegalStateException narrowly around the
            // orchestrator/DI access — not around super.onCreate() — so framework exceptions
            // still propagate normally.
            Logger.e(e) { "MeshService: DI not ready, stopping service" }
            stopSelf()
            return
        }
    }

    @Suppress("ReturnCount")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isServiceInitialized) {
            Logger.w { "onStartCommand called but service is not initialized (likely DI failure). Stopping." }
            stopSelf()
            return START_NOT_STICKY
        }

        val a = radioInterfaceService.getDeviceAddress()
        val wantForeground = a != null && a != "n"

        connectionManager.updateStatusNotification()
        val notification = androidNotifications.getServiceNotification()

        val foregroundServiceType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                if (hasLocationPermission()) {
                    types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
                types
            } else {
                0
            }

        startForegroundSafely(notification, foregroundServiceType)

        return if (!wantForeground) {
            Logger.i { "Stopping mesh service because no device is selected" }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            START_NOT_STICKY
        } else {
            START_STICKY
        }
    }

    private fun startForegroundSafely(notification: android.app.Notification, foregroundServiceType: Int) {
        @Suppress("TooGenericExceptionCaught")
        try {
            ServiceCompat.startForeground(this, SERVICE_NOTIFY_ID, notification, foregroundServiceType)
        } catch (ex: android.app.ForegroundServiceStartNotAllowedException) {
            Logger.e(ex) { "ForegroundServiceStartNotAllowedException: OS restricted background start." }
        } catch (ex: SecurityException) {
            // On Android 14+ starting a location FGS from the background can fail with SecurityException
            // if the app is not in an allowed state. Retry without the location type if that was requested.
            val connectedDeviceOnly =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                }
            if (foregroundServiceType != connectedDeviceOnly) {
                Logger.w(ex) {
                    "Failed to start foreground service with location type, retrying with connectedDevice only"
                }
                try {
                    ServiceCompat.startForeground(this, SERVICE_NOTIFY_ID, notification, connectedDeviceOnly)
                } catch (retryEx: android.app.ForegroundServiceStartNotAllowedException) {
                    Logger.e(retryEx) { "ForegroundServiceStartNotAllowedException on retry." }
                } catch (retryEx: Exception) {
                    Logger.e(retryEx) { "Failed to start foreground service even after retry" }
                }
            } else {
                Logger.e(ex) { "SecurityException starting foreground service" }
            }
        } catch (ex: Exception) {
            Logger.e(ex) { "Error starting foreground service" }
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
        if (isServiceInitialized) {
            orchestrator.stop()
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    private val binder =
        object : IMeshService.Stub() {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun setDeviceAddress(deviceAddr: String?) = toRemoteExceptions {
                Logger.d { "Passing through device change to radio service: ${deviceAddr?.anonymize}" }
                router.actionHandler.handleUpdateLastAddress(deviceAddr)
                radioInterfaceService.setDeviceAddress(deviceAddr)
            }

            override fun subscribeReceiver(packageName: String, receiverName: String) {
                serviceBroadcasts.subscribeReceiver(receiverName, packageName)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun getUpdateStatus(): Int = -4

            @Suppress("OVERRIDE_DEPRECATION")
            override fun startFirmwareUpdate() {
                // No-op: firmware update is handled by the in-app OTA system.
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

            override fun getConfig(): ByteArray = toRemoteExceptions { commandSender.getCachedLocalConfig().encode() }

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
                commandSender.getCachedChannelSet().encode()
            }

            override fun getNodes(): List<NodeInfo> = nodeManager.getNodes()

            override fun connectionState(): String = serviceRepository.connectionState.value.toString()

            override fun startProvideLocation() {
                locationManager.start(serviceScope) { commandSender.sendPosition(it) }
            }

            override fun stopProvideLocation() {
                locationManager.stop()
            }

            override fun removeByNodenum(requestId: Int, nodeNum: Int) = toRemoteExceptions {
                val myNodeNum = nodeManager.myNodeNum.value
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
