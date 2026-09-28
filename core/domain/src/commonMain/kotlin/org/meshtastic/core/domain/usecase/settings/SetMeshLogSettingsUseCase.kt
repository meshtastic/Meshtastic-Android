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
package org.meshtastic.core.domain.usecase.settings

import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.MeshLogRepository

/**
 * Use case for managing mesh log settings.
 *
 * The deletes a settings change triggers run on [applicationScope], not the caller's scope: a retention prune deletes
 * in batches and stops at cancellation, so closing the screen that asked for it must not cut the pass short.
 */
@Single
open class SetMeshLogSettingsUseCase
constructor(
    private val meshLogRepository: MeshLogRepository,
    private val meshLogPrefs: MeshLogPrefs,
    private val applicationScope: ApplicationCoroutineScope,
) {
    /**
     * Sets the retention period for mesh logs and prunes to it in the background.
     *
     * @param days The number of days to retain logs.
     */
    fun setRetentionDays(days: Int) {
        val clamped = days.coerceIn(MeshLogPrefs.MIN_RETENTION_DAYS, MeshLogPrefs.MAX_RETENTION_DAYS)
        meshLogPrefs.setRetentionDays(clamped)
        launchDeletion("prune to the new retention") { meshLogRepository.deleteLogsOlderThan(clamped) }
    }

    /**
     * Enables or disables mesh logging, clearing or pruning the stored logs in the background.
     *
     * @param enabled True to enable logging, false to disable.
     */
    fun setLoggingEnabled(enabled: Boolean) {
        meshLogPrefs.setLoggingEnabled(enabled)
        if (!enabled) {
            launchDeletion("clear the disabled log") { meshLogRepository.deleteAll() }
        } else {
            launchDeletion("prune to retention") {
                meshLogRepository.deleteLogsOlderThan(meshLogPrefs.retentionDays.value)
            }
        }
    }

    private fun launchDeletion(action: String, block: suspend () -> Unit) {
        applicationScope.launch {
            safeCatching { block() }.onFailure { Logger.e(it) { "Mesh log settings failed to $action" } }
        }
    }
}
