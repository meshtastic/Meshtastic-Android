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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.meshtastic.feature.map.util.positionsToLineString
import org.meshtastic.feature.map.util.positionsToPointFeatures

private val TrackColor = Color(0xFF2196F3)
private val SelectedPointColor = Color(0xFFF44336)
private const val TRACK_OPACITY = 0.8f
private const val SELECTED_OPACITY = 0.9f

/**
 * Renders a position history track as a line with marker points. Replaces the Google Maps Polyline + MarkerComposable
 * and OSMDroid Polyline overlay implementations.
 */
@Composable
fun NodeTrackLayers(
    positions: List<org.meshtastic.proto.Position>,
    selectedPositionTime: Int? = null,
    onPositionSelected: ((Int) -> Unit)? = null,
) {
    if (positions.size < 2) return

    // Line track source
    val lineFeatureCollection = remember(positions) { positionsToLineString(positions) }

    val lineSource =
        rememberGeoJsonSource(
            data = GeoJsonData.Features(lineFeatureCollection),
            options = GeoJsonOptions(lineMetrics = true),
        )

    // Track line with gradient
    LineLayer(
        id = "node-track-line",
        source = lineSource,
        width = const(3.dp),
        color = const(TrackColor), // Blue
        opacity = const(TRACK_OPACITY),
    )

    // Position marker points
    val pointFeatureCollection = remember(positions) { positionsToPointFeatures(positions) }

    val pointsSource = rememberGeoJsonSource(data = GeoJsonData.Features(pointFeatureCollection))

    CircleLayer(
        id = "node-track-points",
        source = pointsSource,
        radius = const(5.dp),
        color = const(TrackColor),
        strokeWidth = const(1.dp),
        strokeColor = const(Color.White),
        onClick = { features ->
            val time = features.firstOrNull()?.properties?.get("time")?.toString()?.toIntOrNull()
            if (time != null && onPositionSelected != null) {
                onPositionSelected(time)
                ClickResult.Consume
            } else {
                ClickResult.Pass
            }
        },
    )

    // Highlight selected position
    if (selectedPositionTime != null) {
        CircleLayer(
            id = "node-track-selected",
            source = pointsSource,
            radius = const(10.dp),
            color = const(SelectedPointColor), // Red
            strokeWidth = const(2.dp),
            strokeColor = const(Color.White),
            opacity = const(SELECTED_OPACITY),
        )
    }
}
