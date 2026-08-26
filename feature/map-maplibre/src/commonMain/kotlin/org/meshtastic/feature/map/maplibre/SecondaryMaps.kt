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
package org.meshtastic.feature.map.maplibre

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.util.DiscoveryMapNode
import org.meshtastic.feature.map.maplibre.component.SecondaryMapControls
import org.meshtastic.feature.map.maplibre.component.rememberBasemapSelection
import org.meshtastic.feature.map.maplibre.geojson.NodeFeatureKeys
import org.meshtastic.feature.map.maplibre.geojson.nodesToFeatureCollection
import org.meshtastic.feature.map.maplibre.geojson.rememberFeatureSource
import org.meshtastic.feature.map.maplibre.layers.RasterBasemapLayer
import org.meshtastic.feature.map.maplibre.layers.TracerouteLayers
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.MapColors
import org.meshtastic.feature.map.maplibre.style.toBaseStyle
import org.meshtastic.feature.map.maplibre.style.zoomRange
import org.maplibre.spatialk.geojson.Position as GeoPosition

internal const val DEG_SCALE = 1e-7
internal const val DETAIL_ZOOM = 13.0

/**
 * Single-node mini-map embedded in the node detail sheet.
 *
 * No toolbar: it is a thumbnail a couple of hundred dp tall, and a floating toolbar would cover the node it exists to
 * show. It still follows the basemap preference, so it matches the map the user was just looking at.
 */
@Composable
fun MapLibreInlineMap(
    node: Node,
    modifier: Modifier = Modifier,
    customBasemaps: @Composable () -> List<Basemap.Raster> = { emptyList() },
) {
    if (node.validPosition == null) return
    val basemaps = rememberBasemapSelection(customBasemaps())
    val cameraState =
        rememberCameraState(
            CameraPosition(
                target = GeoPosition(longitude = node.longitude, latitude = node.latitude),
                zoom = DETAIL_ZOOM,
            ),
        )

    MaplibreMap(
        baseStyle = basemaps.current.toBaseStyle(),
        cameraState = cameraState,
        modifier = modifier,
        options = SecondaryMapOptions,
        zoomRange = basemaps.current.zoomRange(),
    ) {
        (basemaps.current as? Basemap.Raster)?.let { RasterBasemapLayer(it) }

        val source = rememberFeatureSource(nodesToFeatureCollection(listOf(node)))
        CircleLayer(
            id = "inline-node",
            source = source,
            color = const(Color(node.colors.second)),
            radius = const(10.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
        )
    }
}

/**
 * Traceroute result map: forward and return paths plus the hops they pass through.
 *
 * The Google flavor reaches this through its main map composable, so it arrives with the toolbar and the basemap
 * already chosen. Here it is its own composable and has to ask for both. No filter: a traceroute is the route it is,
 * and there is nothing on it the main map's filters apply to.
 */
@Composable
fun MapLibreTracerouteMap(
    forwardRoute: List<Int>,
    returnRoute: List<Int>,
    nodeLookup: Map<Int, Node>,
    modifier: Modifier = Modifier,
    customBasemaps: @Composable () -> List<Basemap.Raster> = { emptyList() },
) {
    val cameraState = rememberCameraState()
    val basemaps = rememberBasemapSelection(customBasemaps())
    val hops = (forwardRoute + returnRoute).distinct().mapNotNull { nodeLookup[it] }

    // Wait for the map to report a viewport before fitting. Fitting a bounding box needs a viewport size, and on
    // first composition there is none yet — the fit silently landed on a default, which is why this map used to open
    // zoomed past the ends of the route it exists to show.
    val hasViewport = cameraState.viewport != null
    LaunchedEffect(hops.size, hasViewport) {
        if (hasViewport) {
            // Padded, as the Google flavor pads its own bounds fit: an edge-to-edge fit puts the outermost hops under
            // the toolbar and the legend.
            nodesBoundingBox(hops)?.let {
                cameraState.jumpTo(boundingBox = it, padding = PaddingValues(FIT_PADDING.dp))
            }
        }
    }

    Box(modifier = modifier) {
        MaplibreMap(
            baseStyle = basemaps.current.toBaseStyle(),
            cameraState = cameraState,
            modifier = Modifier.fillMaxSize(),
            options = SecondaryMapOptions,
            zoomRange = basemaps.current.zoomRange(),
        ) {
            (basemaps.current as? Basemap.Raster)?.let { RasterBasemapLayer(it) }
            TracerouteLayers(forwardRoute = forwardRoute, returnRoute = returnRoute, nodeLookup = nodeLookup)
            TracerouteHopLayers(hops)
        }
        SecondaryMapControls(
            cameraState = cameraState,
            basemaps = basemaps,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = TOOLBAR_INSET.dp),
        )
    }
}

@Composable
private fun TracerouteHopLayers(hops: List<Node>) {
    val hopSource = rememberFeatureSource(nodesToFeatureCollection(hops))
    CircleLayer(
        id = "traceroute-hops",
        source = hopSource,
        color = const(MapColors.Slate),
        radius = const(10.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
    )
    SymbolLayer(
        id = "traceroute-hop-labels",
        source = hopSource,
        textField = feature[NodeFeatureKeys.SHORT_NAME].asString(),
        textColor = const(Color.White),
        textAllowOverlap = const(true),
    )
}

/**
 * Discovery scan summary map: the scanner plus everything it heard.
 *
 * Same treatment as the traceroute map, and for the same reason: satellite imagery is often the useful backdrop for
 * asking why one node was heard and another was not, and until now this map could not show it.
 */
@Composable
fun MapLibreDiscoveryMap(
    userLatitude: Double,
    userLongitude: Double,
    nodes: List<DiscoveryMapNode>,
    modifier: Modifier = Modifier,
    customBasemaps: @Composable () -> List<Basemap.Raster> = { emptyList() },
) {
    val scanner = GeoPosition(longitude = userLongitude, latitude = userLatitude)
    val cameraState = rememberCameraState(CameraPosition(target = scanner, zoom = DETAIL_ZOOM))
    val basemaps = rememberBasemapSelection(customBasemaps())

    // Frame the scanner together with everything it heard, which is the OSMdroid map's behaviour and the only view
    // that answers the question this map is opened to answer. Opening at a fixed zoom on the scanner put every
    // discovered node off screen. Waits for a viewport, since fitting a bounding box needs one.
    val hasViewport = cameraState.viewport != null
    LaunchedEffect(nodes.size, hasViewport) {
        if (hasViewport) {
            discoveryBoundingBox(scanner, nodes)?.let {
                cameraState.jumpTo(boundingBox = it, padding = PaddingValues(FIT_PADDING.dp))
            }
        }
    }

    Box(modifier = modifier) {
        MaplibreMap(
            baseStyle = basemaps.current.toBaseStyle(),
            cameraState = cameraState,
            modifier = Modifier.fillMaxSize(),
            options = SecondaryMapOptions,
            zoomRange = basemaps.current.zoomRange(),
        ) {
            (basemaps.current as? Basemap.Raster)?.let { RasterBasemapLayer(it) }
            DiscoveredNodeLayers(nodes)
            ScannerLayer(scanner)
        }
        SecondaryMapControls(
            cameraState = cameraState,
            basemaps = basemaps,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = TOOLBAR_INSET.dp),
        )
    }
}

@Composable
private fun DiscoveredNodeLayers(nodes: List<DiscoveryMapNode>) {
    val source =
        rememberFeatureSource(
            FeatureCollection(
                nodes.map { node ->
                    Feature<Point, JsonObject?>(
                        geometry = Point(GeoPosition(longitude = node.longitude, latitude = node.latitude)),
                        properties =
                        buildJsonObject {
                            put(NodeFeatureKeys.SHORT_NAME, node.shortName.orEmpty())
                            put("snr", node.snr)
                        },
                    )
                },
            ),
        )
    CircleLayer(
        id = "discovery-nodes",
        source = source,
        color = const(MapColors.RouteReturn),
        radius = const(9.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
    )
    SymbolLayer(
        id = "discovery-labels",
        source = source,
        textField = feature[NodeFeatureKeys.SHORT_NAME].asString(),
        textColor = const(Color.White),
        textAllowOverlap = const(true),
    )
}

@Composable
private fun ScannerLayer(scanner: GeoPosition) {
    val source =
        rememberFeatureSource(
            FeatureCollection(
                listOf(
                    Feature<Point, JsonObject?>(
                        geometry = Point(scanner),
                        properties = buildJsonObject { put("role", "scanner") },
                    ),
                ),
            ),
        )
    CircleLayer(
        id = "discovery-scanner",
        source = source,
        color = const(MapColors.Highlight),
        radius = const(11.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(3.dp),
    )
}

/**
 * Gesture set for the small informational maps.
 *
 * Tilt is off. These are compact, often only a couple of hundred pixels tall, and they carry no toolbar — so a stray
 * two-finger drag pitches the map with nothing on screen to put it back. The Google flavor makes the same call with
 * `tiltGesturesEnabled = isMainMode`, and the OSMdroid map never had tilt at all. Rotation stays, which is what Google
 * does too.
 */
internal val SecondaryMapOptions =
    MapOptions(gestureOptions = GestureOptions(isDragRotateTiltEnabled = false, isTwoFingerTiltEnabled = false))

/** Keeps a floating toolbar clear of the top edge, matching the main map's own inset. */
internal const val TOOLBAR_INSET = 8

/** Breathing room around a bounds fit, so the outermost points are not under the toolbar or the legend. */
private const val FIT_PADDING = 48

/**
 * A box covering the scanner and every node it heard.
 *
 * Only nodes with real coordinates count — a discovered node can be heard without ever reporting a position, and
 * folding a (0, 0) into the box would stretch it into the Atlantic.
 */
private fun discoveryBoundingBox(scanner: GeoPosition, nodes: List<DiscoveryMapNode>): BoundingBox? {
    val located = nodes.filter { it.latitude != 0.0 || it.longitude != 0.0 }
    if (located.isEmpty()) return null

    val latitudes = located.map { it.latitude } + scanner.latitude
    val longitudes = located.map { it.longitude } + scanner.longitude
    return BoundingBox(
        west = longitudes.min(),
        south = latitudes.min(),
        east = longitudes.max(),
        north = latitudes.max(),
    )
}
