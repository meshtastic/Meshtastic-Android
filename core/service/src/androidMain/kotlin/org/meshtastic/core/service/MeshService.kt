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
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.SERVICE_NOTIFY_ID
import org.meshtastic.core.repository.ServiceRepository

/**
 * Android foreground service that hosts the Meshtastic mesh radio connection.
 *
 * Acts as the lifecycle anchor for the [MeshServiceOrchestrator], which manages all manager initialization and
 * connection state. With the SDK hard-cutover, this service no longer exposes an AIDL binder — all communication
 * flows through the SDK's RadioClient via [SdkRadioControllerImpl] and [SdkStateBridge].
 */
class MeshService : Service() {

    private val radioPrefs: RadioPrefs by inject()

    private val notifications: MeshServiceNotifications by inject()

    private val serviceRepository: ServiceRepository by inject()

    /** Android-typed accessor for the foreground service notification. */
    private val androidNotifications: MeshServiceNotificationsImpl
        get() = notifications as MeshServiceNotificationsImpl

    private val orchestrator: MeshServiceOrchestrator by inject()

    private val dispatchers: CoroutineDispatchers by inject()

    private val sdkClientLifecycle: SdkClientLifecycle by inject()

    private val serviceJob = Job()
    private val serviceScope by lazy { CoroutineScope(dispatchers.io + serviceJob) }

    private var isServiceInitialized = false

    companion object {
        fun createIntent(context: Context) = Intent(context, MeshService::class.java)

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

        val a = radioPrefs.devAddr.value
        val wantForeground = a != null && a != "n"

        notifications.updateServiceStateNotification(serviceRepository.connectionState.value, null)
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.i { "Destroying mesh service" }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (isServiceInitialized) {
            orchestrator.stop()
        }
        sdkClientLifecycle.disconnect()
        serviceJob.cancel()
        super.onDestroy()
    }
}
