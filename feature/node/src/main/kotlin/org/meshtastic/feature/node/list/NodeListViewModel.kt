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

import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.AdminProtos
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class NodeListViewModel
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val uiPreferencesDataSource: UiPreferencesDataSource,
) : ViewModel() {

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    val onlineNodeCount = nodeRepository.onlineNodeCount.stateInWhileSubscribed(initialValue = 0)

    val totalNodeCount = nodeRepository.totalNodeCount.stateInWhileSubscribed(initialValue = 0)

    val connectionState = serviceRepository.connectionState

    private val _sharedContactRequested: MutableStateFlow<AdminProtos.SharedContact?> = MutableStateFlow(null)
    val sharedContactRequested = _sharedContactRequested.asStateFlow()

    private val nodeSortOption =
        uiPreferencesDataSource.nodeSort.map { NodeSortOption.entries.getOrElse(it) { NodeSortOption.VIA_FAVORITE } }

    private val nodeFilterText = MutableStateFlow("")
    private val includeUnknown = uiPreferencesDataSource.includeUnknown
    private val onlyOnline = uiPreferencesDataSource.onlyOnline
    private val onlyDirect = uiPreferencesDataSource.onlyDirect
    private val showIgnored = uiPreferencesDataSource.showIgnored

    private val nodeFilter: Flow<NodeFilterState> =
        combine(nodeFilterText, includeUnknown, onlyOnline, onlyDirect, showIgnored) {
                filterText,
                includeUnknown,
                onlyOnline,
                onlyDirect,
                showIgnored,
            ->
            NodeFilterState(filterText, includeUnknown, onlyOnline, onlyDirect, showIgnored)
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
                    .map { list -> list.filter { it.isIgnored == filter.showIgnored } }
            }
            .stateInWhileSubscribed(initialValue = emptyList())

    val unfilteredNodeList: StateFlow<List<Node>> =
        nodeRepository.getNodes().stateInWhileSubscribed(initialValue = emptyList())

    fun setNodeFilterText(text: String) {
        nodeFilterText.value = text
    }

    fun toggleIncludeUnknown() {
        uiPreferencesDataSource.setIncludeUnknown(!includeUnknown.value)
    }

    fun toggleOnlyOnline() {
        uiPreferencesDataSource.setOnlyOnline(!onlyOnline.value)
    }

    fun toggleOnlyDirect() {
        uiPreferencesDataSource.setOnlyDirect(!onlyDirect.value)
    }

    fun toggleShowIgnored() {
        uiPreferencesDataSource.setShowIgnored(!showIgnored.value)
    }

    fun setSortOption(sort: NodeSortOption) {
        uiPreferencesDataSource.setNodeSort(sort.ordinal)
    }

    fun setSharedContactRequested(sharedContact: AdminProtos.SharedContact?) {
        _sharedContactRequested.value = sharedContact
    }

    fun favoriteNode(node: Node) = viewModelScope.launch {
        try {
            serviceRepository.onServiceAction(ServiceAction.Favorite(node))
        } catch (ex: RemoteException) {
            Timber.e(ex, "Favorite node error")
        }
    }

    fun ignoreNode(node: Node) = viewModelScope.launch {
        try {
            serviceRepository.onServiceAction(ServiceAction.Ignore(node))
        } catch (ex: RemoteException) {
            Timber.e(ex, "Ignore node error")
        }
    }

    fun removeNode(nodeNum: Int) = viewModelScope.launch(Dispatchers.IO) {
        Timber.i("Removing node '$nodeNum'")
        try {
            val packetId = serviceRepository.meshService?.packetId ?: return@launch
            serviceRepository.meshService?.removeByNodenum(packetId, nodeNum)
            nodeRepository.deleteNode(nodeNum)
        } catch (ex: RemoteException) {
            Timber.e("Remove node error: ${ex.message}")
        }
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
    val onlyOnline: Boolean = false,
    val onlyDirect: Boolean = false,
    val showIgnored: Boolean = false,
)
