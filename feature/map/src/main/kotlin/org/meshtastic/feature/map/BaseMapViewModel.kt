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

import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.prefs.map.MapPrefs
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.any
import org.meshtastic.core.strings.eight_hours
import org.meshtastic.core.strings.one_day
import org.meshtastic.core.strings.one_hour
import org.meshtastic.core.strings.two_days
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.map.model.TracerouteOverlay
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import org.meshtastic.proto.Waypoint
import java.util.concurrent.TimeUnit

@Suppress("MagicNumber")
sealed class LastHeardFilter(val seconds: Long, val label: StringResource) {
    data object Any : LastHeardFilter(0L, Res.string.any)

    data object OneHour : LastHeardFilter(TimeUnit.HOURS.toSeconds(1), Res.string.one_hour)

    data object EightHours : LastHeardFilter(TimeUnit.HOURS.toSeconds(8), Res.string.eight_hours)

    data object OneDay : LastHeardFilter(TimeUnit.DAYS.toSeconds(1), Res.string.one_day)

    data object TwoDays : LastHeardFilter(TimeUnit.DAYS.toSeconds(2), Res.string.two_days)

    companion object {
        fun fromSeconds(seconds: Long): LastHeardFilter = entries.find { it.seconds == seconds } ?: Any

        val entries = listOf(Any, OneHour, EightHours, OneDay, TwoDays)
    }
}

@Suppress("TooManyFunctions")
abstract class BaseMapViewModel(
    protected val mapPrefs: MapPrefs,
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val serviceRepository: ServiceRepository,
) : ViewModel() {

    val myNodeInfo = nodeRepository.myNodeInfo

    val myNodeNum
        get() = myNodeInfo.value?.myNodeNum

    val myId = nodeRepository.myId

    val nodes: StateFlow<List<Node>> =
        nodeRepository
            .getNodes()
            .map { nodes -> nodes.filterNot { node -> node.isIgnored } }
            .stateInWhileSubscribed(initialValue = emptyList())

    val waypoints: StateFlow<Map<Int, Packet>> =
        packetRepository
            .getWaypoints()
            .mapLatest { list ->
                list
                    .associateBy { packet -> packet.data.waypoint!!.id }
                    .filterValues {
                        val expire = it.data.waypoint!!.expire ?: 0
                        expire == 0 || expire > System.currentTimeMillis() / 1000
                    }
            }
            .stateInWhileSubscribed(initialValue = emptyMap())

    private val showOnlyFavorites = MutableStateFlow(mapPrefs.showOnlyFavorites)

    private val showWaypointsOnMap = MutableStateFlow(mapPrefs.showWaypointsOnMap)

    private val showPrecisionCircleOnMap = MutableStateFlow(mapPrefs.showPrecisionCircleOnMap)

    private val lastHeardFilter = MutableStateFlow(LastHeardFilter.fromSeconds(mapPrefs.lastHeardFilter))

    private val lastHeardTrackFilter = MutableStateFlow(LastHeardFilter.fromSeconds(mapPrefs.lastHeardTrackFilter))

    fun setLastHeardFilter(filter: LastHeardFilter) {
        mapPrefs.lastHeardFilter = filter.seconds
        lastHeardFilter.value = filter
    }

    fun setLastHeardTrackFilter(filter: LastHeardFilter) {
        mapPrefs.lastHeardTrackFilter = filter.seconds
        lastHeardTrackFilter.value = filter
    }

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    fun getNodeByNum(nodeNum: Int): Node? = nodeRepository.nodeDBbyNum.value[nodeNum]

    open fun getUser(userId: String?): User = nodeRepository.getUser(userId ?: DataPacket.ID_BROADCAST)

    fun getUser(nodeNum: Int): User = nodeRepository.getUser(nodeNum)

    fun getNodeOrFallback(nodeNum: Int): Node = getNodeByNum(nodeNum) ?: Node(num = nodeNum, user = getUser(nodeNum))

    val isConnected =
        serviceRepository.connectionState.map { it.isConnected() }.stateInWhileSubscribed(initialValue = false)

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

    fun generatePacketId(): Int? {
        return try {
            serviceRepository.meshService?.packetId
        } catch (ex: RemoteException) {
            Logger.e { "RemoteException: ${ex.message}" }
            return null
        }
    }

    fun deleteWaypoint(id: Int) = viewModelScope.launch(Dispatchers.IO) { packetRepository.deleteWaypoint(id) }

    fun sendWaypoint(wpt: Waypoint, contactKey: String = "0${DataPacket.ID_BROADCAST}") {
        // contactKey: unique contact key filter (channel)+(nodeId)
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        val p = DataPacket(dest, channel ?: 0, wpt)
        if (wpt.id != 0) sendDataPacket(p)
    }

    private fun sendDataPacket(p: DataPacket) {
        try {
            serviceRepository.meshService?.send(p)
        } catch (ex: RemoteException) {
            Logger.e { "Send DataPacket error: ${ex.message}" }
        }
    }

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
