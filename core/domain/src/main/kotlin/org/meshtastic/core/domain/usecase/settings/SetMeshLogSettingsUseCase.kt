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
package org.meshtastic.core.domain.usecase.settings

import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.prefs.meshlog.MeshLogPrefs
import javax.inject.Inject

/** Use case for managing mesh log settings. */
class SetMeshLogSettingsUseCase
@Inject
constructor(
    private val meshLogRepository: MeshLogRepository,
    private val meshLogPrefs: MeshLogPrefs,
) {
    /**
     * Sets the retention period for mesh logs.
     *
     * @param days The number of days to retain logs.
     */
    suspend fun setRetentionDays(days: Int) {
        val clamped = days.coerceIn(MeshLogPrefs.MIN_RETENTION_DAYS, MeshLogPrefs.MAX_RETENTION_DAYS)
        meshLogPrefs.retentionDays = clamped
        meshLogRepository.deleteLogsOlderThan(clamped)
    }

    /**
     * Enables or disables mesh logging.
     *
     * @param enabled True to enable logging, false to disable.
     */
    suspend fun setLoggingEnabled(enabled: Boolean) {
        meshLogPrefs.loggingEnabled = enabled
        if (!enabled) {
            meshLogRepository.deleteAll()
        } else {
            meshLogRepository.deleteLogsOlderThan(meshLogPrefs.retentionDays)
        }
    }
}
