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
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.coalesce
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.dp
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.or
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.expressions.value.GeometryType
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource

/**
 * Fill, outline and point styling for every visible imported overlay.
 *
 * Styled per feature from the [simplestyle spec](https://github.com/mapbox/simplestyle-spec) the way the Google flavor
 * styles the same imports — `fill`, `stroke`, `fill-opacity`, `stroke-opacity`, `stroke-width`, falling back to `color`
 * for either colour and to the defaults below. This map used to paint every import one flat blue, so a contour set
 * imported as stacked bands, or a KML whose whole point was its colours, arrived unreadable.
 */
@Composable
internal fun CustomLayers(layers: List<CustomLayer>) {
    layers.forEach { layer ->
        // Keyed on the refresh token as well as the id: a refreshed network layer keeps its URI, so the source's data
        // is unchanged and nothing would re-fetch. Changing the key rebuilds the source, which does. The Google
        // flavour keys its own MapEffect the same way.
        key(layer.id, layer.refreshToken) { ImportedLayer(layer) }
    }
}

/** One imported overlay: a source, and the three layers that between them can draw anything in it. */
@Composable
private fun ImportedLayer(layer: CustomLayer) {
    val source = rememberGeoJsonSource(data = GeoJsonData.Uri(layer.uri))

    val fill = coalesce(feature["fill"].asString(), feature["color"].asString())
    val stroke = coalesce(feature["stroke"].asString(), feature["color"].asString())
    val strokeColor = stroke.convertToColor(const(CustomLayerBlue))
    val strokeWidth = coalesce(feature["stroke-width"].asNumber(), const(DEFAULT_STROKE_WIDTH)).dp

    // One source, three layers, each filtered to the geometry it can actually draw. Unfiltered, the fill layer
    // painted a LineString's vertices as a solid wedge and the circle layer put a dot on every polygon corner —
    // invisible while every import was one flat translucent blue, obvious the moment they carry their own colours.
    FillLayer(
        id = "custom-${layer.id}-fill",
        source = source,
        filter = geometryIsOneOf(GeometryType.Polygon, GeometryType.MultiPolygon),
        color = fill.convertToColor(const(CustomLayerBlue)),
        opacity = coalesce(feature["fill-opacity"].asNumber(), const(DEFAULT_FILL_OPACITY)),
    )
    LineLayer(
        id = "custom-${layer.id}-line",
        source = source,
        // Polygons are here too, for their rings: `fill-outline-color` is always a hairline, so an import asking
        // for a 5dp border would silently get one pixel. The Google flavor honours the width, and so does this.
        filter =
        geometryIsOneOf(
            GeometryType.LineString,
            GeometryType.MultiLineString,
            GeometryType.Polygon,
            GeometryType.MultiPolygon,
        ),
        color = strokeColor,
        opacity = coalesce(feature["stroke-opacity"].asNumber(), const(1f)),
        width = strokeWidth,
    )
    CircleLayer(
        id = "custom-${layer.id}-point",
        source = source,
        filter = geometryIsOneOf(GeometryType.Point, GeometryType.MultiPoint),
        color = strokeColor,
        radius = const(POINT_RADIUS.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(1.dp),
    )
}

/** What an import with no colours of its own is drawn in. */
private val CustomLayerBlue = Color(0xFF3F51B5)

/** True for a feature whose geometry is any of [types]. */
private fun geometryIsOneOf(vararg types: GeometryType): Expression<BooleanValue> =
    types.map { feature.geometryType() eq const(it) }.reduce { left, right -> left or right }

/** simplestyle-spec fallbacks, matching the Google flavor's own: 0.35 lets stacked contour bands read as a gradient. */
private const val DEFAULT_FILL_OPACITY = 0.35f
private const val DEFAULT_STROKE_WIDTH = 2f
private const val POINT_RADIUS = 5
