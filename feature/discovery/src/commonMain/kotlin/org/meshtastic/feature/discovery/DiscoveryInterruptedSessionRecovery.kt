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
package org.meshtastic.feature.discovery

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.ServiceRepository

/** Restores persisted discovery sessions only while their original radio still owns the recovery. */
internal class DiscoveryInterruptedSessionRecovery(
    private val serviceRepository: ServiceRepository,
    private val discoveryDao: DiscoveryDao,
    private val meshPrefs: MeshPrefs,
    private val isScanActive: suspend () -> Boolean,
    private val scheduleRestoreIfIdle: suspend (DiscoverySessionEntity) -> Deferred<Boolean>?,
) {
    suspend fun watch(onRestored: suspend (homePreset: String) -> Unit = {}): Unit = coroutineScope {
        var recoveryJob: Job? = null
        var recoveryDeviceAddress: String? = null
        combine(serviceRepository.connectionState, meshPrefs.deviceAddress) { state, address -> state to address }
            .collect { (state, address) ->
                if (state !is ConnectionState.Connected || address == null) return@collect
                if (recoveryJob?.isActive == true && recoveryDeviceAddress == address) return@collect

                // A restore can wait through a long disconnect. Device identity participates in ownership so a switch
                // while Connected cancels the old waiter and immediately evaluates the replacement radio.
                recoveryJob?.cancel()
                recoveryDeviceAddress = address
                recoveryJob = launch {
                    val result = safeCatching { restoreIfAny(onRestored) }
                    result.exceptionOrNull()?.let { failure ->
                        Logger.w(failure) { "DiscoveryScanEngine: interrupted-session restore failed; will retry" }
                    }
                }
            }
    }

    private suspend fun restoreIfAny(onRestored: suspend (homePreset: String) -> Unit) {
        val address = meshPrefs.deviceAddress.value
        val session =
            if (address != null && !isScanActive()) {
                discoveryDao.getInterruptedSession(address)
            } else {
                null
            }
        val recoverable = session?.takeIf { !isScanActive() && meshPrefs.deviceAddress.value == address }

        when {
            recoverable == null -> return

            recoverable.homeLoraConfig == null -> {
                val finalStatus =
                    finalStatusForPendingRestore(
                        recoverable.completionStatus,
                        default = DiscoverySessionStatus.UNRESTORABLE,
                    )
                val updatedRows = discoveryDao.updateRecoverableSessionCompletionStatus(recoverable.id, finalStatus)
                if (updatedRows != 1) {
                    Logger.w {
                        "DiscoveryScanEngine: session ${recoverable.id} was no longer recoverable; " +
                            "$finalStatus not written"
                    }
                }
            }

            else -> {
                Logger.w { "DiscoveryScanEngine: restoring home config after interrupted session ${recoverable.id}" }
                val restore = scheduleRestoreIfIdle(recoverable) ?: return
                if (awaitForegroundRestore(restore)) {
                    onRestored(recoverable.homePreset)
                } else {
                    // Foreground waiting is bounded, but the application-scope restore can continue. Await the same
                    // Deferred here so a success racing the timeout, or completing later, is still surfaced
                    // exactly once.
                    val lateResult = runCatching { restore.await() }
                    if (lateResult.exceptionOrNull() is CancellationException) {
                        currentCoroutineContext().ensureActive()
                    }
                    if (lateResult.getOrDefault(false)) onRestored(recoverable.homePreset)
                }
            }
        }
    }
}
