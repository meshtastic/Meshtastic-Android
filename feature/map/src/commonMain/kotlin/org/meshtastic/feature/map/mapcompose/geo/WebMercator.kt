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

package org.meshtastic.feature.map.mapcompose.geo

import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.proto.Position
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Web-Mercator projection between WGS84 lat/lon degrees and the normalized `[0,1] x [0,1]` coordinate space MapCompose
 * uses (origin top-left, x east, y south). MapCompose has no lat/lon API of its own, so every geographic value is
 * converted at this boundary — mirroring how the google renderer converts to `LatLng` at its edge.
 */
object WebMercator {

    /** Latitude bound of the square Web-Mercator world; poles beyond this are not representable. */
    const val MAX_LATITUDE = 85.05112878

    /** Radius (meters) of the WGS84 reference sphere used for meter-based offsets. */
    const val EARTH_RADIUS_M = 6_378_137.0

    fun latitudeToY(latitude: Double): Double {
        val lat = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val sinLat = sin(lat * PI / 180.0)
        return 0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)
    }

    fun longitudeToX(longitude: Double): Double = (longitude.coerceIn(-180.0, 180.0) + 180.0) / 360.0

    fun yToLatitude(y: Double): Double = 90.0 - 360.0 * atan(exp((y - 0.5) * 2 * PI)) / PI

    fun xToLongitude(x: Double): Double = x * 360.0 - 180.0

    fun toNormalized(latitude: Double, longitude: Double): NormalizedPoint =
        NormalizedPoint(longitudeToX(longitude), latitudeToY(latitude))

    /** Meters per normalized-x unit at [latitude] — used for drawing meter-radius shapes (precision circles). */
    fun metersToNormalized(meters: Double, latitude: Double): Double {
        val worldCircumference = 2 * PI * EARTH_RADIUS_M * cos(latitude * PI / 180.0)
        return meters / worldCircumference
    }

    /**
     * Smallest axis-aligned bounding box (in normalized space) containing [points], expanded by [paddingRatio] on every
     * side. Returns null when [points] is empty.
     */
    fun boundingBox(points: List<NormalizedPoint>, paddingRatio: Double = 0.1): NormalizedBox? {
        if (points.isEmpty()) return null
        var minX = 1.0
        var minY = 1.0
        var maxX = 0.0
        var maxY = 0.0
        points.forEach { p ->
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }
        val padX = (maxX - minX) * paddingRatio
        val padY = (maxY - minY) * paddingRatio
        return NormalizedBox(
            xLeft = (minX - padX).coerceIn(0.0, 1.0),
            yTop = (minY - padY).coerceIn(0.0, 1.0),
            xRight = (maxX + padX).coerceIn(0.0, 1.0),
            yBottom = (maxY + padY).coerceIn(0.0, 1.0),
        )
    }

    /**
     * Great-circle offset of a lat/lon point by [meters] toward [headingDegrees] (0 = north, 90 = east) — the KMP
     * equivalent of maps-utils `SphericalUtil.computeOffset`, used to draw offset traceroute polylines.
     */
    fun offset(latitude: Double, longitude: Double, meters: Double, headingDegrees: Double): Pair<Double, Double> {
        val distance = meters / EARTH_RADIUS_M
        val heading = headingDegrees * PI / 180.0
        val fromLat = latitude * PI / 180.0
        val fromLon = longitude * PI / 180.0
        val cosDistance = cos(distance)
        val sinDistance = sin(distance)
        val sinFromLat = sin(fromLat)
        val cosFromLat = cos(fromLat)
        val sinLat = cosDistance * sinFromLat + sinDistance * cosFromLat * cos(heading)
        val dLon = atan2(sinDistance * cosFromLat * sin(heading), cosDistance - sinFromLat * sinLat)
        val lat = asin(sinLat) * 180.0 / PI
        val lon = (fromLon + dLon) * 180.0 / PI
        return lat to normalizeLongitude(lon)
    }

    /**
     * Initial great-circle heading in degrees from point 1 to point 2 — the KMP equivalent of maps-utils
     * `SphericalUtil.computeHeading`.
     */
    fun heading(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val fromLat = lat1 * PI / 180.0
        val fromLon = lon1 * PI / 180.0
        val toLat = lat2 * PI / 180.0
        val toLon = lon2 * PI / 180.0
        val dLon = toLon - fromLon
        val y = sin(dLon) * cos(toLat)
        val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(dLon)
        return atan2(y, x) * 180.0 / PI
    }

    private fun normalizeLongitude(longitude: Double): Double {
        var lon = longitude
        while (lon > 180.0) lon -= 360.0
        while (lon < -180.0) lon += 360.0
        return lon
    }
}

/** A point in MapCompose's normalized `[0,1]` coordinate space. */
data class NormalizedPoint(val x: Double, val y: Double)

/** An axis-aligned box in normalized space; used for fit-bounds camera targets. */
data class NormalizedBox(val xLeft: Double, val yTop: Double, val xRight: Double, val yBottom: Double) {
    val centerX: Double
        get() = (xLeft + xRight) / 2

    val centerY: Double
        get() = (yTop + yBottom) / 2

    val width: Double
        get() = xRight - xLeft

    val height: Double
        get() = yBottom - yTop
}

/** Latitude in degrees from the 1e-7 fixed-point proto field. */
val Position.latitudeDegrees: Double
    get() = (latitude_i ?: 0) * DEG_D

/** Longitude in degrees from the 1e-7 fixed-point proto field. */
val Position.longitudeDegrees: Double
    get() = (longitude_i ?: 0) * DEG_D

fun Position.toNormalized(): NormalizedPoint = WebMercator.toNormalized(latitudeDegrees, longitudeDegrees)

fun Node.toNormalized(): NormalizedPoint = WebMercator.toNormalized(latitude, longitude)
