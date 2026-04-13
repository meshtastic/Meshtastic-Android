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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.model.MapStyle
import org.meshtastic.feature.map.util.MARKER_STROKE_WIDTH
import org.meshtastic.feature.map.util.NODE_MARKER_RADIUS
import org.meshtastic.feature.map.util.PRECISION_CIRCLE_STROKE_ALPHA
import org.meshtastic.feature.map.util.precisionBitsToMeters
import org.meshtastic.feature.map.util.toGeoPositionOrNull

private const val DEFAULT_ZOOM = 15.0
private const val PRECISION_CIRCLE_FILL_ALPHA = 0.15f

/**
 * A compact, non-interactive map showing a single node's position. Used in node detail screens. Replaces both the
 * Google Maps and OSMDroid inline map implementations.
 */
@Composable
fun InlineMap(node: Node, modifier: Modifier = Modifier) {
    val position = node.validPosition ?: return
    val geoPos = toGeoPositionOrNull(position.latitude_i, position.longitude_i) ?: return

    key(node.num) {
        val cameraState = rememberCameraState(firstPosition = CameraPosition(target = geoPos, zoom = DEFAULT_ZOOM))

        val nodeFeature =
            remember(node.num, geoPos) {
                FeatureCollection(listOf(Feature(geometry = Point(geoPos), properties = null)))
            }

        MaplibreMap(
            modifier = modifier,
            baseStyle = MapStyle.OpenStreetMap.toBaseStyle(),
            cameraState = cameraState,
            options =
            MapOptions(gestureOptions = GestureOptions.AllDisabled, ornamentOptions = OrnamentOptions.AllDisabled),
        ) {
            val source = rememberGeoJsonSource(data = GeoJsonData.Features(nodeFeature))

            // Node marker dot
            CircleLayer(
                id = "inline-node-marker",
                source = source,
                radius = const(NODE_MARKER_RADIUS),
                color = const(Color(node.colors.second)),
                strokeWidth = const(MARKER_STROKE_WIDTH),
                strokeColor = const(Color.White),
            )

            // Precision circle — radius computed from precision_meters using latitude-aware metersPerDp
            val precisionMeters = precisionBitsToMeters(position.precision_bits)
            val metersPerDp = cameraState.metersPerDpAtTarget
            if (precisionMeters > 0 && metersPerDp > 0) {
                val radiusDp = (precisionMeters / metersPerDp).dp
                CircleLayer(
                    id = "inline-node-precision",
                    source = source,
                    radius = const(radiusDp),
                    color = const(Color(node.colors.second).copy(alpha = PRECISION_CIRCLE_FILL_ALPHA)),
                    strokeWidth = const(1.dp),
                    strokeColor = const(Color(node.colors.second).copy(alpha = PRECISION_CIRCLE_STROKE_ALPHA)),
                )
            }
        }
    }
}
