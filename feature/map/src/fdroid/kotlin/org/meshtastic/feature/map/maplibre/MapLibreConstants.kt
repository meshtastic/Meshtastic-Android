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

package org.meshtastic.feature.map.maplibre

/** Constants used throughout the MapLibre implementation */
object MapLibreConstants {
    // Coordinate conversion
    const val DEG_D = 1e-7

    // Style URL
    const val STYLE_URL = "https://demotiles.maplibre.org/style.json"

    // Source IDs
    const val NODES_SOURCE_ID = "meshtastic-nodes-source"
    const val NODES_CLUSTER_SOURCE_ID = "meshtastic-nodes-source-clustered"
    const val WAYPOINTS_SOURCE_ID = "meshtastic-waypoints-source"
    const val TRACK_LINE_SOURCE_ID = "meshtastic-track-line-source"
    const val TRACK_POINTS_SOURCE_ID = "meshtastic-track-points-source"
    const val HEATMAP_SOURCE_ID = "meshtastic-heatmap-source"
    const val OSM_SOURCE_ID = "osm-tiles"
    const val NODE_COLOR_PROPERTY = "nodeColor"
    const val ROLE_COLOR_PROPERTY = "roleColor"

    // Layer IDs
    const val NODES_LAYER_ID = "meshtastic-nodes-layer" // From clustered source, filtered
    const val NODE_TEXT_LAYER_ID = "meshtastic-node-text-layer" // From clustered source, filtered
    const val NODES_LAYER_NOCLUSTER_ID = "meshtastic-nodes-layer-nocluster" // From non-clustered source
    const val NODE_TEXT_LAYER_NOCLUSTER_ID = "meshtastic-node-text-layer-nocluster" // From non-clustered source
    const val CLUSTER_CIRCLE_LAYER_ID = "meshtastic-cluster-circle-layer"
    const val CLUSTER_COUNT_LAYER_ID = "meshtastic-cluster-count-layer"
    const val WAYPOINTS_LAYER_ID = "meshtastic-waypoints-layer"
    const val PRECISION_CIRCLE_LAYER_ID = "meshtastic-precision-circle-layer"
    const val TRACK_LINE_LAYER_ID = "meshtastic-track-line-layer"
    const val TRACK_POINTS_LAYER_ID = "meshtastic-track-points-layer"
    const val HEATMAP_LAYER_ID = "meshtastic-heatmap-layer"
    const val OSM_LAYER_ID = "osm-layer"

    // Cluster configuration
    const val CLUSTER_RADIAL_MAX = 8
    const val CLUSTER_LIST_FETCH_MAX = 200L
}

/** Base map style options (raster tiles; key-free) */
enum class BaseMapStyle(val label: String, val urlTemplate: String, val minZoom: Float, val maxZoom: Float) {
    OSM_STANDARD(
        label = "OSM",
        urlTemplate = "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png",
        minZoom = 0f,
        maxZoom = 19f,
    ),
    CARTO_LIGHT(
        label = "Light",
        urlTemplate = "https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png",
        minZoom = 0f,
        maxZoom = 20f,
    ),
    CARTO_DARK(
        label = "Dark",
        urlTemplate = "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
        minZoom = 0f,
        maxZoom = 20f,
    ),
    ESRI_SATELLITE(
        label = "Satellite",
        urlTemplate = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        minZoom = 1f,
        maxZoom = 19f,
    ),
}

/** Converts precision bits to meters for accuracy circles */
fun getPrecisionMeters(precisionBits: Int): Double? {
    // Use the same formula as the core UI component for consistency
    // Formula: 23905787.925008 * 0.5^bits
    // Returns null for invalid precision bits (typically < 10 or > 32)
    return if (precisionBits in 10..32) {
        org.meshtastic.core.ui.component.precisionBitsToMeters(precisionBits)
    } else {
        null
    }
}
