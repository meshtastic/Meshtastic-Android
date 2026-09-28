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
import kotlinx.coroutines.delay
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.MeshLogRetention
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Prunes the mesh log to the persisted retention policy. Android runs [runOnce] from `MeshLogCleanupWorker` on
 * WorkManager's hourly schedule; desktop has no WorkManager and runs [runHourly] for as long as the app is open.
 */
@Single
class MeshLogCleanup(private val meshLogRepository: MeshLogRepository, private val meshLogPrefs: MeshLogPrefs) {

    /** One pass under the persisted policy. Failures propagate so each scheduler reports them its own way. */
    suspend fun runOnce() {
        val policy = meshLogPrefs.awaitCleanupPolicy()
        val retentionWindow = MeshLogRetention.windowOrNull(policy.retentionDays)
        if (!policy.loggingEnabled) {
            logger.i { "Skipping cleanup because mesh log storage is disabled" }
        } else if (retentionWindow == null) {
            logger.i { "Skipping cleanup because retention is set to never delete" }
        } else {
            logger.d { "Cleaning logs older than $retentionWindow" }
            meshLogRepository.deleteLogsOlderThan(policy.retentionDays)
            logger.i { "Successfully cleaned old MeshLog entries" }
        }
    }

    /**
     * Runs [runOnce] [FIRST_RUN_DELAY] after it is called and every [INTERVAL] after that, until the calling coroutine
     * is cancelled. A failed pass is logged and the next one still runs, as WorkManager runs the next period after a
     * failed one.
     */
    suspend fun runHourly(): Nothing {
        delay(FIRST_RUN_DELAY)
        while (true) {
            safeCatching { runOnce() }.onFailure { logger.e(it) { "Failed to clean MeshLog entries" } }
            delay(INTERVAL)
        }
    }

    companion object {
        /** Keeps the first pass clear of the startup database switch and radio handshake. */
        val FIRST_RUN_DELAY = 1.minutes

        val INTERVAL = 1.hours

        private val logger = Logger.withTag("MeshLogCleanup")
    }
}
