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
package org.meshtastic.feature.map

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.core.model.geofence.activeWaypointPackets
import org.meshtastic.core.model.isFromLocal
import org.meshtastic.core.model.util.DistanceUnit
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.any
import org.meshtastic.core.resources.eight_hours
import org.meshtastic.core.resources.one_day
import org.meshtastic.core.resources.one_hour
import org.meshtastic.core.resources.two_days
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import org.meshtastic.proto.Position
import org.meshtastic.proto.Waypoint

/**
 * Shared base ViewModel for the map feature, providing node data, waypoints, map filter preferences, and traceroute
 * overlay state.
 *
 * Platform-specific map ViewModels (fdroid/google) extend this to add flavor-specific map provider logic.
 */
@Suppress("TooManyFunctions")
open class BaseMapViewModel(
    protected val mapPrefs: MapPrefs,
    protected val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val radioController: RadioController,
    private val radioConfigRepository: RadioConfigRepository,
    private val notificationPrefs: NotificationPrefs,
    private val uiPrefs: UiPrefs,
) : ViewModel() {

    val myNodeInfo = nodeRepository.myNodeInfo

    /**
     * OS locale display units (metric/imperial) for distance/altitude/speed formatting across map surfaces. StateFlow
     * kept for the existing collectAsState call sites; value is a one-time snapshot at construction and does not react
     * to a mid-session locale change (ViewModel survives config changes).
     */
    val displayUnits: StateFlow<DisplayUnits> = MutableStateFlow(DistanceUnit.getFromLocale()).asStateFlow()

    val ourNodeInfo = nodeRepository.ourNodeInfo

    /**
     * Connected radio's channel set (primary-channel frequency + LoRa config); used to prefill a Site Planner estimate.
     */
    val channelSet: StateFlow<ChannelSet?> =
        radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = null)

    val myNodeNum
        get() = myNodeInfo.value?.myNodeNum

    val myId = nodeRepository.myId

    val isConnected =
        radioController.connectionState
            .map { it is org.meshtastic.core.model.ConnectionState.Connected }
            .stateInWhileSubscribed(initialValue = false)

    /**
     * Nodes sorted per the user's current Nodes-tab sort preference (re-queried live whenever that preference changes,
     * e.g. via the waypoint recipient picker staying in sync with the Nodes tab).
     */
    val nodes: StateFlow<List<Node>> =
        uiPrefs.nodeSort
            .map { NodeSortOption.fromOrdinal(it) }
            .flatMapLatest { sort -> nodeRepository.getNodes(sort = sort) }
            .map { nodes -> nodes.filterNot { node -> node.isIgnored } }
            .stateInWhileSubscribed(initialValue = emptyList())

    val nodesWithPosition: StateFlow<List<Node>> =
        nodes
            .map { nodes -> nodes.filter { node -> node.validPosition != null } }
            .stateInWhileSubscribed(initialValue = emptyList())

    val waypoints: StateFlow<Map<Int, DataPacket>> =
        packetRepository
            .getWaypoints()
            // Shared with GeofenceMonitor via activeWaypointPackets — dedup by waypoint id + drop expired,
            // so the map and the geofence engine can't drift (getWaypoints is a row-per-transmission firehose).
            .mapLatest { list -> list.activeWaypointPackets(nowSeconds) }
            .stateInWhileSubscribed(initialValue = emptyMap())

    /** Waypoint ids of foreign geofences the user opted in to crossing alerts for (see [NotificationPrefs]). */
    val geofenceAlertOptIns: StateFlow<Set<Int>> = notificationPrefs.geofenceAlertOptIns

    fun setGeofenceAlertOptIn(waypointId: Int, enabled: Boolean) =
        notificationPrefs.setGeofenceAlertOptIn(waypointId, enabled)

    /** True if the waypoint with [id] was created by this device (vs. received from another node over the mesh). */
    fun isMyWaypoint(id: Int): Boolean = waypoints.value[id]?.isFromLocal(myNodeNum) == true

    private val showOnlyFavorites = MutableStateFlow(mapPrefs.showOnlyFavorites.value)
    val showOnlyFavoritesOnMap: StateFlow<Boolean> = showOnlyFavorites.asStateFlow()

    fun toggleOnlyFavorites() {
        val newValue = !showOnlyFavorites.value
        showOnlyFavorites.value = newValue
        mapPrefs.setShowOnlyFavorites(newValue)
    }

    private val showWaypoints = MutableStateFlow(mapPrefs.showWaypointsOnMap.value)
    val showWaypointsOnMap: StateFlow<Boolean> = showWaypoints.asStateFlow()

    fun toggleShowWaypointsOnMap() {
        val newValue = !showWaypoints.value
        showWaypoints.value = newValue
        mapPrefs.setShowWaypointsOnMap(newValue)
    }

    private val showPrecisionCircle = MutableStateFlow(mapPrefs.showPrecisionCircleOnMap.value)
    val showPrecisionCircleOnMap: StateFlow<Boolean> = showPrecisionCircle.asStateFlow()

    fun toggleShowPrecisionCircleOnMap() {
        val newValue = !showPrecisionCircle.value
        showPrecisionCircle.value = newValue
        mapPrefs.setShowPrecisionCircleOnMap(newValue)
    }

    private val lastHeardFilterValue = MutableStateFlow(LastHeardFilter.fromSeconds(mapPrefs.lastHeardFilter.value))
    val lastHeardFilter: StateFlow<LastHeardFilter> = lastHeardFilterValue.asStateFlow()

    fun setLastHeardFilter(filter: LastHeardFilter) {
        lastHeardFilterValue.value = filter
        mapPrefs.setLastHeardFilter(filter.seconds)
    }

    private val lastHeardTrackFilterValue =
        MutableStateFlow(LastHeardFilter.fromSeconds(mapPrefs.lastHeardTrackFilter.value))
    val lastHeardTrackFilter: StateFlow<LastHeardFilter> = lastHeardTrackFilterValue.asStateFlow()

    fun setLastHeardTrackFilter(filter: LastHeardFilter) {
        lastHeardTrackFilterValue.value = filter
        mapPrefs.setLastHeardTrackFilter(filter.seconds)
    }

    open fun getUser(userId: String?) =
        nodeRepository.getUser(userId ?: org.meshtastic.core.model.NodeAddress.ID_BROADCAST)

    fun getNodeOrFallback(nodeNum: Int): Node = nodeRepository.nodeDBbyNum.value[nodeNum] ?: Node(num = nodeNum)

    fun deleteWaypoint(id: Int) =
        safeLaunch(context = ioDispatcher, tag = "deleteWaypoint") { packetRepository.deleteWaypoint(id) }

    fun sendWaypoint(wpt: Waypoint, contactKey: String = "0${NodeAddress.ID_BROADCAST}"): Job? {
        // contactKey: unique contact key filter (channel)+(nodeId)
        val parsedKey = ContactKey(contactKey)
        val p = DataPacket(parsedKey.addressString, parsedKey.channel, wpt)
        return if (wpt.id != 0) sendDataPacket(p) else null
    }

    private fun sendDataPacket(p: DataPacket): Job =
        safeLaunch(context = ioDispatcher, tag = "sendDataPacket") { radioController.sendMessage(p) }

    fun generatePacketId(): Int = radioController.generatePacketId()

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

/**
 * Result of resolving a [TracerouteOverlay]'s node nums into displayable [Node] instances.
 *
 * @property overlayNodeNums All unique node nums referenced by the traceroute.
 * @property nodesForMarkers Nodes to render as map markers (with snapshot positions when available).
 * @property nodeLookup Node-num-keyed map for polyline coordinate resolution.
 */
data class TracerouteNodeSelection(
    val overlayNodeNums: Set<Int>,
    val nodesForMarkers: List<Node>,
    val nodeLookup: Map<Int, Node>,
)

/** Convenience extension that delegates to [tracerouteNodeSelection] using the VM's [getNodeOrFallback]. */
fun BaseMapViewModel.tracerouteNodeSelection(
    tracerouteOverlay: TracerouteOverlay?,
    tracerouteNodePositions: Map<Int, Position>,
    nodes: List<Node>,
): TracerouteNodeSelection = tracerouteNodeSelection(
    tracerouteOverlay = tracerouteOverlay,
    tracerouteNodePositions = tracerouteNodePositions,
    nodes = nodes,
    getNodeOrFallback = ::getNodeOrFallback,
)

/**
 * Resolves traceroute overlay node nums into displayable [Node] instances. Snapshot positions (recorded at traceroute
 * time) take priority over live positions from the node database.
 *
 * @param getNodeOrFallback Provides a [Node] for a given num, falling back to a stub if not in the DB.
 */
fun tracerouteNodeSelection(
    tracerouteOverlay: TracerouteOverlay?,
    tracerouteNodePositions: Map<Int, Position>,
    nodes: List<Node>,
    getNodeOrFallback: (Int) -> Node,
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
