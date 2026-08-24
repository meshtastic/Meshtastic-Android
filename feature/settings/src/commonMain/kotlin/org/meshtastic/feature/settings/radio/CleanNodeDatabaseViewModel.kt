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
package org.meshtastic.feature.settings.radio

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.domain.usecase.settings.CleanNodeDatabaseUseCase
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.are_you_sure
import org.meshtastic.core.resources.clean_node_database_confirmation
import org.meshtastic.core.resources.clean_now
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.core.ui.viewmodel.safeLaunch

private const val MIN_DAYS_THRESHOLD = 7f

/**
 * ViewModel for [CleanNodeDatabaseScreen]. Manages the state and logic for cleaning the node database based on
 * specified criteria. The "older than X days" filter is always active.
 */
@KoinViewModel
class CleanNodeDatabaseViewModel(
    private val cleanNodeDatabaseUseCase: CleanNodeDatabaseUseCase,
    private val alertManager: AlertManager,
) : ViewModel() {
    private val _olderThanDays = MutableStateFlow(30f)
    val olderThanDays = _olderThanDays.asStateFlow()

    private val _onlyUnknownNodes = MutableStateFlow(false)
    val onlyUnknownNodes = _onlyUnknownNodes.asStateFlow()

    private val _nodesToDelete = MutableStateFlow<List<Node>>(emptyList())
    val nodesToDelete = _nodesToDelete.asStateFlow()

    fun onOlderThanDaysChanged(value: Float) {
        _olderThanDays.value = value
    }

    fun onOnlyUnknownNodesChanged(value: Boolean) {
        _onlyUnknownNodes.value = value
        if (!value && _olderThanDays.value < MIN_DAYS_THRESHOLD) {
            _olderThanDays.value = MIN_DAYS_THRESHOLD
        }
    }

    /** Updates the list of nodes to be deleted based on the current filter criteria. */
    fun getNodesToDelete() {
        safeLaunch(tag = "getNodesToDelete") {
            _nodesToDelete.value =
                cleanNodeDatabaseUseCase.getNodesToClean(
                    olderThanDays = _olderThanDays.value,
                    onlyUnknownNodes = _onlyUnknownNodes.value,
                    currentTimeSeconds = nowSeconds,
                )
        }
    }

    fun requestCleanNodes() {
        safeLaunch(tag = "requestCleanNodes") {
            val count = _nodesToDelete.value.size
            val message = getString(Res.string.clean_node_database_confirmation, count)
            alertManager.showAlert(
                titleRes = Res.string.are_you_sure,
                message = message,
                confirmTextRes = Res.string.clean_now,
                onConfirm = { cleanNodes() },
            )
        }
    }

    /**
     * Deletes the nodes currently queued in [_nodesToDelete] from the database and instructs the mesh service to remove
     * them.
     */
    fun cleanNodes() {
        safeLaunch(tag = "cleanNodes") {
            val nodeNums = _nodesToDelete.value.map { it.num }
            cleanNodeDatabaseUseCase.cleanNodes(nodeNums)
            // Clear the list after deletion or if it was empty
            _nodesToDelete.value = emptyList()
        }
    }
}
