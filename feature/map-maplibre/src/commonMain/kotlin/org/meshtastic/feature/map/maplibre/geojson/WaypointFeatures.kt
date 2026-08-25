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
package org.meshtastic.feature.map.maplibre.geojson

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.geofence.toGeofence

/** Scale factor turning firmware's fixed-point degrees into floating-point degrees. */
private const val DEG_SCALE = 1e-7

/** Property keys carried on waypoint features. */
object WaypointFeatureKeys {
    const val WAYPOINT_ID = "waypointId"
    const val NAME = "name"
    const val ICON = "icon"
    const val IS_LOCKED = "isLocked"
}

/**
 * Waypoint markers.
 *
 * The emoji `icon` is emitted as a string and drawn through a text field rather than an image layer — MapLibre renders
 * it with the glyph stack, so there is no sprite sheet to maintain.
 */
fun waypointsToFeatureCollection(waypoints: Collection<DataPacket>): FeatureCollection<Point, JsonObject?> =
    FeatureCollection(
        waypoints.mapNotNull { packet ->
            val waypoint = packet.waypoint ?: return@mapNotNull null
            val latitude = (waypoint.latitude_i ?: 0) * DEG_SCALE
            val longitude = (waypoint.longitude_i ?: 0) * DEG_SCALE
            if (latitude == 0.0 && longitude == 0.0) return@mapNotNull null
            Feature(
                geometry = Point(Position(longitude = longitude, latitude = latitude)),
                properties =
                buildJsonObject {
                    put(WaypointFeatureKeys.WAYPOINT_ID, waypoint.id)
                    put(WaypointFeatureKeys.NAME, waypoint.name)
                    put(WaypointFeatureKeys.ICON, iconGlyph(waypoint.icon))
                    put(WaypointFeatureKeys.IS_LOCKED, (waypoint.locked_to ?: 0) != 0)
                },
            )
        },
    )

/** Firmware stores a waypoint icon as a Unicode code point; 0 means "no icon chosen". */
private fun iconGlyph(codePoint: Int?): String {
    val cp = codePoint ?: 0
    return if (cp == 0) "📍" else buildString { appendCodePointCompat(cp) }
}

private fun StringBuilder.appendCodePointCompat(codePoint: Int) {
    @Suppress("MagicNumber")
    if (codePoint <= 0xFFFF) {
        append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        append(((offset shr 10) + 0xD800).toChar())
        append(((offset and 0x3FF) + 0xDC00).toChar())
    }
}

/**
 * Geofence zones attached to waypoints, as real ground polygons.
 *
 * Circles are tessellated with the same helper the precision circles use, so a geofence drawn here and the
 * [org.meshtastic.core.model.geofence.Geofence.contains] test the alert engine runs agree about where the boundary is.
 */
fun geofencesToFeatureCollection(waypoints: Collection<DataPacket>): FeatureCollection<Geometry, JsonObject?> =
    FeatureCollection(
        waypoints.flatMap { packet ->
            val waypoint = packet.waypoint ?: return@flatMap emptyList()
            val geofence = waypoint.toGeofence() ?: return@flatMap emptyList()
            buildList {
                geofence.circle?.let { circle ->
                    add(
                        Feature<Geometry, JsonObject?>(
                            geometry =
                            circlePolygon(circle.centerLat, circle.centerLon, circle.radiusMeters.toDouble()),
                            properties = buildJsonObject { put(WaypointFeatureKeys.WAYPOINT_ID, waypoint.id) },
                        ),
                    )
                }
                geofence.box?.let { box ->
                    val ring =
                        listOf(
                            Position(longitude = box.west, latitude = box.south),
                            Position(longitude = box.west, latitude = box.north),
                            Position(longitude = box.east, latitude = box.north),
                            Position(longitude = box.east, latitude = box.south),
                            Position(longitude = box.west, latitude = box.south),
                        )
                    add(
                        Feature<Geometry, JsonObject?>(
                            geometry = Polygon(listOf(ring)),
                            properties = buildJsonObject { put(WaypointFeatureKeys.WAYPOINT_ID, waypoint.id) },
                        ),
                    )
                }
            }
        },
    )
