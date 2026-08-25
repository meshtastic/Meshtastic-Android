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
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.model.Node
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Mean Earth radius, metres (IUGG). */
private const val EARTH_RADIUS_M = 6371008.8

/** Vertices used to approximate a ground circle. 64 keeps the edge smooth at city zoom levels. */
private const val CIRCLE_STEPS = 64

private const val DEG_TO_RAD = PI / 180.0
private const val RAD_TO_DEG = 180.0 / PI

/**
 * Walks [distanceMeters] from ([latitude], [longitude]) along [bearingDegrees] on a sphere.
 *
 * Great-circle rather than a flat offset: a 23 km precision circle at high latitude is visibly lopsided if you just add
 * degrees.
 */
internal fun destination(
    latitude: Double,
    longitude: Double,
    distanceMeters: Double,
    bearingDegrees: Double,
): Position {
    val angular = distanceMeters / EARTH_RADIUS_M
    val bearing = bearingDegrees * DEG_TO_RAD
    val lat1 = latitude * DEG_TO_RAD
    val lon1 = longitude * DEG_TO_RAD

    val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
    val lon2 = lon1 + atan2(sin(bearing) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))

    return Position(longitude = lon2 * RAD_TO_DEG, latitude = lat2 * RAD_TO_DEG)
}

/**
 * A closed polygon ring approximating a circle of [radiusMeters] on the ground.
 *
 * MapLibre circle radii are screen-space, so a ground-truth circle has to be a real polygon — otherwise the "precision"
 * it communicates would change every time the user zooms.
 */
internal fun circlePolygon(
    latitude: Double,
    longitude: Double,
    radiusMeters: Double,
    steps: Int = CIRCLE_STEPS,
): Polygon {
    val ring = (0 until steps).map { i -> destination(latitude, longitude, radiusMeters, i * (360.0 / steps)) }
    return Polygon(listOf(ring + ring.first()))
}

/**
 * Ground circles for every node broadcasting a degraded position.
 *
 * Nodes at full precision contribute nothing, so an empty collection here means "no one is obscuring their location",
 * not "the layer failed".
 */
fun precisionCirclesToFeatureCollection(nodes: List<Node>): FeatureCollection<Polygon, JsonObject?> = FeatureCollection(
    nodes.mapNotNull { node ->
        node.validPosition ?: return@mapNotNull null
        val radius = precisionMeters(node.position.precision_bits ?: 0) ?: return@mapNotNull null
        val (_, background) = node.colors
        Feature(
            geometry = circlePolygon(node.latitude, node.longitude, radius),
            properties =
            buildJsonObject {
                put(NodeFeatureKeys.NODE_NUM, node.num)
                put(NodeFeatureKeys.BACKGROUND, background.toCssHex())
                put(NodeFeatureKeys.PRECISION_METERS, radius)
            },
        )
    },
)
