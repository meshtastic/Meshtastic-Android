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
package org.meshtastic.core.service.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import org.koin.android.annotation.KoinWorker
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.MeshLogRetention

@KoinWorker
class MeshLogCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val meshLogRepository: MeshLogRepository,
    private val meshLogPrefs: MeshLogPrefs,
) : CoroutineWorker(appContext, workerParams) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result = try {
        val policy = meshLogPrefs.awaitCleanupPolicy()
        val retentionDays = policy.retentionDays
        val retentionWindow = MeshLogRetention.windowOrNull(retentionDays)
        if (!policy.loggingEnabled) {
            logger.i { "Skipping cleanup because mesh log storage is disabled" }
        } else if (retentionWindow == null) {
            logger.i { "Skipping cleanup because retention is set to never delete" }
        } else {
            logger.d { "Cleaning logs older than $retentionWindow" }
            meshLogRepository.deleteLogsOlderThan(retentionDays)
            logger.i { "Successfully cleaned old MeshLog entries" }
        }
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.e(e) { "Failed to clean MeshLog entries" }
        Result.failure()
    }

    companion object {
        const val WORK_NAME = "meshlog_cleanup_worker"
    }

    private val logger = Logger.withTag(WORK_NAME)
}
