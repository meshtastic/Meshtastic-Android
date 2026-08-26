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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.util.DiscoveryMapNode
import org.meshtastic.feature.map.maplibre.geojson.NodeFeatureKeys
import org.meshtastic.feature.map.maplibre.geojson.nodesToFeatureCollection
import org.meshtastic.feature.map.maplibre.layers.TracerouteLayers
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.Basemaps
import org.meshtastic.feature.map.maplibre.style.MapColors
import org.maplibre.spatialk.geojson.Position as GeoPosition

internal const val DEG_SCALE = 1e-7
internal const val DETAIL_ZOOM = 13.0

internal val defaultStyle: BaseStyle
    get() = BaseStyle.Uri((Basemaps.default as Basemap.Vector).styleUri)

/** Single-node mini-map embedded in the node detail sheet. */
@Composable
fun MapLibreInlineMap(node: Node, modifier: Modifier = Modifier) {
    if (node.validPosition == null) return
    val cameraState =
        rememberCameraState(
            CameraPosition(
                target = GeoPosition(longitude = node.longitude, latitude = node.latitude),
                zoom = DETAIL_ZOOM,
            ),
        )

    MaplibreMap(
        baseStyle = defaultStyle,
        cameraState = cameraState,
        modifier = modifier,
        options = SecondaryMapOptions,
    ) {
        val source = rememberGeoJsonSource(data = GeoJsonData.Features(nodesToFeatureCollection(listOf(node))))
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

/** Traceroute result map: forward and return paths plus the hops they pass through. */
@Composable
fun MapLibreTracerouteMap(
    forwardRoute: List<Int>,
    returnRoute: List<Int>,
    nodeLookup: Map<Int, Node>,
    modifier: Modifier = Modifier,
) {
    val cameraState = rememberCameraState()
    val hops = (forwardRoute + returnRoute).distinct().mapNotNull { nodeLookup[it] }

    LaunchedEffect(hops.size) { nodesBoundingBox(hops)?.let { cameraState.jumpTo(boundingBox = it) } }

    MaplibreMap(
        baseStyle = defaultStyle,
        cameraState = cameraState,
        modifier = modifier,
        options = SecondaryMapOptions,
    ) {
        TracerouteLayers(forwardRoute = forwardRoute, returnRoute = returnRoute, nodeLookup = nodeLookup)

        val hopSource = rememberGeoJsonSource(data = GeoJsonData.Features(nodesToFeatureCollection(hops)))
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
}

/** Discovery scan summary map: the scanner plus everything it heard. */
@Composable
fun MapLibreDiscoveryMap(
    userLatitude: Double,
    userLongitude: Double,
    nodes: List<DiscoveryMapNode>,
    modifier: Modifier = Modifier,
) {
    val scanner = GeoPosition(longitude = userLongitude, latitude = userLatitude)
    val cameraState = rememberCameraState(CameraPosition(target = scanner, zoom = DETAIL_ZOOM))

    MaplibreMap(
        baseStyle = defaultStyle,
        cameraState = cameraState,
        modifier = modifier,
        options = SecondaryMapOptions,
    ) {
        DiscoveredNodeLayers(nodes)
        ScannerLayer(scanner)
    }
}

@Composable
private fun DiscoveredNodeLayers(nodes: List<DiscoveryMapNode>) {
    val source =
        rememberGeoJsonSource(
            data =
            GeoJsonData.Features(
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
        rememberGeoJsonSource(
            data =
            GeoJsonData.Features(
                FeatureCollection(
                    listOf(
                        Feature<Point, JsonObject?>(
                            geometry = Point(scanner),
                            properties = buildJsonObject { put("role", "scanner") },
                        ),
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
