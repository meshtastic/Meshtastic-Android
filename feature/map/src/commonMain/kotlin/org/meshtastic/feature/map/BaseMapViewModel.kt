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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.LocaleUnitsProvider
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.core.model.geofence.activeWaypointPackets
import org.meshtastic.core.model.isFromLocal
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.any
import org.meshtastic.core.resources.eight_hours
import org.meshtastic.core.resources.one_day
import org.meshtastic.core.resources.one_hour
import org.meshtastic.core.resources.two_days
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
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
    localeUnitsProvider: LocaleUnitsProvider,
) : ViewModel() {

    val myNodeInfo = nodeRepository.myNodeInfo

    /**
     * Display units (metric/imperial) for distance/altitude/speed formatting across map surfaces. Tracks locale changes
     * and the in-app units setting, because this ViewModel survives the configuration change either triggers.
     */
    val displayUnits: StateFlow<MeasurementSystem> = localeUnitsProvider.measurementSystem

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

    val nodes: StateFlow<List<Node>> =
        nodeRepository
            .getNodes()
            .map { nodes -> nodes.filterNot { node -> node.isIgnored } }
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

    // Every filter reads its persisted flow directly rather than snapshotting `.value` into a mirror at
    // construction. MapPrefsImpl's flows start eagerly but load from DataStore asynchronously, so a view model built
    // before that first read kept the defaults forever — nothing wrote the persisted values back into a mirror.
    val showOnlyFavoritesOnMap: StateFlow<Boolean> = mapPrefs.showOnlyFavorites

    fun toggleOnlyFavorites() = mapPrefs.setShowOnlyFavorites(!showOnlyFavoritesOnMap.value)

    val showWaypointsOnMap: StateFlow<Boolean> = mapPrefs.showWaypointsOnMap

    fun toggleShowWaypointsOnMap() = mapPrefs.setShowWaypointsOnMap(!showWaypointsOnMap.value)

    val showPrecisionCircleOnMap: StateFlow<Boolean> = mapPrefs.showPrecisionCircleOnMap

    fun toggleShowPrecisionCircleOnMap() = mapPrefs.setShowPrecisionCircleOnMap(!showPrecisionCircleOnMap.value)

    val onlyOnlineOnMap: StateFlow<Boolean> = mapPrefs.onlyOnlineOnMap

    fun toggleOnlyOnline() = mapPrefs.setOnlyOnlineOnMap(!onlyOnlineOnMap.value)

    val onlyDirectOnMap: StateFlow<Boolean> = mapPrefs.onlyDirectOnMap

    fun toggleOnlyDirect() = mapPrefs.setOnlyDirectOnMap(!onlyDirectOnMap.value)

    val excludeMqttOnMap: StateFlow<Boolean> = mapPrefs.excludeMqttOnMap

    fun toggleExcludeMqtt() = mapPrefs.setExcludeMqttOnMap(!excludeMqttOnMap.value)

    val showIgnoredOnMap: StateFlow<Boolean> = mapPrefs.showIgnoredOnMap

    fun toggleShowIgnored() = mapPrefs.setShowIgnoredOnMap(!showIgnoredOnMap.value)

    val includeUnknownOnMap: StateFlow<Boolean> = mapPrefs.includeUnknownOnMap

    fun toggleIncludeUnknown() = mapPrefs.setIncludeUnknownOnMap(!includeUnknownOnMap.value)

    /**
     * The nodes the map draws from.
     *
     * Built from the repository rather than from [nodes], which drops every ignored node unconditionally — that is the
     * right default for the pickers that read it, but it left the map's own show-ignored filter with nothing to add
     * back. [MapNodePolicy] still decides; this only stops the discard happening before it is asked.
     *
     * Declared here rather than beside [nodes] because it reads [showIgnoredOnMap], and a property initialiser cannot
     * see one declared below it.
     */
    val nodesWithPosition: StateFlow<List<Node>> =
        combine(nodeRepository.getNodes(), showIgnoredOnMap) { all, showIgnored ->
            all.filter { node -> node.validPosition != null && (showIgnored || !node.isIgnored) }
        }
            .stateInWhileSubscribed(initialValue = emptyList())

    val excludedMapRoles: StateFlow<Set<Config.DeviceConfig.Role>> =
        mapPrefs.excludedMapRoles
            .map(::decodeExcludedRoles)
            .stateInWhileSubscribed(decodeExcludedRoles(mapPrefs.excludedMapRoles.value))

    fun toggleRoleExcluded(role: Config.DeviceConfig.Role) {
        val newValue = excludedMapRoles.value.let { if (role in it) it - role else it + role }
        mapPrefs.setExcludedMapRoles(newValue.mapTo(mutableSetOf()) { it.name })
    }

    fun clearExcludedRoles() = mapPrefs.setExcludedMapRoles(emptySet())

    val lastHeardFilter: StateFlow<LastHeardFilter> =
        mapPrefs.lastHeardFilter
            .map(LastHeardFilter::fromSeconds)
            .stateInWhileSubscribed(LastHeardFilter.fromSeconds(mapPrefs.lastHeardFilter.value))

    fun setLastHeardFilter(filter: LastHeardFilter) = mapPrefs.setLastHeardFilter(filter.seconds)

    val lastHeardTrackFilter: StateFlow<LastHeardFilter> =
        mapPrefs.lastHeardTrackFilter
            .map(LastHeardFilter::fromSeconds)
            .stateInWhileSubscribed(LastHeardFilter.fromSeconds(mapPrefs.lastHeardTrackFilter.value))

    fun setLastHeardTrackFilter(filter: LastHeardFilter) = mapPrefs.setLastHeardTrackFilter(filter.seconds)

    open fun getUser(userId: String?) =
        nodeRepository.getUser(userId ?: org.meshtastic.core.model.NodeAddress.ID_BROADCAST)

    fun getNodeOrFallback(nodeNum: Int): Node = nodeRepository.nodeDBbyNum.value[nodeNum] ?: Node(num = nodeNum)

    fun deleteWaypoint(id: Int) =
        safeLaunch(context = ioDispatcher, tag = "deleteWaypoint") { packetRepository.deleteWaypoint(id) }

    fun sendWaypoint(wpt: Waypoint, contactKey: String = "0${NodeAddress.ID_BROADCAST}") {
        // contactKey: unique contact key filter (channel)+(nodeId)
        val parsedKey = ContactKey(contactKey)
        val p = DataPacket(parsedKey.addressString, parsedKey.channel, wpt)
        if (wpt.id != 0) sendDataPacket(p)
    }

    private fun sendDataPacket(p: DataPacket) {
        safeLaunch(context = ioDispatcher, tag = "sendDataPacket") { radioController.sendMessage(p) }
    }

    fun generatePacketId(): Int = radioController.generatePacketId()

    /**
     * Everything the map's filter sheet controls.
     *
     * The node-level filters mirror the node list's, down to reusing its string resources, so the same words mean the
     * same thing on both screens. [showIgnored] is the one deliberate divergence — see [MapNodePolicy].
     */
    data class MapFilterState(
        val onlyFavorites: Boolean,
        val showWaypoints: Boolean,
        val showPrecisionCircle: Boolean,
        val lastHeardFilter: LastHeardFilter,
        val lastHeardTrackFilter: LastHeardFilter,
        /** Roles the user has switched off. Excluded rather than included so a role added by future firmware shows. */
        val excludedRoles: Set<Config.DeviceConfig.Role> = emptySet(),
        val onlyOnline: Boolean = false,
        val onlyDirect: Boolean = false,
        val excludeMqtt: Boolean = false,
        val showIgnored: Boolean = false,
        val includeUnknown: Boolean = true,
    ) {
        /** True when anything here is narrowing the node set, so the filter button can show it. */
        val isNarrowing: Boolean
            get() =
                onlyFavorites ||
                    excludedRoles.isNotEmpty() ||
                    onlyOnline ||
                    onlyDirect ||
                    excludeMqtt ||
                    !includeUnknown ||
                    lastHeardFilter != LastHeardFilter.Any
    }

    // Two intermediate combines rather than one: `combine` tops out at five flows, and there are eleven.
    private val displayFilters: Flow<MapFilterState> =
        combine(
            showOnlyFavoritesOnMap,
            showWaypointsOnMap,
            showPrecisionCircleOnMap,
            lastHeardFilter,
            lastHeardTrackFilter,
        ) { favoritesOnly, showWaypoints, showPrecisionCircle, lastHeardFilter, lastHeardTrackFilter ->
            MapFilterState(favoritesOnly, showWaypoints, showPrecisionCircle, lastHeardFilter, lastHeardTrackFilter)
        }

    private val nodeFilters: Flow<NodeFilters> =
        combine(excludedMapRoles, onlyOnlineOnMap, onlyDirectOnMap, excludeMqttOnMap, showIgnoredOnMap, ::NodeFilters)

    val mapFilterStateFlow: StateFlow<MapFilterState> =
        combine(displayFilters, nodeFilters, includeUnknownOnMap) { display, nodes, includeUnknown ->
            display.with(nodes, includeUnknown)
        }
            .stateInWhileSubscribed(
                initialValue =
                MapFilterState(
                    showOnlyFavoritesOnMap.value,
                    showWaypointsOnMap.value,
                    showPrecisionCircleOnMap.value,
                    lastHeardFilter.value,
                    lastHeardTrackFilter.value,
                )
                    .with(
                        NodeFilters(
                            excludedMapRoles.value,
                            onlyOnlineOnMap.value,
                            onlyDirectOnMap.value,
                            excludeMqttOnMap.value,
                            showIgnoredOnMap.value,
                        ),
                        includeUnknownOnMap.value,
                    ),
            )

    /** The five node-level toggles, boxed so they fit one `combine`. */
    private data class NodeFilters(
        val excludedRoles: Set<Config.DeviceConfig.Role>,
        val onlyOnline: Boolean,
        val onlyDirect: Boolean,
        val excludeMqtt: Boolean,
        val showIgnored: Boolean,
    )

    private fun MapFilterState.with(nodes: NodeFilters, includeUnknown: Boolean) = copy(
        excludedRoles = nodes.excludedRoles,
        onlyOnline = nodes.onlyOnline,
        onlyDirect = nodes.onlyDirect,
        excludeMqtt = nodes.excludeMqtt,
        showIgnored = nodes.showIgnored,
        includeUnknown = includeUnknown,
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
