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

package org.meshtastic.feature.settings.radio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import org.meshtastic.feature.settings.worker.NodeCleanupWorker
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.service.ServiceRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val MIN_DAYS_THRESHOLD = 7f

/**
 * ViewModel for [CleanNodeDatabaseScreen]. Manages the state and logic for cleaning the node database based on
 * specified criteria. The "older than X days" filter is always active.
 */
@HiltViewModel
class CleanNodeDatabaseViewModel
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _olderThanDays = MutableStateFlow(30f)
    val olderThanDays = _olderThanDays.asStateFlow()

    private val _onlyUnknownNodes = MutableStateFlow(false)
    val onlyUnknownNodes = _onlyUnknownNodes.asStateFlow()

    private val _nodesToDelete = MutableStateFlow<List<NodeEntity>>(emptyList())
    val nodesToDelete = _nodesToDelete.asStateFlow()

    private val _scheduleEvents = MutableSharedFlow<ScheduleResult>()
    val scheduleEvents = _scheduleEvents.asSharedFlow()

    private val _scheduleStatus = MutableStateFlow<ScheduleStatus?>(null)
    val scheduleStatus = _scheduleStatus.asStateFlow()

    private val _runNowEvents = MutableSharedFlow<RunNowResult>()
    val runNowEvents = _runNowEvents.asSharedFlow()

    fun onOlderThanDaysChanged(value: Float) {
        _olderThanDays.value = value
    }

    fun onOnlyUnknownNodesChanged(value: Boolean) {
        _onlyUnknownNodes.value = value
        if (!value && _olderThanDays.value < MIN_DAYS_THRESHOLD) {
            _olderThanDays.value = MIN_DAYS_THRESHOLD
        }
    }

    /**
     * Updates the list of nodes to be deleted based on the current filter criteria. The logic is as follows:
     * - The "older than X days" filter (controlled by the slider) is always active.
     * - If "only unknown nodes" is also enabled, nodes that are BOTH unknown AND older than X days are selected.
     * - If "only unknown nodes" is not enabled, all nodes older than X days are selected.
     * - Nodes with an associated public key (PKI) heard from within the last 7 days are always excluded from deletion.
     * - Nodes marked as ignored or favorite are always excluded from deletion.
     */
    fun getNodesToDelete() {
        viewModelScope.launch {
            _nodesToDelete.value =
                nodeRepository.getNodesForCleanup(
                    olderThanDays = _olderThanDays.value.toInt(),
                    onlyUnknownNodes = _onlyUnknownNodes.value,
                )
        }
    }

    /**
     * Deletes the nodes currently queued in [_nodesToDelete] from the database and instructs the mesh service to remove
     * them.
     */
    fun cleanNodes() {
        viewModelScope.launch {
            val nodeNums = _nodesToDelete.value.map { it.num }
            if (nodeNums.isNotEmpty()) {
                nodeRepository.deleteNodes(nodeNums)

                val service = serviceRepository.meshService
                if (service != null) {
                    for (nodeNum in nodeNums) {
                        service.removeByNodenum(service.packetId, nodeNum)
                    }
                }
            }
            // Clear the list after deletion or if it was empty
            _nodesToDelete.value = emptyList()
        }
    }

    /**
     * Schedule recurring cleanup using WorkManager. Runs hourly (matching mesh log cleanup cadence).
     */
    fun scheduleNodeCleanup(olderThanDays: Int, onlyUnknownNodes: Boolean, olderThanMinutes: Int? = null) {
        viewModelScope.launch {
            try {
                val data =
                    workDataOf(
                        NodeCleanupWorker.KEY_OLDER_THAN_DAYS to olderThanDays,
                        NodeCleanupWorker.KEY_OLDER_THAN_MINUTES to (olderThanMinutes ?: -1),
                        NodeCleanupWorker.KEY_ONLY_UNKNOWN to onlyUnknownNodes,
                    )
                val request =
                    PeriodicWorkRequestBuilder<NodeCleanupWorker>(1, TimeUnit.HOURS)
                        .setInputData(data)
                        .addTag("${NodeCleanupWorker.TAG_DAYS_PREFIX}$olderThanDays")
                        .apply {
                            if (olderThanMinutes != null && olderThanMinutes > 0) {
                                addTag("${NodeCleanupWorker.TAG_MINUTES_PREFIX}$olderThanMinutes")
                            }
                        }
                        .apply {
                            if (onlyUnknownNodes) addTag(NodeCleanupWorker.TAG_UNKNOWN)
                        }
                        .build()

                WorkManager.getInstance(appContext)
                    .enqueueUniquePeriodicWork(
                        NodeCleanupWorker.WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request,
                    )

                refreshScheduleStatus()
                _scheduleEvents.emit(ScheduleResult.Success)
            } catch (e: Exception) {
                _scheduleEvents.emit(ScheduleResult.Failure(e.message ?: ""))
            }
        }
    }

    fun cancelScheduledNodeCleanup() {
        viewModelScope.launch {
            try {
                val workManager = WorkManager.getInstance(appContext)
                val infos = withContext(Dispatchers.IO) { workManager.getWorkInfosForUniqueWork(NodeCleanupWorker.WORK_NAME).get() }
                if (infos.isNullOrEmpty() || infos.all { it.state.isFinished }) {
                    _scheduleEvents.emit(ScheduleResult.NoWork)
                    return@launch
                }
                workManager.cancelUniqueWork(NodeCleanupWorker.WORK_NAME)
                _scheduleEvents.emit(ScheduleResult.Cancelled)
                refreshScheduleStatus()
            } catch (e: Exception) {
                _scheduleEvents.emit(ScheduleResult.Failure(e.message ?: ""))
            }
        }
    }

    fun runNodeCleanupNow(olderThanDays: Int, onlyUnknownNodes: Boolean, olderThanMinutes: Int? = null) {
        viewModelScope.launch {
            try {
                val data =
                    workDataOf(
                        NodeCleanupWorker.KEY_OLDER_THAN_DAYS to olderThanDays,
                        NodeCleanupWorker.KEY_OLDER_THAN_MINUTES to (olderThanMinutes ?: -1),
                        NodeCleanupWorker.KEY_ONLY_UNKNOWN to onlyUnknownNodes,
                    )
                val request =
                    OneTimeWorkRequestBuilder<NodeCleanupWorker>()
                        .setInputData(data)
                        .addTag("${NodeCleanupWorker.TAG_DAYS_PREFIX}$olderThanDays")
                        .apply {
                            if (olderThanMinutes != null && olderThanMinutes > 0) {
                                addTag("${NodeCleanupWorker.TAG_MINUTES_PREFIX}$olderThanMinutes")
                            }
                        }
                        .apply { if (onlyUnknownNodes) addTag(NodeCleanupWorker.TAG_UNKNOWN) }
                        .build()

                WorkManager.getInstance(appContext)
                    .enqueueUniqueWork(
                        "${NodeCleanupWorker.WORK_NAME}_now",
                        ExistingWorkPolicy.REPLACE,
                        request,
                    )
                _runNowEvents.emit(RunNowResult.Success)
                refreshScheduleStatus()
            } catch (e: Exception) {
                _runNowEvents.emit(RunNowResult.Failure(e.message ?: ""))
            }
        }
    }

    fun refreshScheduleStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val workManager = WorkManager.getInstance(appContext)
            val info = workManager.getWorkInfosForUniqueWork(NodeCleanupWorker.WORK_NAME).get().firstOrNull()
            _scheduleStatus.update {
                info?.let { workInfo ->
                    ScheduleStatus(
                        state = workInfo.state,
                        olderThanDays = workInfo.tags.firstOrNull { it.startsWith(NodeCleanupWorker.TAG_DAYS_PREFIX) }
                            ?.removePrefix(NodeCleanupWorker.TAG_DAYS_PREFIX)
                            ?.toIntOrNull(),
                        olderThanMinutes = workInfo.tags.firstOrNull { it.startsWith(NodeCleanupWorker.TAG_MINUTES_PREFIX) }
                            ?.removePrefix(NodeCleanupWorker.TAG_MINUTES_PREFIX)
                            ?.toIntOrNull(),
                        onlyUnknown = workInfo.tags.contains(NodeCleanupWorker.TAG_UNKNOWN),
                        lastRunEpochMillis = workInfo.outputData.getLong(NodeCleanupWorker.KEY_LAST_RUN_EPOCH, -1)
                            .takeIf { it > 0 },
                    )
                }
            }
        }
    }
}

sealed interface ScheduleResult {
    data object Success : ScheduleResult
    data object Cancelled : ScheduleResult
    data object NoWork : ScheduleResult
    data class Failure(val reason: String) : ScheduleResult
}

data class ScheduleStatus(
    val state: WorkInfo.State?,
    val olderThanDays: Int?,
    val olderThanMinutes: Int?,
    val onlyUnknown: Boolean,
    val lastRunEpochMillis: Long?,
)

sealed interface RunNowResult {
    data object Success : RunNowResult
    data class Failure(val reason: String) : RunNowResult
}
