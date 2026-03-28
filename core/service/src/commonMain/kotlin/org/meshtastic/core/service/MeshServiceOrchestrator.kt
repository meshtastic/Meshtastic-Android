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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketHandler
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
    private val packetHandler: PacketHandler,
    private val nodeManager: NodeManager,
    private val messageProcessor: MeshMessageProcessor,
    private val commandSender: CommandSender,
    private val connectionManager: MeshConnectionManager,
    private val router: MeshRouter,
    private val serviceNotifications: MeshServiceNotifications,
    private val takServerManager: TAKServerManager,
    private val takMeshIntegration: TAKMeshIntegration,
    private val takPrefs: TakPrefs,
    private val dispatchers: org.meshtastic.core.di.CoroutineDispatchers,
) {
    private var serviceJob: Job? = null
    private var takJob: Job? = null

    /** The coroutine scope for the service. Available after [start] is called. */
    var serviceScope: CoroutineScope? = null
        private set

    /** Whether the orchestrator is currently running. */
    val isRunning: Boolean
        get() = serviceJob?.isActive == true

    /**
     * Starts the mesh service components and wires up data flows.
     *
     * This is the KMP equivalent of `MeshService.onCreate()`. It starts all managers, connects to the radio, and wires
     * incoming radio data to the message processor and service actions to the router's action handler.
     */
    fun start() {
        if (isRunning) {
            Logger.w { "MeshServiceOrchestrator.start() called while already running" }
            return
        }

        Logger.i { "Starting mesh service orchestrator" }
        val job = Job()
        serviceJob = job
        val scope = CoroutineScope(dispatchers.default + job)
        serviceScope = scope

        serviceNotifications.initChannels()

        packetHandler.start(scope)
        router.start(scope)
        nodeManager.start(scope)
        connectionManager.start(scope)
        messageProcessor.start(scope)
        commandSender.start(scope)

        // Observe TAK server pref to start/stop
        takJob =
            takPrefs.isTakServerEnabled
                .onEach { isEnabled ->
                    if (isEnabled && !takServerManager.isRunning.value) {
                        Logger.i { "TAK Server enabled by preference, starting integration..." }
                        takMeshIntegration.start(scope)
                    } else if (!isEnabled && takServerManager.isRunning.value) {
                        Logger.i { "TAK Server disabled by preference, stopping integration..." }
                        takMeshIntegration.stop()
                    }
                }
                .launchIn(scope)

        scope.handledLaunch { radioInterfaceService.connect() }

        radioInterfaceService.receivedData
            .onEach { bytes -> messageProcessor.handleFromRadio(bytes, nodeManager.myNodeNum) }
            .launchIn(scope)

        serviceRepository.serviceAction.onEach(router.actionHandler::onServiceAction).launchIn(scope)

        nodeManager.loadCachedNodeDB()
    }

    /**
     * Stops the mesh service components and cancels the coroutine scope.
     *
     * This is the KMP equivalent of `MeshService.onDestroy()`.
     */
    fun stop() {
        Logger.i { "Stopping mesh service orchestrator" }
        takJob?.cancel()
        takJob = null
        takMeshIntegration.stop()
        serviceJob?.cancel()
        serviceJob = null
        serviceScope = null
    }
}
