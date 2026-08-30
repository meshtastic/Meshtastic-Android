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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.coalesce
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.dp
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.or
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.times
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.expressions.value.GeometryType
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberImageSource
import org.maplibre.compose.util.PositionQuad
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.feature.map.kml.ICON_URL_PROPERTY
import org.meshtastic.feature.map.layers.mapLayerFileSystem
import org.meshtastic.feature.map.layers.opacityOf
import org.meshtastic.feature.map.layers.toLocalPath

/**
 * Fill, outline and point styling for every visible imported overlay.
 *
 * Styled per feature from the [simplestyle spec](https://github.com/mapbox/simplestyle-spec) the way the Google flavor
 * styles the same imports — `fill`, `stroke`, `fill-opacity`, `stroke-opacity`, `stroke-width`, falling back to `color`
 * for either colour and to the defaults below. This map used to paint every import one flat blue, so a contour set
 * imported as stacked bands, or a KML whose whole point was its colours, arrived unreadable.
 */
@Composable
internal fun CustomLayers(layers: List<CustomLayer>, opacity: Map<String, Float>) {
    layers.forEach { layer ->
        // Keyed on the refresh token as well as the id: a refreshed network layer keeps its URI, so the source's data
        // is unchanged and nothing would re-fetch. Changing the key rebuilds the source, which does. The Google
        // flavour keys its own MapEffect the same way.
        key(layer.id, layer.refreshToken) { ImportedLayer(layer, opacity.opacityOf(layer.uri)) }
    }
}

/** One imported overlay: a source, and the three layers that between them can draw anything in it. */
@Composable
private fun ImportedLayer(layer: CustomLayer, opacity: Float) {
    val source = rememberGeoJsonSource(data = GeoJsonData.Uri(layer.uri))

    // Draped first so a KML that carries both an image and vector features keeps its lines and points readable on
    // top — a ground overlay is a basemap-like backdrop, not a marker.
    layer.groundOverlays.forEachIndexed { index, overlay -> GroundOverlayLayer(layer.id, index, overlay, opacity) }

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
        // The layer's own opacity scales whatever the feature asked for, rather than replacing it: an import that
        // styles some features translucent stays relatively translucent as the whole layer fades.
        opacity = coalesce(feature["fill-opacity"].asNumber(), const(DEFAULT_FILL_OPACITY)) * const(opacity),
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
        opacity = coalesce(feature["stroke-opacity"].asNumber(), const(1f)) * const(opacity),
        width = strokeWidth,
    )
    CircleLayer(
        id = "custom-${layer.id}-point",
        source = source,
        filter = geometryIsOneOf(GeometryType.Point, GeometryType.MultiPoint),
        color = strokeColor,
        opacity = const(opacity),
        radius = const(POINT_RADIUS.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(1.dp),
    )

    // Drawn last so an icon sits over the dot the circle layer already put down. Points keep that dot: an import
    // usually gives icons to a few features and nothing to the rest, and those still have to be visible.
    val icons = rememberLayerIcons(layer.icons)
    if (icons.isNotEmpty()) {
        SymbolLayer(
            id = "custom-${layer.id}-icon",
            source = source,
            // Only features naming an icon that actually loaded. Without this the `switch` fallback would put some
            // arbitrary other feature's icon on every plain point in the layer.
            filter = iconIsOneOf(icons.keys),
            iconImage =
            switch(
                input = coalesce(feature[ICON_URL_PROPERTY].asString(), const("")),
                *icons.map { (url, painter) -> case(url, image(painter, ICON_SIZE)) }.toTypedArray(),
                // Unreachable given the filter, and required: `switch` has no way to say "draw nothing".
                fallback = image(icons.values.first(), ICON_SIZE),
            ),
            // An overlay's icons mark specific places, so a crowded import should show all of them rather than let
            // MapLibre thin them out to keep labels tidy.
            iconAllowOverlap = const(true),
            iconOpacity = const(opacity),
        )
    }
}

/**
 * The icon images the layer asks for, once each has been fetched.
 *
 * Returning a value makes this non-restartable, so the `state` reads below invalidate the *caller* — which is what
 * makes the map redraw as icons arrive. Deliberate: a restartable version would recompose alone and the layer above
 * would never see the painters it produced.
 */
@Composable
private fun rememberLayerIcons(urls: Set<String>): Map<String, Painter> {
    if (urls.isEmpty()) return emptyMap()
    // Sorted so the `switch` arms and the fallback stay in the same order across recompositions.
    val ordered = remember(urls) { urls.sorted() }
    val loaded = mutableMapOf<String, Painter>()
    ordered.forEach { url ->
        key(url) {
            // See `decodeForSoftwareCanvas` — without it the app dies the moment an icon finishes loading.
            val painter =
                rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalPlatformContext.current).data(url).decodeForSoftwareCanvas().build(),
                )
            val state by painter.state.collectAsState()
            // The loaded painter, not the async wrapper around it: MapLibre rasterizes a painter outside the
            // composition driving it, where an AsyncImagePainter draws nothing.
            (state as? AsyncImagePainter.State.Success)?.let { loaded[url] = it.painter }
        }
    }
    return loaded
}

/**
 * One KML `<GroundOverlay>`: its image draped between its corners.
 *
 * The bitmap is decoded once per path and remembered — a ground overlay from an ESRI export is routinely a
 * multi-megapixel tile, and decoding it on every recomposition would hitch the map. A file that fails to decode drapes
 * nothing and logs; the vector half of its layer still renders.
 */
@Composable
private fun GroundOverlayLayer(layerId: String, index: Int, overlay: LayerGroundOverlay, opacity: Float) {
    // Decoded off the composition thread: an ESRI export's tile is routinely multi-megapixel, and a synchronous
    // decode in `remember` would hitch the map for every overlay on every first composition.
    val image by
        produceState<ImageBitmap?>(initialValue = null, overlay.imagePath) {
            value =
                withContext(ioDispatcher) {
                    safeCatching {
                        mapLayerFileSystem()
                            .read(overlay.imagePath.toLocalPath()) { readByteArray() }
                            .decodeToImageBitmap()
                    }
                        .onFailure {
                            Logger.withTag("CustomLayers").w(it) { "Could not decode a ground overlay image" }
                        }
                        .getOrNull()
                }
        }
    val decoded = image ?: return

    val quad =
        remember(overlay.corners) {
            val positions =
                overlay.corners.map { (longitude, latitude) -> Position(longitude = longitude, latitude = latitude) }
            PositionQuad(
                topLeft = positions[0],
                topRight = positions[1],
                bottomRight = positions[2],
                bottomLeft = positions[BOTTOM_LEFT],
            )
        }

    val source = rememberImageSource(quad, decoded)
    RasterLayer(id = "custom-$layerId-ground-$index", source = source, opacity = const(opacity))
}

/** What an import with no colours of its own is drawn in. */
private val CustomLayerBlue = Color(0xFF3F51B5)

/** True for a feature whose geometry is any of [types]. */
private fun geometryIsOneOf(vararg types: GeometryType): Expression<BooleanValue> =
    types.map { feature.geometryType() eq const(it) }.reduce { left, right -> left or right }

/**
 * Matches a feature whose `icon-url` is one of [urls].
 *
 * An `or` chain rather than a set-membership test, matching [geometryIsOneOf] — the same shape the rest of this file's
 * filters already use.
 */
private fun iconIsOneOf(urls: Set<String>): Expression<BooleanValue> =
    urls.map { feature[ICON_URL_PROPERTY].asString() eq const(it) }.reduce { left, right -> left or right }

/** simplestyle-spec fallbacks, matching the Google flavor's own: 0.35 lets stacked contour bands read as a gradient. */
private const val DEFAULT_FILL_OPACITY = 0.35f
private const val DEFAULT_STROKE_WIDTH = 2f
private const val POINT_RADIUS = 5

private val ICON_SIZE = DpSize(28.dp, 28.dp)

/** Corner order is top-left, top-right, bottom-right, bottom-left — see [LayerGroundOverlay.corners]. */
private const val BOTTOM_LEFT = 3
