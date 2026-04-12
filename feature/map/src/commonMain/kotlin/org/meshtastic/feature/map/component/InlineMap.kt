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
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.util.precisionBitsToMeters
import org.maplibre.spatialk.geojson.Position as GeoPosition

private const val DEFAULT_ZOOM = 15.0
private const val COORDINATE_SCALE = 1e-7
private const val PRECISION_CIRCLE_FILL_ALPHA = 0.15f
private const val PRECISION_CIRCLE_STROKE_ALPHA = 0.3f

/**
 * A compact, non-interactive map showing a single node's position. Used in node detail screens. Replaces both the
 * Google Maps and OSMDroid inline map implementations.
 */
@Composable
fun InlineMap(node: Node, modifier: Modifier = Modifier) {
    val position = node.validPosition ?: return
    val lat = (position.latitude_i ?: 0) * COORDINATE_SCALE
    val lng = (position.longitude_i ?: 0) * COORDINATE_SCALE
    if (lat == 0.0 && lng == 0.0) return

    key(node.num) {
        val cameraState =
            rememberCameraState(
                firstPosition =
                CameraPosition(target = GeoPosition(longitude = lng, latitude = lat), zoom = DEFAULT_ZOOM),
            )

        val nodeFeature =
            remember(node.num, lat, lng) {
                FeatureCollection(
                    listOf(Feature(geometry = Point(GeoPosition(longitude = lng, latitude = lat)), properties = null)),
                )
            }

        MaplibreMap(
            modifier = modifier,
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
            cameraState = cameraState,
            options =
            MapOptions(gestureOptions = GestureOptions.AllDisabled, ornamentOptions = OrnamentOptions.AllDisabled),
        ) {
            val source = rememberGeoJsonSource(data = GeoJsonData.Features(nodeFeature))

            // Node marker dot
            CircleLayer(
                id = "inline-node-marker",
                source = source,
                radius = const(8.dp),
                color = const(Color(node.colors.second)),
                strokeWidth = const(2.dp),
                strokeColor = const(Color.White),
            )

            // Precision circle
            val precisionMeters = precisionBitsToMeters(position.precision_bits ?: 0)
            if (precisionMeters > 0) {
                CircleLayer(
                    id = "inline-node-precision",
                    source = source,
                    radius = const(40.dp), // visual approximation
                    color = const(Color(node.colors.second).copy(alpha = PRECISION_CIRCLE_FILL_ALPHA)),
                    strokeWidth = const(1.dp),
                    strokeColor = const(Color(node.colors.second).copy(alpha = PRECISION_CIRCLE_STROKE_ALPHA)),
                )
            }
        }
    }
}
