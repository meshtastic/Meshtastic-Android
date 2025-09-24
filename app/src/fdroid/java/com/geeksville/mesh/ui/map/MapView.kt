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

import android.Manifest // Added for Accompanist
import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geeksville.mesh.MeshProtos.Waypoint
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.gpsDisabled
import com.geeksville.mesh.android.hasGps
import com.geeksville.mesh.copy
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.map.components.CacheLayout
import com.geeksville.mesh.ui.map.components.DownloadButton
import com.geeksville.mesh.ui.map.components.EditWaypointDialog
import com.geeksville.mesh.ui.map.components.MapButton
import com.geeksville.mesh.util.SqlTileWriterExt
import com.geeksville.mesh.util.addCopyright
import com.geeksville.mesh.util.addScaleBarOverlay
import com.geeksville.mesh.util.createLatLongGrid
import com.geeksville.mesh.waypoint
import com.google.accompanist.permissions.ExperimentalPermissionsApi // Added for Accompanist
import com.google.accompanist.permissions.rememberMultiplePermissionsState // Added for Accompanist
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.formatAgo
import org.meshtastic.core.strings.R
import org.meshtastic.feature.map.cluster.RadiusMarkerClusterer
import org.meshtastic.feature.map.model.CustomTileSource
import org.meshtastic.feature.map.model.MarkerWithLabel
import org.meshtastic.feature.map.zoomIn
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
import java.text.DateFormat

@Composable
private fun MapView.UpdateMarkers(
    nodeMarkers: List<MarkerWithLabel>,
    waypointMarkers: List<MarkerWithLabel>,
    nodeClusterer: RadiusMarkerClusterer,
) {
    debug("Showing on map: ${nodeMarkers.size} nodes ${waypointMarkers.size} waypoints")
    overlays.removeAll { it is MarkerWithLabel }
    // overlays.addAll(nodeMarkers + waypointMarkers)
    overlays.addAll(waypointMarkers)
    nodeClusterer.items.clear()
    nodeClusterer.items.addAll(nodeMarkers)
    nodeClusterer.invalidate()
}

//    private fun addWeatherLayer() {
//        if (map.tileProvider.tileSource.name()
//                .equals(CustomTileSource.getTileSource("ESRI World TOPO").name())
//        ) {
//            val layer = TilesOverlay(
//                MapTileProviderBasic(
//                    activity,
//                    CustomTileSource.OPENWEATHER_RADAR
//                ), context
//            )
//            layer.loadingBackgroundColor = Color.TRANSPARENT
//            layer.loadingLineColor = Color.TRANSPARENT
//            map.overlayManager.add(layer)
//        }
//    }

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

private fun Context.purgeTileSource(onResult: (String) -> Unit) {
    val cache = SqlTileWriterExt()
    val builder = MaterialAlertDialogBuilder(this)
    builder.setTitle(R.string.map_tile_source)
    val sources = cache.sources
    val sourceList = mutableListOf<String>()
    for (i in sources.indices) {
        sourceList.add(sources[i].source as String)
    }
    val selected: BooleanArray? = null
    val selectedList = mutableListOf<Int>()
    builder.setMultiChoiceItems(sourceList.toTypedArray(), selected) { _, i, b ->
        if (b) {
            selectedList.add(i)
        } else {
            selectedList.remove(i)
        }
    }
    builder.setPositiveButton(R.string.clear) { _, _ ->
        for (x in selectedList) {
            val item = sources[x]
            val b = cache.purgeCache(item.source)
            onResult(
                if (b) {
                    getString(R.string.map_purge_success, item.source)
                } else {
                    getString(R.string.map_purge_fail)
                },
            )
        }
    }
    builder.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
    builder.show()
}

/**
 * Main composable for displaying the map view, including nodes, waypoints, and user location. It handles user
 * interactions for map manipulation, filtering, and offline caching.
 *
 * @param model The [UIViewModel] providing data and state for the map.
 * @param navigateToNodeDetails Callback to navigate to the details screen of a selected node.
 */
@OptIn(ExperimentalPermissionsApi::class) // Added for Accompanist
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
fun MapView(
    uiViewModel: UIViewModel = viewModel(),
    mapViewModel: MapViewModel = hiltViewModel(),
    navigateToNodeDetails: (Int) -> Unit,
) {
    var mapFilterExpanded by remember { mutableStateOf(false) }

    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val isConnected by uiViewModel.isConnectedStateFlow.collectAsStateWithLifecycle()

    var cacheEstimate by remember { mutableStateOf("") }

    var zoomLevelMin by remember { mutableDoubleStateOf(0.0) }
    var zoomLevelMax by remember { mutableDoubleStateOf(0.0) }

    var downloadRegionBoundingBox: BoundingBox? by remember { mutableStateOf(null) }
    var myLocationOverlay: MyLocationNewOverlay? by remember { mutableStateOf(null) }

    var showDownloadButton: Boolean by remember { mutableStateOf(false) }
    var showEditWaypointDialog by remember { mutableStateOf<Waypoint?>(null) }
    var showCurrentCacheInfo by remember { mutableStateOf(false) }

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
        debug("mapStyleId from prefs: $id")
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
    val map = rememberMapViewWithLifecycle(initialCameraView, loadOnlineTileSourceBase())

    val nodeClusterer = remember { RadiusMarkerClusterer(context) }

    fun MapView.toggleMyLocation() {
        if (context.gpsDisabled()) {
            debug("Telling user we need location turned on for MyLocationNewOverlay")
            uiViewModel.showSnackBar(R.string.location_disabled)
            return
        }
        debug("user clicked MyLocationNewOverlay ${myLocationOverlay == null}")
        if (myLocationOverlay == null) {
            myLocationOverlay =
                MyLocationNewOverlay(this).apply {
                    enableMyLocation()
                    enableFollowLocation()
                    getBitmapFromVectorDrawable(context, com.geeksville.mesh.R.drawable.ic_map_location_dot_24)?.let {
                        setPersonIcon(it)
                        setPersonAnchor(0.5f, 0.5f)
                    }
                    getBitmapFromVectorDrawable(context, com.geeksville.mesh.R.drawable.ic_map_navigation_24)?.let {
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

    val markerIcon = remember {
        AppCompatResources.getDrawable(context, com.geeksville.mesh.R.drawable.ic_baseline_location_on_24)
    }

    fun MapView.onNodesChanged(nodes: Collection<Node>): List<MarkerWithLabel> {
        val nodesWithPosition = nodes.filter { it.validPosition != null }
        val ourNode = uiViewModel.ourNodeInfo.value
        val displayUnits = uiViewModel.config.display.units
        val mapFilterStateValue = mapViewModel.mapFilterStateFlow.value // Access mapFilterState directly
        return nodesWithPosition.mapNotNull { node ->
            if (mapFilterStateValue.onlyFavorites && !node.isFavorite && !node.equals(ourNode)) {
                return@mapNotNull null
            }

            val (p, u) = node.position to node.user
            val nodePosition = GeoPoint(node.latitude, node.longitude)
            MarkerWithLabel(mapView = this, label = "${u.shortName} ${formatAgo(p.time)}").apply {
                id = u.id
                title = u.longName
                snippet =
                    context.getString(
                        R.string.map_node_popup_details,
                        node.gpsString(),
                        formatAgo(node.lastHeard),
                        formatAgo(p.time),
                        if (node.batteryStr != "") node.batteryStr else "?",
                    )
                ourNode?.distanceStr(node, displayUnits)?.let { dist ->
                    subDescription = context.getString(R.string.map_subDescription, ourNode.bearing(node), dist)
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                position = nodePosition
                icon = markerIcon
                setNodeColors(node.colors)
                if (!mapFilterStateValue.showPrecisionCircle) {
                    setPrecisionBits(0)
                } else {
                    setPrecisionBits(p.precisionBits)
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
        builder.setTitle(R.string.waypoint_delete)
        builder.setNeutralButton(R.string.cancel) { _, _ -> debug("User canceled marker delete dialog") }
        builder.setNegativeButton(R.string.delete_for_me) { _, _ ->
            debug("User deleted waypoint ${waypoint.id} for me")
            uiViewModel.deleteWaypoint(waypoint.id)
        }
        if (waypoint.lockedTo in setOf(0, uiViewModel.myNodeNum ?: 0) && isConnected) {
            builder.setPositiveButton(R.string.delete_for_everyone) { _, _ ->
                debug("User deleted waypoint ${waypoint.id} for everyone")
                uiViewModel.sendWaypoint(waypoint.copy { expire = 1 })
                uiViewModel.deleteWaypoint(waypoint.id)
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
        debug("marker long pressed id=$id")
        val waypoint = waypoints[id]?.data?.waypoint ?: return
        // edit only when unlocked or lockedTo myNodeNum
        if (waypoint.lockedTo in setOf(0, uiViewModel.myNodeNum ?: 0) && isConnected) {
            showEditWaypointDialog = waypoint
        } else {
            showDeleteMarkerDialog(waypoint)
        }
    }

    fun getUsername(id: String?) = if (id == DataPacket.ID_LOCAL) {
        context.getString(R.string.you)
    } else {
        uiViewModel.getUser(id).longName
    }

    @Composable
    @Suppress("MagicNumber")
    fun MapView.onWaypointChanged(waypoints: Collection<Packet>): List<MarkerWithLabel> {
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        return waypoints.mapNotNull { waypoint ->
            val pt = waypoint.data.waypoint ?: return@mapNotNull null
            if (!mapFilterState.showWaypoints) return@mapNotNull null // Use collected mapFilterState
            val lock = if (pt.lockedTo != 0) "\uD83D\uDD12" else ""
            val time = dateFormat.format(waypoint.received_time)
            val label = pt.name + " " + formatAgo((waypoint.received_time / 1000).toInt())
            val emoji = String(Character.toChars(if (pt.icon == 0) 128205 else pt.icon))
            val timeLeft = pt.expire * 1000L - System.currentTimeMillis()
            val expireTimeStr =
                when {
                    pt.expire == 0 || pt.expire == Int.MAX_VALUE -> "Never"
                    timeLeft <= 0 -> "Expired"
                    timeLeft < 60_000 -> "${timeLeft / 1000} seconds"
                    timeLeft < 3_600_000 -> "${timeLeft / 60_000} minute${if (timeLeft / 60_000 != 1L) "s" else ""}"
                    timeLeft < 86_400_000 -> {
                        val hours = (timeLeft / 3_600_000).toInt()
                        val minutes = ((timeLeft % 3_600_000) / 60_000).toInt()
                        if (minutes >= 30) {
                            "${hours + 1} hour${if (hours + 1 != 1) "s" else ""}"
                        } else if (minutes > 0) {
                            "$hours hour${if (hours != 1) "s" else ""}, $minutes minute${if (minutes != 1) "s" else ""}"
                        } else {
                            "$hours hour${if (hours != 1) "s" else ""}"
                        }
                    }

                    else -> "${timeLeft / 86_400_000} day${if (timeLeft / 86_400_000 != 1L) "s" else ""}"
                }
            MarkerWithLabel(this, label, emoji).apply {
                id = "${pt.id}"
                title = "${pt.name} (${getUsername(waypoint.data.from)}$lock)"
                snippet = "[$time] ${pt.description}  " + stringResource(R.string.expires) + ": $expireTimeStr"
                position = GeoPoint(pt.latitudeI * 1e-7, pt.longitudeI * 1e-7)
                setVisible(false) // This seems to be always false, was this intended?
                setOnLongClickListener {
                    showMarkerLongPressDialog(pt.id)
                    true
                }
            }
        }
    }

    LaunchedEffect(showCurrentCacheInfo) {
        if (!showCurrentCacheInfo) return@LaunchedEffect
        uiViewModel.showSnackBar(R.string.calculating)
        val cacheManager = CacheManager(map)
        val cacheCapacity = cacheManager.cacheCapacity()
        val currentCacheUsage = cacheManager.currentCacheUsage()

        val mapCacheInfoText =
            context.getString(
                R.string.map_cache_info,
                cacheCapacity / (1024.0 * 1024.0),
                currentCacheUsage / (1024.0 * 1024.0),
            )

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.map_cache_manager)
            .setMessage(mapCacheInfoText)
            .setPositiveButton(R.string.close) { dialog, _ ->
                showCurrentCacheInfo = false
                dialog.dismiss()
            }
            .show()
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
                    showEditWaypointDialog = waypoint {
                        latitudeI = (p.latitude * 1e7).toInt()
                        longitudeI = (p.longitude * 1e7).toInt()
                    }
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

    with(map) { UpdateMarkers(onNodesChanged(nodes), onWaypointChanged(waypoints.values), nodeClusterer) }

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
        cacheEstimate = context.getString(R.string.map_cache_tiles, tileCount)
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
                        uiViewModel.showSnackBar(R.string.map_download_complete)
                        writer.onDetach()
                    },
                    onTaskFailed = { errors ->
                        uiViewModel.showSnackBar(context.getString(R.string.map_download_errors, errors))
                        writer.onDetach()
                    },
                ),
            )
        } catch (ex: TileSourcePolicyException) {
            debug("Tile source does not allow archiving: ${ex.message}")
        } catch (ex: Exception) {
            debug("Tile source exception: ${ex.message}")
        }
    }

    fun showMapStyleDialog() {
        val builder = MaterialAlertDialogBuilder(context)
        val mapStyles: Array<CharSequence> = CustomTileSource.mTileSources.values.toTypedArray()

        val mapStyleInt = mapViewModel.mapStyleId
        builder.setSingleChoiceItems(mapStyles, mapStyleInt) { dialog, which ->
            debug("Set mapStyleId pref to $which")
            mapViewModel.mapStyleId = which
            dialog.dismiss()
            map.setTileSource(loadOnlineTileSourceBase())
        }
        val dialog = builder.create()
        dialog.show()
    }

    fun Context.showCacheManagerDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.map_offline_manager)
            .setItems(
                arrayOf<CharSequence>(
                    getString(R.string.map_cache_size),
                    getString(R.string.map_download_region),
                    getString(R.string.map_clear_tiles),
                    getString(R.string.cancel),
                ),
            ) { dialog, which ->
                when (which) {
                    0 -> showCurrentCacheInfo = true
                    1 -> {
                        map.generateBoxOverlay()
                        dialog.dismiss()
                    }

                    2 -> purgeTileSource { uiViewModel.showSnackBar(it) }
                    else -> dialog.dismiss()
                }
            }
            .show()
    }

    Scaffold(
        floatingActionButton = {
            DownloadButton(showDownloadButton && downloadRegionBoundingBox == null) { context.showCacheManagerDialog() }
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
                update = { mapView -> mapView.drawOverlays() }, // Renamed map to mapView to avoid conflict
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
                        onClick = ::showMapStyleDialog,
                        icon = Icons.Outlined.Layers,
                        contentDescription = R.string.map_style_selection,
                    )
                    Box(modifier = Modifier) {
                        MapButton(
                            onClick = { mapFilterExpanded = true },
                            icon = Icons.Outlined.Tune,
                            contentDescription = R.string.map_filter,
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
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 8.dp),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.only_favorites),
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
                                            imageVector = Icons.Default.PinDrop,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 8.dp),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.show_waypoints),
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
                                            imageVector = Icons.Default.Lens,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 8.dp),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.show_precision_circle),
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
                                Icons.Default.LocationDisabled
                            },
                            contentDescription = stringResource(R.string.toggle_my_position),
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

    if (showEditWaypointDialog != null) {
        EditWaypointDialog(
            waypoint = showEditWaypointDialog ?: return, // Safe call
            onSendClicked = { waypoint ->
                debug("User clicked send waypoint ${waypoint.id}")
                showEditWaypointDialog = null
                uiViewModel.sendWaypoint(
                    waypoint.copy {
                        if (id == 0) id = uiViewModel.generatePacketId() ?: return@EditWaypointDialog
                        if (name == "") name = "Dropped Pin"
                        if (expire == 0) expire = Int.MAX_VALUE
                        lockedTo = if (waypoint.lockedTo != 0) uiViewModel.myNodeNum ?: 0 else 0
                        if (waypoint.icon == 0) icon = 128205
                    },
                )
            },
            onDeleteClicked = { waypoint ->
                debug("User clicked delete waypoint ${waypoint.id}")
                showEditWaypointDialog = null
                showDeleteMarkerDialog(waypoint)
            },
            onDismissRequest = {
                debug("User clicked cancel marker edit dialog")
                showEditWaypointDialog = null
            },
        )
    }
}
