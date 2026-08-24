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
package org.meshtastic.core.model.geofence

import org.meshtastic.core.common.util.latLongToMeter
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.proto.Waypoint

/** A circular geofence centered on [centerLat]/[centerLon] (decimal degrees) with [radiusMeters]. */
data class GeofenceCircle(val centerLat: Double, val centerLon: Double, val radiusMeters: Int) {
    /** True when ([lat], [lon]) is within [radiusMeters] great-circle metres of the centre. */
    fun contains(lat: Double, lon: Double): Boolean = latLongToMeter(lat, lon, centerLat, centerLon) <= radiusMeters
}

/** An axis-aligned WSEN bounding box in decimal degrees. Bounds are inclusive (matches the Apple reference). */
data class GeofenceBox(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(lat: Double, lon: Double): Boolean = lat in south..north && lon in west..east
}

/**
 * A waypoint's geofence region: a [circle] and/or a [box]. A point is inside if it is in EITHER shape (OR semantics) —
 * both may be set, either may be null.
 */
data class Geofence(val circle: GeofenceCircle?, val box: GeofenceBox?) {
    fun contains(lat: Double, lon: Double): Boolean =
        (circle?.contains(lat, lon) == true) || (box?.contains(lat, lon) == true)
}

/** True when this waypoint asks receivers to raise crossing notifications. */
val Waypoint.notifiesOnCrossing: Boolean
    get() = notify_on_enter || notify_on_exit

/**
 * Decode this waypoint's geofence region, or null when it defines neither a circle nor a box. This is the single source
 * of the proto `×1e-7 → decimal-degree` conversion, shared by the alert engine, the map overlays, and the editor — keep
 * all geofence coordinate decoding here so the three consumers cannot drift.
 */
fun Waypoint.toGeofence(): Geofence? {
    val circle =
        if (geofence_radius > 0) {
            GeofenceCircle((latitude_i ?: 0) * DEG_D, (longitude_i ?: 0) * DEG_D, geofence_radius)
        } else {
            null
        }
    val box =
        bounding_box?.let {
            val southDeg = it.latitude_south_i * DEG_D
            val northDeg = it.latitude_north_i * DEG_D
            val westDeg = it.longitude_west_i * DEG_D
            val eastDeg = it.longitude_east_i * DEG_D
            // Normalize transposed corners so an inverted box (south>north / west>east) from another client still
            // describes the intended rectangle. (Antimeridian-crossing boxes remain a documented non-goal.)
            GeofenceBox(
                south = minOf(southDeg, northDeg),
                west = minOf(westDeg, eastDeg),
                north = maxOf(southDeg, northDeg),
                east = maxOf(westDeg, eastDeg),
            )
        }
    return if (circle == null && box == null) null else Geofence(circle, box)
}
