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
package org.meshtastic.app.map.offline.pmtiles

internal enum class RoadClass {
    Major,
    Medium,
    Minor,
    Path,
    Rail,
}

/** Where a road runs, in drawing order: tunnels under everything, bridges over the other roads. */
internal enum class RoadStructure {
    Tunnel,
    Surface,
    Bridge,
}

/** Reads the Protomaps basemap's per-feature attributes (current schema, with the older `pmap:` names as fallback). */
internal object OfflineMapStyle {

    /** Rail and waterways whose `min_zoom` is above this draw only from this zoom on. */
    const val FAR_BAND_ZOOM = 11.0

    private const val MAX_SORT_RANK = 100_000.0

    private val MAJOR_ROAD_WIDTHS = listOf(5.0 to 0.4f, 9.0 to 0.9f, 12.0 to 1.6f, 15.0 to 4f, 18.0 to 12f)
    private val MEDIUM_ROAD_WIDTHS = listOf(9.0 to 0.3f, 12.0 to 0.9f, 15.0 to 3f, 18.0 to 10f)
    private val MINOR_ROAD_WIDTHS = listOf(12.0 to 0.3f, 13.0 to 0.6f, 15.0 to 2f, 18.0 to 9f)
    private val PATH_WIDTHS = listOf(15.0 to 1f, 18.0 to 4f)
    private val RAIL_WIDTHS = listOf(0.0 to 1f)

    fun kind(properties: Map<String, Any>): String? = (properties["kind"] ?: properties["pmap:kind"]) as? String

    fun kindDetail(properties: Map<String, Any>): String? =
        (properties["kind_detail"] ?: properties["pmap:kind_detail"]) as? String

    fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble().takeIf { it.isFinite() }
        is String -> value.toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }

    fun minZoom(properties: Map<String, Any>): Double =
        number(properties["min_zoom"] ?: properties["pmap:min_zoom"]) ?: 0.0

    fun sortRank(properties: Map<String, Any>): Int =
        number(properties["sort_rank"])?.coerceIn(-MAX_SORT_RANK, MAX_SORT_RANK)?.toInt() ?: 0

    /** The current schema has no medium kind: secondary and tertiary roads are `major_road` with that detail. */
    fun roadClass(kind: String?, kindDetail: String?): RoadClass = when {
        kind == "path" && kindDetail == "pedestrian" -> RoadClass.Minor

        kind == "major_road" && (kindDetail == "secondary" || kindDetail == "tertiary") -> RoadClass.Medium

        else ->
            when (kind) {
                "highway",
                "motorway",
                "freeway",
                "major_road",
                "trunk",
                "primary",
                -> RoadClass.Major

                "medium_road",
                "secondary",
                "tertiary",
                -> RoadClass.Medium

                "path",
                "footway",
                "cycleway",
                "track",
                -> RoadClass.Path

                "rail" -> RoadClass.Rail

                else -> RoadClass.Minor
            }
    }

    /** `is_tunnel` / `is_bridge` in the current schema, or an older basemap's `pmap:level` (below 0 a tunnel). */
    fun roadStructure(properties: Map<String, Any>): RoadStructure {
        fun flag(value: Any?): Boolean = value as? Boolean ?: ((number(value) ?: 0.0) != 0.0)
        return when {
            flag(properties["is_tunnel"]) -> RoadStructure.Tunnel

            flag(properties["is_bridge"]) -> RoadStructure.Bridge

            else ->
                when {
                    (number(properties["pmap:level"]) ?: 0.0) < 0 -> RoadStructure.Tunnel
                    (number(properties["pmap:level"]) ?: 0.0) > 0 -> RoadStructure.Bridge
                    else -> RoadStructure.Surface
                }
        }
    }

    /** Stroke width in dp at a map [zoom], 0 where the road class isn't drawn yet. */
    fun roadWidthDp(roadClass: RoadClass, zoom: Double): Float = interpolate(
        when (roadClass) {
            RoadClass.Major -> MAJOR_ROAD_WIDTHS
            RoadClass.Medium -> MEDIUM_ROAD_WIDTHS
            RoadClass.Minor -> MINOR_ROAD_WIDTHS
            RoadClass.Path -> PATH_WIDTHS
            RoadClass.Rail -> RAIL_WIDTHS
        },
        zoom,
    )

    /** Whether a rail or waterway line with this `min_zoom` is drawn at [zoom]. */
    fun lineVisible(minZoom: Double, zoom: Double): Boolean = minZoom <= FAR_BAND_ZOOM || zoom >= FAR_BAND_ZOOM

    /** Linear between the stops either side of [zoom], the last stop's width past the end, 0 before the first. */
    fun interpolate(stops: List<Pair<Double, Float>>, zoom: Double): Float {
        val segment = stops.zipWithNext().firstOrNull { zoom < it.second.first }
        return when {
            stops.isEmpty() || zoom < stops.first().first -> 0f

            segment == null -> stops.last().second

            else -> {
                val (low, high) = segment
                low.second + ((zoom - low.first) / (high.first - low.first)).toFloat() * (high.second - low.second)
            }
        }
    }
}
