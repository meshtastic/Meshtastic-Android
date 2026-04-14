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
package org.meshtastic.feature.map.util

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node

private const val MIN_PRECISION_BITS = 10
private const val MAX_PRECISION_BITS = 19

/** Convert a list of nodes to a GeoJSON [FeatureCollection] for map rendering. */
internal fun nodesToFeatureCollection(nodes: List<Node>, myNodeNum: Int? = null): FeatureCollection<Point, JsonObject> {
    val features =
        nodes.mapNotNull { node ->
            val pos = node.validPosition ?: return@mapNotNull null
            val geoPos = toGeoPositionOrNull(pos.latitude_i, pos.longitude_i) ?: return@mapNotNull null

            val colors = node.colors
            val props = buildJsonObject {
                put("node_num", node.num)
                put("short_name", node.user.short_name)
                put("long_name", node.user.long_name)
                put("last_heard", node.lastHeard)
                put("is_favorite", node.isFavorite)
                put("is_my_node", node.num == myNodeNum)
                put("is_online", node.isOnline)
                put("battery_level", node.batteryLevel ?: -1)
                put("hops_away", node.hopsAway)
                put("via_mqtt", node.viaMqtt)
                put("snr", node.snr.toDouble())
                put("rssi", node.rssi)
                put("foreground_color", intToHexColor(colors.first))
                put("background_color", intToHexColor(colors.second))
                put("has_precision", pos.precision_bits in MIN_PRECISION_BITS..MAX_PRECISION_BITS)
                put("precision_meters", precisionBitsToMeters(pos.precision_bits))
            }

            Feature(geometry = Point(geoPos), properties = props)
        }

    return typedFeatureCollection(features)
}

/** Convert waypoints to a GeoJSON [FeatureCollection]. */
internal fun waypointsToFeatureCollection(waypoints: Map<Int, DataPacket>): FeatureCollection<Point, JsonObject> {
    val features =
        waypoints.values.mapNotNull { packet ->
            val waypoint = packet.waypoint ?: return@mapNotNull null
            val geoPos = toGeoPositionOrNull(waypoint.latitude_i, waypoint.longitude_i) ?: return@mapNotNull null

            val emoji = if (waypoint.icon != 0) convertIntToEmoji(waypoint.icon) else PIN_EMOJI

            val props = buildJsonObject {
                put("waypoint_id", waypoint.id)
                put("name", waypoint.name)
                put("description", waypoint.description)
                put("emoji", emoji)
                put("icon", waypoint.icon)
                put("locked_to", waypoint.locked_to)
                put("expire", waypoint.expire)
            }

            Feature(geometry = Point(geoPos), properties = props)
        }

    return typedFeatureCollection(features)
}

/** Convert position history to a GeoJSON [LineString] for track rendering. */
internal fun positionsToLineString(
    positions: List<org.meshtastic.proto.Position>,
): FeatureCollection<LineString, JsonObject> {
    val coords = positions.mapNotNull { pos -> toGeoPositionOrNull(pos.latitude_i, pos.longitude_i) }

    if (coords.size < 2) return FeatureCollection(emptyList())

    val props = buildJsonObject { put("point_count", coords.size) }

    val feature = Feature(geometry = LineString(coords), properties = props)

    return typedFeatureCollection(listOf(feature))
}

/** Convert position history to individual point features with time metadata. */
internal fun positionsToPointFeatures(
    positions: List<org.meshtastic.proto.Position>,
): FeatureCollection<Point, JsonObject> {
    val features =
        positions.mapNotNull { pos ->
            val geoPos = toGeoPositionOrNull(pos.latitude_i, pos.longitude_i) ?: return@mapNotNull null

            val props = buildJsonObject {
                put("time", pos.time.toString())
                put("altitude", pos.altitude ?: 0)
                put("ground_speed", pos.ground_speed ?: 0)
                put("sats_in_view", pos.sats_in_view)
            }

            Feature(geometry = Point(geoPos), properties = props)
        }

    return typedFeatureCollection(features)
}

/** Approximate meters of positional uncertainty from precision_bits (10-19). */
@Suppress("MagicNumber")
internal fun precisionBitsToMeters(precisionBits: Int): Double = when (precisionBits) {
    10 -> 5886.0
    11 -> 2944.0
    12 -> 1472.0
    13 -> 736.0
    14 -> 368.0
    15 -> 184.0
    16 -> 92.0
    17 -> 46.0
    18 -> 23.0
    19 -> 11.5
    else -> 0.0
}

private const val PIN_EMOJI = "\uD83D\uDCCD" // U+1F4CD Round Pushpin — same as DEFAULT_EMOJI in EditWaypointDialog

/**
 * Wraps [FeatureCollection] constructor with the desired type parameters. Centralizes the typed constructor call
 * required by the spatialk GeoJSON API.
 */
internal fun <G : Geometry, P> typedFeatureCollection(features: List<Feature<G, P>>): FeatureCollection<G, P> =
    FeatureCollection(features)

private const val BMP_MAX = 0xFFFF
private const val SUPPLEMENTARY_OFFSET = 0x10000
private const val HALF_SHIFT = 10
private const val HIGH_SURROGATE_BASE = 0xD800
private const val LOW_SURROGATE_BASE = 0xDC00
private const val SURROGATE_MASK = 0x3FF
private const val HEX_COLOR_MASK = 0xFFFFFF

/** Convert an ARGB color integer to a hex color string (e.g. "#FF6750A4") for MapLibre expressions. */
@Suppress("MagicNumber")
internal fun intToHexColor(argb: Int): String {
    val rgb = argb and HEX_COLOR_MASK
    return "#${rgb.toString(16).padStart(6, '0').uppercase()}"
}

/** Convert a Unicode code point integer to its emoji string representation. */
internal fun convertIntToEmoji(codePoint: Int): String = try {
    if (codePoint <= BMP_MAX) {
        codePoint.toChar().toString()
    } else {
        val offset = codePoint - SUPPLEMENTARY_OFFSET
        val high = (offset shr HALF_SHIFT) + HIGH_SURROGATE_BASE
        val low = (offset and SURROGATE_MASK) + LOW_SURROGATE_BASE
        buildString {
            append(high.toChar())
            append(low.toChar())
        }
    }
} catch (_: Exception) {
    PIN_EMOJI
}
