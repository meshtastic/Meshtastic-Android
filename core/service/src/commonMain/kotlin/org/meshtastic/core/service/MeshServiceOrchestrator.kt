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
import co.touchlab.kermit.Severity
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.isValidDeviceAddress
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceStateWriter
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
    private val serviceStateWriter: ServiceStateWriter,
    private val nodeManager: NodeManager,
    private val messageProcessor: MeshMessageProcessor,
    private val serviceNotifications: MeshNotificationManager,
    private val takServerManager: TAKServerManager,
    private val takMeshIntegration: TAKMeshIntegration,
    private val takPrefs: TakPrefs,
    private val databaseManager: DatabaseManager,
    private val connectionManager: MeshConnectionManager,
    private val dispatchers: CoroutineDispatchers,
) {
    // Per-start coroutine scope. A fresh scope is created on each start() and cancelled on stop(), so all collectors
    // launched from start() are torn down cleanly and do not accumulate across start/stop/start cycles.
    // Held in an atomic ref so concurrent start()/stop() callers serialize on compareAndSet rather than racing through
    // a check-then-set on a plain var.
    private val scopeRef = atomic<CoroutineScope?>(null)

    /** Whether the orchestrator is currently running. */
    val isRunning: Boolean
        get() = scopeRef.value?.isActive == true

    /**
     * Starts the mesh service components and wires up data flows.
     *
     * This is the KMP equivalent of `MeshService.onCreate()`. It connects to the radio and wires incoming radio data to
     * the message processor.
     */
    fun start() {
        val newScope = CoroutineScope(SupervisorJob() + dispatchers.default)
        // Atomic claim — if another thread already installed a live scope, abandon ours.
        if (!scopeRef.compareAndSet(expect = null, update = newScope)) {
            val existing = scopeRef.value
            if (existing?.isActive == true) {
                Logger.d { "start() called while already running, ignoring" }
                return
            }
            // The slot held a dead scope (post-stop). CAS-replace it to avoid racing with another caller.
            if (!scopeRef.compareAndSet(expect = existing, update = newScope)) {
                Logger.d { "start() lost race replacing dead scope, ignoring" }
                newScope.cancel()
                return
            }
        }

        Logger.i { "Starting mesh service orchestrator" }

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

        // Cold-start lifecycle invariant: a transport must NOT start for a selected address until
        // the active DB has been switched to that same address. We enforce the ordering
        // (valid address -> DB switch -> connect) by waiting for currentDeviceAddressFlow to
        // surface a real selected address before performing the DB switch and connecting the
        // radio. Address sync inside SharedRadioInterfaceService now runs independently of
        // connect() (via its init{} listener), so this wait completes as soon as prefs load —
        // without it, connect() would race ahead of the DB switch and the firmware handshake
        // would write into the wrong (or null) DB.
        //
        // If no device is ever selected this suspends indefinitely for the lifetime of newScope;
        // by design the radio must not connect without a selected device. This mirrors
        // MeshService's foreground-service stay-alive gate (isValidDeviceAddress).
        newScope.handledLaunch {
            val address = radioInterfaceService.currentDeviceAddressFlow.first(::isValidDeviceAddress)
            databaseManager.switchActiveDatabase(address)
            // Load cached nodes from the now-active per-device DB before connect() so the firmware
            // handshake doesn't see a stale/empty node set. Previously this ran synchronously in
            // start() and raced ahead of the DB switch, reading the default (or null) DB.
            nodeManager.loadCachedNodeDB()
            Logger.i { "Per-device database initialized, connecting radio" }
            radioInterfaceService.connect()
        }

        // Mid-session device-address transitions (late process-lifecycle devAddr propagation,
        // user-initiated device switch, etc.): keep the active DB in sync with the selected
        // device. The initial emission (matching the .first() snapshot above) is an idempotent
        // no-op in DatabaseManager. wrap in safeCatching so a single transient failure (e.g. Room
        // I/O error) doesn't kill the collector and silently halt ALL future address propagation
        // for the lifetime of this orchestrator scope. safeCatching re-throws CancellationException
        // so scope cancellation still propagates cleanly.
        //
        // Note: cold-start ordering (valid address -> DB switch -> connect) is enforced above,
        // but a mid-session device switch via setDeviceAddress() races this DB switch against the
        // transport restart. Pre-existing behavior, not introduced by the lifecycle refactor.
        radioInterfaceService.currentDeviceAddressFlow
            .onEach { addr ->
                safeCatching { databaseManager.switchActiveDatabase(addr) }
                    .onFailure { err -> Logger.e(err) { "Failed to switch active database on address update" } }
            }
            .launchIn(newScope)

        radioInterfaceService.receivedData
            // This loop is the single lifeline for every inbound packet. handleFromRadio is already total, but guard
            // here too so nothing — now or later — can cancel the collection and leave the radio permanently deaf.
            .onEach { frame ->
                val session = frame.session
                if (!radioInterfaceService.isSessionActive(session)) {
                    Logger.d { "Dropping queued frame from stale transport session gen=${session.generation}" }
                    return@onEach
                }
                safeCatching { messageProcessor.handleFromRadio(frame, nodeManager.myNodeNum.value) }
                    .onFailure { Logger.e(it) { "Dropped inbound frame after a receive-loop error" } }
            }
            .launchIn(newScope)

        radioInterfaceService.connectionError
            .onEach { errorMessage -> serviceStateWriter.setErrorMessage(errorMessage, Severity.Warn) }
            .launchIn(newScope)
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
        // Best-effort polite goodbye on service teardown (onDestroy / process shutdown). We launch
        // on a fresh detached scope — not the orchestrator's per-start scope — so the subsequent
        // scope.cancel() below doesn't interrupt the short drain delay inside disconnect(). The
        // coroutine is fire-and-forget; typical runtime is ~100-150ms which comfortably fits
        // inside Android's onDestroy() grace window.
        CoroutineScope(SupervisorJob() + dispatchers.default).launch {
            runCatching { radioInterfaceService.disconnect() }
        }
        scopeRef.getAndSet(null)?.cancel()
    }
}
