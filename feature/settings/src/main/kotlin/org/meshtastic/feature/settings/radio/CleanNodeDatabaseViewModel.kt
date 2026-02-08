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
package org.meshtastic.feature.settings.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.are_you_sure
import org.meshtastic.core.strings.clean_node_database_confirmation
import org.meshtastic.core.strings.clean_now
import org.meshtastic.core.ui.util.AlertManager
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

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
    private val alertManager: AlertManager,
) : ViewModel() {
    private val _olderThanDays = MutableStateFlow(30f)
    val olderThanDays = _olderThanDays.asStateFlow()

    private val _onlyUnknownNodes = MutableStateFlow(false)
    val onlyUnknownNodes = _onlyUnknownNodes.asStateFlow()

    private val _nodesToDelete = MutableStateFlow<List<NodeEntity>>(emptyList())
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
            val onlyUnknownEnabled = _onlyUnknownNodes.value
            val currentTimeSeconds = System.currentTimeMillis().milliseconds.inWholeSeconds
            val sevenDaysAgoSeconds = currentTimeSeconds - 7.days.inWholeSeconds
            val olderThanTimestamp = currentTimeSeconds - _olderThanDays.value.toInt().days.inWholeSeconds

            val initialNodesToConsider =
                if (onlyUnknownEnabled) {
                    // Both "older than X days" and "only unknown nodes" filters apply
                    val olderNodes = nodeRepository.getNodesOlderThan(olderThanTimestamp.toInt())
                    val unknownNodes = nodeRepository.getUnknownNodes()
                    olderNodes.filter { itNode -> unknownNodes.any { unknownNode -> itNode.num == unknownNode.num } }
                } else {
                    // Only "older than X days" filter applies
                    nodeRepository.getNodesOlderThan(olderThanTimestamp.toInt())
                }

            _nodesToDelete.value =
                initialNodesToConsider.filterNot { node ->
                    // Exclude nodes with PKI heard in the last 7 days
                    (node.hasPKC && node.lastHeard >= sevenDaysAgoSeconds) ||
                        // Exclude ignored or favorite nodes
                        node.isIgnored ||
                        node.isFavorite
                }
        }
    }

    fun requestCleanNodes() {
        viewModelScope.launch {
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
}
