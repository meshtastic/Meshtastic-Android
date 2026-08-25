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
import androidx.compose.ui.unit.dp
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource

/** Fill, outline and point styling for every visible imported overlay. */
@Composable
internal fun CustomLayers(layers: List<CustomLayer>) {
    layers.forEach { layer ->
        val source = rememberGeoJsonSource(data = GeoJsonData.Uri(layer.uri))

        // One source, three layers: a GeoJSON import can mix polygons, lines and points, and a
        // layer silently ignores geometry it cannot draw.
        FillLayer(
            id = "custom-${layer.id}-fill",
            source = source,
            color = const(CustomLayerBlue),
            opacity = const(CUSTOM_FILL_OPACITY),
            outlineColor = const(CustomLayerBlue),
        )
        LineLayer(id = "custom-${layer.id}-line", source = source, color = const(CustomLayerBlue), width = const(2.dp))
        CircleLayer(
            id = "custom-${layer.id}-point",
            source = source,
            color = const(CustomLayerBlue),
            radius = const(5.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(1.dp),
        )
    }
}

private val CustomLayerBlue = Color(0xFF3F51B5)
private const val CUSTOM_FILL_OPACITY = 0.25f
