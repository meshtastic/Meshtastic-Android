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

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.exponential
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.gt
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.product
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
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.meshtastic.core.database.model.Node
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
import org.meshtastic.feature.map.maplibre.MapLibreConstants.PRECISION_CIRCLE_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.WAYPOINTS_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.WAYPOINTS_SOURCE_ID
import timber.log.Timber

/** Ensures all necessary sources and layers exist in the style */
fun ensureSourcesAndLayers(style: Style) {
    Timber.tag("MapLibrePOC")
        .d("ensureSourcesAndLayers() begin. Existing layers=%d, sources=%d", style.layers.size, style.sources.size)

    // Add sources if they don't exist
    if (style.getSource(NODES_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(NODES_SOURCE_ID, emptyFeatureCollectionJson()))
        Timber.tag("MapLibrePOC").d("Added nodes plain GeoJsonSource")
    }
    if (style.getSource(NODES_CLUSTER_SOURCE_ID) == null) {
        val options =
            GeoJsonOptions().withCluster(true).withClusterRadius(50).withClusterMaxZoom(14).withClusterMinPoints(2)
        style.addSource(GeoJsonSource(NODES_CLUSTER_SOURCE_ID, emptyFeatureCollectionJson(), options))
        Timber.tag("MapLibrePOC").d("Added nodes clustered GeoJsonSource")
    }
    if (style.getSource(WAYPOINTS_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(WAYPOINTS_SOURCE_ID, emptyFeatureCollectionJson()))
        Timber.tag("MapLibrePOC").d("Added waypoints GeoJsonSource")
    }

    // Add cluster layers
    if (style.getLayer(CLUSTER_CIRCLE_LAYER_ID) == null) {
        val clusterLayer =
            CircleLayer(CLUSTER_CIRCLE_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(circleColor("#6D4C41"), circleRadius(14f))
                .withFilter(has("point_count"))
        if (style.getLayer(OSM_LAYER_ID) != null) {
            style.addLayerAbove(clusterLayer, OSM_LAYER_ID)
        } else {
            style.addLayer(clusterLayer)
        }
        Timber.tag("MapLibrePOC").d("Added cluster CircleLayer")
    }
    if (style.getLayer(CLUSTER_COUNT_LAYER_ID) == null) {
        val countLayer =
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
                .withFilter(has("point_count"))
        if (style.getLayer(OSM_LAYER_ID) != null) {
            style.addLayerAbove(countLayer, OSM_LAYER_ID)
        } else {
            style.addLayer(countLayer)
        }
        Timber.tag("MapLibrePOC").d("Added cluster count SymbolLayer")
    }

    // Precision circle layer
    if (style.getLayer(PRECISION_CIRCLE_LAYER_ID) == null) {
        val layer =
            CircleLayer(PRECISION_CIRCLE_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(
                    circleRadius(
                        interpolate(
                            exponential(2.0),
                            zoom(),
                            stop(0, product(get("precisionMeters"), literal(0.0000025))),
                            stop(5, product(get("precisionMeters"), literal(0.00008))),
                            stop(10, product(get("precisionMeters"), literal(0.0025))),
                            stop(15, product(get("precisionMeters"), literal(0.08))),
                            stop(18, product(get("precisionMeters"), literal(0.64))),
                            stop(20, product(get("precisionMeters"), literal(2.56))),
                        ),
                    ),
                    circleColor(coalesce(toColor(get("color")), toColor(literal("#2E7D32")))),
                    circleOpacity(0.15f),
                    circleStrokeColor(coalesce(toColor(get("color")), toColor(literal("#2E7D32")))),
                    circleStrokeWidth(1.5f),
                    visibility("none"),
                )
                .withFilter(all(not(has("point_count")), gt(get("precisionMeters"), literal(0))))
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(layer, OSM_LAYER_ID) else style.addLayer(layer)
        Timber.tag("MapLibrePOC").d("Added precision circle layer")
    }

    // Node layers (clustered)
    if (style.getLayer(NODES_LAYER_ID) == null) {
        val layer =
            CircleLayer(NODES_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(
                    circleColor(coalesce(toColor(get("color")), toColor(literal("#2E7D32")))),
                    circleRadius(
                        interpolate(linear(), zoom(), stop(8, 4f), stop(12, 6f), stop(16, 8f), stop(18, 9.5f)),
                    ),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(interpolate(linear(), zoom(), stop(8, 1.5f), stop(12, 2f), stop(16, 2.5f))),
                    circleOpacity(1.0f),
                )
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(layer, OSM_LAYER_ID) else style.addLayer(layer)
        Timber.tag("MapLibrePOC").d("Added nodes CircleLayer")
    }
    if (style.getLayer(NODE_TEXT_LAYER_ID) == null) {
        val textLayer =
            SymbolLayer(NODE_TEXT_LAYER_ID, NODES_CLUSTER_SOURCE_ID)
                .withProperties(
                    textField(get("short")),
                    textColor("#1B1B1B"),
                    textHaloColor("#FFFFFF"),
                    textHaloWidth(3.0f),
                    textHaloBlur(0.7f),
                    textSize(interpolate(linear(), zoom(), stop(8, 9f), stop(12, 11f), stop(15, 13f), stop(18, 16f))),
                    textMaxWidth(4f),
                    textAllowOverlap(step(zoom(), literal(false), stop(11, literal(true)))),
                    textIgnorePlacement(step(zoom(), literal(false), stop(11, literal(true)))),
                    textOffset(arrayOf(0f, -1.4f)),
                    textAnchor("bottom"),
                )
                .withFilter(all(not(has("point_count")), eq(get("showLabel"), literal(1))))
        if (style.getLayer(OSM_LAYER_ID) != null) {
            style.addLayerAbove(textLayer, OSM_LAYER_ID)
        } else {
            style.addLayer(textLayer)
        }
        Timber.tag("MapLibrePOC").d("Added node text SymbolLayer")
    }

    // Waypoints layer
    if (style.getLayer(WAYPOINTS_LAYER_ID) == null) {
        val layer =
            CircleLayer(WAYPOINTS_LAYER_ID, WAYPOINTS_SOURCE_ID)
                .withProperties(
                    circleColor("#FF5722"),
                    circleRadius(8f),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(2f),
                )
        if (style.getLayer(OSM_LAYER_ID) != null) style.addLayerAbove(layer, OSM_LAYER_ID) else style.addLayer(layer)
        Timber.tag("MapLibrePOC").d("Added waypoints CircleLayer")
    }

    Timber.tag("MapLibrePOC")
        .d("ensureSourcesAndLayers() end. Layers=%d, Sources=%d", style.layers.size, style.sources.size)
    val order =
        try {
            style.layers.joinToString(" > ") { it.id }
        } catch (_: Throwable) {
            "<unavailable>"
        }
    Timber.tag("MapLibrePOC").d("Layer order: %s", order)
    logStyleState("ensureSourcesAndLayers(end)", style)
}

/** Show/hide cluster layers vs plain nodes based on zoom, density, and toggle */
fun setClusterVisibilityHysteresis(
    map: MapLibreMap,
    style: Style,
    filteredNodes: List<Node>,
    enableClusters: Boolean,
    currentlyShown: Boolean,
    showPrecisionCircle: Boolean = false,
): Boolean {
    try {
        val zoom = map.cameraPosition.zoom
        val showClusters = enableClusters

        // Enforce intended visibility
        style.getLayer(CLUSTER_CIRCLE_LAYER_ID)?.setProperties(visibility(if (showClusters) "visible" else "none"))
        style.getLayer(CLUSTER_COUNT_LAYER_ID)?.setProperties(visibility(if (showClusters) "visible" else "none"))

        // When clustering is enabled: show clustered source layers (which filter out clusters)
        // When clustering is disabled: show non-clustered source layers (which show ALL nodes)
        style.getLayer(NODES_LAYER_ID)?.setProperties(visibility(if (showClusters) "visible" else "none"))
        style.getLayer(NODE_TEXT_LAYER_ID)?.setProperties(visibility(if (showClusters) "visible" else "none"))
        style.getLayer(NODES_LAYER_NOCLUSTER_ID)?.setProperties(visibility(if (showClusters) "none" else "visible"))
        style.getLayer(NODE_TEXT_LAYER_NOCLUSTER_ID)?.setProperties(visibility(if (showClusters) "none" else "visible"))

        // Precision circle visibility (always controlled by toggle, independent of clustering)
        style
            .getLayer(PRECISION_CIRCLE_LAYER_ID)
            ?.setProperties(visibility(if (showPrecisionCircle) "visible" else "none"))

        Timber.tag("MapLibrePOC")
            .d(
                "Node layer visibility: clustered=%s, nocluster=%s, precision=%s",
                showClusters,
                !showClusters,
                showPrecisionCircle,
            )
        if (showClusters != currentlyShown) {
            Timber.tag("MapLibrePOC").d("Cluster visibility=%s (zoom=%.2f)", showClusters, zoom)
        }
        return showClusters
    } catch (_: Throwable) {
        return currentlyShown
    }
}

/** Log current style state: presence and visibility of key layers/sources */
fun logStyleState(whenTag: String, style: Style) {
    try {
        val layersToCheck =
            listOf(
                OSM_LAYER_ID,
                CLUSTER_CIRCLE_LAYER_ID,
                CLUSTER_COUNT_LAYER_ID,
                NODES_LAYER_ID,
                NODE_TEXT_LAYER_ID,
                NODES_LAYER_NOCLUSTER_ID,
                NODE_TEXT_LAYER_NOCLUSTER_ID,
                PRECISION_CIRCLE_LAYER_ID,
                WAYPOINTS_LAYER_ID,
            )
        val sourcesToCheck = listOf(NODES_SOURCE_ID, NODES_CLUSTER_SOURCE_ID, WAYPOINTS_SOURCE_ID, OSM_SOURCE_ID)
        val layerStates =
            layersToCheck.joinToString(", ") { id ->
                val layer = style.getLayer(id)
                if (layer == null) "$id=∅" else "$id=${layer.visibility?.value}"
            }
        val sourceStates =
            sourcesToCheck.joinToString(", ") { id -> if (style.getSource(id) == null) "$id=∅" else "$id=✓" }
        Timber.tag("MapLibrePOC").d("[%s] layers={%s} sources={%s}", whenTag, layerStates, sourceStates)
    } catch (e: Throwable) {
        Timber.tag("MapLibrePOC").w(e, "Failed to log style state")
    }
}
