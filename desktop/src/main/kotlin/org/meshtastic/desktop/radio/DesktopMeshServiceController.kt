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
package org.meshtastic.desktop.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository

/**
 * Desktop equivalent of Android's `MeshService.onCreate()`.
 *
 * Starts the full message-processing chain that connects the radio transport layer to the business logic:
 * ```
 * radioInterfaceService.receivedData
 *   → messageProcessor.handleFromRadio(bytes, myNodeNum)
 *   → FromRadioPacketHandler → MeshRouter/PacketHandler/etc.
 * ```
 *
 * On Android this chain runs inside an Android `Service` (foreground service with notifications). On Desktop there is
 * no Android Service concept, so this controller manages the same lifecycle in-process, started at app launch time.
 */
@Suppress("LongParameterList")
class DesktopMeshServiceController(
    private val radioInterfaceService: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
    private val messageProcessor: MeshMessageProcessor,
    private val connectionManager: MeshConnectionManager,
    private val packetHandler: PacketHandler,
    private val router: MeshRouter,
    private val nodeManager: NodeManager,
    private val commandSender: CommandSender,
) {
    private var serviceScope: CoroutineScope? = null

    /**
     * Starts the mesh service processing chain.
     *
     * This should be called once at application startup (after Koin is initialized). It mirrors the initialization
     * logic from `MeshService.onCreate()`.
     */
    @Suppress("InjectDispatcher")
    fun start() {
        if (serviceScope != null) {
            Logger.w { "DesktopMeshServiceController: Already started, ignoring duplicate start()" }
            return
        }

        Logger.i { "DesktopMeshServiceController: Starting mesh service processing chain" }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serviceScope = scope

        // Start all processing components (same order as MeshService.onCreate)
        packetHandler.start(scope)
        router.start(scope)
        nodeManager.start(scope)
        connectionManager.start(scope)
        messageProcessor.start(scope)
        commandSender.start(scope)

        // Auto-connect to saved device address (mirrors MeshService.onCreate)
        scope.handledLaunch { radioInterfaceService.connect() }

        // Wire the data flow: radio → message processor
        radioInterfaceService.receivedData
            .onEach { bytes -> messageProcessor.handleFromRadio(bytes, nodeManager.myNodeNum) }
            .launchIn(scope)

        // Wire service actions to the router
        serviceRepository.serviceAction.onEach(router.actionHandler::onServiceAction).launchIn(scope)

        // Load any cached node database
        nodeManager.loadCachedNodeDB()

        Logger.i { "DesktopMeshServiceController: Processing chain started" }
    }

    /** Stops the mesh service processing chain and cancels all coroutines. */
    fun stop() {
        Logger.i { "DesktopMeshServiceController: Stopping" }
        serviceScope?.cancel("DesktopMeshServiceController stopped")
        serviceScope = null
    }
}
