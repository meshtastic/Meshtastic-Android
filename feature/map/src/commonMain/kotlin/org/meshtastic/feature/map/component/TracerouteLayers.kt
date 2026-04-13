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
package org.meshtastic.feature.map.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.feature.map.util.toGeoPositionOrNull
import org.meshtastic.feature.map.util.typedFeatureCollection
import org.maplibre.spatialk.geojson.Position as GeoPosition

private val ForwardRouteColor = Color(0xFF4CAF50)
private val ReturnRouteColor = Color(0xFFF44336)
private val HopMarkerColor = Color(0xFF9C27B0)
private const val HEX_RADIX = 16
private const val ROUTE_OPACITY = 0.8f

/**
 * Renders traceroute forward and return routes with hop markers. Replaces the Google Maps and OSMDroid traceroute
 * polyline implementations.
 */
@Composable
internal fun TracerouteLayers(
    overlay: TracerouteOverlay?,
    nodePositions: Map<Int, org.meshtastic.proto.Position>,
    nodes: Map<Int, Node>,
    onMappableCountChanged: (shown: Int, total: Int) -> Unit,
) {
    if (overlay == null) return

    // Build route line features
    val routeData = remember(overlay, nodePositions, nodes) { buildTracerouteGeoJson(overlay, nodePositions, nodes) }

    // Report mappable count via side effect (avoid state updates during composition)
    val mappableCount = routeData.hopFeatures.features.size
    val totalCount = overlay.relatedNodeNums.size
    LaunchedEffect(mappableCount, totalCount) { onMappableCountChanged(mappableCount, totalCount) }

    // Forward route line
    if (routeData.forwardLine.features.isNotEmpty()) {
        val forwardSource = rememberGeoJsonSource(data = GeoJsonData.Features(routeData.forwardLine))
        LineLayer(
            id = "traceroute-forward",
            source = forwardSource,
            width = const(3.dp),
            color = const(ForwardRouteColor), // Green
            opacity = const(ROUTE_OPACITY),
        )
    }

    // Return route line (dashed)
    if (routeData.returnLine.features.isNotEmpty()) {
        val returnSource = rememberGeoJsonSource(data = GeoJsonData.Features(routeData.returnLine))
        LineLayer(
            id = "traceroute-return",
            source = returnSource,
            width = const(3.dp),
            color = const(ReturnRouteColor), // Red
            opacity = const(ROUTE_OPACITY),
            dasharray = const(listOf(2f, 1f)),
        )
    }

    // Hop markers
    if (routeData.hopFeatures.features.isNotEmpty()) {
        val hopsSource = rememberGeoJsonSource(data = GeoJsonData.Features(routeData.hopFeatures))
        CircleLayer(
            id = "traceroute-hops",
            source = hopsSource,
            radius = const(8.dp),
            color = const(HopMarkerColor), // Purple
            strokeWidth = const(2.dp),
            strokeColor = const(Color.White),
        )
        SymbolLayer(
            id = "traceroute-hop-labels",
            source = hopsSource,
            textField = feature["short_name"].asString(),
            textSize = const(1.em),
            textOffset = offset(0f.em, -2f.em),
            textColor = const(Color.DarkGray),
        )
    }
}

private data class TracerouteGeoJsonData(
    val forwardLine: FeatureCollection<LineString, JsonObject>,
    val returnLine: FeatureCollection<LineString, JsonObject>,
    val hopFeatures: FeatureCollection<Point, JsonObject>,
)

private fun buildTracerouteGeoJson(
    overlay: TracerouteOverlay,
    nodePositions: Map<Int, org.meshtastic.proto.Position>,
    nodes: Map<Int, Node>,
): TracerouteGeoJsonData {
    fun nodeToGeoPosition(nodeNum: Int): GeoPosition? {
        val pos = nodePositions[nodeNum] ?: return null
        return toGeoPositionOrNull(pos.latitude_i, pos.longitude_i)
    }

    // Build forward route line
    val forwardCoords = overlay.forwardRoute.mapNotNull { nodeToGeoPosition(it) }
    val forwardLine =
        if (forwardCoords.size >= 2) {
            val feature =
                Feature(
                    geometry = LineString(forwardCoords),
                    properties = buildJsonObject { put("direction", "forward") },
                )
            typedFeatureCollection(listOf(feature))
        } else {
            typedFeatureCollection(emptyList<Feature<LineString, JsonObject>>())
        }

    // Build return route line
    val returnCoords = overlay.returnRoute.mapNotNull { nodeToGeoPosition(it) }
    val returnLine =
        if (returnCoords.size >= 2) {
            val feature =
                Feature(
                    geometry = LineString(returnCoords),
                    properties = buildJsonObject { put("direction", "return") },
                )
            typedFeatureCollection(listOf(feature))
        } else {
            typedFeatureCollection(emptyList<Feature<LineString, JsonObject>>())
        }

    // Build hop marker points
    val allNodeNums = overlay.relatedNodeNums

    val hopFeatures =
        allNodeNums.mapNotNull { nodeNum ->
            val geoPos = nodeToGeoPosition(nodeNum) ?: return@mapNotNull null
            val node = nodes[nodeNum]
            Feature(
                geometry = Point(geoPos),
                properties =
                buildJsonObject {
                    put("node_num", nodeNum)
                    put("short_name", node?.user?.short_name ?: nodeNum.toUInt().toString(HEX_RADIX))
                    put("long_name", node?.user?.long_name ?: "Unknown")
                },
            )
        }

    return TracerouteGeoJsonData(
        forwardLine = forwardLine,
        returnLine = returnLine,
        hopFeatures = typedFeatureCollection(hopFeatures),
    )
}
