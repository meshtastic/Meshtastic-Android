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

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
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
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioInterfaceService
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
    private val radioInterfaceService: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
    private val nodeManager: NodeManager,
    private val messageProcessor: MeshMessageProcessor,
    private val router: MeshRouter,
    private val serviceNotifications: MeshServiceNotifications,
    private val takServerManager: TAKServerManager,
    private val takMeshIntegration: TAKMeshIntegration,
    private val takPrefs: TakPrefs,
    private val databaseManager: DatabaseManager,
    private val connectionManager: MeshConnectionManager,
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
     * This is the KMP equivalent of `MeshService.onCreate()`. It connects to the radio and wires incoming radio data to
     * the message processor and service actions to the router's action handler.
     */
    fun start() {
        if (isRunning) {
            Logger.d { "start() called while already running, ignoring" }
            return
        }

        Logger.i { "Starting mesh service orchestrator" }
        val newScope = CoroutineScope(SupervisorJob() + dispatchers.default)
        scope = newScope

        // Drop any bytes that piled up in the service's receivedData channel since the last stop(). The channel
        // outlives the orchestrator's per-start scope, so without this drain a stop/start cycle would replay stale
        // packets ahead of the fresh session's firmware handshake.
        radioInterfaceService.resetReceivedBuffer()

        serviceNotifications.initChannels()
        connectionManager.updateStatusNotification()

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
            // Ensure the per-device database is active before the radio connects.
            // On Android this is handled by MeshUtilApplication.init(); on Desktop (and any
            // future KMP host) the orchestrator is the first entry point, so it must initialize
            // the database here. Without this, DatabaseManager._currentDb stays null and all
            // Room writes via withDb() are silently dropped — causing ourNodeInfo to remain null
            // after the handshake completes.
            databaseManager.switchActiveDatabase(radioInterfaceService.getDeviceAddress())
            Logger.i { "Per-device database initialized, connecting radio" }
            radioInterfaceService.connect()
        }

        radioInterfaceService.receivedData
            .onEach { bytes -> messageProcessor.handleFromRadio(bytes, nodeManager.myNodeNum.value) }
            .launchIn(newScope)

        radioInterfaceService.connectionError
            .onEach { errorMessage -> serviceRepository.setErrorMessage(errorMessage, Severity.Warn) }
            .launchIn(newScope)

        // Each action is dispatched in its own supervised coroutine so that a failure in one
        // action (e.g. a timeout in sendAdminAwait) cannot terminate the collector and silently
        // drop all subsequent service actions for the rest of the session.
        serviceRepository.serviceAction
            .onEach { action -> newScope.handledLaunch { router.actionHandler.onServiceAction(action) } }
            .launchIn(newScope)

        nodeManager.loadCachedNodeDB()
    }

    /**
     * Stops the mesh service components and cancels the coroutine scope.
     *
     * This is the KMP equivalent of `MeshService.onDestroy()`.
     */
    fun stop() {
        Logger.i { "Stopping mesh service orchestrator" }
        // Guard stop() so we don't emit a spurious "stopped" log when TAK was never started
        if (takServerManager.isRunning.value) {
            takMeshIntegration.stop()
        }
        scope?.cancel()
        scope = null
    }
}
