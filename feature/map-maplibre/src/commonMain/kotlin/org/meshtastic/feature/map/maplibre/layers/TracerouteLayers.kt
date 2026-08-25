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
package org.meshtastic.feature.map.maplibre.layers

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.model.Node

private val ForwardBlue = Color(0xFF1E88E5)
private val ReturnGreen = Color(0xFF43A047)

/**
 * Forward and return traceroute paths.
 *
 * The two directions almost always traverse the same hops, so they are nudged apart with a
 * screen-space translate rather than by offsetting the geometry: the separation then stays legible
 * at every zoom instead of collapsing as you zoom out.
 */
@Composable
internal fun TracerouteLayers(forwardRoute: List<Int>, returnRoute: List<Int>, nodeLookup: Map<Int, Node>) {
    val forward = rememberGeoJsonSource(data = GeoJsonData.Features(routeToFeatureCollection(forwardRoute, nodeLookup)))
    val back = rememberGeoJsonSource(data = GeoJsonData.Features(routeToFeatureCollection(returnRoute, nodeLookup)))

    LineLayer(
        id = "traceroute-forward",
        source = forward,
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
        color = const(ForwardBlue),
        width = const(4.dp),
        translate = const(DpOffset(0.dp, (-ROUTE_SEPARATION_DP).dp)),
    )

    LineLayer(
        id = "traceroute-return",
        source = back,
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
        color = const(ReturnGreen),
        width = const(4.dp),
        translate = const(DpOffset(0.dp, ROUTE_SEPARATION_DP.dp)),
    )
}

/**
 * Turns a hop list into a single line.
 *
 * Hops with no known position are skipped rather than breaking the line, matching what the
 * OSMdroid overlay drew: a route through an unlocated relay still reads as continuous.
 */
internal fun routeToFeatureCollection(
    route: List<Int>,
    nodeLookup: Map<Int, Node>,
): FeatureCollection<LineString, JsonObject?> {
    val positions =
        route.mapNotNull { nodeNum ->
            val node = nodeLookup[nodeNum] ?: return@mapNotNull null
            node.validPosition ?: return@mapNotNull null
            Position(longitude = node.longitude, latitude = node.latitude)
        }
    if (positions.size < 2) return FeatureCollection(emptyList())
    return FeatureCollection(
        listOf(
            Feature(
                geometry = LineString(positions),
                properties = buildJsonObject { put("hops", route.size) },
            ),
        ),
    )
}

private const val ROUTE_SEPARATION_DP = 3
