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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.prefs.meshlog.MeshLogPrefs
import timber.log.Timber

@HiltWorker
class MeshLogCleanupWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val meshLogRepository: MeshLogRepository,
    private val meshLogPrefs: MeshLogPrefs,
) : CoroutineWorker(appContext, workerParams) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result = try {
        val retentionDays = meshLogPrefs.retentionDays
        if (retentionDays <= 0) {
            Timber.i("MeshLogCleanupWorker: Skipping cleanup because retention is set to never delete")
        } else {
            Timber.d("MeshLogCleanupWorker: Cleaning logs older than $retentionDays days")
            meshLogRepository.deleteLogsOlderThan(retentionDays)
            Timber.i("MeshLogCleanupWorker: Successfully cleaned old MeshLog entries")
        }
        Result.success()
    } catch (e: Exception) {
        Timber.e(e, "MeshLogCleanupWorker: Failed to clean MeshLog entries")
        Result.failure()
    }

    companion object {
        const val WORK_NAME = "meshlog_cleanup_worker"
    }
}
