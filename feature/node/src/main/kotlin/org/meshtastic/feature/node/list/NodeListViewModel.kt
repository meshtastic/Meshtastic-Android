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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.NodeSortOption
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.feature.node.model.isEffectivelyUnmessageable
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.deviceProfile
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

    private val _sharedContactRequested: MutableStateFlow<AdminProtos.SharedContact?> = MutableStateFlow(null)
    val sharedContactRequested = _sharedContactRequested.asStateFlow()

    private val filterText = mutableStateOf(TextFieldState())

    private val moleculeScope = CoroutineScope(viewModelScope.coroutineContext + AndroidUiDispatcher.Main)
    val uiState: StateFlow<NodesUiState> by
        lazy(LazyThreadSafetyMode.NONE) {
            moleculeScope.launchMolecule(mode = RecompositionMode.ContextClock) {
                val ourNodeInfo by nodeRepository.ourNodeInfo.collectAsState()
                val onlineNodeCount by nodeRepository.onlineNodeCount.collectAsState(0)
                val totalNodeCount by nodeRepository.totalNodeCount.collectAsState(0)
                val connectionState by serviceRepository.connectionState.collectAsState()
                val includeUnknown by nodeFilterPreferences.includeUnknown.collectAsState()
                val excludeInfrastructure by nodeFilterPreferences.excludeInfrastructure.collectAsState()
                val onlyOnline by nodeFilterPreferences.onlyOnline.collectAsState()
                val onlyDirect by nodeFilterPreferences.onlyDirect.collectAsState()
                val showIgnored by nodeFilterPreferences.showIgnored.collectAsState()
                val unfilteredNodes by nodeRepository.getNodes().collectAsState(emptyList())

                val filter =
                    NodeFilterState(
                        filterText = filterText.value,
                        includeUnknown = includeUnknown,
                        excludeInfrastructure = excludeInfrastructure,
                        onlyOnline = onlyOnline,
                        onlyDirect = onlyDirect,
                        showIgnored = showIgnored,
                    )
                val sort by
                nodeFilterPreferences.nodeSortOption
                        .collectAsState(NodeSortOption.VIA_FAVORITE)
                val profile by radioConfigRepository.deviceProfileFlow.collectAsState(deviceProfile {})

                val filteredNodes by
                    nodeRepository
                        .getNodes(
                            sort = sort,
                            filter = filter.filterText.text.toString(),
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
                        .collectAsState(emptyList())

                NodesUiState(
                    ourNodeInfo = ourNodeInfo,
                    onlineNodeCount = onlineNodeCount,
                    totalNodeCount = totalNodeCount,
                    connectionState = connectionState,
                    sort = sort,
                    filter = filter,
                    nodes = filteredNodes,
                    distanceUnits = profile.config.display.units.number,
                    tempInFahrenheit = profile.moduleConfig.telemetry.environmentDisplayFahrenheit,
                    ignoredNodeCount = unfilteredNodes.count { it.isIgnored },
                )
            }
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
    val ourNodeInfo: Node? = null,
    val onlineNodeCount: Int = 0,
    val totalNodeCount: Int = 0,
    val connectionState: ConnectionState = ConnectionState.Connected,
    val sort: NodeSortOption = NodeSortOption.LAST_HEARD,
    val filter: NodeFilterState = NodeFilterState(),
    val nodes: List<Node> = emptyList(),
    val distanceUnits: Int = 0,
    val tempInFahrenheit: Boolean = false,
    val ignoredNodeCount: Int = 0,
)

data class NodeFilterState(
    val filterText: TextFieldState = TextFieldState(),
    val includeUnknown: Boolean = false,
    val excludeInfrastructure: Boolean = false,
    val onlyOnline: Boolean = false,
    val onlyDirect: Boolean = false,
    val showIgnored: Boolean = false,
)
