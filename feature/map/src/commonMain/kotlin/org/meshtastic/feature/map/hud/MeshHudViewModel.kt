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
package org.meshtastic.feature.map.hud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.LocaleUnitsProvider
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.model.geofence.activeWaypointPackets
import org.meshtastic.core.model.passesMapFilter
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.repository.MagneticFieldProvider
import org.meshtastic.core.repository.MapFilterPrefs
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.core.repository.PhoneLocationProvider
import org.meshtastic.core.repository.TracerouteResponseProvider
import org.meshtastic.core.repository.TracerouteSnapshotRepository
import org.meshtastic.core.repository.toNodeMapFilter
import org.meshtastic.core.ui.util.toPosition
import org.meshtastic.feature.map.mergeStationaryRuns
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position as ProtoPosition

private const val STOP_TIMEOUT_MS = 5_000L

/** Above this ground speed the user is walking with the phone up, which is when to remind them to look around. */
private const val WALKING_MPS = 1.0f

private data class HudSettings(
    val units: MeasurementSystem,
    val focusKey: String?,
    val rangeLimited: Boolean,
    val filters: MapFilterPrefs,
    /** Nodes a traceroute or track names, shown whatever the filters and range say. */
    val pinned: Set<Int>,
    /**
     * The node the view was opened for, from its detail, compass, track or traceroute. Then only it and the nodes its
     * track or route names are shown.
     */
    val solo: Int?,
    /** Positions to place nodes at when the database has none live (see `fallbackPositions`). */
    val fallbacks: Map<Int, ProtoPosition>,
)

/** A traceroute to draw: its node nums each way, link SNRs in quarter dB, and the positions it was recorded with. */
data class RouteSource(
    val forward: List<Int>,
    val back: List<Int>,
    val snrTowards: List<Int>?,
    val snrBack: List<Int>?,
    val snapshot: Map<Int, ProtoPosition>,
)

/** A node's position history to draw as breadcrumbs. */
data class TrackSource(val nodeNum: Int, val positions: List<ProtoPosition>)

/** A traceroute the user opened, before it is matched to its logged response. */
private data class RouteRequest(val destNum: Int, val requestId: Int, val logUuid: String?) {
    fun resolve(logs: List<MeshLog>, snapshot: Map<Int, ProtoPosition>, response: TracerouteResponse?): RouteSource? {
        val logged =
            if (logUuid != null) {
                logs.find { it.uuid == logUuid }
            } else {
                logs.find { it.fromRadio.packet?.decoded?.request_id == requestId }
            }
        val discovery = logged?.fromRadio?.packet?.fullRouteDiscovery
        val live = response?.takeIf { it.requestId == requestId && it.hasOverlay }
        val forward = discovery?.route ?: live?.forwardRoute
        val back = discovery?.route_back ?: live?.returnRoute
        return if (forward.isNullOrEmpty() && back.isNullOrEmpty()) {
            null
        } else {
            RouteSource(forward.orEmpty(), back.orEmpty(), discovery?.snr_towards, discovery?.snr_back, snapshot)
        }
    }
}

private const val TRACK_FALLBACK_COLOR = 0xFFFFFFFF.toInt()

/** Default reach of the view, a round number in each unit system; farther targets hide unless focused. */
private const val NEAR_RANGE_METRIC_M = 50_000
private const val NEAR_RANGE_IMPERIAL_M = 48_280 // 30 mi

private fun nearRangeM(units: MeasurementSystem) =
    if (units == MeasurementSystem.IMPERIAL) NEAR_RANGE_IMPERIAL_M else NEAR_RANGE_METRIC_M

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
@KoinViewModel
class MeshHudViewModel(
    poseProvider: CameraPoseProvider,
    phoneLocationProvider: PhoneLocationProvider,
    private val magneticFieldProvider: MagneticFieldProvider,
    nodeRepository: NodeRepository,
    packetRepository: PacketRepository,
    meshLogRepository: MeshLogRepository,
    tracerouteResponseProvider: TracerouteResponseProvider,
    tracerouteSnapshotRepository: TracerouteSnapshotRepository,
    private val mapPrefs: MapPrefs,
    private val notificationPrefs: NotificationPrefs,
    localeUnitsProvider: LocaleUnitsProvider,
    dispatchers: CoroutineDispatchers,
) : ViewModel() {

    private val focusKey = MutableStateFlow<String?>(null)

    // The provider checks the permission once per collection, so a grant made while the screen is open needs a new one.
    private val locationPermissionGranted = MutableStateFlow(false)
    private val rangeLimited = MutableStateFlow(true)
    private val fixAverager = FixAverager()
    private val routeRequest = MutableStateFlow<RouteRequest?>(null)
    private val soloNodeNum = MutableStateFlow<Int?>(null)
    private val trackNodeNum = MutableStateFlow<Int?>(null)

    /**
     * The opened traceroute, resolved as the traceroute map resolves it: the logged response first (by its uuid, else
     * its request id), then the service's latest response, with the hop positions recorded alongside it.
     */
    private val routeSource = routeRequest.flatMapLatest { request ->
        if (request == null) {
            flowOf(null)
        } else {
            combine(
                nodeRepository
                    .effectiveLogNodeId(request.destNum)
                    .flatMapLatest { meshLogRepository.getLogsFrom(it, PortNum.TRACEROUTE_APP.value) }
                    .onStart { emit(emptyList()) },
                request.logUuid?.let(tracerouteSnapshotRepository::getSnapshotPositions) ?: flowOf(emptyMap()),
                tracerouteResponseProvider.tracerouteResponse,
            ) { logs, snapshot, response ->
                request.resolve(logs, snapshot, response)
            }
        }
    }

    /** The opened node's position history, trimmed by the map's track filter and with stationary runs merged. */
    private val trackSource = trackNodeNum.flatMapLatest { num ->
        if (num == null) {
            flowOf(null)
        } else {
            combine(
                nodeRepository.effectiveLogNodeId(num).flatMapLatest {
                    meshLogRepository.getMeshPacketsFrom(it, PortNum.POSITION_APP.value)
                },
                mapPrefs.mapFilters,
            ) { packets, filters ->
                val cutoff = filters.lastHeardTrackSeconds.takeIf { it > 0 }?.let { nowSeconds - it }
                val positions =
                    packets
                        .mapNotNull { it.toPosition() }
                        .filter { cutoff == null || it.time >= cutoff }
                        .sortedBy { it.time }
                TrackSource(num, mergeStationaryRuns(positions).map { it.position })
            }
        }
    }
    private val pinned =
        combine(routeSource, trackSource) { route, track ->
            buildSet {
                route?.let { addAll(it.forward + it.back) }
                track?.let { add(it.nodeNum) }
                remove(UNKNOWN_HOP)
            }
        }

    /**
     * Where to place a node the database has no live position for: the position recorded with the open route, else the
     * newest point of its open track. A remote node, or one last heard while disconnected, often has only these, and
     * without them its chip, and the edge arrow leading to it, would never appear.
     */
    private val fallbackPositions =
        combine(routeSource, trackSource) { route, track ->
            buildMap {
                track?.positions?.lastOrNull()?.let { put(track.nodeNum, it) }
                route?.snapshot?.let { putAll(it) }
            }
        }

    /** The phone's averaged fix, shared by the targets, the route and the track. */
    private val location =
        locationPermissionGranted
            .flatMapLatest { phoneLocationProvider.locationUpdates() }
            .map { state -> state.location?.let { state.copy(location = fixAverager.add(it)) } ?: state }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** The magnetic declination where the phone is, zero until it has a fix. */
    private val declinationDeg =
        location.map { state -> state.location?.let(::declinationAt) ?: 0f }.onStart { emit(0f) }.distinctUntilChanged()

    /**
     * The camera's pose, always referenced to magnetic north like the rotation vector: a source that already gives true
     * north is turned back by the declination, which every target, the tape and the radar then add once.
     */
    val pose: StateFlow<CameraPoseState> =
        combine(poseProvider.poseUpdates().conflate(), declinationDeg) { state, declination ->
            if (state.trueNorth) {
                state.copy(pose = state.pose?.rotatedAboutVertical(-declination.toDouble()), trueNorth = false)
            } else {
                state
            }
        }
            .flowOn(dispatchers.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CameraPoseState())

    val targets: StateFlow<MeshHudTargets> =
        combine(
            location,
            nodeRepository.nodeDBbyNum,
            nodeRepository.myNodeInfo,
            combine(
                localeUnitsProvider.measurementSystem,
                focusKey,
                rangeLimited,
                mapPrefs.mapFilters,
                combine(pinned, soloNodeNum, fallbackPositions, ::Triple),
            ) { units, focus, limited, filters, (pinnedNums, solo, fallbacks) ->
                HudSettings(units, focus, limited, filters, pinnedNums, solo, fallbacks)
            },
            packetRepository.getWaypoints(),
        ) { location, nodes, myNode, settings, waypointPackets ->
            val now = nowSeconds
            val filter = settings.filters.toNodeMapFilter()
            val solo = settings.solo
            val shownNodes =
                nodes.values
                    .mapNotNull { node ->
                        if (node.validPosition != null) {
                            node
                        } else {
                            settings.fallbacks[node.num]
                                ?.let { node.copy(position = it) }
                                ?.takeIf { it.validPosition != null }
                        }
                    }
                    .filter { node ->
                        node.num != myNode?.myNodeNum &&
                            if (solo != null) {
                                node.num == solo || node.num in settings.pinned
                            } else {
                                HudSubject.NodeSubject(node).key == settings.focusKey ||
                                    node.num in settings.pinned ||
                                    node.passesMapFilter(filter, now)
                            }
                    }
            val waypoints =
                if (solo == null && settings.filters.showWaypoints) {
                    waypointPackets.activeWaypointPackets(now).values.mapNotNull { it.waypoint }
                } else {
                    emptyList()
                }
            buildHudTargets(
                location,
                shownNodes,
                waypoints,
                settings.units,
                settings.focusKey,
                now,
                ::declinationAt,
            )
                // A scoped view has no range toggle, so it must not drop its own node for being far.
                .limitedTo(settings.rangeLimited && solo == null, settings.units, settings.pinned)
                .let { built ->
                    built.copy(
                        averagedFixes = fixAverager.count,
                        scoped = solo != null,
                        ready = true,
                        moving = (location.location?.speedMps ?: 0f) > WALKING_MPS,
                        showPrecision = settings.filters.showPrecisionCircle,
                        finding = built.findingFor(settings.focusKey),
                    )
                }
        }
            .flowOn(dispatchers.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MeshHudTargets())

    /** The traceroute in view, if one was opened, as legs of points from here. */
    val route: StateFlow<HudRoute?> =
        combine(location, routeSource, nodeRepository.nodeDBbyNum) { state, source, nodes ->
            val here = state.location
            if (source == null || here == null) {
                null
            } else {
                buildHudRoute(
                    here,
                    declinationAt(here),
                    source.forward,
                    source.back,
                    source.snrTowards,
                    source.snrBack,
                ) { num ->
                    source.snapshot[num] ?: nodes[num]?.validPosition
                }
            }
        }
            .flowOn(dispatchers.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The position track in view, if one was opened, as breadcrumbs from here. */
    val track: StateFlow<HudTrack?> =
        combine(location, trackSource, nodeRepository.nodeDBbyNum) { state, source, nodes ->
            val here = state.location
            if (source == null || here == null) {
                null
            } else {
                val color = nodes[source.nodeNum]?.colors?.second ?: TRACK_FALLBACK_COLOR
                buildHudTrack(here, declinationAt(here), source.nodeNum, color, source.positions, nowSeconds)
            }
        }
            .flowOn(dispatchers.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private fun declinationAt(here: PhoneLocation): Float =
        magneticFieldProvider.getDeclination(here.latitude, here.longitude, here.altitude ?: 0.0, nowMillis)

    /** Waypoint ids of foreign geofences the user opted in to crossing alerts for, as the map's dialog shows them. */
    val geofenceAlertOptIns: StateFlow<Set<Int>> = notificationPrefs.geofenceAlertOptIns

    fun setGeofenceAlertOptIn(waypointId: Int, enabled: Boolean) =
        notificationPrefs.setGeofenceAlertOptIn(waypointId, enabled)

    /** Draws traceroute [requestId] to [destNum] in the air; [logUuid] names its logged response when known. */
    fun showTraceroute(destNum: Int?, requestId: Int?, logUuid: String?) {
        routeRequest.value =
            if (destNum != null && requestId != null) RouteRequest(destNum, requestId, logUuid) else null
    }

    /** Draws [nodeNum]'s position track, or clears it when null. */
    fun showTrack(nodeNum: Int?) {
        trackNodeNum.value = nodeNum
    }

    /**
     * Opens the view for [nodeNum], from that node's detail, compass, track or traceroute: the finder points at it, and
     * only it and the nodes its track or route names are shown. Null opens the whole mesh, as from the map.
     */
    fun openFor(nodeNum: Int?) {
        focusKey.value = nodeNum?.let { "node-$it" }
        soloNodeNum.value = nodeNum
    }

    /** Focuses the finder on [subject], or clears the focus when it is already the one focused. */
    fun toggleFocus(subject: HudSubject) {
        focusKey.value = subject.key.takeIf { it != focusKey.value }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        locationPermissionGranted.value = granted
    }

    fun toggleRangeLimit() {
        rangeLimited.value = !rangeLimited.value
    }

    private fun MeshHudTargets.findingFor(focusKey: String?): HudFinding? =
        targets.firstOrNull { it.focused }?.let { HudFinding(it.subject, it.distanceText) }
            ?: directionUnknown
                .firstOrNull { focusKey != null && it.subject.key == focusKey }
                ?.let { HudFinding(it.subject, it.distanceM.toInt().toDistanceString(units)) }

    /** True once the user has dismissed the first-run coaching. */
    val coachSeen: StateFlow<Boolean> = mapPrefs.meshHudCoachSeen

    fun dismissCoach() = mapPrefs.setMeshHudCoachSeen(true)

    private fun MeshHudTargets.limitedTo(limited: Boolean, units: MeasurementSystem, pinned: Set<Int>): MeshHudTargets {
        val range = nearRangeM(units)
        fun HudTarget.isPinned() = (subject as? HudSubject.NodeSubject)?.node?.num in pinned
        val kept = if (limited) targets.filter { it.distanceM <= range || it.focused || it.isPinned() } else targets
        val keptFences =
            if (limited) fences.filter { f -> f.inside || f.edge.any { it.distanceM <= range } } else fences
        return copy(
            targets = kept,
            fences = keptFences,
            rangeLimited = limited,
            units = units,
            nearRangeText = range.toDistanceString(units),
            hiddenByRange = targets.size - kept.size,
        )
    }
}
