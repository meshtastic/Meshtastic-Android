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
package org.meshtastic.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.any
import org.meshtastic.core.resources.eight_hours
import org.meshtastic.core.resources.one_day
import org.meshtastic.core.resources.one_hour
import org.meshtastic.core.resources.two_days
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.map.model.TracerouteOverlay
import org.meshtastic.proto.Position
import org.meshtastic.proto.Waypoint

@Suppress("TooManyFunctions")
open class BaseMapViewModel(
    protected val mapPrefs: MapPrefs,
    protected val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val radioController: RadioController,
) : ViewModel() {

    val myNodeInfo = nodeRepository.myNodeInfo

    val ourNodeInfo = nodeRepository.ourNodeInfo

    val myNodeNum
        get() = myNodeInfo.value?.myNodeNum

    val myId = nodeRepository.myId

    val isConnected =
        radioController.connectionState
            .map { it is org.meshtastic.core.model.ConnectionState.Connected }
            .stateInWhileSubscribed(initialValue = false)

    val nodes: StateFlow<List<Node>> =
        nodeRepository
            .getNodes()
            .map { nodes -> nodes.filterNot { node -> node.isIgnored } }
            .stateInWhileSubscribed(initialValue = emptyList())

    val nodesWithPosition: StateFlow<List<Node>> =
        nodes
            .map { nodes -> nodes.filter { node -> node.validPosition != null } }
            .stateInWhileSubscribed(initialValue = emptyList())

    val waypoints: StateFlow<Map<Int, DataPacket>> =
        packetRepository
            .getWaypoints()
            .mapLatest { list ->
                list
                    .filter { it.waypoint != null }
                    .associateBy { packet -> packet.waypoint!!.id }
                    .filterValues {
                        val expire = it.waypoint?.expire ?: 0
                        expire == 0 || expire.toLong() > nowSeconds
                    }
            }
            .stateInWhileSubscribed(initialValue = emptyMap())

    private val showOnlyFavorites = MutableStateFlow(mapPrefs.showOnlyFavorites.value)
    val showOnlyFavoritesOnMap = showOnlyFavorites

    fun toggleOnlyFavorites() {
        val newValue = !showOnlyFavorites.value
        showOnlyFavorites.value = newValue
        mapPrefs.setShowOnlyFavorites(newValue)
    }

    private val showWaypoints = MutableStateFlow(mapPrefs.showWaypointsOnMap.value)
    val showWaypointsOnMap = showWaypoints

    fun toggleShowWaypointsOnMap() {
        val newValue = !showWaypoints.value
        showWaypoints.value = newValue
        mapPrefs.setShowWaypointsOnMap(newValue)
    }

    private val showPrecisionCircle = MutableStateFlow(mapPrefs.showPrecisionCircleOnMap.value)
    val showPrecisionCircleOnMap = showPrecisionCircle

    fun toggleShowPrecisionCircleOnMap() {
        val newValue = !showPrecisionCircle.value
        showPrecisionCircle.value = newValue
        mapPrefs.setShowPrecisionCircleOnMap(newValue)
    }

    private val lastHeardFilterValue = MutableStateFlow(LastHeardFilter.fromSeconds(mapPrefs.lastHeardFilter.value))
    val lastHeardFilter = lastHeardFilterValue

    fun setLastHeardFilter(filter: LastHeardFilter) {
        lastHeardFilterValue.value = filter
        mapPrefs.setLastHeardFilter(filter.seconds)
    }

    private val lastHeardTrackFilterValue =
        MutableStateFlow(LastHeardFilter.fromSeconds(mapPrefs.lastHeardTrackFilter.value))
    val lastHeardTrackFilter = lastHeardTrackFilterValue

    fun setLastHeardTrackFilter(filter: LastHeardFilter) {
        lastHeardTrackFilterValue.value = filter
        mapPrefs.setLastHeardTrackFilter(filter.seconds)
    }

    open fun getUser(userId: String?) =
        nodeRepository.getUser(userId ?: org.meshtastic.core.model.DataPacket.ID_BROADCAST)

    fun getNodeOrFallback(nodeNum: Int): Node = nodeRepository.nodeDBbyNum.value[nodeNum] ?: Node(num = nodeNum)

    fun deleteWaypoint(id: Int) = viewModelScope.launch(ioDispatcher) { packetRepository.deleteWaypoint(id) }

    fun sendWaypoint(wpt: Waypoint, contactKey: String = "0${DataPacket.ID_BROADCAST}") {
        // contactKey: unique contact key filter (channel)+(nodeId)
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        val p = DataPacket(dest, channel ?: 0, wpt)
        if (wpt.id != 0) sendDataPacket(p)
    }

    private fun sendDataPacket(p: DataPacket) {
        viewModelScope.launch(ioDispatcher) { radioController.sendMessage(p) }
    }

    fun generatePacketId(): Int = radioController.getPacketId()

    data class MapFilterState(
        val onlyFavorites: Boolean,
        val showWaypoints: Boolean,
        val showPrecisionCircle: Boolean,
        val lastHeardFilter: LastHeardFilter,
        val lastHeardTrackFilter: LastHeardFilter,
    )

    val mapFilterStateFlow: StateFlow<MapFilterState> =
        combine(
            showOnlyFavorites,
            showWaypointsOnMap,
            showPrecisionCircleOnMap,
            lastHeardFilter,
            lastHeardTrackFilter,
        ) { favoritesOnly, showWaypoints, showPrecisionCircle, lastHeardFilter, lastHeardTrackFilter ->
            MapFilterState(favoritesOnly, showWaypoints, showPrecisionCircle, lastHeardFilter, lastHeardTrackFilter)
        }
            .stateInWhileSubscribed(
                initialValue =
                MapFilterState(
                    showOnlyFavorites.value,
                    showWaypointsOnMap.value,
                    showPrecisionCircleOnMap.value,
                    lastHeardFilter.value,
                    lastHeardTrackFilter.value,
                ),
            )
}

data class TracerouteNodeSelection(
    val overlayNodeNums: Set<Int>,
    val nodesForMarkers: List<Node>,
    val nodeLookup: Map<Int, Node>,
)

fun BaseMapViewModel.tracerouteNodeSelection(
    tracerouteOverlay: TracerouteOverlay?,
    tracerouteNodePositions: Map<Int, Position>,
    nodes: List<Node>,
): TracerouteNodeSelection {
    val overlayNodeNums = tracerouteOverlay?.relatedNodeNums ?: emptySet()
    val tracerouteSnapshotNodes =
        if (tracerouteOverlay == null || tracerouteNodePositions.isEmpty()) {
            emptyList()
        } else {
            tracerouteNodePositions.map { (nodeNum, position) -> getNodeOrFallback(nodeNum).copy(position = position) }
        }

    val nodesForMarkers =
        if (tracerouteOverlay != null) {
            if (tracerouteSnapshotNodes.isNotEmpty()) {
                tracerouteSnapshotNodes.filter { overlayNodeNums.contains(it.num) }
            } else {
                nodes.filter { overlayNodeNums.contains(it.num) }
            }
        } else {
            nodes
        }

    val nodesForLookup =
        if (tracerouteSnapshotNodes.isNotEmpty()) {
            tracerouteSnapshotNodes
        } else {
            nodes.filter { it.validPosition != null }
        }

    return TracerouteNodeSelection(
        overlayNodeNums = overlayNodeNums,
        nodesForMarkers = nodesForMarkers,
        nodeLookup = nodesForLookup.associateBy { it.num },
    )
}

@Suppress("MagicNumber")
enum class LastHeardFilter(val label: StringResource, val seconds: Long) {
    Any(Res.string.any, 0L),
    OneHour(Res.string.one_hour, 3600L),
    EightHours(Res.string.eight_hours, 28800L),
    OneDay(Res.string.one_day, 86400L),
    TwoDays(Res.string.two_days, 172800L),
    ;

    companion object {
        fun fromSeconds(seconds: Long): LastHeardFilter = entries.find { it.seconds == seconds } ?: Any
    }
}
