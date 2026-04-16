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
package org.meshtastic.app.map

import android.Manifest
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.R
import org.meshtastic.app.map.cluster.RadiusMarkerClusterer
import org.meshtastic.app.map.component.CacheLayout
import org.meshtastic.app.map.component.DownloadButton
import org.meshtastic.app.map.component.EditWaypointDialog
import org.meshtastic.app.map.model.CustomTileSource
import org.meshtastic.app.map.model.MarkerWithLabel
import org.meshtastic.core.common.gpsDisabled
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.calculating
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.clear
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.delete_for_everyone
import org.meshtastic.core.resources.delete_for_me
import org.meshtastic.core.resources.expires
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.last_heard_filter_label
import org.meshtastic.core.resources.location_disabled
import org.meshtastic.core.resources.map_cache_info
import org.meshtastic.core.resources.map_cache_manager
import org.meshtastic.core.resources.map_cache_size
import org.meshtastic.core.resources.map_cache_tiles
import org.meshtastic.core.resources.map_clear_tiles
import org.meshtastic.core.resources.map_download_complete
import org.meshtastic.core.resources.map_download_errors
import org.meshtastic.core.resources.map_download_region
import org.meshtastic.core.resources.map_node_popup_details
import org.meshtastic.core.resources.map_offline_manager
import org.meshtastic.core.resources.map_purge_fail
import org.meshtastic.core.resources.map_purge_success
import org.meshtastic.core.resources.map_style_selection
import org.meshtastic.core.resources.map_subDescription
import org.meshtastic.core.resources.map_tile_source
import org.meshtastic.core.resources.only_favorites
import org.meshtastic.core.resources.show_precision_circle
import org.meshtastic.core.resources.show_waypoints
import org.meshtastic.core.resources.waypoint_delete
import org.meshtastic.core.resources.you
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.icon.Check
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.Layers
import org.meshtastic.core.ui.icon.Lens
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PinDrop
import org.meshtastic.core.ui.util.formatAgo
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.feature.map.BaseMapViewModel.MapFilterState
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.feature.map.component.MapButton
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
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
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlin.math.roundToInt

private fun MapView.updateMarkers(
    nodeMarkers: List<MarkerWithLabel>,
    waypointMarkers: List<MarkerWithLabel>,
    nodeClusterer: RadiusMarkerClusterer,
) {
    Logger.d { "Showing on map: ${nodeMarkers.size} nodes ${waypointMarkers.size} waypoints" }

    overlays.removeAll { overlay ->
        overlay is MarkerWithLabel || (overlay is Marker && overlay !in nodeClusterer.items)
    }

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
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
fun MapView(
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel = koinViewModel(),
    navigateToNodeDetails: (Int) -> Unit,
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
                    getBitmapFromVectorDrawable(context, R.drawable.ic_map_location_dot)?.let {
                        setPersonIcon(it)
                        setPersonAnchor(0.5f, 0.5f)
                    }
                    getBitmapFromVectorDrawable(context, R.drawable.ic_map_navigation)?.let {
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

    // Keep screen on while location tracking is active
    LaunchedEffect(myLocationOverlay) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        if (myLocationOverlay != null) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val nodes by mapViewModel.nodes.collectAsStateWithLifecycle()
    val waypoints by mapViewModel.waypoints.collectAsStateWithLifecycle(emptyMap())
    val selectedWaypointId by mapViewModel.selectedWaypointId.collectAsStateWithLifecycle()
    val myId by mapViewModel.myId.collectAsStateWithLifecycle()

    LaunchedEffect(selectedWaypointId, waypoints) {
        if (selectedWaypointId != null && waypoints.containsKey(selectedWaypointId)) {
            waypoints[selectedWaypointId]?.waypoint?.let { pt ->
                val geoPoint = GeoPoint((pt.latitude_i ?: 0) * 1e-7, (pt.longitude_i ?: 0) * 1e-7)
                map.controller.setCenter(geoPoint)
                map.controller.setZoom(WAYPOINT_ZOOM)
            }
        }
    }

    val markerIcon = remember { AppCompatResources.getDrawable(context, R.drawable.ic_location_on) }

    fun MapView.onNodesChanged(nodes: Collection<Node>): List<MarkerWithLabel> {
        val nodesWithPosition = nodes.filter { it.validPosition != null }
        val ourNode = mapViewModel.ourNodeInfo.value
        val displayUnits = mapViewModel.config.display?.units ?: DisplayUnits.METRIC
        val mapFilterStateValue = mapViewModel.mapFilterStateFlow.value // Access mapFilterState directly
        return nodesWithPosition.mapNotNull { node ->
            if (mapFilterStateValue.onlyFavorites && !node.isFavorite && !node.equals(ourNode)) {
                return@mapNotNull null
            }
            if (
                mapFilterStateValue.lastHeardFilter.seconds != 0L &&
                (nowSeconds - node.lastHeard) > mapFilterStateValue.lastHeardFilter.seconds &&
                node.num != ourNode?.num
            ) {
                return@mapNotNull null
            }

            val (p, u) = node.position to node.user
            val nodePosition = GeoPoint(node.latitude, node.longitude)
            MarkerWithLabel(mapView = this, label = "${u.short_name} ${formatAgo(p.time)}").apply {
                id = u.id
                title = u.long_name
                snippet =
                    getString(
                        Res.string.map_node_popup_details,
                        node.gpsString(),
                        formatAgo(node.lastHeard),
                        formatAgo(p.time),
                        if (node.batteryStr != "") node.batteryStr else "?",
                    )
                ourNode?.distanceStr(node, displayUnits)?.let { dist ->
                    ourNode.bearing(node)?.let { bearing ->
                        subDescription = getString(Res.string.map_subDescription, bearing, dist)
                    }
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                position = nodePosition
                icon = markerIcon
                setNodeColors(node.colors)
                if (!mapFilterStateValue.showPrecisionCircle) {
                    setPrecisionBits(0)
                } else {
                    setPrecisionBits(p.precision_bits)
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
        builder.setTitle(getString(Res.string.waypoint_delete))
        builder.setNeutralButton(getString(Res.string.cancel)) { _, _ ->
            Logger.d { "User canceled marker delete dialog" }
        }
        builder.setNegativeButton(getString(Res.string.delete_for_me)) { _, _ ->
            Logger.d { "User deleted waypoint ${waypoint.id} for me" }
            mapViewModel.deleteWaypoint(waypoint.id)
        }
        if (waypoint.locked_to in setOf(0, mapViewModel.myNodeNum ?: 0) && isConnected) {
            builder.setPositiveButton(getString(Res.string.delete_for_everyone)) { _, _ ->
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
        val waypoint = waypoints[id]?.waypoint ?: return
        // edit only when unlocked or lockedTo myNodeNum
        if (waypoint.locked_to in setOf(0, mapViewModel.myNodeNum ?: 0) && isConnected) {
            showEditWaypointDialog = waypoint
        } else {
            showDeleteMarkerDialog(waypoint)
        }
    }

    fun getUsername(id: String?) = if (id == DataPacket.ID_LOCAL || (myId != null && id == myId)) {
        getString(Res.string.you)
    } else {
        mapViewModel.getUser(id).long_name
    }

    @Suppress("MagicNumber")
    fun MapView.onWaypointChanged(waypoints: Collection<DataPacket>, selectedWaypointId: Int?): List<MarkerWithLabel> {
        return waypoints.mapNotNull { waypoint ->
            val pt = waypoint.waypoint ?: return@mapNotNull null
            if (!mapFilterState.showWaypoints) return@mapNotNull null // Use collected mapFilterState
            val lock = if (pt.locked_to != 0) "\uD83D\uDD12" else ""
            val time = DateFormatter.formatDateTime(waypoint.time)
            val label = pt.name + " " + formatAgo((waypoint.time / 1000).toInt())
            val emoji = String(Character.toChars(if (pt.icon == 0) 128205 else pt.icon))
            val now = nowMillis
            val expireTimeMillis = pt.expire * 1000L
            val expireTimeStr =
                when {
                    pt.expire == 0 || pt.expire == Int.MAX_VALUE -> "Never"
                    expireTimeMillis <= now -> "Expired"
                    else -> DateFormatter.formatRelativeTime(expireTimeMillis)
                }
            MarkerWithLabel(this, label, emoji).apply {
                id = "${pt.id}"
                title = "${pt.name} (${getUsername(waypoint.from)}$lock)"
                snippet = "[$time] ${pt.description}  " + getString(Res.string.expires) + ": $expireTimeStr"
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
        cacheEstimate = getString(Res.string.map_cache_tiles, tileCount)
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
        modifier = modifier,
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
                    with(mapView) {
                        updateMarkers(
                            onNodesChanged(nodes),
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
                MapControlsOverlay(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    onToggleFilterMenu = { mapFilterExpanded = true },
                    filterDropdownContent = {
                        FdroidMainMapFilterDropdown(
                            expanded = mapFilterExpanded,
                            onDismissRequest = { mapFilterExpanded = false },
                            mapFilterState = mapFilterState,
                            mapViewModel = mapViewModel,
                        )
                    },
                    mapTypeContent = {
                        MapButton(
                            icon = MeshtasticIcons.Layers,
                            contentDescription = stringResource(Res.string.map_style_selection),
                            onClick = { showMapStyleDialog = true },
                        )
                    },
                    isLocationTrackingEnabled = myLocationOverlay != null,
                    onToggleLocationTracking = {
                        if (locationPermissionsState.allPermissionsGranted) {
                            map.toggleMyLocation()
                        } else {
                            triggerLocationToggleAfterPermission = true
                            locationPermissionsState.launchMultiplePermissionRequest()
                        }
                    },
                )
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

                val newId = if (waypoint.id == 0) mapViewModel.generatePacketId() else waypoint.id
                val newName = if (waypoint.name.isNullOrEmpty()) "Dropped Pin" else waypoint.name
                val newExpire = if (waypoint.expire == 0) Int.MAX_VALUE else waypoint.expire
                val newLockedTo = if (waypoint.locked_to != 0) mapViewModel.myNodeNum ?: 0 else 0
                val newIcon = if (waypoint.icon == 0) 128205 else waypoint.icon

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

/** F-Droid main map filter dropdown — favorites, waypoints, precision circle, and last-heard time filter slider. */
@Composable
private fun FdroidMainMapFilterDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    mapFilterState: MapFilterState,
    mapViewModel: MapViewModel,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        DropdownMenuItem(
            text = {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MeshtasticIcons.Favorite,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(text = stringResource(Res.string.only_favorites), modifier = Modifier.weight(1f))
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MeshtasticIcons.PinDrop,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(text = stringResource(Res.string.show_waypoints), modifier = Modifier.weight(1f))
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MeshtasticIcons.Lens,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(text = stringResource(Res.string.show_precision_circle), modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = mapFilterState.showPrecisionCircle,
                        onCheckedChange = { mapViewModel.toggleShowPrecisionCircleOnMap() },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            },
            onClick = { mapViewModel.toggleShowPrecisionCircleOnMap() },
        )
        HorizontalDivider()
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            val filterOptions = LastHeardFilter.entries
            val selectedIndex = filterOptions.indexOf(mapFilterState.lastHeardFilter)
            var sliderPosition by remember(selectedIndex) { mutableFloatStateOf(selectedIndex.toFloat()) }
            Text(
                text =
                stringResource(
                    Res.string.last_heard_filter_label,
                    stringResource(mapFilterState.lastHeardFilter.label),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = {
                    val newIndex = sliderPosition.roundToInt().coerceIn(0, filterOptions.size - 1)
                    mapViewModel.setLastHeardFilter(filterOptions[newIndex])
                },
                valueRange = 0f..(filterOptions.size - 1).toFloat(),
                steps = filterOptions.size - 2,
            )
        }
    }
}

@Composable
private fun MapStyleDialog(selectedMapStyle: Int, onDismiss: () -> Unit, onSelectMapStyle: (Int) -> Unit) {
    val selected = remember { mutableStateOf(selectedMapStyle) }

    MapsDialog(onDismiss = onDismiss) {
        CustomTileSource.mTileSources.values.forEachIndexed { index, style ->
            ListItem(
                text = style,
                trailingIcon = if (index == selected.value) MeshtasticIcons.Check else null,
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
        val capacityMb = (cacheCapacity / (1024 * 1024)).toLong()
        val usageMb = (currentCacheUsage / (1024 * 1024)).toLong()
        Text(modifier = Modifier.padding(16.dp), text = stringResource(Res.string.map_cache_info, capacityMb, usageMb))
    }
}

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

private const val WAYPOINT_ZOOM = 15.0
