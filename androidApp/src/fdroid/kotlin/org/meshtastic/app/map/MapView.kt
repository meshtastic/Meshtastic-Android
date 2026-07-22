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
package org.meshtastic.app.map

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.R
import org.meshtastic.app.map.cluster.RadiusMarkerClusterer
import org.meshtastic.app.map.component.CacheLayout
import org.meshtastic.app.map.component.CustomMapLayersSheet
import org.meshtastic.app.map.component.DownloadButton
import org.meshtastic.app.map.model.CustomTileSource
import org.meshtastic.app.map.model.MarkerWithLabel
import org.meshtastic.app.map.offline.BurningManOsmDroidTileProvider
import org.meshtastic.core.common.gpsDisabled
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.geofence.toGeofence
import org.meshtastic.core.model.isLocked
import org.meshtastic.core.model.isModifiableBy
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.calculating
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.clear
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.delete_for_everyone
import org.meshtastic.core.resources.delete_for_me
import org.meshtastic.core.resources.expires
import org.meshtastic.core.resources.geofence
import org.meshtastic.core.resources.geofence_box_author_confirm
import org.meshtastic.core.resources.geofence_box_author_hint_viewport
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.last_heard_filter_label
import org.meshtastic.core.resources.location_disabled
import org.meshtastic.core.resources.manage_map_layers
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
import org.meshtastic.core.resources.now
import org.meshtastic.core.resources.only_favorites
import org.meshtastic.core.resources.show_precision_circle
import org.meshtastic.core.resources.show_waypoints
import org.meshtastic.core.resources.unknown
import org.meshtastic.core.resources.waypoint_delete
import org.meshtastic.core.resources.you
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.icon.Check
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.Layers
import org.meshtastic.core.ui.icon.Lens
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PinDrop
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.formatAgo
import org.meshtastic.core.ui.util.rememberLocationPermissionState
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.feature.map.BaseMapViewModel.MapFilterState
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.feature.map.component.EditWaypointDialog
import org.meshtastic.feature.map.component.MapButton
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.component.SitePlannerParams
import org.meshtastic.feature.map.component.WaypointInfoDialog
import org.meshtastic.feature.map.offline.BurningManPackRuntime
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
import org.meshtastic.proto.BoundingBox as ProtoBoundingBox

/**
 * Marker subclass for waypoint geofence overlays (circles + boxes). Distinguished from the tile-download / geofence
 * authoring rectangle Polygons so [updateMarkers] can rebuild only the waypoint geofences on each refresh. The
 * transient-rectangle cleanups in the download/authoring paths exclude this subclass (`removeAll { it is Polygon && it
 * !is GeofenceOverlayPolygon }`) so persistent waypoint geofences are not wiped.
 */
private class GeofenceOverlayPolygon : Polygon()

private fun MapView.updateMarkers(
    nodeMarkers: List<MarkerWithLabel>,
    waypointMarkers: List<MarkerWithLabel>,
    geofenceOverlays: List<GeofenceOverlayPolygon>,
    nodeClusterer: RadiusMarkerClusterer,
) {
    Logger.d { "Showing on map: ${nodeMarkers.size} nodes ${waypointMarkers.size} waypoints" }

    overlays.removeAll { overlay ->
        overlay is MarkerWithLabel ||
            overlay is GeofenceOverlayPolygon ||
            (overlay is Marker && overlay !in nodeClusterer.items)
    }

    overlays.addAll(geofenceOverlays)
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
@Suppress("CyclomaticComplexMethod", "LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
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

    // Geofence box authoring: when geofenceBoxDraft is non-null the user is positioning a rectangle (reusing the
    // tile-download rectangle machinery) that becomes the draft's bounding box on confirm. Kept distinct from the
    // tile-download box (downloadRegionBoundingBox) so the two rectangle modes never collide.
    var geofenceBoxDraft by remember { mutableStateOf<Waypoint?>(null) }
    var geofenceBoxBoundingBox: BoundingBox? by remember { mutableStateOf(null) }

    var showDownloadButton: Boolean by remember { mutableStateOf(false) }
    var showEditWaypointDialog by remember { mutableStateOf<Waypoint?>(null) }
    var showDeleteWaypointDialog by remember { mutableStateOf<Waypoint?>(null) }
    var showGeofenceInfoDialog by remember { mutableStateOf<Waypoint?>(null) }
    var showCacheManagerDialog by remember { mutableStateOf(false) }
    var showCurrentCacheInfo by remember { mutableStateOf(false) }
    var showPurgeTileSourceDialog by remember { mutableStateOf(false) }
    var showMapStyleDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    val haptic = LocalHapticFeedback.current
    fun performHapticFeedback() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)

    val unknownText = stringResource(Res.string.unknown)
    val nowText = stringResource(Res.string.now)

    // Location permission state (native; recomputed on resume).
    val locationPermission = rememberLocationPermissionState()
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
    val onlineTileProvider = remember(map) { map.tileProvider }
    val burningManRuntime = remember(context.applicationContext) { BurningManPackRuntime.forContext(context) }
    val burningManPack by burningManRuntime.coordinator.selectedPack.collectAsStateWithLifecycle()
    val burningManTileProvider =
        remember(burningManPack?.file) {
            burningManPack?.file?.let { file -> runCatching { BurningManOsmDroidTileProvider(file) }.getOrNull() }
        }

    // Use the opaque app-private pack only inside its validated bounds. Restoring the original provider outside
    // coverage keeps the user's selected online style available, while the local provider has no downloader.
    DisposableEffect(map, onlineTileProvider, burningManTileProvider) {
        fun reconcileTileProvider() {
            val localProvider =
                burningManTileProvider?.takeIf { provider ->
                    map.mapCenter.let { center -> provider.covers(center.latitude, center.longitude) }
                }
            if (localProvider != null) {
                showDownloadButton = false
                if (map.tileProvider !== localProvider) map.setTileProvider(localProvider)
            } else {
                if (map.tileProvider !== onlineTileProvider) map.setTileProvider(onlineTileProvider)
                loadOnlineTileSourceBase()
            }
            map.invalidate()
        }
        val tileProviderListener =
            object : MapListener {
                override fun onScroll(event: ScrollEvent): Boolean {
                    reconcileTileProvider()
                    return false
                }

                override fun onZoom(event: ZoomEvent): Boolean = false
            }
        reconcileTileProvider()
        map.addMapListener(tileProviderListener)
        onDispose {
            map.removeMapListener(tileProviderListener)
            if (map.tileProvider === burningManTileProvider) map.setTileProvider(onlineTileProvider)
        }
    }

    val nodeClusterer = remember { RadiusMarkerClusterer(context) }

    // --- Imported map layers (GeoJSON/KML overlays) — shared model/logic, F-Droid OSMdroid render ---
    val mapLayers by mapViewModel.mapLayers.collectAsStateWithLifecycle()
    val layerRenderer = remember { FdroidMapOverlayRenderer() }
    var showLayersBottomSheet by remember { mutableStateOf(false) }
    var sitePlannerInitial by remember { mutableStateOf<SitePlannerParams?>(null) }
    val ourNodeInfo by mapViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val channelSet by mapViewModel.channelSet.collectAsStateWithLifecycle()

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> mapViewModel.addMapLayer(uri, uri.getFileName(context)) }
            }
        }
    val onAddLayerClicked = {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "application/vnd.google-earth.kml+xml",
                        "application/vnd.google-earth.kmz",
                        "application/vnd.geo+json",
                        "application/geo+json",
                        "application/json",
                    ),
                )
            }
        filePickerLauncher.launch(intent)
    }

    // Draw imported layers on the OSMdroid map, reconciling whenever the list changes; strip them on dispose (the
    // MapView is kept alive by setDestroyMode(false), so leftover overlays would otherwise persist).
    LaunchedEffect(mapLayers) { layerRenderer.reconcile(map, mapLayers, mapViewModel::getInputStreamFromUri) }
    DisposableEffect(Unit) { onDispose { layerRenderer.removeAll(map) } }

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
    LaunchedEffect(locationPermission.isGranted) {
        if (locationPermission.isGranted && triggerLocationToggleAfterPermission) {
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
    val displayUnits by mapViewModel.displayUnits.collectAsStateWithLifecycle()

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
            MarkerWithLabel(mapView = this, label = "${u.short_name} ${formatAgo(p.time, unknownText, nowText)}")
                .apply {
                    id = u.id
                    title = u.long_name
                    snippet =
                        getString(
                            Res.string.map_node_popup_details,
                            node.gpsString(),
                            formatAgo(node.lastHeard, unknownText, nowText),
                            formatAgo(p.time, unknownText, nowText),
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

    fun showMarkerLongPressDialog(id: Int) {
        performHapticFeedback()
        Logger.d { "marker long pressed id=$id" }
        val waypoint = waypoints[id]?.waypoint ?: return
        when {
            // Foreign geofences: read-only view hosting the receiver-local crossing-alert opt-in.
            waypoint.toGeofence() != null && !mapViewModel.isMyWaypoint(id) -> showGeofenceInfoDialog = waypoint

            // edit only when unlocked or locked to us
            waypoint.isModifiableBy(mapViewModel.myNodeNum) && isConnected -> showEditWaypointDialog = waypoint

            else -> showDeleteWaypointDialog = waypoint
        }
    }

    fun getUsername(id: String?) = if (id == NodeAddress.ID_LOCAL || (myId != null && id == myId)) {
        getString(Res.string.you)
    } else {
        mapViewModel.getUser(id).long_name
    }

    @Suppress("MagicNumber")
    fun MapView.onWaypointChanged(waypoints: Collection<DataPacket>, selectedWaypointId: Int?): List<MarkerWithLabel> {
        return waypoints.mapNotNull { waypoint ->
            val pt = waypoint.waypoint ?: return@mapNotNull null
            if (!mapFilterState.showWaypoints) return@mapNotNull null // Use collected mapFilterState
            val lock = if (pt.isLocked) "\uD83D\uDD12" else ""
            val time = DateFormatter.formatDateTime(waypoint.time)
            val label = pt.name + " " + formatAgo((waypoint.time / 1000).toInt(), unknownText, nowText)
            val emoji = String(Character.toChars(if (pt.icon == 0) 128205 else pt.icon))
            val now = nowMillis
            val expireTimeMillis = pt.expire * 1000L
            val expireTimeStr =
                when {
                    pt.expire == 0 || pt.expire == Int.MAX_VALUE -> "Never"
                    expireTimeMillis <= now -> "Expired"
                    else -> DateFormatter.formatRelativeTime(expireTimeMillis)
                }
            // Non-visual cue: the geofence is otherwise only an orange overlay, so surface it in the marker's
            // accessible title for screen-reader and color-challenged users.
            val geofenceLabel = if (pt.toGeofence() != null) " · " + getString(Res.string.geofence) else ""
            MarkerWithLabel(this, label, emoji).apply {
                id = "${pt.id}"
                title = "${pt.name} (${getUsername(waypoint.from)}$lock)$geofenceLabel"
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

    // Builds the orange geofence overlays (a circle for a radius geofence, a 4-point rect for a bounding box) for every
    // displayed waypoint. Uses the shared toGeofence() decoder so the coordinate math stays in one place.
    fun buildGeofenceOverlays(waypoints: Collection<DataPacket>): List<GeofenceOverlayPolygon> {
        if (!mapFilterState.showWaypoints) return emptyList()
        return waypoints.flatMap { packet ->
            val pt = packet.waypoint ?: return@flatMap emptyList()
            val geofence = pt.toGeofence() ?: return@flatMap emptyList()
            buildList {
                geofence.circle?.let { circle ->
                    add(
                        GeofenceOverlayPolygon().apply {
                            points =
                                Polygon.pointsAsCircle(
                                    GeoPoint(circle.centerLat, circle.centerLon),
                                    circle.radiusMeters.toDouble(),
                                )
                            outlinePaint.color = GEOFENCE_OVERLAY_COLOR
                            outlinePaint.strokeWidth = GEOFENCE_STROKE_WIDTH_PX
                            fillPaint.color = GEOFENCE_FILL_COLOR
                        },
                    )
                }
                geofence.box?.let { box ->
                    add(
                        GeofenceOverlayPolygon().apply {
                            points =
                                listOf(
                                    GeoPoint(box.south, box.west),
                                    GeoPoint(box.north, box.west),
                                    GeoPoint(box.north, box.east),
                                    GeoPoint(box.south, box.east),
                                )
                            outlinePaint.color = GEOFENCE_OVERLAY_COLOR
                            outlinePaint.strokeWidth = GEOFENCE_STROKE_WIDTH_PX
                            fillPaint.color = GEOFENCE_FILL_COLOR
                        },
                    )
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
                val enabled = isConnected && downloadRegionBoundingBox == null && geofenceBoxDraft == null

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
        overlays.removeAll { it is Polygon && it !is GeofenceOverlayPolygon }
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

    // Positions the geofence rectangle from the current viewport (reusing the tile-download rectangle approach) and
    // draws an orange preview Polygon. Mirrors generateBoxOverlay(), but stores into geofenceBoxBoundingBox so it
    // never collides with the tile-download box.
    fun MapView.generateGeofenceBoxOverlay() {
        overlays.removeAll { it is Polygon && it !is GeofenceOverlayPolygon }
        geofenceBoxBoundingBox = boundingBox.zoomIn(GEOFENCE_BOX_ZOOM_FACTOR)
        val polygon =
            Polygon().apply {
                points = Polygon.pointsAsRect(geofenceBoxBoundingBox).map { GeoPoint(it.latitude, it.longitude) }
                outlinePaint.color = GEOFENCE_OVERLAY_COLOR
                outlinePaint.strokeWidth = GEOFENCE_STROKE_WIDTH_PX
                fillPaint.color = GEOFENCE_FILL_COLOR
            }
        overlays.add(polygon)
        invalidate()
    }

    val boxOverlayListener =
        object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                when {
                    downloadRegionBoundingBox != null -> event.source.generateBoxOverlay()
                    geofenceBoxDraft != null -> event.source.generateGeofenceBoxOverlay()
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
            DownloadButton(showDownloadButton && downloadRegionBoundingBox == null && geofenceBoxDraft == null) {
                showCacheManagerDialog = true
            }
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
                            buildGeofenceOverlays(waypoints.values),
                            nodeClusterer,
                        )
                    }
                    mapView.drawOverlays()
                }, // Renamed map to mapView to avoid conflict
            )
            if (geofenceBoxDraft != null) {
                GeofenceBoxAuthoringBar(
                    onConfirm = {
                        val draft = geofenceBoxDraft
                        val bb = geofenceBoxBoundingBox
                        if (draft != null && bb != null) {
                            showEditWaypointDialog = draft.copy(bounding_box = bb.toProtoBoundingBox())
                        } else {
                            showEditWaypointDialog = draft
                        }
                        geofenceBoxDraft = null
                        geofenceBoxBoundingBox = null
                        map.overlays.removeAll { it is Polygon && it !is GeofenceOverlayPolygon }
                        map.invalidate()
                    },
                    onCancel = {
                        val draft = geofenceBoxDraft
                        geofenceBoxDraft = null
                        geofenceBoxBoundingBox = null
                        map.overlays.removeAll { it is Polygon && it !is GeofenceOverlayPolygon }
                        map.invalidate()
                        showEditWaypointDialog = draft
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else if (downloadRegionBoundingBox != null) {
                CacheLayout(
                    cacheEstimate = cacheEstimate,
                    onExecuteJob = { startDownload() },
                    onCancelDownload = {
                        downloadRegionBoundingBox = null
                        map.overlays.removeAll { it is Polygon && it !is GeofenceOverlayPolygon }
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
                            icon = MeshtasticIcons.Map,
                            contentDescription = stringResource(Res.string.map_style_selection),
                            onClick = { showMapStyleDialog = true },
                        )
                    },
                    layersContent = {
                        MapButton(
                            icon = MeshtasticIcons.Layers,
                            contentDescription = stringResource(Res.string.manage_map_layers),
                            onClick = { showLayersBottomSheet = true },
                        )
                    },
                    // Hands node/channel-derived params to the hosted Site Planner and imports the returned coverage.
                    onSitePlannerClick =
                    if (sitePlannerAvailable()) {
                        { sitePlannerInitial = ourNodeInfo.toSitePlannerParams(channelSet) }
                    } else {
                        null
                    },
                    isLocationTrackingEnabled = myLocationOverlay != null,
                    onToggleLocationTracking = {
                        when {
                            locationPermission.isGranted -> map.toggleMyLocation()

                            // Permanently denied: the system won't prompt again, so send the user to settings.
                            locationPermission.status == PermissionStatus.PERMANENTLY_DENIED ->
                                locationPermission.openAppSettings()

                            else -> {
                                triggerLocationToggleAfterPermission = true
                                locationPermission.request()
                            }
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
                onlineTileProvider.setTileSource(loadOnlineTileSourceBase())
                map.invalidate()
            },
        )
    }

    if (showLayersBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showLayersBottomSheet = false }) {
            CustomMapLayersSheet(
                mapLayers = mapLayers,
                onToggleVisibility = mapViewModel::toggleLayerVisibility,
                onRemoveLayer = mapViewModel::removeMapLayer,
                onAddLayerClicked = onAddLayerClicked,
                onRefreshLayer = mapViewModel::refreshMapLayer,
                onAddNetworkLayer = { name, url -> mapViewModel.addNetworkMapLayer(name, url) },
            )
        }
    }

    // Site Planner deep link from a node's detail screen — open the estimate dialog prefilled with that node.
    val sitePlannerRequest by mapViewModel.sitePlannerRequest.collectAsStateWithLifecycle()
    LaunchedEffect(sitePlannerRequest) {
        sitePlannerRequest?.let { node ->
            sitePlannerInitial = node.toSitePlannerParams(channelSet)
            mapViewModel.consumeSitePlannerRequest()
        }
    }
    sitePlannerInitial?.let { initial ->
        SitePlannerHost(
            initialParams = initial,
            onDismiss = { sitePlannerInitial = null },
            onImport = { name, geoJson, latitude, longitude ->
                mapViewModel.addGeoJsonLayer(name, geoJson)
                // Recenter on the estimate's transmitter so the freshly-imported coverage is on-screen.
                map.controller.animateTo(GeoPoint(latitude, longitude))
            },
            // OSMdroid GPS fix (only when tracking is active + permission granted); no Play-services location on
            // F-Droid.
            onRequestCurrentLocation =
            if (locationPermission.isGranted) {
                { myLocationOverlay?.myLocation?.let { it.latitude to it.longitude } }
            } else {
                null
            },
            onUseNodeLocation =
            ourNodeInfo?.takeIf { it.validPosition != null }?.let { node -> { node.latitude to node.longitude } },
            onUseMapCenter = { map.mapCenter.let { it.latitude to it.longitude } },
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
            displayUnits = displayUnits,
            myNodeNum = mapViewModel.myNodeNum,
            onSend = { waypoint ->
                Logger.d { "User clicked send waypoint ${waypoint.id}" }
                showEditWaypointDialog = null

                val newId = if (waypoint.id == 0) mapViewModel.generatePacketId() else waypoint.id
                val newName = if (waypoint.name.isNullOrEmpty()) "Dropped Pin" else waypoint.name
                val newExpire = if (waypoint.expire == 0) Int.MAX_VALUE else waypoint.expire
                val newIcon = if (waypoint.icon == 0) 128205 else waypoint.icon

                // locked_to is already resolved by the editor (our node number when locked, 0 when not).
                mapViewModel.sendWaypoint(waypoint.copy(id = newId, name = newName, expire = newExpire, icon = newIcon))
            },
            onDelete = { waypoint ->
                Logger.d { "User clicked delete waypoint ${waypoint.id}" }
                showEditWaypointDialog = null
                showDeleteWaypointDialog = waypoint
            },
            onDismissRequest = {
                Logger.d { "User clicked cancel marker edit dialog" }
                showEditWaypointDialog = null
            },
            onBeginBoxAuthoring = { draft ->
                Logger.d { "User began geofence box authoring for waypoint ${draft.id}" }
                showEditWaypointDialog = null
                geofenceBoxDraft = draft
                map.generateGeofenceBoxOverlay()
            },
        )
    }

    showGeofenceInfoDialog?.let { waypoint ->
        val optIns by mapViewModel.geofenceAlertOptIns.collectAsStateWithLifecycle()
        WaypointInfoDialog(
            waypoint = waypoint,
            displayUnits = displayUnits,
            alertsEnabled = waypoint.id in optIns,
            onToggleAlerts = { mapViewModel.setGeofenceAlertOptIn(waypoint.id, it) },
            onDismissRequest = { showGeofenceInfoDialog = null },
            // Unlocked foreign geofences can still be edited/re-broadcast (only while connected, since editing means
            // re-sending); locked ones stay read-only.
            onEdit =
            if (!waypoint.isLocked && isConnected) {
                {
                    showGeofenceInfoDialog = null
                    showEditWaypointDialog = waypoint
                }
            } else {
                null
            },
        )
    }

    if (showDeleteWaypointDialog != null) {
        val waypoint = showDeleteWaypointDialog ?: return
        val canDeleteForEveryone = waypoint.isModifiableBy(mapViewModel.myNodeNum) && isConnected
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                Logger.d { "User canceled marker delete dialog" }
                showDeleteWaypointDialog = null
            },
            title = { Text(stringResource(Res.string.waypoint_delete)) },
            // Both deletes are confirmations; Cancel is the dismiss action (mirrors the old neutral button).
            confirmButton = {
                Column {
                    TextButton(
                        onClick = {
                            Logger.d { "User deleted waypoint ${waypoint.id} for me" }
                            mapViewModel.deleteWaypoint(waypoint.id)
                            showDeleteWaypointDialog = null
                        },
                    ) {
                        Text(stringResource(Res.string.delete_for_me))
                    }
                    if (canDeleteForEveryone) {
                        TextButton(
                            onClick = {
                                Logger.d { "User deleted waypoint ${waypoint.id} for everyone" }
                                mapViewModel.sendWaypoint(waypoint.copy(expire = 1))
                                mapViewModel.deleteWaypoint(waypoint.id)
                                showDeleteWaypointDialog = null
                            },
                        ) {
                            Text(stringResource(Res.string.delete_for_everyone))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        Logger.d { "User canceled marker delete dialog" }
                        showDeleteWaypointDialog = null
                    },
                ) {
                    Text(stringResource(Res.string.cancel))
                }
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
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
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
        }
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
    val selected = remember { mutableIntStateOf(selectedMapStyle) }

    MapsDialog(onDismiss = onDismiss) {
        CustomTileSource.mTileSources.values.forEachIndexed { index, style ->
            ListItem(
                text = style,
                trailingIcon = if (index == selected.intValue) MeshtasticIcons.Check else null,
                onClick = {
                    selected.intValue = index
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
        val capacityMb = cacheCapacity / (1024 * 1024)
        val usageMb = currentCacheUsage / (1024 * 1024)
        Text(modifier = Modifier.padding(16.dp), text = stringResource(Res.string.map_cache_info, capacityMb, usageMb))
    }
}

@Composable
private fun PurgeTileSourceDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cache = SqlTileWriterExt()

    val sourceList by remember { derivedStateOf { cache.sources.map { it.source as String } } }

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

// Geofence / authoring rectangle styling (orange) for the imperative OSMDroid Polygon paints (android.graphics ints).
private const val GEOFENCE_OVERLAY_COLOR = 0xFFFF9800.toInt()
private const val GEOFENCE_FILL_COLOR = 0x1FFF9800 // ~12% alpha orange
private const val GEOFENCE_STROKE_WIDTH_PX = 4f
private const val GEOFENCE_BOX_ZOOM_FACTOR = 1.3

/** Converts an OSMDroid [BoundingBox] (decimal degrees) to a proto [ProtoBoundingBox] (degrees ×1e7). */
@Suppress("MagicNumber")
private fun BoundingBox.toProtoBoundingBox(): ProtoBoundingBox = ProtoBoundingBox(
    longitude_west_i = (lonWest * 1e7).toInt(),
    latitude_south_i = (latSouth * 1e7).toInt(),
    longitude_east_i = (lonEast * 1e7).toInt(),
    latitude_north_i = (latNorth * 1e7).toInt(),
)

/** Bottom bar shown while the user positions the geofence rectangle: confirm applies it, cancel re-opens the editor. */
@Composable
private fun GeofenceBoxAuthoringBar(onConfirm: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.geofence_box_author_hint_viewport),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
            Button(onClick = onConfirm) { Text(stringResource(Res.string.geofence_box_author_confirm)) }
        }
    }
}
