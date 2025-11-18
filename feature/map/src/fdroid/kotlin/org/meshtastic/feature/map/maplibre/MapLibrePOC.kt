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
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
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
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.outlined.Layers
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
import org.maplibre.android.style.sources.TileSet
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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
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
private const val CLUSTER_TIER0_LAYER_ID = "meshtastic-cluster-0"
private const val CLUSTER_TIER1_LAYER_ID = "meshtastic-cluster-1"
private const val CLUSTER_TIER2_LAYER_ID = "meshtastic-cluster-2"
private const val CLUSTER_TEXT_TIER_LAYER_ID = "meshtastic-cluster-text"
private const val WAYPOINTS_LAYER_ID = "meshtastic-waypoints-layer"
private const val TRACKS_LAYER_ID = "meshtastic-tracks-layer"
private const val OSM_LAYER_ID = "osm-layer"
private const val CLUSTER_RADIAL_MAX = 8
private const val CLUSTER_LIST_FETCH_MAX = 200L
private const val TEST_POINT_SOURCE_ID = "meshtastic-test-point-source"
private const val TEST_POINT_LAYER_ID = "meshtastic-test-point-layer"
private const val DEBUG_ALL_LAYER_ID = "meshtastic-debug-all"
private const val DEBUG_SYMBOL_LAYER_ID = "meshtastic-debug-symbols"
private const val DEBUG_RAW_SOURCE_ID = "meshtastic-debug-raw-source"
private const val DEBUG_RAW_LAYER_ID = "meshtastic-debug-raw-layer"
private const val DEBUG_RAW_SYMBOL_LAYER_ID = "meshtastic-debug-raw-symbols"

// Base map style options (raster tiles; key-free)
private enum class BaseMapStyle(val label: String, val urlTemplate: String) {
    OSM_STANDARD("OSM", "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png"),
    CARTO_LIGHT("Light", "https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"),
    CARTO_DARK("Dark", "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"),
    ESRI_SATELLITE("Satellite", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"),
}

private fun buildMeshtasticStyle(base: BaseMapStyle): Style.Builder {
    // Load a complete vector style first (has fonts, glyphs, sprites MapLibre needs)
    val builder = Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")
            // Add our raster overlay on top
            .withSource(
                RasterSource(
                    OSM_SOURCE_ID,
                    TileSet("osm", base.urlTemplate).apply {
                        minZoom = 0f
                        maxZoom = 22f
                    },
                    128,
                ),
            )
            .withLayer(RasterLayer(OSM_LAYER_ID, OSM_SOURCE_ID).withProperties(rasterOpacity(1.0f)))
            // Sources
            .withSource(GeoJsonSource(NODES_SOURCE_ID, emptyFeatureCollectionJson()))
            .withSource(
                GeoJsonSource(
                    NODES_CLUSTER_SOURCE_ID,
                    emptyFeatureCollectionJson(),
                    GeoJsonOptions().withCluster(true).withClusterRadius(50).withClusterMaxZoom(14).withClusterMinPoints(2),
                ),
            )
            // Non-clustered debug copy of nodes to bypass clustering entirely
            .withSource(GeoJsonSource(DEBUG_RAW_SOURCE_ID, emptyFeatureCollectionJson()))
            .withSource(GeoJsonSource(WAYPOINTS_SOURCE_ID, emptyFeatureCollectionJson()))
            // Test point source to validate rendering independent of data plumbing
            .withSource(GeoJsonSource(TEST_POINT_SOURCE_ID, emptyFeatureCollectionJson()))
            // Layers - order ensures they are above raster
            .withLayer(
                CircleLayer(CLUSTER_CIRCLE_LAYER_ID, NODES_CLUSTER_SOURCE_ID).withProperties(circleColor("#6D4C41"), circleRadius(14f)).withFilter(has("point_count")),
            )
            .withLayer(
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
                    .withFilter(has("point_count")),
            )
            .withLayer(
                CircleLayer(NODES_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                    .withProperties(
                        circleColor(coalesce(toColor(get("color")), toColor(literal("#2E7D32")))),
                        circleRadius(
                            interpolate(
                                linear(),
                                zoom(),
                                stop(8, 4f),
                                stop(12, 6f),
                                stop(16, 8f),
                                stop(18, 9.5f),
                            ),
                        ),
                    )
                    .withFilter(not(has("point_count"))), // Only show unclustered points
            )
            .withLayer(
                SymbolLayer(NODE_TEXT_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
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
                        textAllowOverlap(step(zoom(), literal(false), stop(11, literal(true)))),
                        textIgnorePlacement(step(zoom(), literal(false), stop(11, literal(true)))),
                        textOffset(arrayOf(0f, -1.4f)),
                        textAnchor("bottom"),
                    )
                    .withFilter(all(not(has("point_count")), eq(get("showLabel"), literal(1)))),
            )
            .withLayer(CircleLayer(WAYPOINTS_LAYER_ID, WAYPOINTS_SOURCE_ID).withProperties(circleColor("#2E7D32"), circleRadius(5f)))
            // Debug layers (hidden by default, can be toggled for diagnosis)
            .withLayer(
                CircleLayer(DEBUG_ALL_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                    .withProperties(circleColor("#FF00FF"), circleRadius(5.0f), visibility("none")),
            )
            .withLayer(
                SymbolLayer(DEBUG_SYMBOL_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                    .withProperties(
                        textField(literal("•")),
                        textColor("#FF00FF"),
                        textSize(14f),
                        textAllowOverlap(true),
                        textIgnorePlacement(true),
                        visibility("none"),
                    ),
            )
            .withLayer(
                CircleLayer(TEST_POINT_LAYER_ID, TEST_POINT_SOURCE_ID)
                    .withProperties(circleColor("#00FF00"), circleRadius(12.0f), visibility("none")),
            )
            .withLayer(
                CircleLayer(DEBUG_RAW_LAYER_ID, DEBUG_RAW_SOURCE_ID)
                    .withProperties(circleColor("#00FFFF"), circleRadius(9.0f), visibility("none")),
            )
            .withLayer(
                SymbolLayer(DEBUG_RAW_SYMBOL_LAYER_ID, DEBUG_RAW_SOURCE_ID)
                    .withProperties(
                        textField(literal("•")),
                        textColor("#00FFFF"),
                        textSize(16f),
                        textAllowOverlap(true),
                        textIgnorePlacement(true),
                        visibility("none"),
                    ),
            )
            // Tiered cluster layers and text (initially hidden; we toggle later)
            .withLayer(
                CircleLayer(CLUSTER_TIER0_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                    .withProperties(circleColor("#6D4C41"), circleRadius(18f), visibility("none"))
                    .withFilter(all(has("point_count"), gte(toNumber(get("point_count")), literal(150)))),
            )
            .withLayer(
                CircleLayer(CLUSTER_TIER1_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                    .withProperties(circleColor("#795548"), circleRadius(18f), visibility("none"))
                    .withFilter(all(has("point_count"), gt(toNumber(get("point_count")), literal(20)), lt(toNumber(get("point_count")), literal(150)))),
            )
            .withLayer(
                CircleLayer(CLUSTER_TIER2_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                    .withProperties(circleColor("#8D6E63"), circleRadius(18f), visibility("none"))
                    .withFilter(all(has("point_count"), gte(toNumber(get("point_count")), literal(0)), lt(toNumber(get("point_count")), literal(20)))),
            )
            .withLayer(
                SymbolLayer(CLUSTER_TEXT_TIER_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                    .withProperties(
                        textField(toString(get("point_count"))),
                        textColor("#FFFFFF"),
                        textHaloColor("#000000"),
                        textHaloWidth(1.5f),
                        textHaloBlur(0.5f),
                        textSize(12f),
                        textAllowOverlap(true),
                        textIgnorePlacement(true),
                        visibility("none"),
                    )
                    .withFilter(has("point_count")),
            )
    return builder
}
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
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var didInitialCenter by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    var enabledRoles by remember { mutableStateOf<Set<ConfigProtos.Config.DeviceConfig.Role>>(emptySet()) }
    var clusteringEnabled by remember { mutableStateOf(true) }
    // Base map style rotation
    val baseStyles = remember { enumValues<BaseMapStyle>().toList() }
    var baseStyleIndex by remember { mutableStateOf(0) }
    val baseStyle = baseStyles[baseStyleIndex % baseStyles.size]
    // Remember last applied cluster visibility to reduce flashing
    var clustersShown by remember { mutableStateOf(false) }
    var lastClusterEvalMs by remember { mutableStateOf(0L) }

    val nodes by mapViewModel.nodes.collectAsStateWithLifecycle()
    val waypoints by mapViewModel.waypoints.collectAsStateWithLifecycle()

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
                            logStyleState("after-style-load(pre-ensure)", style)
                            ensureSourcesAndLayers(style)
                            // Push current data immediately after style load
                            try {
                                val density = context.resources.displayMetrics.density
                                val bounds = map.projection.visibleRegion.latLngBounds
                                val labelSet =
                                    run {
                                        val visible = applyFilters(nodes, mapFilterState, enabledRoles).filter { n ->
                                                val p = n.validPosition ?: return@filter false
                                                bounds.contains(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
                                            }
                                        val sorted = visible.sortedWith(
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
                                (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)
                                    ?.setGeoJson(waypointsToFeatureCollectionFC(waypoints.values))
                                // Set clustered source only (like MapLibre example)
                                val filteredNodes = applyFilters(nodes, mapFilterState, enabledRoles)
                                val json = nodesToFeatureCollectionJsonWithSelection(filteredNodes, labelSet)
                                Timber.tag("MapLibrePOC").d("Setting nodes clustered source: %d nodes, jsonBytes=%d", nodes.size, json.length)
                                safeSetGeoJson(style, NODES_CLUSTER_SOURCE_ID, json)
                                safeSetGeoJson(style, DEBUG_RAW_SOURCE_ID, json)
                                // Place a known visible test point (San Jose area) to verify rendering
                                val testFC = singlePointFC(-121.8893, 37.3349)
                                (style.getSource(TEST_POINT_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(testFC)
                                Timber.tag("MapLibrePOC").d(
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
                            try {
                                    if (hasAnyLocationPermission(context)) {
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
                                    } else {
                                        Timber.tag("MapLibrePOC").w("Location permission missing; skipping location component enablement")
                                    }
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
                                        } ?: run {
                                            // Fallback: center to bounds of current nodes if available
                                            val filtered = applyFilters(nodes, mapFilterState, enabledRoles)
                                            val boundsBuilder = org.maplibre.android.geometry.LatLngBounds.Builder()
                                            var any = false
                                            filtered.forEach { n ->
                                                n.validPosition?.let { vp ->
                                                    boundsBuilder.include(LatLng(vp.latitudeI * DEG_D, vp.longitudeI * DEG_D))
                                                    any = true
                                                }
                                            }
                                            if (any) {
                                                val b = boundsBuilder.build()
                                                map.animateCamera(CameraUpdateFactory.newLatLngBounds(b, 64))
                                            } else {
                                                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(0.0, 0.0), 2.5))
                                            }
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
                                val st = map.style ?: return@addOnCameraIdleListener
                                // Debounce to avoid rapid toggling during kinetic flings/tiles loading
                                val now = SystemClock.uptimeMillis()
                                if (now - lastClusterEvalMs < 300) return@addOnCameraIdleListener
                                lastClusterEvalMs = now
                                val filtered = applyFilters(nodes, mapFilterState, enabledRoles)
                                Timber.tag("MapLibrePOC").d("onCameraIdle: filtered nodes=%d (of %d)", filtered.size, nodes.size)
                                clustersShown = setClusterVisibilityHysteresis(map, st, filtered, clusteringEnabled, clustersShown)
                                // Compute which nodes get labels in viewport and update source
                                val density = context.resources.displayMetrics.density
                                val labelSet = selectLabelsForViewport(map, filtered, density)
                                val jsonIdle = nodesToFeatureCollectionJsonWithSelection(filtered, labelSet)
                                Timber.tag("MapLibrePOC").d("onCameraIdle: updating clustered source. labelSet=%d jsonBytes=%d", labelSet.size, jsonIdle.length)
                                // Update only the clustered source (unclustered points are filtered by not has(\"point_count\"))
                                safeSetGeoJson(st, NODES_CLUSTER_SOURCE_ID, jsonIdle)
                                logStyleState("onCameraIdle(post-update)", st)
                                try {
                                    val w = mapViewRef?.width ?: 0
                                    val h = mapViewRef?.height ?: 0
                                    val bbox = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
                                    val rendered = map.queryRenderedFeatures(
                                        bbox,
                                        DEBUG_ALL_LAYER_ID,
                                        DEBUG_SYMBOL_LAYER_ID,
                                        DEBUG_RAW_LAYER_ID,
                                        DEBUG_RAW_SYMBOL_LAYER_ID,
                                        NODES_LAYER_ID,
                                        CLUSTER_CIRCLE_LAYER_ID,
                                    )
                                    Timber.tag("MapLibrePOC").d("onCameraIdle: rendered features in viewport=%d", rendered.size)
                                } catch (_: Throwable) { }
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
                            val visible = nodes.filter { n ->
                                    val p = n.validPosition ?: return@filter false
                                    bounds2.contains(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
                                }
                            val sorted = visible.sortedWith(
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
                    (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)
                        ?.setGeoJson(waypointsToFeatureCollectionFC(waypoints.values))
                    val filteredNow = applyFilters(nodes, mapFilterState, enabledRoles)
                    val jsonNow = nodesToFeatureCollectionJsonWithSelection(filteredNow, labelSet)
                    safeSetGeoJson(style, NODES_CLUSTER_SOURCE_ID, jsonNow)
                    safeSetGeoJson(style, DEBUG_RAW_SOURCE_ID, jsonNow)
                    // Apply visibility now
                    clustersShown = setClusterVisibilityHysteresis(map, style, applyFilters(nodes, mapFilterState, enabledRoles), clusteringEnabled, clustersShown)
                    logStyleState("update(block)", style)
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
                onClick = {
                    // Cycle base styles via setStyle(new Style.Builder(...))
                    baseStyleIndex = (baseStyleIndex + 1) % baseStyles.size
                    val next = baseStyles[baseStyleIndex]
                    mapRef?.let { map ->
                                map.setStyle(buildMeshtasticStyle(next)) { style ->
                            Timber.tag("MapLibrePOC").d("Base map switched to: %s", next.label)
                            ensureSourcesAndLayers(style)
                            try {
                                val density = context.resources.displayMetrics.density
                                val labelSet = selectLabelsForViewport(map, applyFilters(nodes, mapFilterState, enabledRoles), density)
                                        val filtered = applyFilters(nodes, mapFilterState, enabledRoles)
                                        val json = nodesToFeatureCollectionJsonWithSelection(filtered, labelSet)
                                        safeSetGeoJson(style, NODES_CLUSTER_SOURCE_ID, json)
                                        safeSetGeoJson(style, DEBUG_RAW_SOURCE_ID, json)
                                        (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)
                                            ?.setGeoJson(waypointsToFeatureCollectionFC(waypoints.values))
                                        // Re-arm test point after style switch
                                        (style.getSource(TEST_POINT_SOURCE_ID) as? GeoJsonSource)
                                            ?.setGeoJson(singlePointFC(-121.8893, 37.3349))
                                // Re-enable location component for new style
                                try {
                                            if (hasAnyLocationPermission(context)) {
                                                val locationComponent = map.locationComponent
                                                locationComponent.activateLocationComponent(
                                                    org.maplibre.android.location.LocationComponentActivationOptions.builder(
                                                        context,
                                                        style,
                                                    ).useDefaultLocationEngine(true).build(),
                                                )
                                                locationComponent.isLocationComponentEnabled = true
                                                locationComponent.renderMode = RenderMode.COMPASS
                                            } else {
                                                Timber.tag("MapLibrePOC").w("Location permission missing on style switch; skipping location component")
                                            }
                                } catch (_: Throwable) {
                                }
                            } catch (_: Throwable) {
                            }
                        }
                    }
                },
                icon = Icons.Outlined.Layers,
                contentDescription = null,
            )
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
                        trailingIcon = { Checkbox(checked = mapFilterState.onlyFavorites, onCheckedChange = {
                            mapViewModel.toggleOnlyFavorites()
                            // Refresh both sources when filters change
                            mapRef?.style?.let { st ->
                                val filtered = applyFilters(nodes, mapFilterState.copy(onlyFavorites = !mapFilterState.onlyFavorites), enabledRoles)
                                (st.getSource(NODES_SOURCE_ID) as? GeoJsonSource)
                                    ?.setGeoJson(nodesToFeatureCollectionJsonWithSelection(filtered, emptySet()))
                                (st.getSource(NODES_CLUSTER_SOURCE_ID) as? GeoJsonSource)
                                    ?.setGeoJson(nodesToFeatureCollectionJsonWithSelection(filtered, emptySet()))
                            }
                        }) },
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
                                mapRef?.style?.let { st -> mapRef?.let { map ->
                                    clustersShown = setClusterVisibilityHysteresis(map, st, applyFilters(nodes, mapFilterState, enabledRoles), clusteringEnabled, clustersShown)
                                } }
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        enabledRoles =
                                            if (enabledRoles.isEmpty()) setOf(role)
                                            else if (enabledRoles.contains(role)) enabledRoles - role else enabledRoles + role
                                        mapRef?.style?.let { st -> mapRef?.let { map ->
                                            clustersShown = setClusterVisibilityHysteresis(map, st, applyFilters(nodes, mapFilterState, enabledRoles), clusteringEnabled, clustersShown)
                                        } }
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
                            mapRef?.style?.let { st -> mapRef?.let { map ->
                                clustersShown = setClusterVisibilityHysteresis(map, st, applyFilters(nodes, mapFilterState, enabledRoles), clusteringEnabled, clustersShown)
                            } }
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = clusteringEnabled,
                                onCheckedChange = {
                                    clusteringEnabled = it
                                    mapRef?.style?.let { st -> mapRef?.let { map ->
                                        clustersShown = setClusterVisibilityHysteresis(map, st, applyFilters(nodes, mapFilterState, enabledRoles), clusteringEnabled, clustersShown)
                                    } }
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
    if (style.getSource(NODES_SOURCE_ID) == null) {
        // Plain (non-clustered) source for nodes and labels
        style.addSource(GeoJsonSource(NODES_SOURCE_ID, emptyFeatureCollectionJson()))
        Timber.tag("MapLibrePOC").d("Added nodes plain GeoJsonSource")
    }
    if (style.getSource(NODES_CLUSTER_SOURCE_ID) == null) {
        // Clustered source for cluster layers
        val options =
            GeoJsonOptions()
                .withCluster(true)
                .withClusterRadius(50) // match TestApp defaults
                .withClusterMaxZoom(14) // allow clusters up to z14 like examples
                .withClusterMinPoints(2)
        style.addSource(GeoJsonSource(NODES_CLUSTER_SOURCE_ID, emptyFeatureCollectionJson(), options))
        Timber.tag("MapLibrePOC").d("Added nodes clustered GeoJsonSource")
    }
    if (style.getSource(WAYPOINTS_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(WAYPOINTS_SOURCE_ID, emptyFeatureCollectionJson()))
        Timber.tag("MapLibrePOC").d("Added waypoints GeoJsonSource")
    }
    // Removed test track GeoJsonSource

    if (style.getLayer(CLUSTER_CIRCLE_LAYER_ID) == null) {
        val clusterLayer =
            CircleLayer(CLUSTER_CIRCLE_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(circleColor("#6D4C41"), circleRadius(14f))
                .withFilter(has("point_count"))
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(clusterLayer, OSM_LAYER_ID) else style.addLayer(clusterLayer)
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
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(countLayer, OSM_LAYER_ID) else style.addLayer(countLayer)
        Timber.tag("MapLibrePOC").d("Added cluster count SymbolLayer")
    }
    // Tiered cluster layers similar to GeoJsonClusteringActivity
    if (style.getLayer(CLUSTER_TIER0_LAYER_ID) == null) {
        val pointCount = toNumber(get("point_count"))
        val layer =
            CircleLayer(CLUSTER_TIER0_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(circleColor("#6D4C41"), circleRadius(18f), visibility("none"))
                .withFilter(
                    all(
                        has("point_count"),
                        gte(pointCount, literal(150)),
                    ),
                )
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(layer, OSM_LAYER_ID) else style.addLayer(layer)
    }
    if (style.getLayer(CLUSTER_TIER1_LAYER_ID) == null) {
        val pointCount = toNumber(get("point_count"))
        val layer =
            CircleLayer(CLUSTER_TIER1_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(circleColor("#795548"), circleRadius(18f), visibility("none"))
                .withFilter(
                    all(
                        has("point_count"),
                        gt(pointCount, literal(20)),
                        lt(pointCount, literal(150)),
                    ),
                )
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(layer, OSM_LAYER_ID) else style.addLayer(layer)
    }
    if (style.getLayer(CLUSTER_TIER2_LAYER_ID) == null) {
        val pointCount = toNumber(get("point_count"))
        val layer =
            CircleLayer(CLUSTER_TIER2_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(circleColor("#8D6E63"), circleRadius(18f), visibility("none"))
                .withFilter(
                    all(
                        has("point_count"),
                        gte(pointCount, literal(0)),
                        lt(pointCount, literal(20)),
                    ),
                )
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(layer, OSM_LAYER_ID) else style.addLayer(layer)
    }
    if (style.getLayer(CLUSTER_TEXT_TIER_LAYER_ID) == null) {
        val textLayer =
            SymbolLayer(CLUSTER_TEXT_TIER_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(
                    textField(toString(get("point_count"))),
                    textColor("#FFFFFF"),
                    textHaloColor("#000000"),
                    textHaloWidth(1.5f),
                    textHaloBlur(0.5f),
                    textSize(12f),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                    visibility("none"),
                )
                .withFilter(has("point_count"))
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(textLayer, OSM_LAYER_ID) else style.addLayer(textLayer)
    }
    if (style.getLayer(NODES_LAYER_ID) == null) {
        val layer =
            CircleLayer(NODES_LAYER_ID, DEBUG_RAW_SOURCE_ID)
                .withProperties(circleColor(coalesce(toColor(get("color")), toColor(literal("#2E7D32")))), circleRadius(7f))
                // No filter: render raw points for diagnosis
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(layer, OSM_LAYER_ID) else style.addLayer(layer)
        Timber.tag("MapLibrePOC").d("Added nodes CircleLayer")
    }
    if (style.getLayer(NODE_TEXT_LAYER_ID) == null) {
        val textLayer =
            SymbolLayer(NODE_TEXT_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
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
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(textLayer, OSM_LAYER_ID) else style.addLayer(textLayer)
        Timber.tag("MapLibrePOC").d("Added node text SymbolLayer")
    }
    if (style.getLayer(WAYPOINTS_LAYER_ID) == null) {
        val layer = CircleLayer(WAYPOINTS_LAYER_ID, WAYPOINTS_SOURCE_ID).withProperties(circleColor("#2E7D32"), circleRadius(5f))
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(layer, OSM_LAYER_ID) else style.addLayer(layer)
        Timber.tag("MapLibrePOC").d("Added waypoints CircleLayer")
    }
    // Removed test track LineLayer
    Timber.tag("MapLibrePOC").d("ensureSourcesAndLayers() end. Layers=%d, Sources=%d", style.layers.size, style.sources.size)
    val order = try { style.layers.joinToString(" > ") { it.id } } catch (_: Throwable) { "<unavailable>" }
    Timber.tag("MapLibrePOC").d("Layer order: %s", order)
    logStyleState("ensureSourcesAndLayers(end)", style)
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
            val longEsc = escapeJson(node.user.longName ?: "")
            // Default showLabel=0; it will be turned on for selected nodes by viewport selection
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"kind":"node","num":${node.num},"name":"$longEsc","short":"$short","role":"$role","color":"$color","showLabel":0}}"""
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
            val longEsc = escapeJson(node.user.longName ?: "")
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"kind":"node","num":${node.num},"name":"$longEsc","short":"$short","role":"$role","color":"$color","showLabel":$show}}"""
        }
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

// FeatureCollection builders using MapLibre GeoJSON types (safer than manual JSON strings)
private fun nodesToFeatureCollectionWithSelectionFC(nodes: List<Node>, labelNums: Set<Int>): FeatureCollection {
    var minLat = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var minLon = Double.POSITIVE_INFINITY
    var maxLon = Double.NEGATIVE_INFINITY
    val features =
        nodes.mapNotNull { node ->
            val pos = node.validPosition ?: return@mapNotNull null
            val lat = pos.latitudeI * DEG_D
            val lon = pos.longitudeI * DEG_D
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
            val point = Point.fromLngLat(lon, lat)
            val f = Feature.fromGeometry(point)
            f.addStringProperty("kind", "node")
            f.addNumberProperty("num", node.num)
            f.addStringProperty("name", node.user.longName ?: "")
            val short = (protoShortName(node) ?: shortNameFallback(node)).take(4)
            f.addStringProperty("short", short)
            f.addStringProperty("role", node.user.role.name)
            f.addStringProperty("color", roleColorHex(node))
            f.addNumberProperty("showLabel", if (labelNums.contains(node.num)) 1 else 0)
            f
        }
    Timber.tag("MapLibrePOC").d(
        "FC bounds: lat=[%.5f, %.5f] lon=[%.5f, %.5f] count=%d",
        minLat, maxLat, minLon, maxLon, features.size,
    )
    return FeatureCollection.fromFeatures(features)
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

private fun waypointsToFeatureCollectionFC(
    waypoints: Collection<org.meshtastic.core.database.entity.Packet>,
): FeatureCollection {
    val features =
        waypoints.mapNotNull { pkt ->
            val w: Waypoint = pkt.data.waypoint ?: return@mapNotNull null
            val lat = w.latitudeI * DEG_D
            val lon = w.longitudeI * DEG_D
            if (lat == 0.0 && lon == 0.0) return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(lon, lat)).also { it.addNumberProperty("id", w.id) }
        }
    return FeatureCollection.fromFeatures(features)
}

private fun emptyFeatureCollectionJson(): String {
    return """{"type":"FeatureCollection","features":[]}"""
}

private fun singlePointFC(lon: Double, lat: Double): FeatureCollection {
    val f = Feature.fromGeometry(Point.fromLngLat(lon, lat))
    return FeatureCollection.fromFeatures(listOf(f))
}

private fun safeSetGeoJson(style: Style, sourceId: String, json: String) {
    try {
        val fc = FeatureCollection.fromJson(json)
        val count = fc.features()?.size ?: -1
        Timber.tag("MapLibrePOC").d("safeSetGeoJson(%s): features=%d", sourceId, count)
        (style.getSource(sourceId) as? GeoJsonSource)?.setGeoJson(fc)
    } catch (t: Throwable) {
        Timber.tag("MapLibrePOC").e(t, "safeSetGeoJson(%s) failed to parse", sourceId)
    }
}

// Minimal JSON string escaper for embedding user-provided names in properties
private fun escapeJson(input: String): String {
    if (input.isEmpty()) return ""
    val sb = StringBuilder(input.length + 8)
    input.forEach { ch ->
        when (ch) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> {
                if (ch < ' ') {
                    val hex = ch.code.toString(16).padStart(4, '0')
                    sb.append("\\u").append(hex)
                } else {
                    sb.append(ch)
                }
            }
        }
    }
    return sb.toString()
}

// Log current style state: presence and visibility of key layers/sources
private fun logStyleState(whenTag: String, style: Style) {
    try {
        val layersToCheck =
            listOf(
                OSM_LAYER_ID,
                CLUSTER_CIRCLE_LAYER_ID,
                CLUSTER_COUNT_LAYER_ID,
                CLUSTER_TIER0_LAYER_ID,
                CLUSTER_TIER1_LAYER_ID,
                CLUSTER_TIER2_LAYER_ID,
                CLUSTER_TEXT_TIER_LAYER_ID,
                NODES_LAYER_ID,
                NODE_TEXT_LAYER_ID,
                NODES_LAYER_CLUSTERED_ID,
                NODE_TEXT_LAYER_CLUSTERED_ID,
                WAYPOINTS_LAYER_ID,
                DEBUG_ALL_LAYER_ID,
            )
        val sourcesToCheck = listOf(NODES_SOURCE_ID, NODES_CLUSTER_SOURCE_ID, WAYPOINTS_SOURCE_ID, OSM_SOURCE_ID)
        val layerStates =
            layersToCheck.joinToString(", ") { id ->
                val layer = style.getLayer(id)
                if (layer == null) "$id=∅" else "$id=${layer.visibility?.value}"
            }
        val sourceStates =
            sourcesToCheck.joinToString(", ") { id ->
                if (style.getSource(id) == null) "$id=∅" else "$id=ok"
            }
        Timber.tag("MapLibrePOC").d("[%s] Layers: %s", whenTag, layerStates)
        Timber.tag("MapLibrePOC").d("[%s] Sources: %s", whenTag, sourceStates)
    } catch (_: Throwable) {
    }
}

private fun hasAnyLocationPermission(context: Context): Boolean {
    val fine =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
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
private fun setClusterVisibilityHysteresis(
    map: MapLibreMap,
    style: Style,
    filteredNodes: List<Node>,
    enableClusters: Boolean,
    currentlyShown: Boolean,
): Boolean {
    try {
        val zoom = map.cameraPosition.zoom
        // Render like the MapLibre example:
        // - Always show unclustered nodes (filtered by not has("point_count"))
        // - Show cluster circle/count layers only when clustering is enabled
        val showClusters = enableClusters

        // Enforce intended visibility
        // Cluster circle/count
        style.getLayer(CLUSTER_CIRCLE_LAYER_ID)?.setProperties(visibility(if (showClusters) "visible" else "none"))
        style.getLayer(CLUSTER_COUNT_LAYER_ID)?.setProperties(visibility(if (showClusters) "visible" else "none"))
        // Hide tiered cluster layers for now (ignored)
        style.getLayer(CLUSTER_TIER0_LAYER_ID)?.setProperties(visibility("none"))
        style.getLayer(CLUSTER_TIER1_LAYER_ID)?.setProperties(visibility("none"))
        style.getLayer(CLUSTER_TIER2_LAYER_ID)?.setProperties(visibility("none"))
        style.getLayer(CLUSTER_TEXT_TIER_LAYER_ID)?.setProperties(visibility("none"))
        // Always show unclustered nodes and labels; label density still controlled by showLabel property
        style.getLayer(NODES_LAYER_ID)?.setProperties(visibility("visible"))
        style.getLayer(NODE_TEXT_LAYER_ID)?.setProperties(visibility("visible"))
        // Legacy clustered node layers are not used
        style.getLayer(NODES_LAYER_CLUSTERED_ID)?.setProperties(visibility("none"))
        style.getLayer(NODE_TEXT_LAYER_CLUSTERED_ID)?.setProperties(visibility("none"))
        if (showClusters != currentlyShown) {
            Timber.tag("MapLibrePOC").d("Cluster visibility=%s (zoom=%.2f)", showClusters, zoom)
        }
        return showClusters
    } catch (_: Throwable) {
        return currentlyShown
    }
}


