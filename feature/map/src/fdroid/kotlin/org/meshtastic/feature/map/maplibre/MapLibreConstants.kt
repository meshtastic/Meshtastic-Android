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
    const val OSM_SOURCE_ID = "osm-tiles"

    // Layer IDs
    const val NODES_LAYER_ID = "meshtastic-nodes-layer" // From clustered source, filtered
    const val NODE_TEXT_LAYER_ID = "meshtastic-node-text-layer" // From clustered source, filtered
    const val NODES_LAYER_NOCLUSTER_ID = "meshtastic-nodes-layer-nocluster" // From non-clustered source
    const val NODE_TEXT_LAYER_NOCLUSTER_ID = "meshtastic-node-text-layer-nocluster" // From non-clustered source
    const val CLUSTER_CIRCLE_LAYER_ID = "meshtastic-cluster-circle-layer"
    const val CLUSTER_COUNT_LAYER_ID = "meshtastic-cluster-count-layer"
    const val WAYPOINTS_LAYER_ID = "meshtastic-waypoints-layer"
    const val PRECISION_CIRCLE_LAYER_ID = "meshtastic-precision-circle-layer"
    const val OSM_LAYER_ID = "osm-layer"

    // Cluster configuration
    const val CLUSTER_RADIAL_MAX = 8
    const val CLUSTER_LIST_FETCH_MAX = 200L
}

/** Base map style options (raster tiles; key-free) */
enum class BaseMapStyle(val label: String, val urlTemplate: String) {
    OSM_STANDARD("OSM", "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png"),
    CARTO_LIGHT("Light", "https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"),
    CARTO_DARK("Dark", "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"),
    ESRI_SATELLITE(
        "Satellite",
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
    ),
}

/** Converts precision bits to meters for accuracy circles */
fun getPrecisionMeters(precisionBits: Int): Double? = when (precisionBits) {
    10 -> 23345.484932
    11 -> 11672.7369
    12 -> 5836.36288
    13 -> 2918.175876
    14 -> 1459.0823719999053
    15 -> 729.5370149076749
    16 -> 364.76796802673495
    17 -> 182.38363847854606
    18 -> 91.19178201473192
    19 -> 45.59587874512555
    20 -> 22.797938919871483
    21 -> 11.398969292955733
    22 -> 5.699484588175269
    23 -> 2.8497422889870207
    24 -> 1.424871149078816
    25 -> 0.7124355732781771
    26 -> 0.3562177850463231
    27 -> 0.17810889188369584
    28 -> 0.08905444562935878
    29 -> 0.04452722265708971
    30 -> 0.022263611293647812
    31 -> 0.011131805632411625
    32 -> 0.005565902808395108
    else -> null
}
