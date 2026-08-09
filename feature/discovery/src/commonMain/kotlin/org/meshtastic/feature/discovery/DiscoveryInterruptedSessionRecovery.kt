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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
    private val homeRestorer: DiscoveryHomeRestorer,
    private val meshPrefs: MeshPrefs,
    private val isScanActive: suspend () -> Boolean,
    private val scheduleRestoreIfIdle: suspend (DiscoverySessionEntity) -> Deferred<Boolean>?,
) {
    suspend fun watch(onRestored: suspend (homePreset: String) -> Unit = {}): Unit = coroutineScope {
        var recoveryJob: Job? = null
        serviceRepository.connectionState.collect { state ->
            if (state is ConnectionState.Connected && recoveryJob?.isActive != true) {
                // The restore can wait through a long disconnect/reconnect cycle. Keep collection responsive so no
                // connection transition is hidden behind that foreground wait.
                recoveryJob = launch {
                    val result = safeCatching { restoreIfAny() }
                    val failure = result.exceptionOrNull()
                    if (failure != null) {
                        Logger.w(failure) { "DiscoveryScanEngine: interrupted-session restore failed; will retry" }
                    }
                    result.getOrNull()?.let { homePreset -> onRestored(homePreset) }
                }
            }
        }
    }

    private suspend fun restoreIfAny(): String? {
        val address = meshPrefs.deviceAddress.value
        val session =
            if (address != null && !isScanActive()) {
                discoveryDao.getInterruptedSession(address)
            } else {
                null
            }
        val recoverable = session?.takeIf { !isScanActive() && meshPrefs.deviceAddress.value == address }

        return when {
            recoverable == null -> null

            recoverable.homeLoraConfig == null -> {
                val finalStatus =
                    finalStatusForPendingRestore(
                        recoverable.completionStatus,
                        default = DiscoverySessionStatus.UNRESTORABLE,
                    )
                discoveryDao.updateRecoverableSessionCompletionStatus(recoverable.id, finalStatus)
                null
            }

            else -> {
                Logger.w { "DiscoveryScanEngine: restoring home config after interrupted session ${recoverable.id}" }
                val restore = scheduleRestoreIfIdle(recoverable) ?: return null
                val restored = awaitForegroundRestore(restore)
                recoverable.homePreset.takeIf { restored }
            }
        }
    }
}
