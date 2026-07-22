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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import org.meshtastic.core.common.hasLocationPermission
import org.meshtastic.core.common.util.isValidDeviceAddress
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.model.util.anonymize
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

    private val conversationShortcutPublisher: ConversationShortcutPublisher by inject()

    private var isServiceInitialized = false

    /**
     * Scope for short-lived coroutines owned by this service (e.g. waiting for the selected-device address to load).
     * Canceled in [onDestroy].
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Active job waiting for the selected-device address to be loaded from DataStore. Null when no wait is pending
     * (either the address was already valid at [onStartCommand] time, or the wait already resolved).
     */
    private var addressWaitJob: Job? = null

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

        /**
         * How long [onStartCommand] will keep the service alive waiting for the selected-device address flow to emit a
         * valid value before concluding that no device is genuinely selected. Covers cold-start DataStore load time;
         * only the initial null/blank value is treated as transient.
         */
        private const val DEVICE_ADDRESS_SETTLE_MS = 5_000L
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

        // Keep conversation shortcuts published for the whole service lifetime (not only during an Android Auto
        // session) so message notifications can link to them and get Conversations-section treatment on phones.
        conversationShortcutPublisher.startObserving(serviceScope)
    }

    @Suppress("ReturnCount")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isServiceInitialized) {
            Logger.w { "onStartCommand called but service is not initialized (likely DI failure). Stopping." }
            stopSelf()
            return START_NOT_STICKY
        }

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

        // Start foreground FIRST. Android requires startForeground() within ~5s of onStartCommand and before any
        // potentially-blocking work. We never defer this — even when the selected-device address is not yet loaded.
        startForegroundSafely(notification, foregroundServiceType)

        val address = radioInterfaceService.getDeviceAddress()
        if (isValidDeviceAddress(address)) {
            // Address is already loaded and valid — proceed normally.
            addressWaitJob?.cancel()
            addressWaitJob = null
            acquireWakeLock()
            Logger.i { "MeshService: selected device ready (${address.anonymize}), staying foreground" }
            return START_STICKY
        }

        // Address is currently null/blank/sentinel. This may be a transient state while DataStore emits the persisted
        // value (cold-start race: RadioPrefsImpl.devAddr starts as null until the first DataStore emission). Make no
        // irreversible stopSelf() decision here; wait briefly for the address flow to settle.
        Logger.i { "MeshService: selected address not yet loaded (${address.anonymize}); waiting for address flow" }
        scheduleDeviceAddressResolution()
        return START_STICKY
    }

    /**
     * Waits for [RadioInterfaceService.currentDeviceAddressFlow] to emit a valid device address. Resolves the transient
     * null/blank window at cold start without busy-polling [RadioInterfaceService.getDeviceAddress].
     * - On a valid emission: acquires the wake lock and the service continues as a normal foreground service.
     * - On timeout (no valid address observed): concludes no device is genuinely selected and stops cleanly.
     *
     * Uses [first] with a predicate rather than `drop(1)` so we never skip an already-current valid StateFlow value: if
     * the address arrived between the synchronous [onStartCommand] read and this subscription, [first] returns it
     * immediately instead of waiting the full timeout and spuriously stopping a service that has a valid device.
     */
    private fun scheduleDeviceAddressResolution() {
        addressWaitJob?.cancel()
        addressWaitJob =
            serviceScope.launch {
                val resolved =
                    withTimeoutOrNull(DEVICE_ADDRESS_SETTLE_MS) {
                        radioInterfaceService.currentDeviceAddressFlow.first(::isValidDeviceAddress)
                    }
                if (isValidDeviceAddress(resolved)) {
                    Logger.i { "MeshService: selected device resolved (${resolved.anonymize}) after address-flow wait" }
                    acquireWakeLock()
                } else {
                    Logger.i { "MeshService: no device selected after address flow settled; stopping" }
                    releaseWakeLock()
                    ServiceCompat.stopForeground(this@MeshService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
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
        conversationShortcutPublisher.stopObserving()
        addressWaitJob?.cancel()
        addressWaitJob = null
        serviceScope.cancel()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (isServiceInitialized) {
            orchestrator.stop()
        }
        super.onDestroy()
    }
}
