/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.map.maplibre.core

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.toColor
import org.maplibre.android.style.expressions.Expression.toString
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloBlur
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textMaxWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.meshtastic.feature.map.maplibre.BaseMapStyle
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_CIRCLE_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_COUNT_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_CLUSTER_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_LAYER_NOCLUSTER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODE_TEXT_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODE_TEXT_LAYER_NOCLUSTER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.OSM_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.OSM_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.STYLE_URL
import org.meshtastic.feature.map.maplibre.MapLibreConstants.WAYPOINTS_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.WAYPOINTS_SOURCE_ID

/** Builds a complete MapLibre style with all necessary sources and layers */
fun buildMeshtasticStyle(base: BaseMapStyle, customTileUrl: String? = null): Style.Builder {
    // Load a complete vector style first (has fonts, glyphs, sprites MapLibre needs)
    val tileUrl = customTileUrl ?: base.urlTemplate
    val builder =
        Style.Builder()
            .fromUri(STYLE_URL)
            // Add our raster overlay on top
            .withSource(
                RasterSource(
                    OSM_SOURCE_ID,
                    TileSet("osm", tileUrl).apply {
                        minZoom = 0f
                        maxZoom = 22f
                    },
                    128,
                ),
            )
            .withLayer(RasterLayer(OSM_LAYER_ID, OSM_SOURCE_ID).withProperties(rasterOpacity(1.0f)))
            // Sources
            .withSource(GeoJsonSource(NODES_SOURCE_ID, emptyFeatureCollectionJson()))
            .withSource(
                GeoJsonSource(
                    NODES_CLUSTER_SOURCE_ID,
                    emptyFeatureCollectionJson(),
                    GeoJsonOptions()
                        .withCluster(true)
                        .withClusterRadius(50)
                        .withClusterMaxZoom(14)
                        .withClusterMinPoints(2)
                        .withLineMetrics(false)
                        .withTolerance(0.375f), // Smooth clustering transitions
                ),
            )
            .withSource(GeoJsonSource(WAYPOINTS_SOURCE_ID, emptyFeatureCollectionJson()))
            // Layers - order ensures they are above raster
            .withLayer(
                CircleLayer(CLUSTER_CIRCLE_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                    .withProperties(
                        circleColor("#6D4C41"),
                        circleRadius(14f),
                        circleOpacity(1.0f), // Needed for transitions
                    )
                    .withFilter(has("point_count")),
            )
            .withLayer(
                SymbolLayer(CLUSTER_COUNT_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                    .withProperties(
                        textField(toString(get("point_count"))),
                        textColor("#FFFFFF"),
                        textHaloColor("#000000"),
                        textHaloWidth(1.5f),
                        textHaloBlur(0.5f),
                        textSize(12f),
                        textAllowOverlap(true),
                        textIgnorePlacement(true),
                    )
                    .withFilter(has("point_count")),
            )
            .withLayer(
                CircleLayer(NODES_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                    .withProperties(
                        circleColor(coalesce(toColor(get("color")), toColor(literal("#2E7D32")))),
                        circleRadius(
                            interpolate(linear(), zoom(), stop(8, 4f), stop(12, 6f), stop(16, 8f), stop(18, 9.5f)),
                        ),
                        circleStrokeColor("#FFFFFF"), // White border
                        circleStrokeWidth(
                            interpolate(linear(), zoom(), stop(8, 1.5f), stop(12, 2f), stop(16, 2.5f), stop(18, 3f)),
                        ),
                        circleOpacity(1.0f), // Needed for transitions
                    )
                    .withFilter(not(has("point_count"))),
            )
            .withLayer(
                SymbolLayer(NODE_TEXT_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                    .withProperties(
                        textField(get("short")),
                        textColor("#1B1B1B"),
                        textHaloColor("#FFFFFF"),
                        textHaloWidth(3.0f),
                        textHaloBlur(0.7f),
                        textSize(
                            interpolate(linear(), zoom(), stop(8, 9f), stop(12, 11f), stop(15, 13f), stop(18, 16f)),
                        ),
                        textMaxWidth(4f),
                        textAllowOverlap(step(zoom(), literal(false), stop(11, literal(true)))),
                        textIgnorePlacement(step(zoom(), literal(false), stop(11, literal(true)))),
                        textOffset(arrayOf(0f, -1.4f)),
                        textAnchor("bottom"),
                    )
                    .withFilter(all(not(has("point_count")), eq(get("showLabel"), literal(1)))),
            )
            // Non-clustered node layers (shown when clustering is disabled)
            .withLayer(
                CircleLayer(NODES_LAYER_NOCLUSTER_ID, NODES_SOURCE_ID)
                    .withProperties(
                        circleColor(coalesce(toColor(get("color")), toColor(literal("#2E7D32")))),
                        circleRadius(
                            interpolate(linear(), zoom(), stop(8, 4f), stop(12, 6f), stop(16, 8f), stop(18, 9.5f)),
                        ),
                        circleStrokeColor("#FFFFFF"), // White border
                        circleStrokeWidth(
                            interpolate(linear(), zoom(), stop(8, 1.5f), stop(12, 2f), stop(16, 2.5f), stop(18, 3f)),
                        ),
                        circleOpacity(1.0f), // Needed for transitions
                        visibility("none"), // Hidden by default, shown when clustering disabled
                    ),
            )
            .withLayer(
                SymbolLayer(NODE_TEXT_LAYER_NOCLUSTER_ID, NODES_SOURCE_ID)
                    .withProperties(
                        textField(get("short")),
                        textColor("#1B1B1B"),
                        textHaloColor("#FFFFFF"),
                        textHaloWidth(3.0f),
                        textHaloBlur(0.7f),
                        textSize(
                            interpolate(linear(), zoom(), stop(8, 9f), stop(12, 11f), stop(15, 13f), stop(18, 16f)),
                        ),
                        textMaxWidth(4f),
                        textAllowOverlap(step(zoom(), literal(false), stop(11, literal(true)))),
                        textIgnorePlacement(step(zoom(), literal(false), stop(11, literal(true)))),
                        textOffset(arrayOf(0f, -1.4f)),
                        textAnchor("bottom"),
                        visibility("none"), // Hidden by default, shown when clustering disabled
                    )
                    .withFilter(eq(get("showLabel"), literal(1))),
            )
            .withLayer(
                CircleLayer(WAYPOINTS_LAYER_ID, WAYPOINTS_SOURCE_ID)
                    .withProperties(
                        circleColor("#FFFFFF"), // White center for precision
                        circleRadius(4f), // Small for precision
                        circleStrokeColor("#FF3B30"), // Red ring
                        circleStrokeWidth(2f),
                    ),
            )
    return builder
}

/** Returns an empty GeoJSON FeatureCollection as a JSON string */
fun emptyFeatureCollectionJson(): String = """{"type":"FeatureCollection","features":[]}"""
