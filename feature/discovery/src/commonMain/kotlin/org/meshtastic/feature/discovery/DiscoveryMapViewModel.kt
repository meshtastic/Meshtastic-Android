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
package org.meshtastic.feature.discovery

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

@KoinViewModel
class DiscoveryMapViewModel(@InjectedParam private val sessionId: Long, private val discoveryDao: DiscoveryDao) :
    ViewModel() {

    val session: StateFlow<DiscoverySessionEntity?> =
        discoveryDao.getSessionFlow(sessionId).stateInWhileSubscribed(initialValue = null)

    /** All preset results for this session. Used for filter chip UI. */
    private val presetResultsState = MutableStateFlow<List<DiscoveryPresetResultEntity>>(emptyList())
    val presetResults: StateFlow<List<DiscoveryPresetResultEntity>> = presetResultsState.asStateFlow()

    /** Nodes keyed by preset result ID. */
    private val nodesByPresetState = MutableStateFlow<Map<Long, List<DiscoveredNodeEntity>>>(emptyMap())

    /**
     * Currently selected preset filter. `null` means "All presets" (deduplicated). Set to a preset result ID to show
     * only nodes discovered under that preset.
     */
    private val selectedPresetFilterState = MutableStateFlow<Long?>(null)
    val selectedPresetFilter: StateFlow<Long?> = selectedPresetFilterState.asStateFlow()

    /** Whether the topology overlay (neighbor connections) is visible. */
    private val showTopologyOverlayState = MutableStateFlow(false)
    val showTopologyOverlay: StateFlow<Boolean> = showTopologyOverlayState.asStateFlow()

    /** Filtered and deduplicated nodes based on the current preset filter. */
    val filteredNodes: StateFlow<List<DiscoveredNodeEntity>> =
        combine(nodesByPresetState, selectedPresetFilterState) { nodesByPreset, filter ->
            val raw =
                if (filter == null) {
                    nodesByPreset.values.flatten()
                } else {
                    nodesByPreset[filter].orEmpty()
                }
            // Deduplicate by nodeNum — keep the entry with strongest signal
            raw.groupBy { it.nodeNum }.values.map { dupes -> dupes.maxByOrNull { it.snr } ?: dupes.first() }
        }
            .stateInWhileSubscribed(initialValue = emptyList())

    /** Map statistics: how many nodes have valid GPS coordinates vs total. */
    val mapStats: StateFlow<DiscoveryMapStats> =
        combine(filteredNodes, nodesByPresetState) { filtered, _ ->
            val mappedCount = filtered.count { hasValidCoordinates(it.latitude, it.longitude) }
            DiscoveryMapStats(
                totalNodes = filtered.size,
                mappedNodes = mappedCount,
                unmappedNodes = filtered.size - mappedCount,
            )
        }
            .stateInWhileSubscribed(initialValue = DiscoveryMapStats())

    // Keep backward-compatible allNodes as alias to filteredNodes
    val allNodes: StateFlow<List<DiscoveredNodeEntity>> = filteredNodes

    init {
        loadAllNodes()
    }

    fun selectPresetFilter(presetResultId: Long?) {
        selectedPresetFilterState.value = presetResultId
    }

    fun toggleTopologyOverlay() {
        showTopologyOverlayState.value = !showTopologyOverlayState.value
    }

    private fun loadAllNodes() {
        safeLaunch(tag = "loadAllNodes") {
            val results = discoveryDao.getPresetResults(sessionId)
            presetResultsState.value = results
            val nodesMap = mutableMapOf<Long, List<DiscoveredNodeEntity>>()
            for (result in results) {
                nodesMap[result.id] = discoveryDao.getDiscoveredNodes(result.id)
            }
            nodesByPresetState.value = nodesMap
        }
    }

    private fun hasValidCoordinates(lat: Double?, lon: Double?): Boolean =
        lat != null && lon != null && lat != 0.0 && lon != 0.0
}

/** Presentation model for map node statistics. */
data class DiscoveryMapStats(val totalNodes: Int = 0, val mappedNodes: Int = 0, val unmappedNodes: Int = 0)
