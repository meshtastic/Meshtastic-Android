/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.prefs.meshlog.MeshLogPrefs

@HiltWorker
class MeshLogCleanupWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val meshLogRepository: MeshLogRepository,
    private val meshLogPrefs: MeshLogPrefs,
) : CoroutineWorker(appContext, workerParams) {

    // Fallback constructor for cases where HiltWorkerFactory is not used (e.g., some WorkManager initializations)
    constructor(appContext: Context, workerParams: WorkerParameters) : this(
        appContext,
        workerParams,
        entryPoint(appContext).meshLogRepository(),
        entryPoint(appContext).meshLogPrefs(),
    )

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result = try {
        val retentionDays = meshLogPrefs.retentionDays
        if (!meshLogPrefs.loggingEnabled) {
            logger.i { "Skipping cleanup because mesh log storage is disabled" }
        } else if (retentionDays == MeshLogPrefs.NEVER_CLEAR_RETENTION_DAYS) {
            logger.i { "Skipping cleanup because retention is set to never delete" }
        } else {
            val retentionLabel =
                if (retentionDays == MeshLogPrefs.ONE_HOUR_RETENTION_DAYS) {
                    "1 hour"
                } else {
                    "$retentionDays days"
                }
            logger.d { "Cleaning logs older than $retentionLabel" }
            meshLogRepository.deleteLogsOlderThan(retentionDays)
            logger.i { "Successfully cleaned old MeshLog entries" }
        }
        Result.success()
    } catch (e: Exception) {
        logger.e(e) { "Failed to clean MeshLog entries" }
        Result.failure()
    }

    companion object {
        const val WORK_NAME = "meshlog_cleanup_worker"

        private fun entryPoint(context: Context): WorkerEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, WorkerEntryPoint::class.java)
    }

    private val logger = Logger.withTag(WORK_NAME)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerEntryPoint {
    fun meshLogRepository(): MeshLogRepository
    fun meshLogPrefs(): MeshLogPrefs
}
