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

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloBlur
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import kotlin.math.cos
import kotlin.math.sin
 
import org.meshtastic.core.database.model.Node
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.MapViewModel
import org.meshtastic.proto.MeshProtos.Waypoint
import timber.log.Timber
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.feature.map.component.MapButton
 
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.PropertyFactory.textMaxWidth
import org.maplibre.geojson.Point
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

private const val DEG_D = 1e-7

private const val STYLE_URL = "https://demotiles.maplibre.org/style.json"

private const val NODES_SOURCE_ID = "meshtastic-nodes-source"
private const val NODES_CLUSTER_SOURCE_ID = "meshtastic-nodes-source-clustered"
private const val WAYPOINTS_SOURCE_ID = "meshtastic-waypoints-source"
private const val TRACKS_SOURCE_ID = "meshtastic-tracks-source"
private const val OSM_SOURCE_ID = "osm-tiles"

private const val NODES_LAYER_ID = "meshtastic-nodes-layer"
private const val NODE_TEXT_LAYER_ID = "meshtastic-node-text-layer"
private const val NODE_TEXT_BG_LAYER_ID = "meshtastic-node-text-bg-layer"
private const val NODES_LAYER_CLUSTERED_ID = "meshtastic-nodes-layer-clustered"
private const val NODE_TEXT_LAYER_CLUSTERED_ID = "meshtastic-node-text-layer-clustered"
private const val CLUSTER_CIRCLE_LAYER_ID = "meshtastic-cluster-circle-layer"
private const val CLUSTER_COUNT_LAYER_ID = "meshtastic-cluster-count-layer"
private const val WAYPOINTS_LAYER_ID = "meshtastic-waypoints-layer"
private const val TRACKS_LAYER_ID = "meshtastic-tracks-layer"
private const val OSM_LAYER_ID = "osm-layer"
private const val CLUSTER_RADIAL_MAX = 8
private const val CLUSTER_LIST_FETCH_MAX = 200L

@SuppressLint("MissingPermission")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MapLibrePOC(
    mapViewModel: MapViewModel = hiltViewModel(),
    onNavigateToNodeDetails: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedInfo by remember { mutableStateOf<String?>(null) }
    var selectedNodeNum by remember { mutableStateOf<Int?>(null) }
    val ourNode by mapViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    var recenterRequest by remember { mutableStateOf(false) }
    var followBearing by remember { mutableStateOf(false) }
    data class ExpandedCluster(val centerPx: android.graphics.PointF, val members: List<Node>)
    var expandedCluster by remember { mutableStateOf<ExpandedCluster?>(null) }
    var clusterListMembers by remember { mutableStateOf<List<Node>?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var didInitialCenter by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    var enabledRoles by remember { mutableStateOf<Set<ConfigProtos.Config.DeviceConfig.Role>>(emptySet()) }
    var clusteringEnabled by remember { mutableStateOf(true) }

    val nodes by mapViewModel.nodes.collectAsStateWithLifecycle()
    val waypoints by mapViewModel.waypoints.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView<MapView>(
            modifier = Modifier.fillMaxSize(),
            factory = {
                MapLibre.getInstance(context)
                Timber.tag("MapLibrePOC").d("Creating MapView + initializing MapLibre")
                MapView(context).apply {
                    getMapAsync { map ->
                        mapRef = map
                        Timber.tag("MapLibrePOC").d("getMapAsync() map acquired, setting style...")
                        map.setStyle(STYLE_URL) { style ->
                            Timber.tag("MapLibrePOC").d("Style loaded: %s", STYLE_URL)
                            ensureSourcesAndLayers(style)
                            // Push current data immediately after style load
                            try {
                                val density = context.resources.displayMetrics.density
                                val bounds = map.projection.visibleRegion.latLngBounds
                                val labelSet =
                                    run {
                                        val visible =
                                            applyFilters(nodes, mapFilterState, enabledRoles).filter { n ->
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
                                (style.getSource(NODES_SOURCE_ID) as? GeoJsonSource)
                                    ?.setGeoJson(nodesToFeatureCollectionJsonWithSelection(applyFilters(nodes, mapFilterState, enabledRoles), labelSet))
                                (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)
                                    ?.setGeoJson(waypointsToFeatureCollectionJson(waypoints.values))
                                // Removed test track line rendering
                                // Update clustered source too
                                (style.getSource(NODES_CLUSTER_SOURCE_ID) as? GeoJsonSource)
                                    ?.setGeoJson(nodesToFeatureCollectionJsonWithSelection(applyFilters(nodes, mapFilterState, enabledRoles), labelSet))
                                Timber.tag("MapLibrePOC").d(
                                    "Initial data set after style load. nodes=%d waypoints=%d",
                                    nodes.size,
                                    waypoints.size,
                                )
                            } catch (t: Throwable) {
                                Timber.tag("MapLibrePOC").e(t, "Failed to set initial data after style load")
                            }
                            // Keep base vector layers; OSM raster will sit below node layers for labels/roads
                            // Enable location component (if permissions granted)
                            try {
                                val locationComponent = map.locationComponent
                                locationComponent.activateLocationComponent(
                                    org.maplibre.android.location.LocationComponentActivationOptions.builder(
                                        context,
                                        style,
                                    ).useDefaultLocationEngine(true).build(),
                                )
                                locationComponent.isLocationComponentEnabled = true
                                locationComponent.renderMode = RenderMode.COMPASS
                                Timber.tag("MapLibrePOC").d("Location component enabled")
                            } catch (_: Throwable) {
                                // ignore
                                Timber.tag("MapLibrePOC").w("Location component not enabled (likely missing permissions)")
                            }
                            // Initial center on user's device location if available, else our node
                            if (!didInitialCenter) {
                                try {
                                    val loc = map.locationComponent.lastKnownLocation
                                    if (loc != null) {
                                        map.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(loc.latitude, loc.longitude),
                                                12.0,
                                            ),
                                        )
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
                                    }
                                } catch (_: Throwable) {
                                }
                            }
                            map.addOnMapClickListener { latLng ->
                                // Any tap on the map clears overlays unless replaced below
                                expandedCluster = null
                                clusterListMembers = null
                                val screenPoint = map.projection.toScreenLocation(latLng)
                                // Use a small hitbox to improve taps on small circles
                                val r = (24 * context.resources.displayMetrics.density)
                                val rect = android.graphics.RectF(
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
                                        NODES_LAYER_CLUSTERED_ID,
                                        WAYPOINTS_LAYER_ID,
                                    )
                                Timber.tag("MapLibrePOC").d("Map click at (%.5f, %.5f) -> %d features", latLng.latitude, latLng.longitude, features.size)
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
                                                expandedCluster = ExpandedCluster(clusterCenter, members.take(CLUSTER_RADIAL_MAX))
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
                                                n?.let { node -> "Node ${node.user.longName.ifBlank { node.num.toString() }} (${node.gpsString()})" }
                                                    ?: "Node $num"
                                            }
                                            "waypoint" -> {
                                                val id = it.getNumberProperty("id")?.toInt() ?: -1
                                                "Waypoint $id"
                                            }
                                            else -> null
                                        }
                                    }
                                true
                            }
                            // Update clustering visibility on camera idle (zoom changes)
                            map.addOnCameraIdleListener {
                                setClusterVisibility(map, style, applyFilters(nodes, mapFilterState, enabledRoles), clusteringEnabled)
                                // Compute which nodes get labels in viewport and update source
                                val density = context.resources.displayMetrics.density
                                val labelSet = selectLabelsForViewport(map, applyFilters(nodes, mapFilterState, enabledRoles), density)
                                (style.getSource(NODES_SOURCE_ID) as? GeoJsonSource)
                                    ?.setGeoJson(nodesToFeatureCollectionJsonWithSelection(applyFilters(nodes, mapFilterState, enabledRoles), labelSet))
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
                    } catch (_: Throwable) { /* ignore */ }
                    // Handle recenter requests
                    if (recenterRequest) {
                        recenterRequest = false
                        ourNode?.validPosition?.let { p ->
                            val ll = LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D)
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 14.5))
                        }
                    }
                    Timber.tag("MapLibrePOC").d(
                        "Updating sources. nodes=%d, waypoints=%d",
                        nodes.size,
                        waypoints.size,
                    )
                    val density = context.resources.displayMetrics.density
                    val bounds2 = map.projection.visibleRegion.latLngBounds
                    val labelSet =
                        run {
                            val visible =
                                nodes.filter { n ->
                                    val p = n.validPosition ?: return@filter false
                                    bounds2.contains(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
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
                    (style.getSource(NODES_SOURCE_ID) as? GeoJsonSource)
                        ?.setGeoJson(nodesToFeatureCollectionJsonWithSelection(nodes, labelSet))
                    (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)
                        ?.setGeoJson(waypointsToFeatureCollectionJson(waypoints.values))
                    // Removed test track line rendering
                    (style.getSource(NODES_CLUSTER_SOURCE_ID) as? GeoJsonSource)
                        ?.setGeoJson(nodesToFeatureCollectionJsonWithSelection(applyFilters(nodes, mapFilterState, enabledRoles), labelSet))
                    // Toggle clustering visibility now (no need to wait for camera move)
                    setClusterVisibility(map, style, applyFilters(nodes, mapFilterState, enabledRoles), clusteringEnabled)
                }
            },
        )

        selectedInfo?.let { info ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(12.dp)) { Text(text = info, style = MaterialTheme.typography.bodyMedium) }
            }
        }

        // Role legend (based on roles present in current nodes)
        val rolesPresent = remember(nodes) { nodes.map { it.user.role }.toSet() }
        if (showLegend && rolesPresent.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    rolesPresent.take(6).forEach { role ->
                        val fakeNode = Node(num = 0, user = org.meshtastic.proto.MeshProtos.User.newBuilder().setRole(role).build())
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(shape = CircleShape, color = roleColor(fakeNode), modifier = Modifier.size(12.dp)) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = role.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Map controls: recenter/follow and filter menu
        var mapFilterExpanded by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
        ) {
            MapButton(
                onClick = { recenterRequest = true },
                icon = Icons.Outlined.MyLocation,
                contentDescription = null,
            )
            MapButton(
                onClick = { followBearing = !followBearing },
                icon = Icons.Outlined.Explore,
                contentDescription = null,
            )
            Box {
                MapButton(
                    onClick = { mapFilterExpanded = true },
                    icon = Icons.Outlined.Tune,
                    contentDescription = null,
                )
                DropdownMenu(expanded = mapFilterExpanded, onDismissRequest = { mapFilterExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Only favorites") },
                        onClick = { mapViewModel.toggleOnlyFavorites(); mapFilterExpanded = false },
                        trailingIcon = { Checkbox(checked = mapFilterState.onlyFavorites, onCheckedChange = { mapViewModel.toggleOnlyFavorites() }) },
                    )
                    DropdownMenuItem(
                        text = { Text("Show precision circle") },
                        onClick = { mapViewModel.toggleShowPrecisionCircleOnMap(); mapFilterExpanded = false },
                        trailingIcon = { Checkbox(checked = mapFilterState.showPrecisionCircle, onCheckedChange = { mapViewModel.toggleShowPrecisionCircleOnMap() }) },
                    )
                    androidx.compose.material3.Divider()
                    Text(text = "Roles", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    val roles = nodes.map { it.user.role }.distinct().sortedBy { it.name }
                    roles.forEach { role ->
                        val checked = if (enabledRoles.isEmpty()) true else enabledRoles.contains(role)
                        DropdownMenuItem(
                            text = { Text(role.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                enabledRoles =
                                    if (enabledRoles.isEmpty()) setOf(role)
                                    else if (enabledRoles.contains(role)) enabledRoles - role else enabledRoles + role
                                mapRef?.style?.let { st -> mapRef?.let { setClusterVisibility(it, st, applyFilters(nodes, mapFilterState, enabledRoles), clusteringEnabled) } }
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        enabledRoles =
                                            if (enabledRoles.isEmpty()) setOf(role)
                                            else if (enabledRoles.contains(role)) enabledRoles - role else enabledRoles + role
                                        mapRef?.style?.let { st -> mapRef?.let { setClusterVisibility(it, st, applyFilters(nodes, mapFilterState, enabledRoles), clusteringEnabled) } }
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
                            mapRef?.style?.let { st -> mapRef?.let { setClusterVisibility(it, st, applyFilters(nodes, mapFilterState, enabledRoles), clusteringEnabled) } }
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = clusteringEnabled,
                                onCheckedChange = {
                                    clusteringEnabled = it
                                    mapRef?.style?.let { st -> mapRef?.let { setClusterVisibility(it, st, applyFilters(nodes, mapFilterState, enabledRoles), clusteringEnabled) } }
                                },
                            )
                        },
                    )
                }
            }
            MapButton(
                onClick = { showLegend = !showLegend },
                icon = Icons.Outlined.Info,
                contentDescription = null,
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
                    modifier = Modifier
                        .align(Alignment.TopStart)
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
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = label, color = Color.White, maxLines = 1)
                    }
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
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
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
                                NodeChip(node = node, onClick = {
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
                                })
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
            ModalBottomSheet(
                onDismissRequest = { selectedNodeNum = null },
                sheetState = sheetState,
            ) {
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
                        Button(onClick = {
                            onNavigateToNodeDetails(selectedNode.num)
                            selectedNodeNum = null
                        }) {
                            Text("View full node")
                        }
                    }
                }
            }
        }
    }

    // Forward lifecycle events to MapView
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
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

private fun ensureSourcesAndLayers(style: Style) {
    Timber.tag("MapLibrePOC").d("ensureSourcesAndLayers() begin. Existing layers=%d, sources=%d", style.layers.size, style.sources.size)
    if (style.getSource(OSM_SOURCE_ID) == null) {
        // Try standard OpenStreetMap raster tiles (with subdomain 'a') for streets/cities
        style.addSource(RasterSource(OSM_SOURCE_ID, "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png", 256))
        Timber.tag("MapLibrePOC").d("Added OSM Standard RasterSource")
    }
    if (style.getSource(NODES_SOURCE_ID) == null) {
        // Plain (non-clustered) source for nodes and labels
        style.addSource(GeoJsonSource(NODES_SOURCE_ID, emptyFeatureCollectionJson()))
        Timber.tag("MapLibrePOC").d("Added nodes plain GeoJsonSource")
    }
    if (style.getSource(NODES_CLUSTER_SOURCE_ID) == null) {
        // Clustered source for cluster layers
        val options = GeoJsonOptions().withCluster(true).withClusterRadius(36).withClusterMaxZoom(10)
        style.addSource(GeoJsonSource(NODES_CLUSTER_SOURCE_ID, emptyFeatureCollectionJson(), options))
        Timber.tag("MapLibrePOC").d("Added nodes clustered GeoJsonSource")
    }
    if (style.getSource(WAYPOINTS_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(WAYPOINTS_SOURCE_ID, emptyFeatureCollectionJson()))
        Timber.tag("MapLibrePOC").d("Added waypoints GeoJsonSource")
    }
    // Removed test track GeoJsonSource

    if (style.getLayer(OSM_LAYER_ID) == null) {
        // Put OSM tiles on TOP so labels/roads are visible during POC
        val rl = RasterLayer(OSM_LAYER_ID, OSM_SOURCE_ID).withProperties(rasterOpacity(1.0f))
        // Add early, then ensure it's below node layers if present
        style.addLayer(rl)
        Timber.tag("MapLibrePOC").d("Added OSM RasterLayer")
    }
    if (style.getLayer(CLUSTER_CIRCLE_LAYER_ID) == null) {
        val clusterLayer =
            CircleLayer(CLUSTER_CIRCLE_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(circleColor("#6D4C41"), circleRadius(14f))
                .withFilter(has("point_count"))
        style.addLayer(clusterLayer)
        Timber.tag("MapLibrePOC").d("Added cluster CircleLayer")
    }
    if (style.getLayer(CLUSTER_COUNT_LAYER_ID) == null) {
        val countLayer =
            SymbolLayer(CLUSTER_COUNT_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(
                    textField(toString(get("point_count"))),
                    textColor("#FFFFFF"),
                    textHaloColor("#000000"),
                    textHaloWidth(1.5f),
                    textHaloBlur(0.5f),
                    textSize(12f),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                )
                .withFilter(has("point_count"))
        style.addLayer(countLayer)
        Timber.tag("MapLibrePOC").d("Added cluster count SymbolLayer")
    }
    if (style.getLayer(NODES_LAYER_ID) == null) {
        val layer =
            CircleLayer(NODES_LAYER_ID, NODES_SOURCE_ID)
                .withProperties(circleColor(get("color")), circleRadius(7f))
                .withFilter(not(has("point_count")))
        style.addLayer(layer)
        Timber.tag("MapLibrePOC").d("Added nodes CircleLayer")
    }
    if (style.getLayer(NODES_LAYER_CLUSTERED_ID) == null) {
        val layer =
            CircleLayer(NODES_LAYER_CLUSTERED_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(circleColor(get("color")), circleRadius(7f), visibility("none"))
                .withFilter(not(has("point_count")))
        style.addLayer(layer)
        Timber.tag("MapLibrePOC").d("Added clustered nodes CircleLayer")
    }
    if (style.getLayer(NODE_TEXT_LAYER_ID) == null) {
        val textLayer =
            SymbolLayer(NODE_TEXT_LAYER_ID, NODES_SOURCE_ID)
                .withProperties(
                    textField(get("short")),
                    textColor("#1B1B1B"),
                    textHaloColor("#FFFFFF"),
                    textHaloWidth(3.0f),
                    textHaloBlur(0.7f),
                    // Scale label size with zoom to reduce clutter
                    textSize(
                        interpolate(
                            linear(),
                            zoom(),
                            stop(8, 9f),
                            stop(12, 11f),
                            stop(15, 13f),
                            stop(18, 16f),
                        ),
                    ),
                    textMaxWidth(4f),
                    // At close zooms, prefer showing all labels even if they overlap a bit
                    textAllowOverlap(
                        step(
                            zoom(),
                            literal(false), // default for low zooms
                            stop(11, literal(true)), // enable overlap >= 11
                        ),
                    ),
                    textIgnorePlacement(
                        step(
                            zoom(),
                            literal(false),
                            stop(11, literal(true)),
                        ),
                    ),
                    // place label above the circle
                    textOffset(arrayOf(0f, -1.4f)),
                    textAnchor("bottom"),
                )
                .withFilter(
                    all(
                        not(has("point_count")),
                        eq(get("showLabel"), literal(1)),
                    ),
                )
        style.addLayer(textLayer)
        Timber.tag("MapLibrePOC").d("Added node text SymbolLayer")
    }
    if (style.getLayer(NODE_TEXT_LAYER_CLUSTERED_ID) == null) {
        val textLayer =
            SymbolLayer(NODE_TEXT_LAYER_CLUSTERED_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(
                    textField(get("short")),
                    textColor("#1B1B1B"),
                    textHaloColor("#FFFFFF"),
                    textHaloWidth(3.0f),
                    textHaloBlur(0.7f),
                    textSize(
                        interpolate(
                            linear(),
                            zoom(),
                            stop(8, 9f),
                            stop(12, 11f),
                            stop(15, 13f),
                            stop(18, 16f),
                        ),
                    ),
                    textMaxWidth(4f),
                    textAllowOverlap(
                        step(
                            zoom(),
                            literal(false),
                            stop(11, literal(true)),
                        ),
                    ),
                    textIgnorePlacement(
                        step(
                            zoom(),
                            literal(false),
                            stop(11, literal(true)),
                        ),
                    ),
                    textOffset(arrayOf(0f, -1.4f)),
                    textAnchor("bottom"),
                    visibility("none"),
                )
                .withFilter(
                    all(
                        not(has("point_count")),
                        eq(get("showLabel"), literal(1)),
                    ),
                )
        style.addLayer(textLayer)
        Timber.tag("MapLibrePOC").d("Added clustered node text SymbolLayer")
    }
    if (style.getLayer(WAYPOINTS_LAYER_ID) == null) {
        val layer = CircleLayer(WAYPOINTS_LAYER_ID, WAYPOINTS_SOURCE_ID).withProperties(circleColor("#2E7D32"), circleRadius(5f))
        style.addLayer(layer)
        Timber.tag("MapLibrePOC").d("Added waypoints CircleLayer")
    }
    // Removed test track LineLayer
    Timber.tag("MapLibrePOC").d("ensureSourcesAndLayers() end. Layers=%d, Sources=%d", style.layers.size, style.sources.size)
}

private fun applyFilters(
    all: List<Node>,
    filter: BaseMapViewModel.MapFilterState,
    enabledRoles: Set<ConfigProtos.Config.DeviceConfig.Role>,
): List<Node> {
    var out = all
    if (filter.onlyFavorites) out = out.filter { it.isFavorite }
    if (enabledRoles.isNotEmpty()) out = out.filter { enabledRoles.contains(it.user.role) }
    return out
}

private fun nodesToFeatureCollectionJson(nodes: List<Node>): String {
    val features =
        nodes.mapNotNull { node ->
            val pos = node.validPosition ?: return@mapNotNull null
            val lat = pos.latitudeI * DEG_D
            val lon = pos.longitudeI * DEG_D
            val short = (protoShortName(node) ?: shortNameFallback(node)).take(4)
            val role = node.user.role.name
            val color = roleColorHex(node)
            // Default showLabel=0; it will be turned on for selected nodes by viewport selection
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"kind":"node","num":${node.num},"name":"${node.user.longName}","short":"$short","role":"$role","color":"$color","showLabel":0}}"""
        }
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

private fun nodesToFeatureCollectionJsonWithSelection(nodes: List<Node>, labelNums: Set<Int>): String {
    val features =
        nodes.mapNotNull { node ->
            val pos = node.validPosition ?: return@mapNotNull null
            val lat = pos.latitudeI * DEG_D
            val lon = pos.longitudeI * DEG_D
            val short = (protoShortName(node) ?: shortNameFallback(node)).take(4)
            val show = if (labelNums.contains(node.num)) 1 else 0
            val role = node.user.role.name
            val color = roleColorHex(node)
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"kind":"node","num":${node.num},"name":"${node.user.longName}","short":"$short","role":"$role","color":"$color","showLabel":$show}}"""
        }
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

private fun waypointsToFeatureCollectionJson(
    waypoints: Collection<org.meshtastic.core.database.entity.Packet>,
): String {
    val features =
        waypoints.mapNotNull { pkt ->
            val w: Waypoint = pkt.data.waypoint ?: return@mapNotNull null
            // Filter invalid/placeholder coordinates (avoid 0,0 near Africa)
            val lat = w.latitudeI * DEG_D
            val lon = w.longitudeI * DEG_D
            if (lat == 0.0 && lon == 0.0) return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"kind":"waypoint","id":${w.id}}}"""
        }
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

private fun emptyFeatureCollectionJson(): String {
    return """{"type":"FeatureCollection","features":[]}"""
}

private fun oneTrackFromNodesJson(nodes: List<Node>): String {
    val valid = nodes.mapNotNull { it.validPosition }
    if (valid.size < 2) return emptyFeatureCollectionJson()
    val a = valid[0]
    val b = valid[1]
    val lat1 = a.latitudeI * DEG_D
    val lon1 = a.longitudeI * DEG_D
    val lat2 = b.latitudeI * DEG_D
    val lon2 = b.longitudeI * DEG_D
    val line =
        """{"type":"Feature","geometry":{"type":"LineString","coordinates":[[$lon1,$lat1],[$lon2,$lat2]]},"properties":{"kind":"track"}}"""
    return """{"type":"FeatureCollection","features":[$line]}"""
}

private fun shortName(node: Node): String {
    // Deprecated; kept for compatibility
    return shortNameFallback(node)
}

private fun protoShortName(node: Node): String? {
    // Prefer the protocol-defined short name if present
    val s = node.user.shortName
    return if (s.isNullOrBlank()) null else s
}

private fun shortNameFallback(node: Node): String {
    val long = node.user.longName
    if (!long.isNullOrBlank()) return long.take(4)
    val hex = node.num.toString(16).uppercase()
    return if (hex.length >= 4) hex.takeLast(4) else hex
}

// Select one label per grid cell in the current viewport, prioritizing favorites and recent nodes.
private fun selectLabelsForViewport(map: MapLibreMap, nodes: List<Node>, density: Float): Set<Int> {
    val bounds = map.projection.visibleRegion.latLngBounds
    val visible =
        nodes.filter { n ->
            val p = n.validPosition ?: return@filter false
            val lat = p.latitudeI * DEG_D
            val lon = p.longitudeI * DEG_D
            bounds.contains(LatLng(lat, lon))
        }
    if (visible.isEmpty()) return emptySet()
    // Priority: favorites first, then more recently heard
    val sorted =
        visible.sortedWith(
            compareByDescending<Node> { it.isFavorite }
                .thenByDescending { it.lastHeard },
        )
    // Dynamic cell size by zoom so more labels appear as you zoom in
    val zoom = map.cameraPosition.zoom
    val baseCellDp =
        when {
            zoom < 10 -> 96f
            zoom < 11 -> 88f
            zoom < 12 -> 80f
            zoom < 13 -> 72f
            zoom < 14 -> 64f
            zoom < 15 -> 56f
            zoom < 16 -> 48f
            else -> 36f
        }
    val cellSizePx = (baseCellDp * density).toInt().coerceAtLeast(32)
    val occupied = HashSet<Long>()
    val chosen = LinkedHashSet<Int>()
    for (n in sorted) {
        val p = n.validPosition ?: continue
        val pt = map.projection.toScreenLocation(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
        val cx = (pt.x / cellSizePx).toInt()
        val cy = (pt.y / cellSizePx).toInt()
        val key = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffff)
        if (occupied.add(key)) {
            chosen.add(n.num)
        }
    }
    return chosen
}

// Human friendly "x min ago" from epoch seconds
private fun formatSecondsAgo(lastHeardEpochSeconds: Int): String {
    val now = System.currentTimeMillis() / 1000
    val delta = (now - lastHeardEpochSeconds).coerceAtLeast(0)
    val minutes = delta / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        delta < 60 -> "$delta s ago"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours h ago"
        else -> "$days d ago"
    }
}

// Simple haversine distance between two nodes in kilometers
private fun distanceKmBetween(a: Node, b: Node): Double? {
    val pa = a.validPosition ?: return null
    val pb = b.validPosition ?: return null
    val lat1 = pa.latitudeI * DEG_D
    val lon1 = pa.longitudeI * DEG_D
    val lat2 = pb.latitudeI * DEG_D
    val lon2 = pb.longitudeI * DEG_D
    val R = 6371.0 // km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val s1 = kotlin.math.sin(dLat / 2)
    val s2 = kotlin.math.sin(dLon / 2)
    val aTerm = s1 * s1 + kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) * s2 * s2
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(aTerm), kotlin.math.sqrt(1 - aTerm))
    return R * c
}

// Role -> hex color used for map dots and radial overlay
private fun roleColorHex(node: Node): String {
    return when (node.user.role) {
        ConfigProtos.Config.DeviceConfig.Role.ROUTER -> "#616161" // gray
        ConfigProtos.Config.DeviceConfig.Role.ROUTER_CLIENT -> "#00897B" // teal
        ConfigProtos.Config.DeviceConfig.Role.REPEATER -> "#EF6C00" // orange
        ConfigProtos.Config.DeviceConfig.Role.TRACKER -> "#8E24AA" // purple
        ConfigProtos.Config.DeviceConfig.Role.SENSOR -> "#1E88E5" // blue
        ConfigProtos.Config.DeviceConfig.Role.TAK, ConfigProtos.Config.DeviceConfig.Role.TAK_TRACKER -> "#C62828" // red
        ConfigProtos.Config.DeviceConfig.Role.CLIENT -> "#2E7D32" // green
        ConfigProtos.Config.DeviceConfig.Role.CLIENT_BASE -> "#43A047" // green (lighter)
        ConfigProtos.Config.DeviceConfig.Role.CLIENT_MUTE -> "#9E9D24" // olive
        ConfigProtos.Config.DeviceConfig.Role.CLIENT_HIDDEN -> "#546E7A" // blue-grey
        ConfigProtos.Config.DeviceConfig.Role.LOST_AND_FOUND -> "#AD1457" // magenta
        ConfigProtos.Config.DeviceConfig.Role.ROUTER_LATE -> "#757575" // mid-grey
        null,
        ConfigProtos.Config.DeviceConfig.Role.UNRECOGNIZED -> "#2E7D32" // default green
    }
}

private fun roleColor(node: Node): Color = Color(android.graphics.Color.parseColor(roleColorHex(node)))

// Show/hide cluster layers vs plain nodes based on zoom, density, and toggle
private fun setClusterVisibility(map: MapLibreMap, style: Style, filteredNodes: List<Node>, enableClusters: Boolean) {
    val zoom = map.cameraPosition.zoom
    val bounds = map.projection.visibleRegion.latLngBounds
    val visibleCount =
        filteredNodes.count { n ->
            val p = n.validPosition ?: return@count false
            bounds.contains(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
        }
    val showClustersNow = enableClusters && (zoom <= 10.0 && visibleCount > 50)
    style.getLayer(CLUSTER_CIRCLE_LAYER_ID)?.setProperties(visibility(if (showClustersNow) "visible" else "none"))
    style.getLayer(CLUSTER_COUNT_LAYER_ID)?.setProperties(visibility(if (showClustersNow) "visible" else "none"))
    // Plain nodes visible when clusters hidden
    style.getLayer(NODES_LAYER_ID)?.setProperties(visibility(if (showClustersNow) "none" else "visible"))
    style.getLayer(NODE_TEXT_LAYER_ID)?.setProperties(visibility(if (showClustersNow) "none" else "visible"))
    // Clustered unclustered-points layers visible when clusters shown
    style.getLayer(NODES_LAYER_CLUSTERED_ID)?.setProperties(visibility(if (showClustersNow) "visible" else "none"))
    style.getLayer(NODE_TEXT_LAYER_CLUSTERED_ID)?.setProperties(visibility(if (showClustersNow) "visible" else "none"))
    Timber.tag("MapLibrePOC").d("Cluster visibility=%s (visible=%d, zoom=%.2f)", showClustersNow, visibleCount, zoom)
}


