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
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.heatmapColor
import org.maplibre.android.style.layers.PropertyFactory.heatmapIntensity
import org.maplibre.android.style.layers.PropertyFactory.heatmapOpacity
import org.maplibre.android.style.layers.PropertyFactory.heatmapRadius
import org.maplibre.android.style.layers.PropertyFactory.heatmapWeight
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
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
import org.maplibre.geojson.FeatureCollection
import org.meshtastic.core.database.model.Node
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_CIRCLE_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_COUNT_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.HEATMAP_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.HEATMAP_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_CLUSTER_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_LAYER_NOCLUSTER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODE_TEXT_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODE_TEXT_LAYER_NOCLUSTER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.OSM_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.OSM_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.PRECISION_CIRCLE_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.TRACK_LINE_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.TRACK_LINE_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.TRACK_POINTS_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.TRACK_POINTS_SOURCE_ID
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

/** Ensures a GeoJSON source and layers exist for an imported map layer */
fun ensureImportedLayerSourceAndLayers(style: Style, layerId: String, geoJson: String?, isVisible: Boolean) {
    val sourceId = "imported-layer-source-$layerId"
    val pointLayerId = "imported-layer-points-$layerId"
    val lineLayerId = "imported-layer-lines-$layerId"
    val fillLayerId = "imported-layer-fills-$layerId"

    Timber.tag("MapLibreLayerManager")
        .d(
            "ensureImportedLayerSourceAndLayers: layerId=%s, hasGeoJson=%s, isVisible=%s",
            layerId,
            geoJson != null,
            isVisible,
        )

    try {
        // Add or update source
        val existingSource = style.getSource(sourceId)
        if (existingSource == null) {
            // Create new source
            if (geoJson != null) {
                Timber.tag("MapLibreLayerManager")
                    .d("Creating new GeoJSON source: %s (%d bytes)", sourceId, geoJson.length)
                style.addSource(GeoJsonSource(sourceId, geoJson))
            } else {
                Timber.tag("MapLibreLayerManager").d("Creating empty GeoJSON source: %s", sourceId)
                style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyList())))
            }
        } else if (geoJson != null && existingSource is GeoJsonSource) {
            // Update existing source
            Timber.tag("MapLibreLayerManager")
                .d("Updating existing GeoJSON source: %s (%d bytes)", sourceId, geoJson.length)
            existingSource.setGeoJson(geoJson)
        } else {
            Timber.tag("MapLibreLayerManager").d("Source already exists: %s", sourceId)
        }

        // Add point layer (CircleLayer for points)
        if (style.getLayer(pointLayerId) == null) {
            Timber.tag("MapLibreLayerManager").d("Creating point layer: %s", pointLayerId)
            val pointLayer = CircleLayer(pointLayerId, sourceId)
            pointLayer.setProperties(
                circleColor("#3388ff"),
                circleRadius(5f),
                circleOpacity(0.8f),
                circleStrokeColor("#ffffff"),
                circleStrokeWidth(1f),
                visibility(if (isVisible) "visible" else "none"),
            )
            style.addLayerAbove(pointLayer, OSM_LAYER_ID)
        } else {
            Timber.tag("MapLibreLayerManager")
                .d("Updating point layer visibility: %s -> %s", pointLayerId, if (isVisible) "visible" else "none")
            style.getLayer(pointLayerId)?.setProperties(visibility(if (isVisible) "visible" else "none"))
        }

        // Add line layer (LineLayer for LineStrings)
        if (style.getLayer(lineLayerId) == null) {
            Timber.tag("MapLibreLayerManager").d("Creating line layer: %s", lineLayerId)
            val lineLayer = LineLayer(lineLayerId, sourceId)
            lineLayer.setProperties(
                lineColor("#3388ff"),
                lineWidth(2f),
                lineOpacity(0.8f),
                visibility(if (isVisible) "visible" else "none"),
            )
            style.addLayerAbove(lineLayer, OSM_LAYER_ID)
        } else {
            Timber.tag("MapLibreLayerManager")
                .d("Updating line layer visibility: %s -> %s", lineLayerId, if (isVisible) "visible" else "none")
            style.getLayer(lineLayerId)?.setProperties(visibility(if (isVisible) "visible" else "none"))
        }

        // Add fill layer (FillLayer for Polygons)
        if (style.getLayer(fillLayerId) == null) {
            Timber.tag("MapLibreLayerManager").d("Creating fill layer: %s", fillLayerId)
            val fillLayer = FillLayer(fillLayerId, sourceId)
            fillLayer.setProperties(
                fillColor("#3388ff"),
                fillOpacity(0.3f),
                visibility(if (isVisible) "visible" else "none"),
            )
            style.addLayerAbove(fillLayer, OSM_LAYER_ID)
        } else {
            Timber.tag("MapLibreLayerManager")
                .d("Updating fill layer visibility: %s -> %s", fillLayerId, if (isVisible) "visible" else "none")
            style.getLayer(fillLayerId)?.setProperties(visibility(if (isVisible) "visible" else "none"))
        }

        Timber.tag("MapLibreLayerManager").d("Successfully ensured layers for: %s", layerId)
    } catch (e: Exception) {
        Timber.tag("MapLibreLayerManager").e(e, "Error ensuring imported layer source and layers for $layerId")
    }
}

/** Removes an imported layer's source and layers */
fun removeImportedLayerSourceAndLayers(style: Style, layerId: String) {
    val sourceId = "imported-layer-source-$layerId"
    val pointLayerId = "imported-layer-points-$layerId"
    val lineLayerId = "imported-layer-lines-$layerId"
    val fillLayerId = "imported-layer-fills-$layerId"

    try {
        style.getLayer(pointLayerId)?.let { style.removeLayer(it) }
        style.getLayer(lineLayerId)?.let { style.removeLayer(it) }
        style.getLayer(fillLayerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }
    } catch (e: Exception) {
        Timber.tag("MapLibreLayerManager").e(e, "Error removing imported layer source and layers for $layerId")
    }
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

/** Manages track line and point sources and layers for node track display */
fun ensureTrackSourcesAndLayers(style: Style, trackColor: String = "#FF5722") {
    // Add track line source if it doesn't exist
    if (style.getSource(TRACK_LINE_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(TRACK_LINE_SOURCE_ID, emptyFeatureCollectionJson()))
        Timber.tag("MapLibrePOC").d("Added track line GeoJsonSource")
    }

    // Add track points source if it doesn't exist
    if (style.getSource(TRACK_POINTS_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(TRACK_POINTS_SOURCE_ID, emptyFeatureCollectionJson()))
        Timber.tag("MapLibrePOC").d("Added track points GeoJsonSource")
    }

    // Add track line layer if it doesn't exist
    if (style.getLayer(TRACK_LINE_LAYER_ID) == null) {
        val lineLayer =
            LineLayer(TRACK_LINE_LAYER_ID, TRACK_LINE_SOURCE_ID)
                .withProperties(lineColor(trackColor), lineWidth(3f), lineOpacity(0.8f))

        // Add above OSM layer if it exists
        if (style.getLayer(OSM_LAYER_ID) != null) {
            style.addLayerAbove(lineLayer, OSM_LAYER_ID)
        } else {
            style.addLayer(lineLayer)
        }
        Timber.tag("MapLibrePOC").d("Added track line LineLayer")
    }

    // Add track points layer if it doesn't exist
    if (style.getLayer(TRACK_POINTS_LAYER_ID) == null) {
        val pointsLayer =
            CircleLayer(TRACK_POINTS_LAYER_ID, TRACK_POINTS_SOURCE_ID)
                .withProperties(
                    circleColor(trackColor),
                    circleRadius(5f),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(2f),
                    circleOpacity(0.7f),
                )

        // Add above track line layer
        style.addLayerAbove(pointsLayer, TRACK_LINE_LAYER_ID)
        Timber.tag("MapLibrePOC").d("Added track points CircleLayer")
    }
}

/** Removes track sources and layers from the style */
fun removeTrackSourcesAndLayers(style: Style) {
    style.getLayer(TRACK_POINTS_LAYER_ID)?.let { style.removeLayer(it) }
    style.getLayer(TRACK_LINE_LAYER_ID)?.let { style.removeLayer(it) }
    style.getSource(TRACK_POINTS_SOURCE_ID)?.let { style.removeSource(it) }
    style.getSource(TRACK_LINE_SOURCE_ID)?.let { style.removeSource(it) }
    Timber.tag("MapLibrePOC").d("Removed track sources and layers")
}

/** Ensures heatmap source and layer exist in the style */
fun ensureHeatmapSourceAndLayer(style: Style) {
    // Add heatmap source if it doesn't exist
    if (style.getSource(HEATMAP_SOURCE_ID) == null) {
        val emptyFeatureCollection = FeatureCollection.fromFeatures(emptyList())
        val heatmapSource = GeoJsonSource(HEATMAP_SOURCE_ID, emptyFeatureCollection)
        style.addSource(heatmapSource)
        Timber.tag("MapLibrePOC").d("Added heatmap GeoJsonSource")
    }

    // Add heatmap layer if it doesn't exist
    if (style.getLayer(HEATMAP_LAYER_ID) == null) {
        val heatmapLayer =
            HeatmapLayer(HEATMAP_LAYER_ID, HEATMAP_SOURCE_ID)
                .withProperties(
                    // Each node contributes equally to the heatmap
                    heatmapWeight(literal(1.0)),
                    // Increase the heatmap intensity by zoom level
                    // Higher intensity = more sensitive to node density
                    heatmapIntensity(interpolate(linear(), zoom(), stop(0, 0.3), stop(9, 0.8), stop(15, 1.5))),
                    // Color ramp for heatmap - requires higher density to reach warmer colors
                    heatmapColor(
                        interpolate(
                            linear(),
                            literal("heatmap-density"),
                            stop(0.0, toColor(literal("rgba(33,102,172,0)"))),
                            stop(0.1, toColor(literal("rgb(33,102,172)"))),
                            stop(0.3, toColor(literal("rgb(103,169,207)"))),
                            stop(0.5, toColor(literal("rgb(209,229,240)"))),
                            stop(0.7, toColor(literal("rgb(253,219,199)"))),
                            stop(0.85, toColor(literal("rgb(239,138,98)"))),
                            stop(1.0, toColor(literal("rgb(178,24,43)"))),
                        ),
                    ),
                    // Smaller radius = each node influences a smaller area
                    // More nodes needed in close proximity to create high density
                    heatmapRadius(interpolate(linear(), zoom(), stop(0, 2.0), stop(9, 6.0), stop(15, 10.0))),
                    // Transition from heatmap to circle layer by zoom level
                    heatmapOpacity(interpolate(linear(), zoom(), stop(7, 1.0), stop(22, 1.0))),
                )

        // Add above OSM layer if it exists, otherwise add at bottom
        if (style.getLayer(OSM_LAYER_ID) != null) {
            style.addLayerAbove(heatmapLayer, OSM_LAYER_ID)
        } else {
            style.addLayerAt(heatmapLayer, 0) // Add at bottom
        }
        Timber.tag("MapLibrePOC").d("Added heatmap HeatmapLayer")
    }
}

/** Removes heatmap source and layer from the style */
fun removeHeatmapSourceAndLayer(style: Style) {
    style.getLayer(HEATMAP_LAYER_ID)?.let { style.removeLayer(it) }
    style.getSource(HEATMAP_SOURCE_ID)?.let { style.removeSource(it) }
    Timber.tag("MapLibrePOC").d("Removed heatmap source and layer")
}

/** Toggle visibility of node/cluster/waypoint layers */
fun setNodeLayersVisibility(style: Style, visible: Boolean) {
    val visibilityValue = if (visible) "visible" else "none"
    style.getLayer(NODES_LAYER_ID)?.setProperties(visibility(visibilityValue))
    style.getLayer(NODE_TEXT_LAYER_ID)?.setProperties(visibility(visibilityValue))
    style.getLayer(NODES_LAYER_NOCLUSTER_ID)?.setProperties(visibility(visibilityValue))
    style.getLayer(NODE_TEXT_LAYER_NOCLUSTER_ID)?.setProperties(visibility(visibilityValue))
    style.getLayer(CLUSTER_CIRCLE_LAYER_ID)?.setProperties(visibility(visibilityValue))
    style.getLayer(CLUSTER_COUNT_LAYER_ID)?.setProperties(visibility(visibilityValue))
    style.getLayer(WAYPOINTS_LAYER_ID)?.setProperties(visibility(visibilityValue))
    style.getLayer(PRECISION_CIRCLE_LAYER_ID)?.setProperties(visibility(visibilityValue))
    Timber.tag("MapLibrePOC").d("Set node layers visibility to: $visibilityValue")
}
