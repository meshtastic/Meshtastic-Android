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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import org.koin.core.annotation.Single
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.TakPrefs
import org.meshtastic.core.takserver.TAKMeshIntegration
import org.meshtastic.core.takserver.TAKServerManager

/**
 * Platform-agnostic orchestrator for the mesh service lifecycle.
 *
 * Extracts the startup wiring previously embedded in Android's `MeshService.onCreate()` into a reusable component. Both
 * Android's foreground `Service` and the Desktop `main()` function can use this to start/stop the mesh service graph.
 *
 * All injected dependencies are `commonMain` interfaces with real implementations in `core:data`.
 */
@Suppress("LongParameterList")
@Single
class MeshServiceOrchestrator(
    private val radioPrefs: RadioPrefs,
    private val serviceNotifications: MeshServiceNotifications,
    private val takServerManager: TAKServerManager,
    private val takMeshIntegration: TAKMeshIntegration,
    private val takPrefs: TakPrefs,
    private val databaseManager: DatabaseManager,
    private val serviceRepository: ServiceRepository,
    private val appWidgetUpdater: AppWidgetUpdater,
    private val dispatchers: CoroutineDispatchers,
) {
    // Per-start coroutine scope. A fresh scope is created on each start() and cancelled on stop(), so all collectors
    // launched from start() are torn down cleanly and do not accumulate across start/stop/start cycles.
    private var scope: CoroutineScope? = null

    /** Whether the orchestrator is currently running. */
    val isRunning: Boolean
        get() = scope?.isActive == true

    /**
     * Starts the mesh service components and wires up data flows.
     *
     * With the SDK hard-cutover, the RadioClient (via RadioClientProvider) owns transport and
     * packet handling. This orchestrator retains responsibility for:
     * - Per-device database initialization
     * - TAK server integration lifecycle
     * - Service notification channels
     *
     * ServiceAction dispatch and radio data flows are handled by [SdkStateBridge].
     */
    fun start() {
        if (isRunning) {
            Logger.d { "start() called while already running, ignoring" }
            return
        }

        Logger.i { "Starting mesh service orchestrator (SDK mode)" }
        val newScope = CoroutineScope(SupervisorJob() + dispatchers.default)
        scope = newScope

        serviceNotifications.initChannels()
        serviceNotifications.updateServiceStateNotification(serviceRepository.connectionState.value, null)

        // Keep notification in sync with connection state changes
        serviceRepository.connectionState
            .onEach { state -> serviceNotifications.updateServiceStateNotification(state, null) }
            .launchIn(newScope)

        // Kickstart app widget
        newScope.handledLaunch {
            try {
                appWidgetUpdater.updateAll()
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.e(e) { "Failed to kickstart LocalStatsWidget" }
            }
        }

        // Observe TAK server pref to start/stop
        takPrefs.isTakServerEnabled
            .onEach { isEnabled ->
                if (isEnabled && !takServerManager.isRunning.value) {
                    Logger.i { "TAK Server enabled by preference, starting integration" }
                    takMeshIntegration.start(newScope)
                } else if (!isEnabled && takServerManager.isRunning.value) {
                    Logger.i { "TAK Server disabled by preference, stopping integration" }
                    takMeshIntegration.stop()
                }
            }
            .launchIn(newScope)

        newScope.handledLaunch {
            // Ensure the per-device database is active before SDK connects.
            databaseManager.switchActiveDatabase(radioPrefs.devAddr.value)
            Logger.i { "Per-device database initialized" }
        }
    }

    /**
     * Stops the mesh service components and cancels the coroutine scope.
     *
     * Radio disconnect is handled by [RadioClientProvider.disconnect] / SDK client teardown.
     */
    fun stop() {
        Logger.i { "Stopping mesh service orchestrator" }
        if (takServerManager.isRunning.value) {
            takMeshIntegration.stop()
        }
        scope?.cancel()
        scope = null
    }
}
