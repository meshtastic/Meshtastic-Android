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

package org.meshtastic.feature.map.maplibre

// Import modularized MapLibre components

import android.annotation.SuppressLint
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.feature.map.MapViewModel
import org.meshtastic.feature.map.component.EditWaypointDialog
import org.meshtastic.feature.map.component.MapButton
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_CIRCLE_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_LIST_FETCH_MAX
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_RADIAL_MAX
import org.meshtastic.feature.map.maplibre.MapLibreConstants.DEG_D
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_CLUSTER_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_LAYER_NOCLUSTER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.WAYPOINTS_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.WAYPOINTS_SOURCE_ID
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.MeshProtos.Waypoint
import org.meshtastic.proto.copy
import org.meshtastic.proto.waypoint
import timber.log.Timber
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("MissingPermission")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MapLibrePOC(mapViewModel: MapViewModel = hiltViewModel(), onNavigateToNodeDetails: (Int) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedInfo by remember { mutableStateOf<String?>(null) }
    var selectedNodeNum by remember { mutableStateOf<Int?>(null) }
    val ourNode by mapViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    var isLocationTrackingEnabled by remember { mutableStateOf(false) }
    var followBearing by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    data class ExpandedCluster(val centerPx: android.graphics.PointF, val members: List<Node>)
    var expandedCluster by remember { mutableStateOf<ExpandedCluster?>(null) }
    var clusterListMembers by remember { mutableStateOf<List<Node>?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var didInitialCenter by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    var enabledRoles by remember { mutableStateOf<Set<ConfigProtos.Config.DeviceConfig.Role>>(emptySet()) }
    var clusteringEnabled by remember { mutableStateOf(true) }
    var editingWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    var mapTypeMenuExpanded by remember { mutableStateOf(false) }
    var showCustomTileDialog by remember { mutableStateOf(false) }
    var customTileUrl by remember { mutableStateOf("") }
    var customTileUrlInput by remember { mutableStateOf("") }
    var usingCustomTiles by remember { mutableStateOf(false) }
    // Base map style rotation
    val baseStyles = remember { enumValues<BaseMapStyle>().toList() }
    var baseStyleIndex by remember { mutableStateOf(0) }
    val baseStyle = baseStyles[baseStyleIndex % baseStyles.size]
    // Remember last applied cluster visibility to reduce flashing
    var clustersShown by remember { mutableStateOf(false) }
    var lastClusterEvalMs by remember { mutableStateOf(0L) }

    val nodes by mapViewModel.nodes.collectAsStateWithLifecycle()
    val waypoints by mapViewModel.waypoints.collectAsStateWithLifecycle()
    val isConnected by mapViewModel.isConnected.collectAsStateWithLifecycle()
    val displayableWaypoints = waypoints.values.mapNotNull { it.data.waypoint }

    // Check location permission
    hasLocationPermission = hasAnyLocationPermission(context)

    // Apply location tracking settings when state changes
    LaunchedEffect(isLocationTrackingEnabled, followBearing, hasLocationPermission) {
        mapRef?.let { map ->
            map.style?.let { style ->
                try {
                    if (hasLocationPermission) {
                        val locationComponent = map.locationComponent

                        // Enable/disable location component based on tracking state
                        if (isLocationTrackingEnabled) {
                            // Enable and show location component
                            if (!locationComponent.isLocationComponentEnabled) {
                                locationComponent.activateLocationComponent(
                                    org.maplibre.android.location.LocationComponentActivationOptions.builder(
                                        context,
                                        style,
                                    )
                                        .useDefaultLocationEngine(true)
                                        .build(),
                                )
                                locationComponent.isLocationComponentEnabled = true
                            }

                            // Set render mode
                            locationComponent.renderMode =
                                if (followBearing) {
                                    RenderMode.COMPASS
                                } else {
                                    RenderMode.NORMAL
                                }

                            // Set camera mode
                            locationComponent.cameraMode =
                                if (followBearing) {
                                    org.maplibre.android.location.modes.CameraMode.TRACKING_COMPASS
                                } else {
                                    org.maplibre.android.location.modes.CameraMode.TRACKING
                                }
                        } else {
                            // Disable location component to hide the blue dot
                            if (locationComponent.isLocationComponentEnabled) {
                                locationComponent.isLocationComponentEnabled = false
                            }
                        }

                        Timber.tag("MapLibrePOC")
                            .d(
                                "Location component updated: enabled=%s, follow=%s",
                                isLocationTrackingEnabled,
                                followBearing,
                            )
                    }
                } catch (e: Exception) {
                    Timber.tag("MapLibrePOC").w(e, "Failed to update location component")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView<MapView>(
            modifier = Modifier.fillMaxSize(),
            factory = {
                MapLibre.getInstance(context)
                Timber.tag("MapLibrePOC").d("Creating MapView + initializing MapLibre")
                MapView(context).apply {
                    mapViewRef = this
                    getMapAsync { map ->
                        mapRef = map
                        Timber.tag("MapLibrePOC").d("getMapAsync() map acquired, setting style...")
                        // Set initial base raster style using MapLibre test-app pattern
                        map.setStyle(buildMeshtasticStyle(baseStyle)) { style ->
                            Timber.tag("MapLibrePOC").d("Style loaded (base=%s)", baseStyle.label)
                            style.setTransition(TransitionOptions(0, 0))
                            logStyleState("after-style-load(pre-ensure)", style)
                            ensureSourcesAndLayers(style)
                            // Push current data immediately after style load
                            try {
                                val density = context.resources.displayMetrics.density
                                val bounds = map.projection.visibleRegion.latLngBounds
                                val labelSet = run {
                                    val visible =
                                        applyFilters(
                                            nodes,
                                            mapFilterState,
                                            enabledRoles,
                                            ourNode?.num,
                                            isLocationTrackingEnabled,
                                        )
                                            .filter { n ->
                                                val p = n.validPosition ?: return@filter false
                                                bounds.contains(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
                                            }
                                    val sorted =
                                        visible.sortedWith(
                                            compareByDescending<Node> { it.isFavorite }
                                                .thenByDescending { it.lastHeard },
                                        )
                                    val cell = (80f * density).toInt().coerceAtLeast(48)
                                    val occupied = HashSet<Long>()
                                    val chosen = LinkedHashSet<Int>()
                                    for (n in sorted) {
                                        val p = n.validPosition ?: continue
                                        val pt =
                                            map.projection.toScreenLocation(
                                                LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D),
                                            )
                                        val cx = (pt.x / cell).toInt()
                                        val cy = (pt.y / cell).toInt()
                                        val key = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffff)
                                        if (occupied.add(key)) chosen.add(n.num)
                                    }
                                    chosen
                                }
                                (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
                                    waypointsToFeatureCollectionFC(waypoints.values),
                                )
                                // Set clustered source only (like MapLibre example)
                                val filteredNodes =
                                    applyFilters(
                                        nodes,
                                        mapFilterState,
                                        enabledRoles,
                                        ourNode?.num,
                                        isLocationTrackingEnabled,
                                    )
                                val json = nodesToFeatureCollectionJsonWithSelection(filteredNodes, labelSet)
                                Timber.tag("MapLibrePOC")
                                    .d("Setting nodes sources: %d nodes, jsonBytes=%d", nodes.size, json.length)
                                safeSetGeoJson(style, NODES_CLUSTER_SOURCE_ID, json)
                                safeSetGeoJson(style, NODES_SOURCE_ID, json) // Also populate non-clustered source
                                Timber.tag("MapLibrePOC")
                                    .d(
                                        "Initial data set after style load. nodes=%d waypoints=%d",
                                        nodes.size,
                                        waypoints.size,
                                    )
                                logStyleState("after-style-load(post-sources)", style)
                            } catch (t: Throwable) {
                                Timber.tag("MapLibrePOC").e(t, "Failed to set initial data after style load")
                            }
                            // Keep base vector layers; OSM raster will sit below node layers for labels/roads
                            // Enable location component (if permissions granted)
                            activateLocationComponentForStyle(context, map, style)
                            Timber.tag("MapLibrePOC").d("Location component initialization attempted")
                            // Initial center on user's device location if available, else our node
                            if (!didInitialCenter) {
                                try {
                                    val loc = map.locationComponent.lastKnownLocation
                                    if (loc != null) {
                                        val target = LatLng(loc.latitude, loc.longitude)
                                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 12.0))
                                        didInitialCenter = true
                                    } else {
                                        ourNode?.validPosition?.let { p ->
                                            map.animateCamera(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D),
                                                    12.0,
                                                ),
                                            )
                                            didInitialCenter = true
                                        }
                                            ?: run {
                                                // Fallback: center to bounds of current nodes if available
                                                val filtered =
                                                    applyFilters(
                                                        nodes,
                                                        mapFilterState,
                                                        enabledRoles,
                                                        ourNode?.num,
                                                        isLocationTrackingEnabled,
                                                    )
                                                val boundsBuilder = org.maplibre.android.geometry.LatLngBounds.Builder()
                                                var any = false
                                                filtered.forEach { n ->
                                                    n.validPosition?.let { vp ->
                                                        boundsBuilder.include(
                                                            LatLng(vp.latitudeI * DEG_D, vp.longitudeI * DEG_D),
                                                        )
                                                        any = true
                                                    }
                                                }
                                                if (any) {
                                                    val b = boundsBuilder.build()
                                                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(b, 64))
                                                } else {
                                                    map.moveCamera(
                                                        CameraUpdateFactory.newLatLngZoom(LatLng(0.0, 0.0), 2.5),
                                                    )
                                                }
                                                didInitialCenter = true
                                            }
                                    }
                                } catch (_: Throwable) {}
                            }
                            map.addOnMapClickListener { latLng ->
                                // Any tap on the map clears overlays unless replaced below
                                expandedCluster = null
                                clusterListMembers = null
                                val screenPoint = map.projection.toScreenLocation(latLng)
                                // Use a small hitbox to improve taps on small circles
                                val r = (24 * context.resources.displayMetrics.density)
                                val rect =
                                    android.graphics.RectF(
                                        (screenPoint.x - r).toFloat(),
                                        (screenPoint.y - r).toFloat(),
                                        (screenPoint.x + r).toFloat(),
                                        (screenPoint.y + r).toFloat(),
                                    )
                                val features =
                                    map.queryRenderedFeatures(
                                        rect,
                                        CLUSTER_CIRCLE_LAYER_ID,
                                        NODES_LAYER_ID,
                                        NODES_LAYER_NOCLUSTER_ID,
                                        WAYPOINTS_LAYER_ID,
                                    )
                                Timber.tag("MapLibrePOC")
                                    .d(
                                        "Map click at (%.5f, %.5f) -> %d features",
                                        latLng.latitude,
                                        latLng.longitude,
                                        features.size,
                                    )
                                val f = features.firstOrNull()
                                // If cluster tapped, expand using true cluster leaves from the source
                                if (f != null && f.hasProperty("point_count")) {
                                    val pointCount = f.getNumberProperty("point_count")?.toInt() ?: 0
                                    val limit = kotlin.math.min(CLUSTER_LIST_FETCH_MAX, pointCount.toLong())
                                    val src = (map.style?.getSource(NODES_CLUSTER_SOURCE_ID) as? GeoJsonSource)
                                    if (src != null) {
                                        val fc = src.getClusterLeaves(f, limit, 0L)
                                        val nums =
                                            fc.features()?.mapNotNull { feat ->
                                                try {
                                                    feat.getNumberProperty("num")?.toInt()
                                                } catch (_: Throwable) {
                                                    null
                                                }
                                            } ?: emptyList()
                                        val members = nodes.filter { nums.contains(it.num) }
                                        if (members.isNotEmpty()) {
                                            // Center the radial overlay on the actual cluster point (not the raw click)
                                            val clusterCenter =
                                                (f.geometry() as? Point)?.let { p ->
                                                    map.projection.toScreenLocation(LatLng(p.latitude(), p.longitude()))
                                                } ?: screenPoint
                                            if (pointCount > CLUSTER_RADIAL_MAX) {
                                                // Show list for large clusters
                                                clusterListMembers = members
                                            } else {
                                                // Show radial overlay for small clusters
                                                expandedCluster =
                                                    ExpandedCluster(clusterCenter, members.take(CLUSTER_RADIAL_MAX))
                                            }
                                        }
                                        return@addOnMapClickListener true
                                    } else {
                                        map.animateCamera(CameraUpdateFactory.zoomIn())
                                        return@addOnMapClickListener true
                                    }
                                }
                                selectedInfo =
                                    f?.let {
                                        val kind = it.getStringProperty("kind")
                                        when (kind) {
                                            "node" -> {
                                                val num = it.getNumberProperty("num")?.toInt() ?: -1
                                                val n = nodes.firstOrNull { node -> node.num == num }
                                                selectedNodeNum = num
                                                n?.let { node ->
                                                    "Node ${node.user.longName.ifBlank {
                                                        node.num.toString()
                                                    }} (${node.gpsString()})"
                                                } ?: "Node $num"
                                            }
                                            "waypoint" -> {
                                                val id = it.getNumberProperty("id")?.toInt() ?: -1
                                                // Open edit dialog for waypoint
                                                waypoints.values
                                                    .find { pkt -> pkt.data.waypoint?.id == id }
                                                    ?.let { pkt -> editingWaypoint = pkt.data.waypoint }
                                                "Waypoint: ${it.getStringProperty("name") ?: id}"
                                            }
                                            else -> null
                                        }
                                    }
                                true
                            }
                            // Long-press to create waypoint
                            map.addOnMapLongClickListener { latLng ->
                                if (isConnected) {
                                    val newWaypoint = waypoint {
                                        latitudeI = (latLng.latitude / DEG_D).toInt()
                                        longitudeI = (latLng.longitude / DEG_D).toInt()
                                    }
                                    editingWaypoint = newWaypoint
                                    Timber.tag("MapLibrePOC")
                                        .d("Long press created waypoint at ${latLng.latitude}, ${latLng.longitude}")
                                }
                                true
                            }
                            // Update clustering visibility on camera idle (zoom changes)
                            map.addOnCameraIdleListener {
                                val st = map.style ?: return@addOnCameraIdleListener
                                // Debounce to avoid rapid toggling during kinetic flings/tiles loading
                                val now = SystemClock.uptimeMillis()
                                if (now - lastClusterEvalMs < 300) return@addOnCameraIdleListener
                                lastClusterEvalMs = now
                                val filtered =
                                    applyFilters(
                                        nodes,
                                        mapFilterState,
                                        enabledRoles,
                                        ourNode?.num,
                                        isLocationTrackingEnabled,
                                    )
                                Timber.tag("MapLibrePOC")
                                    .d("onCameraIdle: filtered nodes=%d (of %d)", filtered.size, nodes.size)
                                clustersShown =
                                    setClusterVisibilityHysteresis(
                                        map,
                                        st,
                                        filtered,
                                        clusteringEnabled,
                                        clustersShown,
                                        mapFilterState.showPrecisionCircle,
                                    )
                                // Compute which nodes get labels in viewport and update source
                                val density = context.resources.displayMetrics.density
                                val labelSet = selectLabelsForViewport(map, filtered, density)
                                val jsonIdle = nodesToFeatureCollectionJsonWithSelection(filtered, labelSet)
                                Timber.tag("MapLibrePOC")
                                    .d(
                                        "onCameraIdle: updating sources. labelSet=%d (nums=%s) jsonBytes=%d",
                                        labelSet.size,
                                        labelSet.take(5).joinToString(","),
                                        jsonIdle.length,
                                    )
                                // Update both clustered and non-clustered sources
                                safeSetGeoJson(st, NODES_CLUSTER_SOURCE_ID, jsonIdle)
                                safeSetGeoJson(st, NODES_SOURCE_ID, jsonIdle)
                                logStyleState("onCameraIdle(post-update)", st)
                                try {
                                    val w = mapViewRef?.width ?: 0
                                    val h = mapViewRef?.height ?: 0
                                    val bbox = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
                                    val rendered =
                                        map.queryRenderedFeatures(bbox, NODES_LAYER_ID, CLUSTER_CIRCLE_LAYER_ID)
                                    Timber.tag("MapLibrePOC")
                                        .d("onCameraIdle: rendered features in viewport=%d", rendered.size)
                                } catch (_: Throwable) {}
                            }
                            // Hide expanded cluster overlay whenever camera moves to avoid stale screen positions
                            map.addOnCameraMoveListener {
                                if (expandedCluster != null || clusterListMembers != null) {
                                    expandedCluster = null
                                    clusterListMembers = null
                                }
                            }
                        }
                    }
                }
            },
            update = { mapView: MapView ->
                mapView.getMapAsync { map ->
                    val style = map.style
                    if (style == null) {
                        Timber.tag("MapLibrePOC").w("Style not yet available in update()")
                        return@getMapAsync
                    }
                    // Apply bearing render mode toggle
                    try {
                        map.locationComponent.renderMode = if (followBearing) RenderMode.COMPASS else RenderMode.NORMAL
                    } catch (_: Throwable) {
                        /* ignore */
                    }

                    // Handle location tracking state changes
                    if (isLocationTrackingEnabled && hasAnyLocationPermission(context)) {
                        try {
                            val locationComponent = map.locationComponent
                            if (!locationComponent.isLocationComponentEnabled) {
                                locationComponent.activateLocationComponent(
                                    org.maplibre.android.location.LocationComponentActivationOptions.builder(
                                        context,
                                        map.style!!,
                                    )
                                        .useDefaultLocationEngine(true)
                                        .build(),
                                )
                                locationComponent.isLocationComponentEnabled = true
                            }
                            locationComponent.renderMode = if (followBearing) RenderMode.COMPASS else RenderMode.NORMAL
                            locationComponent.cameraMode =
                                if (isLocationTrackingEnabled) {
                                    if (followBearing) {
                                        org.maplibre.android.location.modes.CameraMode.TRACKING_COMPASS
                                    } else {
                                        org.maplibre.android.location.modes.CameraMode.TRACKING
                                    }
                                } else {
                                    org.maplibre.android.location.modes.CameraMode.NONE
                                }
                            Timber.tag("MapLibrePOC")
                                .d(
                                    "Location tracking: enabled=%s, follow=%s, mode=%s",
                                    isLocationTrackingEnabled,
                                    followBearing,
                                    locationComponent.cameraMode,
                                )
                        } catch (e: Exception) {
                            Timber.tag("MapLibrePOC").w(e, "Failed to update location component")
                        }
                    }
                    Timber.tag("MapLibrePOC").d("Updating sources. nodes=%d, waypoints=%d", nodes.size, waypoints.size)
                    val density = context.resources.displayMetrics.density
                    val bounds2 = map.projection.visibleRegion.latLngBounds
                    val labelSet = run {
                        val visible =
                            nodes.filter { n ->
                                val p = n.validPosition ?: return@filter false
                                bounds2.contains(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
                            }
                        val sorted =
                            visible.sortedWith(
                                compareByDescending<Node> { it.isFavorite }.thenByDescending { it.lastHeard },
                            )
                        val cell = (80f * density).toInt().coerceAtLeast(48)
                        val occupied = HashSet<Long>()
                        val chosen = LinkedHashSet<Int>()
                        for (n in sorted) {
                            val p = n.validPosition ?: continue
                            val pt = map.projection.toScreenLocation(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
                            val cx = (pt.x / cell).toInt()
                            val cy = (pt.y / cell).toInt()
                            val key = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffff)
                            if (occupied.add(key)) chosen.add(n.num)
                        }
                        chosen
                    }
                    (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
                        waypointsToFeatureCollectionFC(waypoints.values),
                    )
                    val filteredNow =
                        applyFilters(nodes, mapFilterState, enabledRoles, ourNode?.num, isLocationTrackingEnabled)
                    val jsonNow = nodesToFeatureCollectionJsonWithSelection(filteredNow, labelSet)
                    safeSetGeoJson(style, NODES_CLUSTER_SOURCE_ID, jsonNow)
                    safeSetGeoJson(style, NODES_SOURCE_ID, jsonNow) // Also populate non-clustered source
                    // Apply visibility now
                    clustersShown =
                        setClusterVisibilityHysteresis(
                            map,
                            style,
                            applyFilters(nodes, mapFilterState, enabledRoles, ourNode?.num, isLocationTrackingEnabled),
                            clusteringEnabled,
                            clustersShown,
                            mapFilterState.showPrecisionCircle,
                        )
                    logStyleState("update(block)", style)
                }
            },
        )

        selectedInfo?.let { info ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = info, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Role legend (based on roles present in current nodes)
        val rolesPresent = remember(nodes) { nodes.map { it.user.role }.toSet() }
        if (showLegend && rolesPresent.isNotEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    rolesPresent.take(6).forEach { role ->
                        val fakeNode =
                            Node(
                                num = 0,
                                user = org.meshtastic.proto.MeshProtos.User.newBuilder().setRole(role).build(),
                            )
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = roleColor(fakeNode),
                                modifier = Modifier.size(12.dp),
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = role.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }

        // Map controls: recenter/follow and filter menu
        var mapFilterExpanded by remember { mutableStateOf(false) }
        Column(
            modifier =
            Modifier.align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 16.dp), // Increased top padding to avoid exit button
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            // My Location button with visual feedback
            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        isLocationTrackingEnabled = !isLocationTrackingEnabled
                        if (!isLocationTrackingEnabled) {
                            followBearing = false
                        }
                        Timber.tag("MapLibrePOC").d("Location tracking toggled: %s", isLocationTrackingEnabled)
                    } else {
                        Timber.tag("MapLibrePOC").w("Location permission not granted")
                    }
                },
                containerColor =
                if (isLocationTrackingEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint =
                    if (isLocationTrackingEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            // Compass button with visual feedback
            FloatingActionButton(
                onClick = {
                    if (isLocationTrackingEnabled) {
                        followBearing = !followBearing
                        Timber.tag("MapLibrePOC").d("Follow bearing toggled: %s", followBearing)
                    } else {
                        // Enable tracking when compass is clicked
                        if (hasLocationPermission) {
                            isLocationTrackingEnabled = true
                            followBearing = true
                            Timber.tag("MapLibrePOC").d("Enabled tracking + bearing from compass button")
                        } else {
                            Timber.tag("MapLibrePOC").w("Location permission not granted")
                        }
                    }
                },
                containerColor =
                if (followBearing) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint =
                    if (followBearing) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Box {
                MapButton(onClick = { mapFilterExpanded = true }, icon = Icons.Outlined.Tune, contentDescription = null)
                DropdownMenu(expanded = mapFilterExpanded, onDismissRequest = { mapFilterExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Only favorites") },
                        onClick = {
                            mapViewModel.toggleOnlyFavorites()
                            mapFilterExpanded = false
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = mapFilterState.onlyFavorites,
                                onCheckedChange = {
                                    mapViewModel.toggleOnlyFavorites()
                                    // Refresh both sources when filters change
                                    mapRef?.style?.let { st ->
                                        val filtered =
                                            applyFilters(
                                                nodes,
                                                mapFilterState.copy(onlyFavorites = !mapFilterState.onlyFavorites),
                                                enabledRoles,
                                            )
                                        (st.getSource(NODES_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
                                            nodesToFeatureCollectionJsonWithSelection(filtered, emptySet()),
                                        )
                                        (st.getSource(NODES_CLUSTER_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
                                            nodesToFeatureCollectionJsonWithSelection(filtered, emptySet()),
                                        )
                                    }
                                },
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Show precision circle") },
                        onClick = {
                            mapViewModel.toggleShowPrecisionCircleOnMap()
                            mapFilterExpanded = false
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = mapFilterState.showPrecisionCircle,
                                onCheckedChange = { mapViewModel.toggleShowPrecisionCircleOnMap() },
                            )
                        },
                    )
                    androidx.compose.material3.Divider()
                    Text(
                        text = "Roles",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    val roles = nodes.map { it.user.role }.distinct().sortedBy { it.name }
                    roles.forEach { role ->
                        val checked = if (enabledRoles.isEmpty()) true else enabledRoles.contains(role)
                        DropdownMenuItem(
                            text = { Text(role.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                enabledRoles =
                                    if (enabledRoles.isEmpty()) {
                                        setOf(role)
                                    } else if (enabledRoles.contains(role)) {
                                        enabledRoles - role
                                    } else {
                                        enabledRoles + role
                                    }
                                mapRef?.style?.let { st ->
                                    mapRef?.let { map ->
                                        clustersShown =
                                            setClusterVisibilityHysteresis(
                                                map,
                                                st,
                                                applyFilters(
                                                    nodes,
                                                    mapFilterState,
                                                    enabledRoles,
                                                    ourNode?.num,
                                                    isLocationTrackingEnabled,
                                                ),
                                                clusteringEnabled,
                                                clustersShown,
                                                mapFilterState.showPrecisionCircle,
                                            )
                                    }
                                }
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        enabledRoles =
                                            if (enabledRoles.isEmpty()) {
                                                setOf(role)
                                            } else if (enabledRoles.contains(role)) {
                                                enabledRoles - role
                                            } else {
                                                enabledRoles + role
                                            }
                                        mapRef?.style?.let { st ->
                                            mapRef?.let { map ->
                                                clustersShown =
                                                    setClusterVisibilityHysteresis(
                                                        map,
                                                        st,
                                                        applyFilters(
                                                            nodes,
                                                            mapFilterState,
                                                            enabledRoles,
                                                            ourNode?.num,
                                                            isLocationTrackingEnabled,
                                                        ),
                                                        clusteringEnabled,
                                                        clustersShown,
                                                        mapFilterState.showPrecisionCircle,
                                                    )
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    }
                    androidx.compose.material3.Divider()
                    DropdownMenuItem(
                        text = { Text("Enable clustering") },
                        onClick = {
                            clusteringEnabled = !clusteringEnabled
                            mapRef?.style?.let { st ->
                                mapRef?.let { map ->
                                    clustersShown =
                                        setClusterVisibilityHysteresis(
                                            map,
                                            st,
                                            applyFilters(
                                                nodes,
                                                mapFilterState,
                                                enabledRoles,
                                                ourNode?.num,
                                                isLocationTrackingEnabled,
                                            ),
                                            clusteringEnabled,
                                            clustersShown,
                                            mapFilterState.showPrecisionCircle,
                                        )
                                }
                            }
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = clusteringEnabled,
                                onCheckedChange = {
                                    clusteringEnabled = it
                                    mapRef?.style?.let { st ->
                                        mapRef?.let { map ->
                                            clustersShown =
                                                setClusterVisibilityHysteresis(
                                                    map,
                                                    st,
                                                    applyFilters(
                                                        nodes,
                                                        mapFilterState,
                                                        enabledRoles,
                                                        ourNode?.num,
                                                        isLocationTrackingEnabled,
                                                    ),
                                                    clusteringEnabled,
                                                    clustersShown,
                                                    mapFilterState.showPrecisionCircle,
                                                )
                                        }
                                    }
                                },
                            )
                        },
                    )
                }
            }
            MapButton(onClick = { showLegend = !showLegend }, icon = Icons.Outlined.Info, contentDescription = null)
            // Map style selector
            Box {
                MapButton(
                    onClick = { mapTypeMenuExpanded = true },
                    icon = Icons.Outlined.Layers,
                    contentDescription = null,
                )
                DropdownMenu(expanded = mapTypeMenuExpanded, onDismissRequest = { mapTypeMenuExpanded = false }) {
                    Text(
                        text = "Map Style",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    baseStyles.forEachIndexed { index, style ->
                        DropdownMenuItem(
                            text = { Text(style.label) },
                            onClick = {
                                baseStyleIndex = index
                                usingCustomTiles = false
                                mapTypeMenuExpanded = false
                                val next = baseStyles[baseStyleIndex % baseStyles.size]
                                mapRef?.let { map ->
                                    Timber.tag("MapLibrePOC").d("Switching base style to: %s", next.label)
                                    map.setStyle(buildMeshtasticStyle(next)) { st ->
                                        Timber.tag("MapLibrePOC").d("Base map switched to: %s", next.label)
                                        val density = context.resources.displayMetrics.density
                                        clustersShown =
                                            reinitializeStyleAfterSwitch(
                                                context,
                                                map,
                                                st,
                                                waypoints,
                                                nodes,
                                                mapFilterState,
                                                enabledRoles,
                                                ourNode?.num,
                                                isLocationTrackingEnabled,
                                                clusteringEnabled,
                                                clustersShown,
                                                density,
                                            )
                                    }
                                }
                            },
                            trailingIcon = {
                                if (index == baseStyleIndex && !usingCustomTiles) {
                                    Icon(imageVector = Icons.Outlined.Check, contentDescription = "Selected")
                                }
                            },
                        )
                    }
                    androidx.compose.material3.HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (customTileUrl.isEmpty()) {
                                    "Custom Tile URL..."
                                } else {
                                    "Custom: ${customTileUrl.take(30)}..."
                                },
                            )
                        },
                        onClick = {
                            mapTypeMenuExpanded = false
                            customTileUrlInput = customTileUrl
                            showCustomTileDialog = true
                        },
                        trailingIcon = {
                            if (usingCustomTiles && customTileUrl.isNotEmpty()) {
                                Icon(imageVector = Icons.Outlined.Check, contentDescription = "Selected")
                            }
                        },
                    )
                }
            }
        }

        // Custom tile URL dialog
        if (showCustomTileDialog) {
            AlertDialog(
                onDismissRequest = { showCustomTileDialog = false },
                title = { Text("Custom Tile URL") },
                text = {
                    Column {
                        Text(
                            text = "Enter tile URL with {z}/{x}/{y} placeholders:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            text = "Example: https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        OutlinedTextField(
                            value = customTileUrlInput,
                            onValueChange = { customTileUrlInput = it },
                            label = { Text("Tile URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            customTileUrl = customTileUrlInput.trim()
                            if (customTileUrl.isNotEmpty()) {
                                usingCustomTiles = true
                                // Apply custom tiles (use first base style as template but we'll override the raster
                                // source)
                                mapRef?.let { map ->
                                    Timber.tag("MapLibrePOC").d("Switching to custom tiles: %s", customTileUrl)
                                    map.setStyle(buildMeshtasticStyle(baseStyles[0], customTileUrl)) { st ->
                                        Timber.tag("MapLibrePOC").d("Custom tiles applied")
                                        val density = context.resources.displayMetrics.density
                                        clustersShown =
                                            reinitializeStyleAfterSwitch(
                                                context,
                                                map,
                                                st,
                                                waypoints,
                                                nodes,
                                                mapFilterState,
                                                enabledRoles,
                                                ourNode?.num,
                                                isLocationTrackingEnabled,
                                                clusteringEnabled,
                                                clustersShown,
                                                density,
                                            )
                                    }
                                }
                            }
                            showCustomTileDialog = false
                        },
                    ) {
                        Text("Apply")
                    }
                },
                dismissButton = { TextButton(onClick = { showCustomTileDialog = false }) { Text("Cancel") } },
            )
        }

        // Expanded cluster radial overlay
        expandedCluster?.let { ec ->
            val d = context.resources.displayMetrics.density
            val centerX = (ec.centerPx.x / d).dp
            val centerY = (ec.centerPx.y / d).dp
            val radiusPx = 72f * d
            val itemSize = 40.dp
            val n = ec.members.size.coerceAtLeast(1)
            ec.members.forEachIndexed { idx, node ->
                val theta = (2.0 * Math.PI * idx / n)
                val x = (ec.centerPx.x + (radiusPx * kotlin.math.cos(theta))).toFloat()
                val y = (ec.centerPx.y + (radiusPx * kotlin.math.sin(theta))).toFloat()
                val xDp = (x / d).dp
                val yDp = (y / d).dp
                val label = (protoShortName(node) ?: shortNameFallback(node)).take(4)
                val itemHeight = 36.dp
                val itemWidth = (40 + label.length * 10).dp
                Surface(
                    modifier =
                    Modifier.align(Alignment.TopStart)
                        .offset(x = xDp - itemWidth / 2, y = yDp - itemHeight / 2)
                        .size(width = itemWidth, height = itemHeight)
                        .clickable {
                            selectedNodeNum = node.num
                            expandedCluster = null
                            node.validPosition?.let { p ->
                                mapRef?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D),
                                        15.0,
                                    ),
                                )
                            }
                        },
                    shape = CircleShape,
                    color = roleColor(node),
                    shadowElevation = 6.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(text = label, color = Color.White, maxLines = 1) }
                }
            }
        }

        // Bottom sheet with node details and actions
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val selectedNode = selectedNodeNum?.let { num -> nodes.firstOrNull { it.num == num } }
        // Cluster list bottom sheet (for large clusters)
        clusterListMembers?.let { members ->
            ModalBottomSheet(
                onDismissRequest = { clusterListMembers = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(text = "Cluster items (${members.size})", style = MaterialTheme.typography.titleMedium)
                    LazyColumn {
                        items(members) { node ->
                            Row(
                                modifier =
                                Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                                    selectedNodeNum = node.num
                                    clusterListMembers = null
                                    node.validPosition?.let { p ->
                                        mapRef?.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D),
                                                15.0,
                                            ),
                                        )
                                    }
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NodeChip(
                                    node = node,
                                    onClick = {
                                        selectedNodeNum = node.num
                                        clusterListMembers = null
                                        node.validPosition?.let { p ->
                                            mapRef?.animateCamera(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D),
                                                    15.0,
                                                ),
                                            )
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                val longName = node.user.longName
                                if (!longName.isNullOrBlank()) {
                                    Text(text = longName, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (selectedNode != null) {
            ModalBottomSheet(onDismissRequest = { selectedNodeNum = null }, sheetState = sheetState) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    NodeChip(node = selectedNode)
                    val longName = selectedNode.user.longName
                    if (!longName.isNullOrBlank()) {
                        Text(
                            text = longName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    val lastHeardAgo = formatSecondsAgo(selectedNode.lastHeard)
                    val coords = selectedNode.gpsString()
                    Text(text = "Last heard: $lastHeardAgo", modifier = Modifier.padding(top = 8.dp))
                    Text(text = "Coordinates: $coords")
                    val km = ourNode?.let { me -> distanceKmBetween(me, selectedNode) }
                    if (km != null) Text(text = "Distance: ${"%.1f".format(km)} km")
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Button(
                            onClick = {
                                onNavigateToNodeDetails(selectedNode.num)
                                selectedNodeNum = null
                            },
                        ) {
                            Text("View full node")
                        }
                    }
                }
            }
        }
        // Waypoint editing dialog
        editingWaypoint?.let { waypointToEdit ->
            EditWaypointDialog(
                waypoint = waypointToEdit,
                onSendClicked = { updatedWp ->
                    var finalWp = updatedWp
                    if (updatedWp.id == 0) {
                        finalWp = finalWp.copy { id = mapViewModel.generatePacketId() ?: 0 }
                    }
                    if (updatedWp.icon == 0) {
                        finalWp = finalWp.copy { icon = 0x1F4CD }
                    }
                    mapViewModel.sendWaypoint(finalWp)
                    editingWaypoint = null
                },
                onDeleteClicked = { wpToDelete ->
                    if (wpToDelete.lockedTo == 0 && isConnected && wpToDelete.id != 0) {
                        val deleteMarkerWp = wpToDelete.copy { expire = 1 }
                        mapViewModel.sendWaypoint(deleteMarkerWp)
                    }
                    mapViewModel.deleteWaypoint(wpToDelete.id)
                    editingWaypoint = null
                },
                onDismissRequest = { editingWaypoint = null },
            )
        }
    }

    // Forward lifecycle events to MapView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Note: AndroidView handles View lifecycle, but MapView benefits from explicit forwarding
            when (event) {
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_PAUSE -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
