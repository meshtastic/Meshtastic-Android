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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.map.terrain.spatial

import org.meshtastic.feature.map.terrain.GeoBounds
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/** A latitude and longitude in degrees. */
data class LatLon(val latitude: Double, val longitude: Double)

/** A point or vector in meters or model units. */
data class Vec3(val x: Float, val y: Float, val z: Float)

/** A point or vector on the table plane, x east and y toward the viewer. */
data class Vec2(val x: Float, val y: Float)

/**
 * The local frame a spatial map is built in: meters from the center of a geographic box, with x east, y up and z south
 * (north is away from a viewer standing south of the model). The box is small enough that a flat-earth conversion
 * around its center is accurate to well under a meter, which is finer than any mesh position.
 */
data class SpatialFrame(val bounds: GeoBounds) {
    val centerLatitude: Double = (bounds.south + bounds.north) / 2
    val centerLongitude: Double = (bounds.west + bounds.east) / 2

    /** Meters per degree of longitude at the center latitude. */
    val metersPerDegreeLongitude: Double = METERS_PER_DEGREE_LATITUDE * cos(centerLatitude * PI / 180)

    /** Width (east-west) of the box in meters. */
    val widthMeters: Double
        get() = (bounds.east - bounds.west) * metersPerDegreeLongitude

    /** Depth (north-south) of the box in meters. */
    val depthMeters: Double
        get() = (bounds.north - bounds.south) * METERS_PER_DEGREE_LATITUDE

    /** Local position of a coordinate at a height in meters above sea level. */
    fun localPosition(latitude: Double, longitude: Double, altitude: Double): Vec3 = Vec3(
        ((longitude - centerLongitude) * metersPerDegreeLongitude).toFloat(),
        altitude.toFloat(),
        (-(latitude - centerLatitude) * METERS_PER_DEGREE_LATITUDE).toFloat(),
    )

    fun localPosition(coordinate: LatLon, altitude: Double): Vec3 =
        localPosition(coordinate.latitude, coordinate.longitude, altitude)

    /** The coordinate at a local x/z position. */
    fun coordinate(x: Float, z: Float): LatLon = LatLon(
        latitude = centerLatitude - z.toDouble() / METERS_PER_DEGREE_LATITUDE,
        longitude = centerLongitude + x.toDouble() / metersPerDegreeLongitude,
    )

    /**
     * Fraction (0...1) across the box, west to east and north to south, in Web Mercator. Used to line vertices up with
     * a Mercator map image of the same box. Returns (u, v).
     */
    fun mercatorFraction(latitude: Double, longitude: Double): Pair<Double, Double> {
        val u = (longitude - bounds.west) / (bounds.east - bounds.west)
        val top = mercatorY(bounds.north)
        val bottom = mercatorY(bounds.south)
        val v = (mercatorY(latitude) - top) / (bottom - top)
        return u to v
    }

    /** Latitude at a Mercator fraction (0 = north edge, 1 = south edge) of the box. */
    fun latitudeAtMercatorFraction(v: Double): Double {
        val top = mercatorY(bounds.north)
        val bottom = mercatorY(bounds.south)
        return latitudeOfMercatorY(top + v * (bottom - top))
    }

    fun longitudeAtFraction(u: Double): Double = bounds.west + u * (bounds.east - bounds.west)

    companion object {
        const val METERS_PER_DEGREE_LATITUDE = 111_320.0

        /** Web Mercator y in 0...1 (0 at the north pole limit). */
        fun mercatorY(latitude: Double): Double {
            val clamped = latitude.coerceIn(-85.05, 85.05)
            val rad = clamped * PI / 180
            return (1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2
        }

        fun latitudeOfMercatorY(y: Double): Double = atan(sinh(PI * (1 - 2 * y))) * 180 / PI
    }
}

/**
 * The round area the spatial map shows. The terrain is a disk of this radius; the elevation grid, the map image and
 * terrain downloads cover [bounds], the square around it.
 */
data class SpatialCircle(val latitude: Double, val longitude: Double, val radiusMeters: Double) {

    val metersPerDegreeLongitude: Double
        get() = SpatialFrame.METERS_PER_DEGREE_LATITUDE * cos(latitude * PI / 180)

    /** Ground distance from the center, flat-earth in the same frame the model is built in. */
    fun distanceMeters(coordinate: LatLon): Double {
        val x = (coordinate.longitude - longitude) * metersPerDegreeLongitude
        val z = (coordinate.latitude - latitude) * SpatialFrame.METERS_PER_DEGREE_LATITUDE
        return sqrt(x * x + z * z)
    }

    fun contains(coordinate: LatLon): Boolean = distanceMeters(coordinate) <= radiusMeters

    /** The square around the circle. */
    val bounds: GeoBounds
        get() {
            val latSpan = radiusMeters / SpatialFrame.METERS_PER_DEGREE_LATITUDE
            val lonSpan = radiusMeters / metersPerDegreeLongitude
            return GeoBounds(
                south = max(latitude - latSpan, -85.0),
                west = longitude - lonSpan,
                north = min(latitude + latSpan, 85.0),
                east = longitude + lonSpan,
            )
        }

    companion object {
        /**
         * A circle centered on the middle of the coordinates' extent, reaching the farthest one plus [padding] of that
         * distance, and never smaller than [minimumRadiusMeters]. Null when empty.
         */
        fun around(
            coordinates: List<LatLon>,
            padding: Double = 0.15,
            minimumRadiusMeters: Double = 1_000.0,
        ): SpatialCircle? {
            val first = coordinates.firstOrNull() ?: return null
            var minLat = first.latitude
            var maxLat = first.latitude
            var minLon = first.longitude
            var maxLon = first.longitude
            for (c in coordinates) {
                minLat = min(minLat, c.latitude)
                maxLat = max(maxLat, c.latitude)
                minLon = min(minLon, c.longitude)
                maxLon = max(maxLon, c.longitude)
            }
            val unpadded = SpatialCircle((minLat + maxLat) / 2, (minLon + maxLon) / 2, 0.0)
            val farthest = coordinates.maxOf { unpadded.distanceMeters(it) }
            return SpatialCircle(
                unpadded.latitude,
                unpadded.longitude,
                max(farthest * (1 + padding), minimumRadiusMeters),
            )
        }
    }
}

fun GeoBounds.contains(coordinate: LatLon): Boolean = coordinate.latitude >= south &&
    coordinate.latitude <= north &&
    coordinate.longitude >= west &&
    coordinate.longitude <= east

fun GeoBounds.contains(other: GeoBounds): Boolean =
    other.west >= west && other.east <= east && other.south >= south && other.north <= north

fun GeoBounds.intersects(other: GeoBounds): Boolean =
    east > other.west && west < other.east && north > other.south && south < other.north
