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
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.meshtastic.core.database.model.Node
import org.meshtastic.feature.map.maplibre.MapLibreConstants.DEG_D
import org.meshtastic.feature.map.maplibre.getPrecisionMeters
import org.meshtastic.feature.map.maplibre.utils.protoShortName
import org.meshtastic.feature.map.maplibre.utils.roleColorHex
import org.meshtastic.feature.map.maplibre.utils.safeSubstring
import org.meshtastic.feature.map.maplibre.utils.shortNameFallback
import org.meshtastic.feature.map.maplibre.utils.stripEmojisForMapLabel
import org.meshtastic.proto.MeshProtos.Position
import org.meshtastic.proto.MeshProtos.Waypoint
import timber.log.Timber

/** Converts nodes to GeoJSON FeatureCollection JSON string with label selection */
fun nodesToFeatureCollectionJsonWithSelection(nodes: List<Node>, labelNums: Set<Int>): String {
    val features =
        nodes.mapNotNull { node ->
            val pos = node.validPosition ?: return@mapNotNull null
            val lat = pos.latitudeI * DEG_D
            val lon = pos.longitudeI * DEG_D
            val short = safeSubstring(protoShortName(node) ?: shortNameFallback(node), 4)
            // Strip emojis for MapLibre rendering; if emoji-only, fall back to hex ID
            val shortForMap =
                stripEmojisForMapLabel(short)
                    ?: run {
                        val hex = node.num.toString(16).uppercase()
                        if (hex.length >= 4) hex.takeLast(4) else hex
                    }
            val shortEsc = escapeJson(shortForMap)
            val show = if (labelNums.contains(node.num)) 1 else 0
            val role = node.user.role.name
            val color = roleColorHex(node)
            val longEsc = escapeJson(node.user.longName ?: "")
            val precisionMeters = getPrecisionMeters(pos.precisionBits) ?: 0.0
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"kind":"node","num":${node.num},"name":"$longEsc","short":"$shortEsc","role":"$role","color":"$color","showLabel":$show,"precisionMeters":$precisionMeters}}"""
        }
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

/** Converts nodes to GeoJSON FeatureCollection object with label selection */
fun nodesToFeatureCollectionWithSelectionFC(nodes: List<Node>, labelNums: Set<Int>): FeatureCollection {
    var minLat = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var minLon = Double.POSITIVE_INFINITY
    var maxLon = Double.NEGATIVE_INFINITY
    val features =
        nodes.mapNotNull { node ->
            val pos = node.validPosition ?: return@mapNotNull null
            val lat = pos.latitudeI * DEG_D
            val lon = pos.longitudeI * DEG_D
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
            val point = Point.fromLngLat(lon, lat)
            val f = Feature.fromGeometry(point)
            f.addStringProperty("kind", "node")
            f.addNumberProperty("num", node.num)
            f.addStringProperty("name", node.user.longName ?: "")
            val short = safeSubstring(protoShortName(node) ?: shortNameFallback(node), 4)
            // Strip emojis for MapLibre rendering; if emoji-only, fall back to hex ID
            val shortForMap =
                stripEmojisForMapLabel(short)
                    ?: run {
                        val hex = node.num.toString(16).uppercase()
                        if (hex.length >= 4) hex.takeLast(4) else hex
                    }
            f.addStringProperty("short", shortForMap)
            f.addStringProperty("role", node.user.role.name)
            f.addStringProperty("color", roleColorHex(node))
            f.addNumberProperty("showLabel", if (labelNums.contains(node.num)) 1 else 0)
            val precisionMeters = getPrecisionMeters(pos.precisionBits) ?: 0.0
            f.addNumberProperty("precisionMeters", precisionMeters)
            f
        }
    Timber.tag("MapLibrePOC")
        .d("FC bounds: lat=[%.5f, %.5f] lon=[%.5f, %.5f] count=%d", minLat, maxLat, minLon, maxLon, features.size)
    return FeatureCollection.fromFeatures(features)
}

/** Converts waypoints to GeoJSON FeatureCollection */
fun waypointsToFeatureCollectionFC(
    waypoints: Collection<org.meshtastic.core.database.entity.Packet>,
): FeatureCollection {
    val features =
        waypoints.mapNotNull { pkt ->
            val w: Waypoint = pkt.data.waypoint ?: return@mapNotNull null
            val lat = w.latitudeI * DEG_D
            val lon = w.longitudeI * DEG_D
            if (lat == 0.0 && lon == 0.0) return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(lon, lat)).also { f ->
                f.addStringProperty("kind", "waypoint")
                f.addNumberProperty("id", w.id)
                f.addStringProperty("name", w.name ?: "Waypoint ${w.id}")
                // Convert icon codepoint to emoji string, use 📍 (0x1F4CD) as default
                val iconEmoji =
                    if (w.icon == 0) {
                        String(Character.toChars(0x1F4CD)) // 📍 Round Pushpin
                    } else {
                        String(Character.toChars(w.icon))
                    }
                f.addStringProperty("icon", iconEmoji)
                timber.log.Timber.tag("MapLibrePOC")
                    .d("Waypoint feature: lat=%.5f, lon=%.5f, icon=%s, name=%s", lat, lon, iconEmoji, w.name)
            }
        }
    timber.log.Timber.tag("MapLibrePOC").d("Created waypoints FC: %d features", features.size)
    return FeatureCollection.fromFeatures(features)
}

/** Safely sets GeoJSON on a source, parsing and validating first */
fun safeSetGeoJson(style: Style, sourceId: String, json: String) {
    try {
        val fc = FeatureCollection.fromJson(json)
        val count = fc.features()?.size ?: -1
        Timber.tag("MapLibrePOC").d("Setting %s: %d features", sourceId, count)
        (style.getSource(sourceId) as? GeoJsonSource)?.setGeoJson(fc)
    } catch (e: Throwable) {
        Timber.tag("MapLibrePOC").e(e, "Failed to parse/set GeoJSON for %s", sourceId)
    }
}

/** Escapes a string for safe inclusion in JSON */
fun escapeJson(input: String): String {
    val sb = StringBuilder()
    for (c in input) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\b' -> sb.append("\\b")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> {
                if (c < ' ') {
                    sb.append(String.format("\\u%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
    }
    return sb.toString()
}

/** Converts a list of positions to a GeoJSON LineString for track rendering */
fun positionsToLineStringFeature(positions: List<Position>): Feature? {
    if (positions.size < 2) return null

    val points =
        positions.map { pos ->
            val lat = pos.latitudeI * DEG_D
            val lon = pos.longitudeI * DEG_D
            Point.fromLngLat(lon, lat)
        }

    return Feature.fromGeometry(LineString.fromLngLats(points))
}

/** Converts a list of positions to a GeoJSON FeatureCollection for track point markers */
fun positionsToPointFeatures(positions: List<Position>): FeatureCollection {
    val features =
        positions.mapIndexed { index, pos ->
            val lat = pos.latitudeI * DEG_D
            val lon = pos.longitudeI * DEG_D
            val point = Point.fromLngLat(lon, lat)
            val feature = Feature.fromGeometry(point)

            feature.addStringProperty("kind", "track_point")
            feature.addNumberProperty("index", index)
            feature.addNumberProperty("time", pos.time)
            feature.addNumberProperty("altitude", pos.altitude)
            feature.addNumberProperty("groundSpeed", pos.groundSpeed)
            feature.addNumberProperty("groundTrack", pos.groundTrack)
            feature.addNumberProperty("satsInView", pos.satsInView)
            feature.addNumberProperty("latitude", lat)
            feature.addNumberProperty("longitude", lon)

            feature
        }

    return FeatureCollection.fromFeatures(features)
}

/** Converts nodes to simple GeoJSON FeatureCollection for heatmap */
fun nodesToHeatmapFeatureCollection(nodes: List<Node>): FeatureCollection {
    val features =
        nodes.mapNotNull { node ->
            val pos = node.validPosition ?: return@mapNotNull null
            val lat = pos.latitudeI * DEG_D
            val lon = pos.longitudeI * DEG_D
            val point = Point.fromLngLat(lon, lat)
            Feature.fromGeometry(point)
        }
    return FeatureCollection.fromFeatures(features)
}
