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

package org.meshtastic.feature.node.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.NodeSortOption
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.node.model.isEffectivelyUnmessageable
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.ConfigProtos
import javax.inject.Inject

@HiltViewModel
class NodeListViewModel
@Inject
constructor(
    private val savedStateHandle: SavedStateHandle,
    private val nodeRepository: NodeRepository,
    radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    val nodeActions: NodeActions,
    val nodeFilterPreferences: NodeFilterPreferences,
) : ViewModel() {

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    val onlineNodeCount = nodeRepository.onlineNodeCount.stateInWhileSubscribed(initialValue = 0)

    val totalNodeCount = nodeRepository.totalNodeCount.stateInWhileSubscribed(initialValue = 0)

    val connectionState = serviceRepository.connectionState

    private val _sharedContactRequested: MutableStateFlow<AdminProtos.SharedContact?> = MutableStateFlow(null)
    val sharedContactRequested = _sharedContactRequested.asStateFlow()

    private val nodeSortOption = nodeFilterPreferences.nodeSortOption

    private val _nodeFilterText = savedStateHandle.getStateFlow(KEY_FILTER_TEXT, "")
    private val includeUnknown = nodeFilterPreferences.includeUnknown
    private val excludeInfrastructure = nodeFilterPreferences.excludeInfrastructure
    private val onlyOnline = nodeFilterPreferences.onlyOnline
    private val onlyDirect = nodeFilterPreferences.onlyDirect
    private val showIgnored = nodeFilterPreferences.showIgnored

    private val nodeFilter: Flow<NodeFilterState> =
        combine(_nodeFilterText, includeUnknown, excludeInfrastructure, onlyOnline, onlyDirect, showIgnored) { values ->
            NodeFilterState(
                filterText = values[0] as String,
                includeUnknown = values[1] as Boolean,
                excludeInfrastructure = values[2] as Boolean,
                onlyOnline = values[3] as Boolean,
                onlyDirect = values[4] as Boolean,
                showIgnored = values[5] as Boolean,
            )
        }
    val nodesUiState: StateFlow<NodesUiState> =
        combine(nodeSortOption, nodeFilter, radioConfigRepository.deviceProfileFlow) { sort, nodeFilter, profile ->
            NodesUiState(
                sort = sort,
                filter = nodeFilter,
                distanceUnits = profile.config.display.units.number,
                tempInFahrenheit = profile.moduleConfig.telemetry.environmentDisplayFahrenheit,
            )
        }
            .stateInWhileSubscribed(initialValue = NodesUiState())

    val nodeList: StateFlow<List<Node>> =
        combine(nodeFilter, nodeSortOption, ::Pair)
            .flatMapLatest { (filter, sort) ->
                nodeRepository
                    .getNodes(
                        sort = sort,
                        filter = filter.filterText,
                        includeUnknown = filter.includeUnknown,
                        onlyOnline = filter.onlyOnline,
                        onlyDirect = filter.onlyDirect,
                    )
                    .map { list ->
                        list
                            .filter { filter.showIgnored || !it.isIgnored }
                            .filter { node ->
                                if (filter.excludeInfrastructure) {
                                    val role = node.user.role
                                    val infrastructureRoles =
                                        listOf(
                                            ConfigProtos.Config.DeviceConfig.Role.ROUTER,
                                            ConfigProtos.Config.DeviceConfig.Role.REPEATER,
                                            ConfigProtos.Config.DeviceConfig.Role.ROUTER_LATE,
                                            ConfigProtos.Config.DeviceConfig.Role.CLIENT_BASE,
                                        )
                                    role !in infrastructureRoles && !node.isEffectivelyUnmessageable
                                } else {
                                    true
                                }
                            }
                    }
            }
            .stateInWhileSubscribed(initialValue = emptyList())

    val unfilteredNodeList: StateFlow<List<Node>> =
        nodeRepository.getNodes().stateInWhileSubscribed(initialValue = emptyList())

    var nodeFilterText: String
        get() = _nodeFilterText.value
        set(value) {
            savedStateHandle[KEY_FILTER_TEXT] = value
        }

    fun setSortOption(sort: NodeSortOption) {
        nodeFilterPreferences.setNodeSort(sort)
    }

    fun setSharedContactRequested(sharedContact: AdminProtos.SharedContact?) {
        _sharedContactRequested.value = sharedContact
    }

    fun favoriteNode(node: Node) = viewModelScope.launch { nodeActions.favoriteNode(node) }

    fun ignoreNode(node: Node) = viewModelScope.launch { nodeActions.ignoreNode(node) }

    fun removeNode(nodeNum: Int) = viewModelScope.launch { nodeActions.removeNode(nodeNum) }

    companion object {
        private const val KEY_FILTER_TEXT = "filter_text"
    }
}

data class NodesUiState(
    val sort: NodeSortOption = NodeSortOption.LAST_HEARD,
    val filter: NodeFilterState = NodeFilterState(),
    val distanceUnits: Int = 0,
    val tempInFahrenheit: Boolean = false,
)

data class NodeFilterState(
    val filterText: String = "",
    val includeUnknown: Boolean = false,
    val excludeInfrastructure: Boolean = false,
    val onlyOnline: Boolean = false,
    val onlyDirect: Boolean = false,
    val showIgnored: Boolean = false,
)
