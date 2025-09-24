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

package com.geeksville.mesh.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.prefs.map.MapPrefs

@Suppress("TooManyFunctions")
abstract class BaseMapViewModel(
    protected val mapPrefs: MapPrefs,
    nodeRepository: NodeRepository,
    packetRepository: PacketRepository,
    radioConfigRepository: RadioConfigRepository,
) : ViewModel() {

    val nodes: StateFlow<List<Node>> =
        nodeRepository
            .getNodes()
            .map { nodes -> nodes.filterNot { node -> node.isIgnored } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val waypoints: StateFlow<Map<Int, Packet>> =
        packetRepository
            .getWaypoints()
            .mapLatest { list ->
                list
                    .associateBy { packet -> packet.data.waypoint!!.id }
                    .filterValues {
                        it.data.waypoint!!.expire == 0 || it.data.waypoint!!.expire > System.currentTimeMillis() / 1000
                    }
            }
            .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyMap())

    private val showOnlyFavorites = MutableStateFlow(mapPrefs.showOnlyFavorites)

    private val showWaypointsOnMap = MutableStateFlow(mapPrefs.showWaypointsOnMap)

    private val showPrecisionCircleOnMap = MutableStateFlow(mapPrefs.showPrecisionCircleOnMap)

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    val isConnected =
        radioConfigRepository.connectionState
            .map { it.isConnected() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleOnlyFavorites() {
        val current = showOnlyFavorites.value
        mapPrefs.showOnlyFavorites = !current
        showOnlyFavorites.value = !current
    }

    fun toggleShowWaypointsOnMap() {
        val current = showWaypointsOnMap.value
        mapPrefs.showWaypointsOnMap = !current
        showWaypointsOnMap.value = !current
    }

    fun toggleShowPrecisionCircleOnMap() {
        val current = showPrecisionCircleOnMap.value
        mapPrefs.showPrecisionCircleOnMap = !current
        showPrecisionCircleOnMap.value = !current
    }

    data class MapFilterState(val onlyFavorites: Boolean, val showWaypoints: Boolean, val showPrecisionCircle: Boolean)

    val mapFilterStateFlow: StateFlow<MapFilterState> =
        combine(showOnlyFavorites, showWaypointsOnMap, showPrecisionCircleOnMap) {
                favoritesOnly,
                showWaypoints,
                showPrecisionCircle,
            ->
            MapFilterState(favoritesOnly, showWaypoints, showPrecisionCircle)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                MapFilterState(showOnlyFavorites.value, showWaypointsOnMap.value, showPrecisionCircleOnMap.value),
            )
}
