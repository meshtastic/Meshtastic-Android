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
package org.meshtastic.feature.node.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.node.detail.NodeManagementActions
import org.meshtastic.feature.node.domain.usecase.GetFilteredNodesUseCase
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config

@Suppress("LongParameterList")
@KoinViewModel
class NodeListViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val radioController: RadioController,
    private val radioInterfaceService: RadioInterfaceService,
    val nodeManagementActions: NodeManagementActions,
    private val getFilteredNodesUseCase: GetFilteredNodesUseCase,
    val nodeFilterPreferences: NodeFilterPreferences,
) : ViewModel() {

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    val onlineNodeCount = nodeRepository.onlineNodeCount.stateInWhileSubscribed(initialValue = 0)

    val totalNodeCount = nodeRepository.totalNodeCount.stateInWhileSubscribed(initialValue = 0)

    val connectionState = serviceRepository.connectionState

    val deviceType: StateFlow<DeviceType?> =
        radioInterfaceService.currentDeviceAddressFlow
            .map { address -> address?.let { DeviceType.fromAddress(it) } }
            .stateInWhileSubscribed(initialValue = null)

    private val nodeSortOption = nodeFilterPreferences.nodeSortOption

    private val _nodeFilterText = savedStateHandle.getStateFlow(KEY_FILTER_TEXT, "")

    private val filterToggles =
        combine(
            nodeFilterPreferences.includeUnknown,
            nodeFilterPreferences.excludeInfrastructure,
            nodeFilterPreferences.onlyOnline,
            nodeFilterPreferences.onlyDirect,
            nodeFilterPreferences.showIgnored,
        ) { includeUnknown, excludeInfrastructure, onlyOnline, onlyDirect, showIgnored ->
            NodeFilterToggles(
                includeUnknown = includeUnknown,
                excludeInfrastructure = excludeInfrastructure,
                onlyOnline = onlyOnline,
                onlyDirect = onlyDirect,
                showIgnored = showIgnored,
            )
        }

    private val nodeFilter: Flow<NodeFilterState> =
        combine(_nodeFilterText, filterToggles, nodeFilterPreferences.excludeMqtt) {
                filterText,
                filterToggles,
                excludeMqtt,
            ->
            NodeFilterState(
                filterText = filterText,
                includeUnknown = filterToggles.includeUnknown,
                excludeInfrastructure = filterToggles.excludeInfrastructure,
                onlyOnline = filterToggles.onlyOnline,
                onlyDirect = filterToggles.onlyDirect,
                showIgnored = filterToggles.showIgnored,
                excludeMqtt = excludeMqtt,
            )
        }
    val nodesUiState: StateFlow<NodesUiState> =
        combine(nodeSortOption, nodeFilter, radioConfigRepository.deviceProfileFlow) { sort, nodeFilter, profile ->
            NodesUiState(
                sort = sort,
                filter = nodeFilter,
                distanceUnits = profile.config?.display?.units?.value ?: 0,
                tempInFahrenheit = profile.module_config?.telemetry?.environment_display_fahrenheit ?: false,
            )
        }
            .stateInWhileSubscribed(initialValue = NodesUiState())

    val nodeList: StateFlow<List<Node>> =
        combine(nodeFilter, nodeSortOption, ::Pair)
            .flatMapLatest { (filter, sort) -> getFilteredNodesUseCase.invoke(filter, sort) }
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

    fun setChannels(channelSet: ChannelSet) = viewModelScope.launch {
        radioConfigRepository.replaceAllSettings(channelSet.settings)
        val newLoraConfig = channelSet.lora_config
        if (newLoraConfig != null) {
            radioController.setLocalConfig(Config(lora = newLoraConfig))
        }
    }

    fun favoriteNode(node: Node) = nodeManagementActions.requestFavoriteNode(viewModelScope, node)

    fun ignoreNode(node: Node) = nodeManagementActions.requestIgnoreNode(viewModelScope, node)

    fun muteNode(node: Node) = nodeManagementActions.requestMuteNode(viewModelScope, node)

    fun removeNode(node: Node) = nodeManagementActions.requestRemoveNode(viewModelScope, node)

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
    val excludeMqtt: Boolean = false,
) {
    /** True if any user-applied filter is narrowing the visible node set. */
    val isActive: Boolean
        get() = filterText.isNotEmpty() || excludeInfrastructure || onlyOnline || onlyDirect || excludeMqtt
}

data class NodeFilterToggles(
    val includeUnknown: Boolean = false,
    val excludeInfrastructure: Boolean = false,
    val onlyOnline: Boolean = false,
    val onlyDirect: Boolean = false,
    val showIgnored: Boolean = false,
)
