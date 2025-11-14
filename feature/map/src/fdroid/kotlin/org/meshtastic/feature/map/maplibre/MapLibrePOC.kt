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
 
import org.meshtastic.core.database.model.Node
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.MapViewModel
import org.meshtastic.proto.MeshProtos.Waypoint
import timber.log.Timber
import org.meshtastic.core.ui.component.NodeChip
import org.maplibre.android.style.layers.BackgroundLayer

private const val DEG_D = 1e-7

private const val STYLE_URL = "https://demotiles.maplibre.org/style.json"

private const val NODES_SOURCE_ID = "meshtastic-nodes-source"
private const val WAYPOINTS_SOURCE_ID = "meshtastic-waypoints-source"
private const val TRACKS_SOURCE_ID = "meshtastic-tracks-source"
private const val OSM_SOURCE_ID = "osm-tiles"

private const val NODES_LAYER_ID = "meshtastic-nodes-layer"
private const val NODE_TEXT_LAYER_ID = "meshtastic-node-text-layer"
private const val CLUSTER_CIRCLE_LAYER_ID = "meshtastic-cluster-circle-layer"
private const val CLUSTER_COUNT_LAYER_ID = "meshtastic-cluster-count-layer"
private const val WAYPOINTS_LAYER_ID = "meshtastic-waypoints-layer"
private const val TRACKS_LAYER_ID = "meshtastic-tracks-layer"
private const val OSM_LAYER_ID = "osm-layer"

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
                                            nodes.filter { n ->
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
                                    ?.setGeoJson(nodesToFeatureCollectionJsonWithSelection(nodes, labelSet))
                                (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)
                                    ?.setGeoJson(waypointsToFeatureCollectionJson(waypoints.values))
                                (style.getSource(TRACKS_SOURCE_ID) as? GeoJsonSource)
                                    ?.setGeoJson(oneTrackFromNodesJson(nodes))
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
                            map.addOnMapClickListener { latLng ->
                                val screenPoint = map.projection.toScreenLocation(latLng)
                                // Use a small hitbox to improve taps on small circles
                                val r = (24 * context.resources.displayMetrics.density)
                                val rect = android.graphics.RectF(
                                    (screenPoint.x - r).toFloat(),
                                    (screenPoint.y - r).toFloat(),
                                    (screenPoint.x + r).toFloat(),
                                    (screenPoint.y + r).toFloat(),
                                )
                                val features = map.queryRenderedFeatures(rect, CLUSTER_CIRCLE_LAYER_ID, NODES_LAYER_ID, WAYPOINTS_LAYER_ID)
                                Timber.tag("MapLibrePOC").d("Map click at (%.5f, %.5f) -> %d features", latLng.latitude, latLng.longitude, features.size)
                                val f = features.firstOrNull()
                                // If cluster tapped, zoom in a bit
                                if (f != null && f.hasProperty("point_count")) {
                                    map.animateCamera(CameraUpdateFactory.zoomIn())
                                    return@addOnMapClickListener true
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
                                val zoomNow = map.cameraPosition.zoom
                                val bounds = map.projection.visibleRegion.latLngBounds
                                val visibleCount =
                                    nodes.count { n ->
                                        val p = n.validPosition ?: return@count false
                                        val lat = p.latitudeI * DEG_D
                                        val lon = p.longitudeI * DEG_D
                                        bounds.contains(LatLng(lat, lon))
                                    }
                                // Cluster only when zoom <= 10 and viewport density is high
                                val showClustersNow = zoomNow <= 10.0 && visibleCount > 50
                                style.getLayer(CLUSTER_CIRCLE_LAYER_ID)?.setProperties(visibility(if (showClustersNow) "visible" else "none"))
                                style.getLayer(CLUSTER_COUNT_LAYER_ID)?.setProperties(visibility(if (showClustersNow) "visible" else "none"))
                                Timber.tag("MapLibrePOC").d(
                                    "Camera idle; cluster visibility=%s (visible=%d, zoom=%.2f)",
                                    showClustersNow,
                                    visibleCount,
                                    zoomNow,
                                )
                                // Compute which nodes get labels in viewport and update source
                                val density = context.resources.displayMetrics.density
                                val labelSet = selectLabelsForViewport(map, nodes, density)
                                (style.getSource(NODES_SOURCE_ID) as? GeoJsonSource)
                                    ?.setGeoJson(nodesToFeatureCollectionJsonWithSelection(nodes, labelSet))
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
                    (style.getSource(TRACKS_SOURCE_ID) as? GeoJsonSource)
                        ?.setGeoJson(oneTrackFromNodesJson(nodes))
                    // Toggle clustering visibility based on zoom and VISIBLE node count (viewport density)
                    val zoom = map.cameraPosition.zoom
                    val bounds = map.projection.visibleRegion.latLngBounds
                    val visibleCount =
                        nodes.count { n ->
                            val p = n.validPosition ?: return@count false
                            val lat = p.latitudeI * DEG_D
                            val lon = p.longitudeI * DEG_D
                            bounds.contains(LatLng(lat, lon))
                        }
                    val showClusters = zoom <= 10.0 && visibleCount > 50
                    style.getLayer(CLUSTER_CIRCLE_LAYER_ID)?.setProperties(visibility(if (showClusters) "visible" else "none"))
                    style.getLayer(CLUSTER_COUNT_LAYER_ID)?.setProperties(visibility(if (showClusters) "visible" else "none"))
                    Timber.tag("MapLibrePOC").d(
                        "Sources updated; cluster visibility=%s (visible=%d, zoom=%.2f)",
                        showClusters,
                        visibleCount,
                        zoom,
                    )
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

        // Bottom sheet with node details and actions
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val selectedNode = selectedNodeNum?.let { num -> nodes.firstOrNull { it.num == num } }
        if (selectedNode != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedNodeNum = null },
                sheetState = sheetState,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    NodeChip(node = selectedNode)
                    val lastHeard = selectedNode.lastHeard
                    val coords = selectedNode.gpsString()
                    Text(text = "Last heard: $lastHeard s ago")
                    Text(text = "Coordinates: $coords")
                    // Quick actions (placeholder)
                    Button(
                        onClick = {
                            onNavigateToNodeDetails(selectedNode.num)
                            selectedNodeNum = null
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("View full node")
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
        // Enable clustering only for lower zooms; stop clustering once zoom > 10
        val options = GeoJsonOptions()
            .withCluster(true)
            .withClusterRadius(36)
            .withClusterMaxZoom(10)
        style.addSource(GeoJsonSource(NODES_SOURCE_ID, emptyFeatureCollectionJson(), options))
        Timber.tag("MapLibrePOC").d("Added nodes GeoJsonSource")
    }
    if (style.getSource(WAYPOINTS_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(WAYPOINTS_SOURCE_ID, emptyFeatureCollectionJson()))
        Timber.tag("MapLibrePOC").d("Added waypoints GeoJsonSource")
    }
    if (style.getSource(TRACKS_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(TRACKS_SOURCE_ID, emptyFeatureCollectionJson()))
        Timber.tag("MapLibrePOC").d("Added tracks GeoJsonSource")
    }

    if (style.getLayer(OSM_LAYER_ID) == null) {
        // Put OSM tiles on TOP so labels/roads are visible during POC
        val rl = RasterLayer(OSM_LAYER_ID, OSM_SOURCE_ID).withProperties(rasterOpacity(1.0f))
        // Add early, then ensure it's below node layers if present
        style.addLayer(rl)
        Timber.tag("MapLibrePOC").d("Added OSM RasterLayer")
    }
    if (style.getLayer(CLUSTER_CIRCLE_LAYER_ID) == null) {
        val clusterLayer =
            CircleLayer(CLUSTER_CIRCLE_LAYER_ID, NODES_SOURCE_ID)
                .withProperties(circleColor("#6D4C41"), circleRadius(14f))
                .withFilter(has("point_count"))
        style.addLayer(clusterLayer)
        Timber.tag("MapLibrePOC").d("Added cluster CircleLayer")
    }
    if (style.getLayer(CLUSTER_COUNT_LAYER_ID) == null) {
        val countLayer =
            SymbolLayer(CLUSTER_COUNT_LAYER_ID, NODES_SOURCE_ID)
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
                .withProperties(circleColor("#1565C0"), circleRadius(6f))
                .withFilter(not(has("point_count")))
        style.addLayer(layer)
        Timber.tag("MapLibrePOC").d("Added nodes CircleLayer")
    }
    if (style.getLayer(NODE_TEXT_LAYER_ID) == null) {
        val textLayer =
            SymbolLayer(NODE_TEXT_LAYER_ID, NODES_SOURCE_ID)
                .withProperties(
                    textField(get("short")),
                    textColor("#FFFFFF"),
                    textHaloColor("#000000"),
                    textHaloWidth(1.5f),
                    textHaloBlur(0.5f),
                    // Scale label size with zoom to reduce clutter
                    textSize(
                        interpolate(
                            linear(),
                            zoom(),
                            stop(8, 10f),
                            stop(12, 12f),
                            stop(15, 14f),
                            stop(18, 16f),
                        ),
                    ),
                    // At close zooms, prefer showing all labels even if they overlap
                    textAllowOverlap(
                        step(
                            zoom(),
                            literal(false), // default for low zooms
                            stop(12, literal(true)), // enable overlap >= 12
                        ),
                    ),
                    textIgnorePlacement(
                        step(
                            zoom(),
                            literal(false),
                            stop(12, literal(true)),
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
    if (style.getLayer(WAYPOINTS_LAYER_ID) == null) {
        val layer = CircleLayer(WAYPOINTS_LAYER_ID, WAYPOINTS_SOURCE_ID).withProperties(circleColor("#2E7D32"), circleRadius(5f))
        style.addLayer(layer)
        Timber.tag("MapLibrePOC").d("Added waypoints CircleLayer")
    }
    if (style.getLayer(TRACKS_LAYER_ID) == null) {
        val layer = LineLayer(TRACKS_LAYER_ID, TRACKS_SOURCE_ID).withProperties(lineColor("#FF6D00"), lineWidth(3f))
        style.addLayer(layer)
        Timber.tag("MapLibrePOC").d("Added tracks LineLayer")
    }
    Timber.tag("MapLibrePOC").d("ensureSourcesAndLayers() end. Layers=%d, Sources=%d", style.layers.size, style.sources.size)
}

private fun nodesToFeatureCollectionJson(nodes: List<Node>): String {
    val features =
        nodes.mapNotNull { node ->
            val pos = node.validPosition ?: return@mapNotNull null
            val lat = pos.latitudeI * DEG_D
            val lon = pos.longitudeI * DEG_D
            val short = protoShortName(node) ?: shortNameFallback(node)
            // Default showLabel=0; it will be turned on for selected nodes by viewport selection
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"kind":"node","num":${node.num},"name":"${node.user.longName}","short":"$short","showLabel":0}}"""
        }
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

private fun nodesToFeatureCollectionJsonWithSelection(nodes: List<Node>, labelNums: Set<Int>): String {
    val features =
        nodes.mapNotNull { node ->
            val pos = node.validPosition ?: return@mapNotNull null
            val lat = pos.latitudeI * DEG_D
            val lon = pos.longitudeI * DEG_D
            val short = protoShortName(node) ?: shortNameFallback(node)
            val show = if (labelNums.contains(node.num)) 1 else 0
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"kind":"node","num":${node.num},"name":"${node.user.longName}","short":"$short","showLabel":$show}}"""
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
    val cellSizePx = (80f * density).toInt().coerceAtLeast(48)
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


