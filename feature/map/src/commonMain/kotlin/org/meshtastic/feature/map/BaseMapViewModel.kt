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
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.repository.MapFilterPrefs
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
    networkRepository: NetworkRepository,
) : ViewModel() {

    val myNodeInfo = nodeRepository.myNodeInfo

    /**
     * Whether the device currently has network connectivity, for the offline-basemap banner and (on the Google flavor)
     * the auto-fallback in [org.meshtastic.app.map.MapViewModel]. Optimistically `true` until the first emission
     * arrives, so the map never flashes an "offline" banner on cold start.
     */
    val mapNetworkAvailable: StateFlow<Boolean> =
        networkRepository.networkAvailable.stateInWhileSubscribed(initialValue = true)

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

    // Every filter reads the persisted flow directly rather than snapshotting `.value` into a mirror at
    // construction. MapPrefsImpl's flow starts eagerly but loads from DataStore asynchronously, so a view model built
    // before that first read kept the defaults forever — nothing wrote the persisted values back into a mirror.
    private val storedFilters: StateFlow<MapFilterPrefs> = mapPrefs.mapFilters

    fun toggleOnlyFavorites() = mapPrefs.updateMapFilters { it.copy(onlyFavorites = !it.onlyFavorites) }

    fun toggleShowWaypointsOnMap() = mapPrefs.updateMapFilters { it.copy(showWaypoints = !it.showWaypoints) }

    fun toggleShowPrecisionCircleOnMap() =
        mapPrefs.updateMapFilters { it.copy(showPrecisionCircle = !it.showPrecisionCircle) }

    fun toggleOnlyOnline() = mapPrefs.updateMapFilters { it.copy(onlyOnline = !it.onlyOnline) }

    fun toggleOnlyDirect() = mapPrefs.updateMapFilters { it.copy(onlyDirect = !it.onlyDirect) }

    fun toggleOnlySigned() = mapPrefs.updateMapFilters { it.copy(onlySigned = !it.onlySigned) }

    fun toggleOnlyEncrypted() = mapPrefs.updateMapFilters { it.copy(onlyEncrypted = !it.onlyEncrypted) }

    fun toggleExcludeMqtt() = mapPrefs.updateMapFilters { it.copy(excludeMqtt = !it.excludeMqtt) }

    fun toggleShowIgnored() = mapPrefs.updateMapFilters { it.copy(showIgnored = !it.showIgnored) }

    fun toggleIncludeUnknown() = mapPrefs.updateMapFilters { it.copy(includeUnknown = !it.includeUnknown) }

    /**
     * The nodes the map draws from.
     *
     * Built from the repository rather than from [nodes], which drops every ignored node unconditionally — that is the
     * right default for the pickers that read it, but it left the map's own show-ignored filter with nothing to add
     * back. [MapNodePolicy] still decides; this only stops the discard happening before it is asked.
     *
     * Declared here rather than beside [nodes] because it reads [storedFilters], and a property initialiser cannot see
     * one declared below it.
     */
    val nodesWithPosition: StateFlow<List<Node>> =
        combine(nodeRepository.getNodes(), storedFilters) { all, filters ->
            all.filter { node -> node.validPosition != null && (filters.showIgnored || !node.isIgnored) }
        }
            .stateInWhileSubscribed(initialValue = emptyList())

    /**
     * Toggles by name rather than through [decodeExcludedRoles], which drops names the current protobufs do not define.
     * Decoding and re-encoding here would discard a role excluded by a newer build on every toggle.
     */
    fun toggleRoleExcluded(role: Config.DeviceConfig.Role) = mapPrefs.updateMapFilters { prefs ->
        val excluded = prefs.excludedRoles
        val next = if (role.name in excluded) excluded - role.name else excluded + role.name
        prefs.copy(excludedRoles = next)
    }

    fun clearExcludedRoles() = mapPrefs.updateMapFilters { it.copy(excludedRoles = emptySet()) }

    fun setLastHeardFilter(filter: LastHeardFilter) =
        mapPrefs.updateMapFilters { it.copy(lastHeardSeconds = filter.seconds) }

    fun setLastHeardTrackFilter(filter: LastHeardFilter) =
        mapPrefs.updateMapFilters { it.copy(lastHeardTrackSeconds = filter.seconds) }

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
        /** Show only nodes whose signed broadcasts the radio has verified (design#149). */
        val onlySigned: Boolean = false,
        /** Show only nodes a public key is on file for. */
        val onlyEncrypted: Boolean = false,
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
                    onlySigned ||
                    onlyEncrypted ||
                    excludeMqtt ||
                    !includeUnknown ||
                    lastHeardFilter != LastHeardFilter.Any
    }

    // One flow, one map: the store hands every filter back as a single value, so there is nothing to recombine.
    val mapFilterStateFlow: StateFlow<MapFilterState> =
        storedFilters.map(::toFilterState).stateInWhileSubscribed(toFilterState(storedFilters.value))

    private fun toFilterState(prefs: MapFilterPrefs) = MapFilterState(
        onlyFavorites = prefs.onlyFavorites,
        showWaypoints = prefs.showWaypoints,
        showPrecisionCircle = prefs.showPrecisionCircle,
        lastHeardFilter = LastHeardFilter.fromSeconds(prefs.lastHeardSeconds),
        lastHeardTrackFilter = LastHeardFilter.fromSeconds(prefs.lastHeardTrackSeconds),
        excludedRoles = decodeExcludedRoles(prefs.excludedRoles),
        onlyOnline = prefs.onlyOnline,
        onlyDirect = prefs.onlyDirect,
        onlySigned = prefs.onlySigned,
        onlyEncrypted = prefs.onlyEncrypted,
        excludeMqtt = prefs.excludeMqtt,
        showIgnored = prefs.showIgnored,
        includeUnknown = prefs.includeUnknown,
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
