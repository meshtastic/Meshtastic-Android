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

import android.Manifest
import android.graphics.Paint
import android.text.format.DateUtils
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lens
import androidx.compose.material.icons.rounded.LocationDisabled
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.gpsDisabled
import org.meshtastic.core.common.hasGps
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.calculating
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.clear
import org.meshtastic.core.strings.close
import org.meshtastic.core.strings.delete_for_everyone
import org.meshtastic.core.strings.delete_for_me
import org.meshtastic.core.strings.expires
import org.meshtastic.core.strings.location_disabled
import org.meshtastic.core.strings.map_cache_info
import org.meshtastic.core.strings.map_cache_manager
import org.meshtastic.core.strings.map_cache_size
import org.meshtastic.core.strings.map_cache_tiles
import org.meshtastic.core.strings.map_clear_tiles
import org.meshtastic.core.strings.map_download_complete
import org.meshtastic.core.strings.map_download_errors
import org.meshtastic.core.strings.map_download_region
import org.meshtastic.core.strings.map_filter
import org.meshtastic.core.strings.map_node_popup_details
import org.meshtastic.core.strings.map_offline_manager
import org.meshtastic.core.strings.map_purge_fail
import org.meshtastic.core.strings.map_purge_success
import org.meshtastic.core.strings.map_style_selection
import org.meshtastic.core.strings.map_subDescription
import org.meshtastic.core.strings.map_tile_source
import org.meshtastic.core.strings.only_favorites
import org.meshtastic.core.strings.show_precision_circle
import org.meshtastic.core.strings.show_waypoints
import org.meshtastic.core.strings.toggle_my_position
import org.meshtastic.core.strings.waypoint_delete
import org.meshtastic.core.strings.you
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.theme.TracerouteColors
import org.meshtastic.core.ui.util.formatAgo
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.feature.map.cluster.RadiusMarkerClusterer
import org.meshtastic.feature.map.component.CacheLayout
import org.meshtastic.feature.map.component.DownloadButton
import org.meshtastic.feature.map.component.EditWaypointDialog
import org.meshtastic.feature.map.component.MapButton
import org.meshtastic.feature.map.model.CustomTileSource
import org.meshtastic.feature.map.model.MarkerWithLabel
import org.meshtastic.feature.map.model.TracerouteOverlay
import org.meshtastic.proto.Position
import org.meshtastic.proto.Waypoint
import org.osmdroid.bonuspack.utils.BonusPackHelper.getBitmapFromVectorDrawable
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqliteArchiveTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicyException
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private fun MapView.updateMarkers(
    nodeMarkers: List<MarkerWithLabel>,
    waypointMarkers: List<MarkerWithLabel>,
    nodeClusterer: RadiusMarkerClusterer,
) {
    Logger.d { "Showing on map: ${nodeMarkers.size} nodes ${waypointMarkers.size} waypoints" }
    overlays.removeAll { it is MarkerWithLabel }
    // overlays.addAll(nodeMarkers + waypointMarkers)
    overlays.addAll(waypointMarkers)
    nodeClusterer.items.clear()
    nodeClusterer.items.addAll(nodeMarkers)
    nodeClusterer.invalidate()
}

private fun cacheManagerCallback(onTaskComplete: () -> Unit, onTaskFailed: (Int) -> Unit) =
    object : CacheManager.CacheManagerCallback {
        override fun onTaskComplete() {
            onTaskComplete()
        }

        override fun onTaskFailed(errors: Int) {
            onTaskFailed(errors)
        }

        override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
            // NOOP since we are using the build in UI
        }

        override fun downloadStarted() {
            // NOOP since we are using the build in UI
        }

        override fun setPossibleTilesInArea(total: Int) {
            // NOOP since we are using the build in UI
        }
    }

/**
 * Main composable for displaying the map view, including nodes, waypoints, and user location. It handles user
 * interactions for map manipulation, filtering, and offline caching.
 *
 * @param mapViewModel The [MapViewModel] providing data and state for the map.
 * @param navigateToNodeDetails Callback to navigate to the details screen of a selected node.
 */
@OptIn(ExperimentalPermissionsApi::class) // Added for Accompanist
@Suppress("CyclomaticComplexMethod", "LongParameterList", "LongMethod")
@Composable
fun MapView(
    mapViewModel: MapViewModel = hiltViewModel(),
    navigateToNodeDetails: (Int) -> Unit,
    tracerouteOverlay: TracerouteOverlay? = null,
    tracerouteNodePositions: Map<Int, Position> = emptyMap(),
    onTracerouteMappableCountChanged: (shown: Int, total: Int) -> Unit = { _, _ -> },
) {
    var mapFilterExpanded by remember { mutableStateOf(false) }

    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val isConnected by mapViewModel.isConnected.collectAsStateWithLifecycle()

    var cacheEstimate by remember { mutableStateOf("") }

    var zoomLevelMin by remember { mutableDoubleStateOf(0.0) }
    var zoomLevelMax by remember { mutableDoubleStateOf(0.0) }

    var downloadRegionBoundingBox: BoundingBox? by remember { mutableStateOf(null) }
    var myLocationOverlay: MyLocationNewOverlay? by remember { mutableStateOf(null) }

    var showDownloadButton: Boolean by remember { mutableStateOf(false) }
    var showEditWaypointDialog by remember { mutableStateOf<Waypoint?>(null) }
    var showCacheManagerDialog by remember { mutableStateOf(false) }
    var showCurrentCacheInfo by remember { mutableStateOf(false) }
    var showPurgeTileSourceDialog by remember { mutableStateOf(false) }
    var showMapStyleDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    val haptic = LocalHapticFeedback.current
    fun performHapticFeedback() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)

    val hasGps = remember { context.hasGps() }

    // Accompanist permissions state for location
    val locationPermissionsState =
        rememberMultiplePermissionsState(permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION))
    var triggerLocationToggleAfterPermission by remember { mutableStateOf(false) }

    fun loadOnlineTileSourceBase(): ITileSource {
        val id = mapViewModel.mapStyleId
        Logger.d { "mapStyleId from prefs: $id" }
        return CustomTileSource.getTileSource(id).also {
            zoomLevelMax = it.maximumZoomLevel.toDouble()
            showDownloadButton = if (it is OnlineTileSourceBase) it.tileSourcePolicy.acceptsBulkDownload() else false
        }
    }

    val initialCameraView = remember {
        val nodes = mapViewModel.nodes.value
        val nodesWithPosition = nodes.filter { it.validPosition != null }
        val geoPoints = nodesWithPosition.map { GeoPoint(it.latitude, it.longitude) }
        BoundingBox.fromGeoPoints(geoPoints)
    }
    val map =
        rememberMapViewWithLifecycle(
            applicationId = mapViewModel.applicationId,
            box = initialCameraView,
            tileSource = loadOnlineTileSourceBase(),
        )

    val nodeClusterer = remember { RadiusMarkerClusterer(context) }

    fun MapView.toggleMyLocation() {
        if (context.gpsDisabled()) {
            Logger.d { "Telling user we need location turned on for MyLocationNewOverlay" }
            scope.launch { context.showToast(Res.string.location_disabled) }
            return
        }
        Logger.d { "user clicked MyLocationNewOverlay ${myLocationOverlay == null}" }
        if (myLocationOverlay == null) {
            myLocationOverlay =
                MyLocationNewOverlay(this).apply {
                    enableMyLocation()
                    enableFollowLocation()
                    getBitmapFromVectorDrawable(context, org.meshtastic.core.ui.R.drawable.ic_map_location_dot_24)
                        ?.let {
                            setPersonIcon(it)
                            setPersonAnchor(0.5f, 0.5f)
                        }
                    getBitmapFromVectorDrawable(context, org.meshtastic.core.ui.R.drawable.ic_map_navigation_24)?.let {
                        setDirectionIcon(it)
                        setDirectionAnchor(0.5f, 0.5f)
                    }
                }
            overlays.add(myLocationOverlay)
        } else {
            myLocationOverlay?.apply {
                disableMyLocation()
                disableFollowLocation()
            }
            overlays.remove(myLocationOverlay)
            myLocationOverlay = null
        }
    }

    // Effect to toggle MyLocation after permission is granted
    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted && triggerLocationToggleAfterPermission) {
            map.toggleMyLocation()
            triggerLocationToggleAfterPermission = false
        }
    }

    val nodes by mapViewModel.nodes.collectAsStateWithLifecycle()
    val waypoints by mapViewModel.waypoints.collectAsStateWithLifecycle(emptyMap())
    val selectedWaypointId by mapViewModel.selectedWaypointId.collectAsStateWithLifecycle()
    val myId by mapViewModel.myId.collectAsStateWithLifecycle()

    LaunchedEffect(selectedWaypointId, waypoints) {
        if (selectedWaypointId != null && waypoints.containsKey(selectedWaypointId)) {
            waypoints[selectedWaypointId]?.data?.waypoint?.let { pt ->
                val geoPoint = GeoPoint((pt.latitude_i ?: 0) * 1e-7, (pt.longitude_i ?: 0) * 1e-7)
                map.controller.setCenter(geoPoint)
                map.controller.setZoom(WAYPOINT_ZOOM)
            }
        }
    }

    val tracerouteSelection =
        remember(tracerouteOverlay, tracerouteNodePositions, nodes) {
            mapViewModel.tracerouteNodeSelection(
                tracerouteOverlay = tracerouteOverlay,
                tracerouteNodePositions = tracerouteNodePositions,
                nodes = nodes,
            )
        }
    val overlayNodeNums = tracerouteSelection.overlayNodeNums
    val nodeLookup = tracerouteSelection.nodeLookup
    val nodesForMarkers = tracerouteSelection.nodesForMarkers
    val tracerouteForwardPoints =
        remember(tracerouteOverlay, nodeLookup) {
            tracerouteOverlay?.forwardRoute?.mapNotNull {
                nodeLookup[it]?.let { node -> GeoPoint(node.latitude, node.longitude) }
            } ?: emptyList()
        }
    val tracerouteReturnPoints =
        remember(tracerouteOverlay, nodeLookup) {
            tracerouteOverlay?.returnRoute?.mapNotNull {
                nodeLookup[it]?.let { node -> GeoPoint(node.latitude, node.longitude) }
            } ?: emptyList()
        }
    LaunchedEffect(tracerouteOverlay, nodesForMarkers) {
        if (tracerouteOverlay != null) {
            onTracerouteMappableCountChanged(nodesForMarkers.size, tracerouteOverlay.relatedNodeNums.size)
        }
    }
    val tracerouteHeadingReferencePoints =
        remember(tracerouteForwardPoints, tracerouteReturnPoints) {
            when {
                tracerouteForwardPoints.size >= 2 -> tracerouteForwardPoints
                tracerouteReturnPoints.size >= 2 -> tracerouteReturnPoints
                else -> emptyList()
            }
        }
    val tracerouteForwardOffsetPoints =
        remember(tracerouteForwardPoints, tracerouteHeadingReferencePoints) {
            offsetPolyline(
                points = tracerouteForwardPoints,
                offsetMeters = TRACEROUTE_OFFSET_METERS,
                headingReferencePoints = tracerouteHeadingReferencePoints,
                sideMultiplier = 1.0,
            )
        }
    val tracerouteReturnOffsetPoints =
        remember(tracerouteReturnPoints, tracerouteHeadingReferencePoints) {
            offsetPolyline(
                points = tracerouteReturnPoints,
                offsetMeters = TRACEROUTE_OFFSET_METERS,
                headingReferencePoints = tracerouteHeadingReferencePoints,
                sideMultiplier = -1.0,
            )
        }
    val traceroutePolylines = remember { mutableStateListOf<Polyline>() }
    var hasCenteredTraceroute by remember(tracerouteOverlay) { mutableStateOf(false) }

    val markerIcon = remember {
        AppCompatResources.getDrawable(context, org.meshtastic.core.ui.R.drawable.ic_baseline_location_on_24)
    }

    fun MapView.onNodesChanged(nodes: Collection<Node>): List<MarkerWithLabel> {
        val nodesWithPosition = nodes.filter { it.validPosition != null }
        val ourNode = mapViewModel.ourNodeInfo.value
        val displayUnits =
            mapViewModel.config.display?.units ?: org.meshtastic.proto.Config.DisplayConfig.DisplayUnits.METRIC
        val mapFilterStateValue = mapViewModel.mapFilterStateFlow.value // Access mapFilterState directly
        return nodesWithPosition.mapNotNull { node ->
            if (
                mapFilterStateValue.onlyFavorites &&
                !node.isFavorite &&
                !overlayNodeNums.contains(node.num) &&
                !node.equals(ourNode)
            ) {
                return@mapNotNull null
            }

            val (p, u) = node.position to node.user
            val nodePosition = GeoPoint(node.latitude, node.longitude)
            MarkerWithLabel(mapView = this, label = "${u.short_name} ${formatAgo(p.time)}").apply {
                id = u.id
                title = u.long_name
                snippet =
                    com.meshtastic.core.strings.getString(
                        Res.string.map_node_popup_details,
                        node.gpsString(),
                        formatAgo(node.lastHeard),
                        formatAgo(p.time),
                        if (node.batteryStr != "") node.batteryStr else "?",
                    )
                ourNode?.distanceStr(node, displayUnits)?.let { dist ->
                    subDescription =
                        com.meshtastic.core.strings.getString(
                            Res.string.map_subDescription,
                            ourNode.bearing(node).toString(),
                            dist,
                        )
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                position = nodePosition
                icon = markerIcon
                setNodeColors(node.colors)
                if (!mapFilterStateValue.showPrecisionCircle) {
                    setPrecisionBits(0)
                } else {
                    setPrecisionBits(p.precision_bits ?: 0)
                }
                setOnLongClickListener {
                    navigateToNodeDetails(node.num)
                    true
                }
            }
        }
    }

    fun showDeleteMarkerDialog(waypoint: Waypoint) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(com.meshtastic.core.strings.getString(Res.string.waypoint_delete))
        builder.setNeutralButton(com.meshtastic.core.strings.getString(Res.string.cancel)) { _, _ ->
            Logger.d { "User canceled marker delete dialog" }
        }
        builder.setNegativeButton(com.meshtastic.core.strings.getString(Res.string.delete_for_me)) { _, _ ->
            Logger.d { "User deleted waypoint ${waypoint.id} for me" }
            mapViewModel.deleteWaypoint(waypoint.id)
        }
        if ((waypoint.locked_to ?: 0) in setOf(0, mapViewModel.myNodeNum ?: 0) && isConnected) {
            builder.setPositiveButton(com.meshtastic.core.strings.getString(Res.string.delete_for_everyone)) { _, _ ->
                Logger.d { "User deleted waypoint ${waypoint.id} for everyone" }
                mapViewModel.sendWaypoint(waypoint.copy(expire = 1))
                mapViewModel.deleteWaypoint(waypoint.id)
            }
        }
        val dialog = builder.show()
        for (
        button in
        setOf(
            androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL,
            androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE,
            androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE,
        )
        ) {
            with(dialog.getButton(button)) {
                textSize = 12F
                isAllCaps = false
            }
        }
    }

    fun showMarkerLongPressDialog(id: Int) {
        performHapticFeedback()
        Logger.d { "marker long pressed id=$id" }
        val waypoint = waypoints[id]?.data?.waypoint ?: return
        // edit only when unlocked or lockedTo myNodeNum
        if ((waypoint.locked_to ?: 0) in setOf(0, mapViewModel.myNodeNum ?: 0) && isConnected) {
            showEditWaypointDialog = waypoint
        } else {
            showDeleteMarkerDialog(waypoint)
        }
    }

    fun getUsername(id: String?) = if (id == DataPacket.ID_LOCAL || (myId != null && id == myId)) {
        com.meshtastic.core.strings.getString(Res.string.you)
    } else {
        mapViewModel.getUser(id).long_name
    }

    @Suppress("MagicNumber")
    fun MapView.onWaypointChanged(waypoints: Collection<Packet>, selectedWaypointId: Int?): List<MarkerWithLabel> {
        return waypoints.mapNotNull { waypoint ->
            val pt = waypoint.data.waypoint ?: return@mapNotNull null
            if (!mapFilterState.showWaypoints) return@mapNotNull null // Use collected mapFilterState
            val lock = if ((pt.locked_to ?: 0) != 0) "\uD83D\uDD12" else ""
            val time =
                DateUtils.formatDateTime(
                    context,
                    waypoint.received_time,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
                )
            val label = (pt.name ?: "") + " " + formatAgo((waypoint.received_time / 1000).toInt())
            val emoji = String(Character.toChars(if ((pt.icon ?: 0) == 0) 128205 else pt.icon!!))
            val now = nowMillis
            val expireTimeMillis = (pt.expire ?: 0) * 1000L
            val expireTimeStr =
                when {
                    (pt.expire ?: 0) == 0 || pt.expire == Int.MAX_VALUE -> "Never"
                    expireTimeMillis <= now -> "Expired"
                    else ->
                        DateUtils.getRelativeTimeSpanString(
                            expireTimeMillis,
                            now,
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        )
                            .toString()
                }
            MarkerWithLabel(this, label, emoji).apply {
                id = "${pt.id}"
                title = "${pt.name} (${getUsername(waypoint.data.from)}$lock)"
                snippet =
                    "[$time] ${pt.description}  " +
                    com.meshtastic.core.strings.getString(Res.string.expires) +
                    ": $expireTimeStr"
                position = GeoPoint((pt.latitude_i ?: 0) * 1e-7, (pt.longitude_i ?: 0) * 1e-7)
                if (selectedWaypointId == pt.id) {
                    showInfoWindow()
                }
                setOnLongClickListener {
                    showMarkerLongPressDialog(pt.id)
                    true
                }
            }
        }
    }

    val mapEventsReceiver =
        object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                InfoWindow.closeAllInfoWindowsOn(map)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                performHapticFeedback()
                val enabled = isConnected && downloadRegionBoundingBox == null

                if (enabled) {
                    showEditWaypointDialog =
                        Waypoint(latitude_i = (p.latitude * 1e7).toInt(), longitude_i = (p.longitude * 1e7).toInt())
                }
                return true
            }
        }

    fun MapView.drawOverlays() {
        if (overlays.none { it is MapEventsOverlay }) {
            overlays.add(0, MapEventsOverlay(mapEventsReceiver))
        }
        if (myLocationOverlay != null && overlays.none { it is MyLocationNewOverlay }) {
            overlays.add(myLocationOverlay)
        }
        if (overlays.none { it is RadiusMarkerClusterer }) {
            overlays.add(nodeClusterer)
        }

        addCopyright()
        addScaleBarOverlay(density)
        createLatLongGrid(false)

        invalidate()
    }

    fun MapView.updateTracerouteOverlay(forwardPoints: List<GeoPoint>, returnPoints: List<GeoPoint>) {
        overlays.removeAll(traceroutePolylines)
        traceroutePolylines.clear()

        fun buildPolyline(points: List<GeoPoint>, color: Int, strokeWidth: Float): Polyline = Polyline().apply {
            setPoints(points)
            outlinePaint.apply {
                this.color = color
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                style = Paint.Style.STROKE
            }
        }

        forwardPoints
            .takeIf { it.size >= 2 }
            ?.let { points ->
                traceroutePolylines.add(
                    buildPolyline(points, TracerouteColors.OutgoingRoute.toArgb(), with(density) { 6.dp.toPx() }),
                )
            }
        returnPoints
            .takeIf { it.size >= 2 }
            ?.let { points ->
                traceroutePolylines.add(
                    buildPolyline(points, TracerouteColors.ReturnRoute.toArgb(), with(density) { 5.dp.toPx() }),
                )
            }
        overlays.addAll(traceroutePolylines)
        invalidate()
    }

    LaunchedEffect(tracerouteOverlay, tracerouteForwardPoints, tracerouteReturnPoints) {
        if (tracerouteOverlay == null || hasCenteredTraceroute) return@LaunchedEffect
        val allPoints = (tracerouteForwardPoints + tracerouteReturnPoints).distinct()
        if (allPoints.isNotEmpty()) {
            if (allPoints.size == 1) {
                map.controller.setCenter(allPoints.first())
                map.controller.setZoom(TRACEROUTE_SINGLE_POINT_ZOOM)
            } else {
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(allPoints).zoomIn(-TRACEROUTE_ZOOM_OUT_LEVELS), true)
            }
            hasCenteredTraceroute = true
        }
    }

    fun MapView.generateBoxOverlay() {
        overlays.removeAll { it is Polygon }
        val zoomFactor = 1.3
        zoomLevelMin = minOf(zoomLevelDouble, zoomLevelMax)
        downloadRegionBoundingBox = boundingBox.zoomIn(zoomFactor)
        val polygon =
            Polygon().apply {
                points = Polygon.pointsAsRect(downloadRegionBoundingBox).map { GeoPoint(it.latitude, it.longitude) }
            }
        overlays.add(polygon)
        invalidate()
        val tileCount: Int =
            CacheManager(this)
                .possibleTilesInArea(downloadRegionBoundingBox, zoomLevelMin.toInt(), zoomLevelMax.toInt())
        cacheEstimate = com.meshtastic.core.strings.getString(Res.string.map_cache_tiles, tileCount)
    }

    val boxOverlayListener =
        object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                if (downloadRegionBoundingBox != null) {
                    event.source.generateBoxOverlay()
                }
                return true
            }

            override fun onZoom(event: ZoomEvent): Boolean = false
        }

    fun startDownload() {
        val boundingBox = downloadRegionBoundingBox ?: return
        try {
            val outputName = buildString {
                append(Configuration.getInstance().osmdroidBasePath.absolutePath)
                append(File.separator)
                append("mainFile.sqlite")
            }
            val writer = SqliteArchiveTileWriter(outputName)
            val cacheManager = CacheManager(map, writer)
            cacheManager.downloadAreaAsync(
                context,
                boundingBox,
                zoomLevelMin.toInt(),
                zoomLevelMax.toInt(),
                cacheManagerCallback(
                    onTaskComplete = {
                        scope.launch { context.showToast(Res.string.map_download_complete) }
                        writer.onDetach()
                    },
                    onTaskFailed = { errors ->
                        scope.launch { context.showToast(Res.string.map_download_errors, errors) }
                        writer.onDetach()
                    },
                ),
            )
        } catch (ex: TileSourcePolicyException) {
            Logger.d { "Tile source does not allow archiving: ${ex.message}" }
        } catch (ex: Exception) {
            Logger.d { "Tile source exception: ${ex.message}" }
        }
    }

    Scaffold(
        floatingActionButton = {
            DownloadButton(showDownloadButton && downloadRegionBoundingBox == null) { showCacheManagerDialog = true }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AndroidView(
                factory = {
                    map.apply {
                        setDestroyMode(false)
                        addMapListener(boxOverlayListener)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    mapView.updateTracerouteOverlay(tracerouteForwardOffsetPoints, tracerouteReturnOffsetPoints)
                    with(mapView) {
                        updateMarkers(
                            onNodesChanged(nodesForMarkers),
                            onWaypointChanged(waypoints.values, selectedWaypointId),
                            nodeClusterer,
                        )
                    }
                    mapView.drawOverlays()
                }, // Renamed map to mapView to avoid conflict
            )
            if (downloadRegionBoundingBox != null) {
                CacheLayout(
                    cacheEstimate = cacheEstimate,
                    onExecuteJob = { startDownload() },
                    onCancelDownload = {
                        downloadRegionBoundingBox = null
                        map.overlays.removeAll { it is Polygon }
                        map.invalidate()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else {
                Column(
                    modifier = Modifier.padding(top = 16.dp, end = 16.dp).align(Alignment.TopEnd),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapButton(
                        onClick = { showMapStyleDialog = true },
                        icon = Icons.Outlined.Layers,
                        contentDescription = Res.string.map_style_selection,
                    )
                    Box(modifier = Modifier) {
                        MapButton(
                            onClick = { mapFilterExpanded = true },
                            icon = Icons.Outlined.Tune,
                            contentDescription = Res.string.map_filter,
                        )
                        DropdownMenu(
                            expanded = mapFilterExpanded,
                            onDismissRequest = { mapFilterExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Star,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 8.dp),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(Res.string.only_favorites),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Checkbox(
                                            checked = mapFilterState.onlyFavorites,
                                            onCheckedChange = { mapViewModel.toggleOnlyFavorites() },
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                },
                                onClick = { mapViewModel.toggleOnlyFavorites() },
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PinDrop,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 8.dp),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(Res.string.show_waypoints),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Checkbox(
                                            checked = mapFilterState.showWaypoints,
                                            onCheckedChange = { mapViewModel.toggleShowWaypointsOnMap() },
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                },
                                onClick = { mapViewModel.toggleShowWaypointsOnMap() },
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lens,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 8.dp),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(Res.string.show_precision_circle),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Checkbox(
                                            checked = mapFilterState.showPrecisionCircle,
                                            onCheckedChange = { mapViewModel.toggleShowPrecisionCircleOnMap() },
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                },
                                onClick = { mapViewModel.toggleShowPrecisionCircleOnMap() },
                            )
                        }
                    }
                    if (hasGps) {
                        MapButton(
                            icon =
                            if (myLocationOverlay == null) {
                                Icons.Outlined.MyLocation
                            } else {
                                Icons.Rounded.LocationDisabled
                            },
                            contentDescription = stringResource(Res.string.toggle_my_position),
                        ) {
                            if (locationPermissionsState.allPermissionsGranted) {
                                map.toggleMyLocation()
                            } else {
                                triggerLocationToggleAfterPermission = true
                                locationPermissionsState.launchMultiplePermissionRequest()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMapStyleDialog) {
        MapStyleDialog(
            selectedMapStyle = mapViewModel.mapStyleId,
            onDismiss = { showMapStyleDialog = false },
            onSelectMapStyle = {
                mapViewModel.mapStyleId = it
                map.setTileSource(loadOnlineTileSourceBase())
            },
        )
    }

    if (showCacheManagerDialog) {
        CacheManagerDialog(
            onClickOption = { option ->
                when (option) {
                    CacheManagerOption.CurrentCacheSize -> {
                        scope.launch { context.showToast(Res.string.calculating) }
                        showCurrentCacheInfo = true
                    }
                    CacheManagerOption.DownloadRegion -> map.generateBoxOverlay()

                    CacheManagerOption.ClearTiles -> showPurgeTileSourceDialog = true
                    CacheManagerOption.Cancel -> Unit
                }
                showCacheManagerDialog = false
            },
            onDismiss = { showCacheManagerDialog = false },
        )
    }

    if (showCurrentCacheInfo) {
        CacheInfoDialog(mapView = map, onDismiss = { showCurrentCacheInfo = false })
    }

    if (showPurgeTileSourceDialog) {
        PurgeTileSourceDialog(onDismiss = { showPurgeTileSourceDialog = false })
    }

    if (showEditWaypointDialog != null) {
        EditWaypointDialog(
            waypoint = showEditWaypointDialog ?: return, // Safe call
            onSendClicked = { waypoint ->
                Logger.d { "User clicked send waypoint ${waypoint.id}" }
                showEditWaypointDialog = null

                val newId =
                    if (waypoint.id == 0) mapViewModel.generatePacketId() ?: return@EditWaypointDialog else waypoint.id
                val newName = if (waypoint.name.isNullOrEmpty()) "Dropped Pin" else waypoint.name
                val newExpire = if ((waypoint.expire ?: 0) == 0) Int.MAX_VALUE else (waypoint.expire ?: Int.MAX_VALUE)
                val newLockedTo = if ((waypoint.locked_to ?: 0) != 0) mapViewModel.myNodeNum ?: 0 else 0
                val newIcon = if ((waypoint.icon ?: 0) == 0) 128205 else waypoint.icon

                mapViewModel.sendWaypoint(
                    waypoint.copy(
                        id = newId,
                        name = newName,
                        expire = newExpire,
                        locked_to = newLockedTo,
                        icon = newIcon,
                    ),
                )
            },
            onDeleteClicked = { waypoint ->
                Logger.d { "User clicked delete waypoint ${waypoint.id}" }
                showEditWaypointDialog = null
                showDeleteMarkerDialog(waypoint)
            },
            onDismissRequest = {
                Logger.d { "User clicked cancel marker edit dialog" }
                showEditWaypointDialog = null
            },
        )
    }
}

@Composable
private fun MapStyleDialog(selectedMapStyle: Int, onDismiss: () -> Unit, onSelectMapStyle: (Int) -> Unit) {
    val selected = remember { mutableStateOf(selectedMapStyle) }

    MapsDialog(onDismiss = onDismiss) {
        CustomTileSource.mTileSources.values.forEachIndexed { index, style ->
            ListItem(
                text = style,
                trailingIcon = if (index == selected.value) Icons.Rounded.Check else null,
                onClick = {
                    selected.value = index
                    onSelectMapStyle(index)
                    onDismiss()
                },
            )
        }
    }
}

private enum class CacheManagerOption(val label: StringResource) {
    CurrentCacheSize(label = Res.string.map_cache_size),
    DownloadRegion(label = Res.string.map_download_region),
    ClearTiles(label = Res.string.map_clear_tiles),
    Cancel(label = Res.string.cancel),
}

@Composable
private fun CacheManagerDialog(onClickOption: (CacheManagerOption) -> Unit, onDismiss: () -> Unit) {
    MapsDialog(title = stringResource(Res.string.map_offline_manager), onDismiss = onDismiss) {
        CacheManagerOption.entries.forEach { option ->
            ListItem(text = stringResource(option.label), trailingIcon = null) {
                onClickOption(option)
                onDismiss()
            }
        }
    }
}

@Composable
private fun CacheInfoDialog(mapView: MapView, onDismiss: () -> Unit) {
    val (cacheCapacity, currentCacheUsage) =
        remember(mapView) {
            val cacheManager = CacheManager(mapView)
            cacheManager.cacheCapacity() to cacheManager.currentCacheUsage()
        }

    MapsDialog(
        title = stringResource(Res.string.map_cache_manager),
        onDismiss = onDismiss,
        negativeButton = { TextButton(onClick = { onDismiss() }) { Text(text = stringResource(Res.string.close)) } },
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text =
            stringResource(
                Res.string.map_cache_info,
                cacheCapacity / (1024.0 * 1024.0),
                currentCacheUsage / (1024.0 * 1024.0),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PurgeTileSourceDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cache = SqlTileWriterExt()

    val sourceList by derivedStateOf { cache.sources.map { it.source as String } }

    val selected = remember { mutableStateListOf<Int>() }

    MapsDialog(
        title = stringResource(Res.string.map_tile_source),
        positiveButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    selected.forEach { selectedIndex ->
                        val source = sourceList[selectedIndex]
                        scope.launch {
                            context.showToast(
                                if (cache.purgeCache(source)) {
                                    getString(Res.string.map_purge_success, source)
                                } else {
                                    getString(Res.string.map_purge_fail)
                                },
                            )
                        }
                    }

                    onDismiss()
                },
            ) {
                Text(text = stringResource(Res.string.clear))
            }
        },
        negativeButton = { TextButton(onClick = onDismiss) { Text(text = stringResource(Res.string.cancel)) } },
        onDismiss = onDismiss,
    ) {
        sourceList.forEachIndexed { index, source ->
            val isSelected = selected.contains(index)
            BasicListItem(
                text = source,
                trailingContent = { Checkbox(checked = isSelected, onCheckedChange = {}) },
                onClick = {
                    if (isSelected) {
                        selected.remove(index)
                    } else {
                        selected.add(index)
                    }
                },
            ) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapsDialog(
    title: String? = null,
    onDismiss: () -> Unit,
    positiveButton: (@Composable () -> Unit)? = null,
    negativeButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.wrapContentWidth().wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column {
                title?.let {
                    Text(
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                        text = it,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
                if (positiveButton != null || negativeButton != null) {
                    Row(Modifier.align(Alignment.End)) {
                        positiveButton?.invoke()
                        negativeButton?.invoke()
                    }
                }
            }
        }
    }
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val TRACEROUTE_OFFSET_METERS = 100.0
private const val TRACEROUTE_SINGLE_POINT_ZOOM = 12.0
private const val TRACEROUTE_ZOOM_OUT_LEVELS = 0.5
private const val WAYPOINT_ZOOM = 15.0

private fun Double.toRad(): Double = Math.toRadians(this)

private fun bearingRad(from: GeoPoint, to: GeoPoint): Double {
    val lat1 = from.latitude.toRad()
    val lat2 = to.latitude.toRad()
    val dLon = (to.longitude - from.longitude).toRad()
    return atan2(sin(dLon) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon))
}

private fun GeoPoint.offsetPoint(headingRad: Double, offsetMeters: Double): GeoPoint {
    val distanceByRadius = offsetMeters / EARTH_RADIUS_METERS
    val lat1 = latitude.toRad()
    val lon1 = longitude.toRad()
    val lat2 = asin(sin(lat1) * cos(distanceByRadius) + cos(lat1) * sin(distanceByRadius) * cos(headingRad))
    val lon2 =
        lon1 + atan2(sin(headingRad) * sin(distanceByRadius) * cos(lat1), cos(distanceByRadius) - sin(lat1) * sin(lat2))
    return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

private fun offsetPolyline(
    points: List<GeoPoint>,
    offsetMeters: Double,
    headingReferencePoints: List<GeoPoint> = points,
    sideMultiplier: Double = 1.0,
): List<GeoPoint> {
    val headingPoints = headingReferencePoints.takeIf { it.size >= 2 } ?: points
    if (points.size < 2 || headingPoints.size < 2 || offsetMeters == 0.0) return points

    val headings =
        headingPoints.mapIndexed { index, _ ->
            when (index) {
                0 -> bearingRad(headingPoints[0], headingPoints[1])
                headingPoints.lastIndex ->
                    bearingRad(headingPoints[headingPoints.lastIndex - 1], headingPoints[headingPoints.lastIndex])

                else -> bearingRad(headingPoints[index - 1], headingPoints[index + 1])
            }
        }

    return points.mapIndexed { index, point ->
        val heading = headings[index.coerceIn(0, headings.lastIndex)]
        val perpendicularHeading = heading + (Math.PI / 2 * sideMultiplier)
        point.offsetPoint(perpendicularHeading, abs(offsetMeters))
    }
}
