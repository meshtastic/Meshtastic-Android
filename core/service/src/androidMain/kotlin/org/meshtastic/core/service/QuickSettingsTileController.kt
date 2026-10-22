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

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.util.isValidDeviceAddress
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Backs [MeshQuickSettingsTileService]: publishes the tile's state and performs its taps on the application scope, so a
 * tap's work outlives the tile service, which the system unbinds as soon as the shade closes.
 */
@Single
@Suppress("LongParameterList")
class QuickSettingsTileController(
    private val context: Context,
    private val radioController: RadioController,
    private val radioInterfaceService: RadioInterfaceService,
    private val radioPrefs: RadioPrefs,
    nodeRepository: NodeRepository,
    private val orchestrator: MeshServiceOrchestrator,
    private val appScope: ApplicationCoroutineScope,
) {
    private val observing = AtomicBoolean(false)

    val state: Flow<QuickSettingsTileState> =
        combine(
            radioController.connectionState,
            radioInterfaceService.currentDeviceAddressFlow,
            nodeRepository.ourNodeInfo.map { it?.user?.short_name },
        ) { connectionState, address, shortName ->
            quickSettingsTileState(
                connectionState,
                isValidDeviceAddress(address),
                orchestrator.isRunning,
                shortName,
            )
        }
            .distinctUntilChanged()

    /** Asks the system to refresh the tile on every state change. Idempotent; runs for the life of the process. */
    fun startObserving() {
        if (!observing.compareAndSet(false, true)) return
        val component = ComponentName(context, MeshQuickSettingsTileService::class.java)
        appScope.launch {
            // The first emission refreshes a tile left stale by a previous process.
            state.collect { _ ->
                safeCatching { TileService.requestListeningState(context, component) }
                    .onFailure { error -> Logger.w(error) { "Quick Settings tile refresh request failed" } }
            }
        }
    }

    /**
     * Performs a tap on the tile.
     *
     * @return true when the tile must open the Connect tab instead, either because no radio is selected or because the
     *   platform will not let the service start from here; opening the app then starts it the usual way.
     */
    suspend fun onClick(): Boolean {
        // The live selection when there is one; otherwise the saved one, which is null until DataStore's first load.
        val address =
            radioInterfaceService.currentDeviceAddressFlow.value
                ?: withTimeoutOrNull(ADDRESS_SETTLE_MS) {
                    try {
                        radioPrefs.awaitDevAddr()
                    } catch (e: IOException) {
                        Logger.w(e) { "Quick Settings tile could not read the saved radio" }
                        null
                    }
                }
        val tile =
            quickSettingsTileState(
                connectionState = radioController.connectionState.value,
                hasSelectedDevice = isValidDeviceAddress(address),
                serviceRunning = orchestrator.isRunning,
                ourShortName = null,
            )
        return when (tile.action) {
            QuickSettingsTileAction.OpenConnections -> true

            QuickSettingsTileAction.Disconnect -> {
                appScope.launch {
                    radioPrefs.setDevName(null)
                    safeCatching { radioController.setDeviceAddress(NO_DEVICE_SELECTED) }
                        .onFailure { Logger.w(it) { "Quick Settings tile disconnect failed" } }
                }
                false
            }

            QuickSettingsTileAction.Connect ->
                if (orchestrator.isRunning && address != null) {
                    // Reselecting the current radio is the Connect tab's own repair path for a dropped link.
                    appScope.launch {
                        safeCatching { radioController.setDeviceAddress(address) }
                            .onFailure { Logger.w(it) { "Quick Settings tile reconnect failed" } }
                    }
                    false
                } else {
                    !MeshService.startService(context, ServiceStartTrigger.QuickSettingsTile)
                }
        }
    }

    private companion object {
        const val ADDRESS_SETTLE_MS = 2_000L
        const val NO_DEVICE_SELECTED = "n"
    }
}
