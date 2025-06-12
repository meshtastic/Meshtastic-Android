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

package com.geeksville.mesh.util

import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.Position
import mil.nga.grid.features.Point
import mil.nga.mgrs.MGRS
import mil.nga.mgrs.utm.UTM
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.PI

/*******************************************************************************
 * Revive some of my old Gaggle source code...
 *
 * GNU Public License, version 2
 * All other distribution of Gaggle must conform to the terms of the GNU Public License, version 2.  The full
 * text of this license is included in the Gaggle source, see assets/manual/gpl-2.0.txt.
 ******************************************************************************/

object GPSFormat {
    fun DEC(p: Position): String {
        return String.format("%.5f %.5f", p.latitude, p.longitude).replace(",", ".")
    }

    fun DMS(p: Position): String {
        val lat = degreesToDMS(p.latitude, true)
        val lon = degreesToDMS(p.longitude, false)
        fun string(a: Array<String>) = String.format("%s°%s'%.5s\"%s", a[0], a[1], a[2], a[3])
        return string(lat) + " " + string(lon)
    }

    fun UTM(p: Position): String {
        val UTM = UTM.from(Point.point(p.longitude, p.latitude))
        return String.format(
            "%s%s %.6s %.7s",
            UTM.zone,
            UTM.toMGRS().band,
            UTM.easting,
            UTM.northing
        )
    }

    fun MGRS(p: Position): String {
        val MGRS = MGRS.from(Point.point(p.longitude, p.latitude))
        return String.format(
            "%s%s %s%s %05d %05d",
            MGRS.zone,
            MGRS.band,
            MGRS.column,
            MGRS.row,
            MGRS.easting,
            MGRS.northing
        )
    }

    fun toDEC(latitude: Double, longitude: Double): String {
        return "%.5f %.5f".format(latitude, longitude).replace(",", ".")
    }

    fun toDMS(latitude: Double, longitude: Double): String {
        val lat = degreesToDMS(latitude, true)
        val lon = degreesToDMS(longitude, false)
        fun string(a: Array<String>) = "%s°%s'%.5s\"%s".format(a[0], a[1], a[2], a[3])
        return string(lat) + " " + string(lon)
    }

    fun toUTM(latitude: Double, longitude: Double): String {
        val UTM = UTM.from(Point.point(longitude, latitude))
        return "%s%s %.6s %.7s".format(UTM.zone, UTM.toMGRS().band, UTM.easting, UTM.northing)
    }

    fun toMGRS(latitude: Double, longitude: Double): String {
        val MGRS = MGRS.from(Point.point(longitude, latitude))
        return "%s%s %s%s %05d %05d".format(
            MGRS.zone,
            MGRS.band,
            MGRS.column,
            MGRS.row,
            MGRS.easting,
            MGRS.northing
        )
    }
}

/**
 * Format as degrees, minutes, secs
 *
 * @param degIn
 * @param isLatitude
 * @return a string like 120deg
 */
fun degreesToDMS(
    _degIn: Double,
    isLatitude: Boolean
): Array<String> {
    var degIn = _degIn
    val isPos = degIn >= 0
    val dirLetter =
        if (isLatitude) if (isPos) 'N' else 'S' else if (isPos) 'E' else 'W'
    degIn = abs(degIn)
    val degOut = degIn.toInt()
    val minutes = 60 * (degIn - degOut)
    val minwhole = minutes.toInt()
    val seconds = (minutes - minwhole) * 60
    return arrayOf(
        degOut.toString(), minwhole.toString(),
        seconds.toString(),
        dirLetter.toString()
    )
}

fun degreesToDM(_degIn: Double, isLatitude: Boolean): Array<String> {
    var degIn = _degIn
    val isPos = degIn >= 0
    val dirLetter =
        if (isLatitude) if (isPos) 'N' else 'S' else if (isPos) 'E' else 'W'
    degIn = abs(degIn)
    val degOut = degIn.toInt()
    val minutes = 60 * (degIn - degOut)
    val seconds = 0
    return arrayOf(
        degOut.toString(), minutes.toString(),
        seconds.toString(),
        dirLetter.toString()
    )
}

fun degreesToD(_degIn: Double, isLatitude: Boolean): Array<String> {
    var degIn = _degIn
    val isPos = degIn >= 0
    val dirLetter =
        if (isLatitude) if (isPos) 'N' else 'S' else if (isPos) 'E' else 'W'
    degIn = abs(degIn)
    val degOut = degIn
    val minutes = 0
    val seconds = 0
    return arrayOf(
        degOut.toString(), minutes.toString(),
        seconds.toString(),
        dirLetter.toString()
    )
}

/**
 * A not super efficent mapping from a starting lat/long + a distance at a
 * certain direction
 *
 * @param lat
 * @param longitude
 * @param distMeters
 * @param theta
 * in radians, 0 == north
 * @return an array with lat and long
 */
fun addDistance(
    lat: Double,
    longitude: Double,
    distMeters: Double,
    theta: Double
): DoubleArray {
    val dx = distMeters * sin(theta) // theta measured clockwise
    // from due north
    val dy = distMeters * cos(theta) // dx, dy same units as R
    val dLong = dx / (111320 * cos(lat)) // dx, dy in meters
    val dLat = dy / 110540 // result in degrees long/lat
    return doubleArrayOf(lat + dLat, longitude + dLong)
}

/**
 * @return distance in meters along the surface of the earth (ish)
 */
fun latLongToMeter(
    lat_a: Double,
    lng_a: Double,
    lat_b: Double,
    lng_b: Double
): Double {
    val pk = (180 / PI)
    val a1 = lat_a / pk
    val a2 = lng_a / pk
    val b1 = lat_b / pk
    val b2 = lng_b / pk
    val t1 = cos(a1) * cos(a2) * cos(b1) * cos(b2)
    val t2 = cos(a1) * sin(a2) * cos(b1) * sin(b2)
    val t3 = sin(a1) * sin(b1)
    var tt = acos(t1 + t2 + t3)
    if (java.lang.Double.isNaN(tt)) tt = 0.0 // Must have been the same point?
    return 6366000 * tt
}

// Same as above, but takes Mesh Position proto.
fun positionToMeter(a: MeshProtos.Position, b: MeshProtos.Position): Double {
    return latLongToMeter(
        a.latitudeI * 1e-7,
        a.longitudeI * 1e-7,
        b.latitudeI * 1e-7,
        b.longitudeI * 1e-7
    )
}

/**
 * Convert degrees/mins/secs to a single double
 *
 * @param degrees
 * @param minutes
 * @param seconds
 * @param isPostive
 * @return
 */
fun DMSToDegrees(
    degrees: Int,
    minutes: Int,
    seconds: Float,
    isPostive: Boolean
): Double {
    return (if (isPostive) 1 else -1) * (degrees + minutes / 60.0 + seconds / 3600.0)
}

fun DMSToDegrees(
    degrees: Double,
    minutes: Double,
    seconds: Double,
    isPostive: Boolean
): Double {
    return (if (isPostive) 1 else -1) * (degrees + minutes / 60.0 + seconds / 3600.0)
}

/**
 * Computes the bearing in degrees between two points on Earth.
 *
 * @param lat1
 * Latitude of the first point
 * @param lon1
 * Longitude of the first point
 * @param lat2
 * Latitude of the second point
 * @param lon2
 * Longitude of the second point
 * @return Bearing between the two points in degrees. A value of 0 means due
 * north.
 */
fun bearing(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val deltaLonRad = Math.toRadians(lon2 - lon1)
    val y = sin(deltaLonRad) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - (sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad))
    return radToBearing(atan2(y, x))
}

/**
 * Converts an angle in radians to degrees
 */
fun radToBearing(rad: Double): Double {
    return (Math.toDegrees(rad) + 360) % 360
}

/**
 * Calculates the zoom level required to fit the entire [BoundingBox] inside the map view.
 * @return The zoom level as a Double value.
 */
fun BoundingBox.requiredZoomLevel(): Double {
    val topLeft = GeoPoint(this.latNorth, this.lonWest)
    val bottomRight = GeoPoint(this.latSouth, this.lonEast)
    val latLonWidth = topLeft.distanceToAsDouble(GeoPoint(topLeft.latitude, bottomRight.longitude))
    val latLonHeight = topLeft.distanceToAsDouble(GeoPoint(bottomRight.latitude, topLeft.longitude))
    val requiredLatZoom = log2(360.0 / (latLonHeight / 111320))
    val requiredLonZoom = log2(360.0 / (latLonWidth / 111320))
    return maxOf(requiredLatZoom, requiredLonZoom) * 0.8
}

/**
 * Creates a new bounding box with adjusted dimensions based on the provided [zoomFactor].
 * @return A new [BoundingBox] with added [zoomFactor]. Example:
 * ```
 * // Setting the zoom level directly using setZoom()
 * map.setZoom(14.0)
 * val boundingBoxZoom14 = map.boundingBox
 *
 * // Using zoomIn() results the equivalent BoundingBox with setZoom(15.0)
 * val boundingBoxZoom15 = boundingBoxZoom14.zoomIn(1.0)
 * ```
 */
fun BoundingBox.zoomIn(zoomFactor: Double): BoundingBox {
    val center = GeoPoint((latNorth + latSouth) / 2, (lonWest + lonEast) / 2)
    val latDiff = latNorth - latSouth
    val lonDiff = lonEast - lonWest

    val newLatDiff = latDiff / (2.0.pow(zoomFactor))
    val newLonDiff = lonDiff / (2.0.pow(zoomFactor))

    return BoundingBox(
        center.latitude + newLatDiff / 2,
        center.longitude + newLonDiff / 2,
        center.latitude - newLatDiff / 2,
        center.longitude - newLonDiff / 2
    )
}
