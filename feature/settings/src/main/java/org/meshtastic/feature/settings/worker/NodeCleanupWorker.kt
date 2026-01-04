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
package org.meshtastic.feature.settings.worker

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
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.service.ServiceRepository

@HiltWorker
class NodeCleanupWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
) : CoroutineWorker(appContext, workerParams) {

    constructor(
        appContext: Context,
        workerParams: WorkerParameters,
    ) : this(
        appContext,
        workerParams,
        entryPoint(appContext).nodeRepository(),
        entryPoint(appContext).serviceRepository(),
    )

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result = try {
        val olderThanDays = inputData.getInt(KEY_OLDER_THAN_DAYS, DEFAULT_DAYS)
        val olderThanMinutes = inputData.getInt(KEY_OLDER_THAN_MINUTES, -1)
        val onlyUnknownNodes = inputData.getBoolean(KEY_ONLY_UNKNOWN, false)

        val nodesToDelete =
            if (olderThanMinutes > 0) {
                nodeRepository.getNodesForCleanupMinutes(olderThanMinutes, onlyUnknownNodes)
            } else {
                nodeRepository.getNodesForCleanup(olderThanDays, onlyUnknownNodes)
            }
        if (nodesToDelete.isEmpty()) {
            logger.i { "Node cleanup: no nodes eligible for deletion (olderThanDays=$olderThanDays, unknownOnly=$onlyUnknownNodes)" }
            return Result.success()
        }

        val nodeNums = nodesToDelete.map { it.num }
        nodeRepository.deleteNodes(nodeNums)

        serviceRepository.meshService?.let { service ->
            nodeNums.forEach { nodeNum -> service.removeByNodenum(service.packetId, nodeNum) }
        }

        logger.i { "Node cleanup: removed ${nodeNums.size} nodes older than $olderThanDays days" }
        Result.success()
    } catch (e: Exception) {
        logger.e(e) { "Node cleanup failed" }
        Result.failure()
    }

    companion object {
        const val WORK_NAME = "node_cleanup_worker"
        const val KEY_OLDER_THAN_DAYS = "older_than_days"
        const val KEY_OLDER_THAN_MINUTES = "older_than_minutes"
        const val KEY_ONLY_UNKNOWN = "only_unknown"
        const val DEFAULT_DAYS = 30

        private fun entryPoint(context: Context): NodeWorkerEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, NodeWorkerEntryPoint::class.java)
    }

    private val logger = Logger.withTag(WORK_NAME)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NodeWorkerEntryPoint {
    fun nodeRepository(): NodeRepository

    fun serviceRepository(): ServiceRepository
}

