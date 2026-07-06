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
package org.meshtastic.feature.node.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.getSystemMeasurementSystem
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.NodeListDensity
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.model.util.DistanceUnit
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.node.detail.NodeManagementActions
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.feature.node.domain.usecase.GetFilteredNodesUseCase
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config

@Suppress("LongParameterList")
@KoinViewModel
class NodeListViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val connectionStateProvider: ConnectionStateProvider,
    private val adminController: AdminController,
    private val radioInterfaceService: RadioInterfaceService,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    val nodeManagementActions: NodeManagementActions,
    private val nodeRequestActions: NodeRequestActions,
    private val getFilteredNodesUseCase: GetFilteredNodesUseCase,
    val nodeFilterPreferences: NodeFilterPreferences,
) : ViewModel() {

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    val onlineNodeCount = nodeRepository.onlineNodeCount.stateInWhileSubscribed(initialValue = 0)

    val totalNodeCount = nodeRepository.totalNodeCount.stateInWhileSubscribed(initialValue = 0)

    val connectionState = connectionStateProvider.connectionState

    val deviceType: StateFlow<DeviceType?> =
        radioInterfaceService.currentDeviceAddressFlow
            .map { address -> address?.let { DeviceType.fromAddress(it) } }
            .stateInWhileSubscribed(initialValue = null)

    val nodeListDensity: StateFlow<NodeListDensity> =
        nodeFilterPreferences.nodeListDensity
            .map { name -> NodeListDensity.fromName(name) }
            .stateInWhileSubscribed(initialValue = NodeListDensity.COMPLETE)

    val shouldShowPower = nodeFilterPreferences.shouldShowPower
    val shouldShowLastHeard = nodeFilterPreferences.shouldShowLastHeard
    val lastHeardIsRelative = nodeFilterPreferences.lastHeardIsRelative
    val shouldShowLocation = nodeFilterPreferences.shouldShowLocation
    val shouldShowHops = nodeFilterPreferences.shouldShowHops
    val shouldShowSignal = nodeFilterPreferences.shouldShowSignal
    val shouldShowChannel = nodeFilterPreferences.shouldShowChannel
    val shouldShowRole = nodeFilterPreferences.shouldShowRole
    val shouldShowTelemetry = nodeFilterPreferences.shouldShowTelemetry

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

    // OS locale rarely changes mid-session; snapshot once instead of per filter/sort emission.
    private val distanceUnits = DistanceUnit.getFromLocale().value
    private val tempInFahrenheit = getSystemMeasurementSystem() == MeasurementSystem.IMPERIAL
    val nodesUiState: StateFlow<NodesUiState> =
        combine(nodeSortOption, nodeFilter) { sort, nodeFilter ->
            NodesUiState(
                sort = sort,
                filter = nodeFilter,
                distanceUnits = distanceUnits,
                tempInFahrenheit = tempInFahrenheit,
            )
        }
            .stateInWhileSubscribed(initialValue = NodesUiState())

    val nodeList: StateFlow<List<Node>> =
        combine(nodeFilter, nodeSortOption, ::Pair)
            .flatMapLatest { (filter, sort) -> getFilteredNodesUseCase.invoke(filter, sort) }
            .stateInWhileSubscribed(initialValue = emptyList())

    val unfilteredNodeList: StateFlow<List<Node>> =
        nodeRepository.getNodes().stateInWhileSubscribed(initialValue = emptyList())

    private val _deviceImageUrls = MutableStateFlow<Map<Int, String>>(emptyMap())

    /** Maps hw_model int value → device image URL from the flasher CDN. */
    val deviceImageUrls: StateFlow<Map<Int, String>> = _deviceImageUrls.asStateFlow()

    init {
        // Resolve device image URLs as nodes arrive
        viewModelScope.launch {
            nodeList.collect { nodes ->
                val newModels = nodes.map { it.user.hw_model.value }.distinct().filter { it !in _deviceImageUrls.value }
                for (hwModel in newModels) {
                    resolveDeviceImageUrl(hwModel)
                }
            }
        }
    }

    private suspend fun resolveDeviceImageUrl(hwModel: Int) {
        val hw = deviceHardwareRepository.getDeviceHardwareByModel(hwModel).getOrNull() ?: return
        val imageFile = hw.images?.getOrNull(1) ?: hw.images?.getOrNull(0) ?: return
        val url = "$FLASHER_DEVICE_IMAGE_BASE_URL$imageFile"
        _deviceImageUrls.value = _deviceImageUrls.value + (hwModel to url)
    }

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
            adminController.setLocalConfig(Config(lora = newLoraConfig))
        }
    }

    fun favoriteNode(node: Node) = nodeManagementActions.requestFavoriteNode(viewModelScope, node)

    fun ignoreNode(node: Node) = nodeManagementActions.requestIgnoreNode(viewModelScope, node)

    fun muteNode(node: Node) = nodeManagementActions.requestMuteNode(viewModelScope, node)

    fun removeNode(node: Node) = nodeManagementActions.requestRemoveNode(viewModelScope, node)

    /** Returns the contact key for navigating to a direct message conversation with this node. */
    fun getDirectMessageRoute(node: Node): String {
        val ourNode = ourNodeInfo.value
        val hasPKC = ourNode?.hasPKC == true && node.hasPKC
        val channel = if (hasPKC) NodeAddress.PKC_CHANNEL_INDEX else node.channel
        return "${channel}${node.user.id}"
    }

    /** Initiates a trace route request to the specified node. */
    fun traceRoute(node: Node) {
        viewModelScope.launch { nodeRequestActions.requestTraceroute(node.num, node.user.long_name) }
    }

    companion object {
        private const val KEY_FILTER_TEXT = "filter_text"
        private const val FLASHER_DEVICE_IMAGE_BASE_URL = "https://flasher.meshtastic.org/img/devices/"
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
