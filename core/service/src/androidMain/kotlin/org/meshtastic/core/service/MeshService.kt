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
@file:Suppress("TooGenericExceptionCaught")

package org.meshtastic.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import co.touchlab.kermit.Logger
import org.koin.android.ext.android.inject
import org.meshtastic.core.common.hasLocationPermission
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.SERVICE_NOTIFY_ID

/**
 * Android foreground service that hosts the Meshtastic mesh radio connection.
 *
 * Acts as the lifecycle anchor for the [MeshServiceOrchestrator], which manages all manager initialization and
 * connection state.
 */
@Suppress("LargeClass")
class MeshService : Service() {

    private val radioInterfaceService: RadioInterfaceService by inject()

    private val connectionManager: MeshConnectionManager by inject()

    private val notifications: MeshNotificationManager by inject()

    /** Android-typed accessor for the foreground service notification. */
    private val androidNotifications: MeshNotificationManagerImpl
        get() = notifications as MeshNotificationManagerImpl

    private val orchestrator: MeshServiceOrchestrator by inject()

    private var isServiceInitialized = false

    /**
     * Partial wake lock held while the foreground service is running. Prevents the CPU from being throttled while the
     * TAK server's keepalive coroutines, socket writes, and mesh packet handlers need to run on a regular cadence.
     * Without this, OEM battery optimizations can pause coroutines for long enough that connected TAK clients
     * (ATAK/iTAK) time out waiting for data, even though the foreground service itself keeps the process alive.
     */
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        fun createIntent(context: Context) = Intent(context, MeshService::class.java)

        val minDeviceVersion = DeviceVersion(DeviceVersion.MIN_FW_VERSION)
        val absoluteMinDeviceVersion = DeviceVersion(DeviceVersion.ABS_MIN_FW_VERSION)

        private const val WAKE_LOCK_TIMEOUT_MS = 30L * 60L * 1_000L // 30 minutes
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i { "Creating mesh service" }

        try {
            orchestrator.start()
            isServiceInitialized = true
        } catch (e: IllegalStateException) {
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

        val address = radioInterfaceService.getDeviceAddress()
        val wantForeground = address != null && address != "n"

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
            releaseWakeLock()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            START_NOT_STICKY
        } else {
            acquireWakeLock()
            START_STICKY
        }
    }

    private fun startForegroundSafely(notification: android.app.Notification, foregroundServiceType: Int) {
        try {
            ServiceCompat.startForeground(this, SERVICE_NOTIFY_ID, notification, foregroundServiceType)
        } catch (ex: android.app.ForegroundServiceStartNotAllowedException) {
            Logger.e(ex) { "ForegroundServiceStartNotAllowedException: OS restricted background start." }
        } catch (ex: SecurityException) {
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

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val lock =
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Meshtastic::MeshServiceWakeLock").apply {
                    setReferenceCounted(false)
                }
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
            wakeLock = lock
            Logger.i { "Acquired partial wake lock for mesh service" }
        } catch (e: SecurityException) {
            Logger.w(e) { "Failed to acquire wake lock — WAKE_LOCK permission missing?" }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to acquire wake lock" }
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        try {
            if (lock.isHeld) {
                lock.release()
                Logger.i { "Released partial wake lock for mesh service" }
            }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to release wake lock" }
        } finally {
            wakeLock = null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Logger.i { "Mesh service: onTaskRemoved" }
    }

    // Required by Service — this is a started service (not bound), so always returns null.
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.i { "Destroying mesh service" }
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (isServiceInitialized) {
            orchestrator.stop()
        }
        super.onDestroy()
    }
}
