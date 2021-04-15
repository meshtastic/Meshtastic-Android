package com.geeksville.mesh.ui

import com.geeksville.mesh.MeshProtos
import kotlin.math.cos
import kotlin.math.sin

/*******************************************************************************
 * Revive some of my old Gaggle source code...
 *
 * GNU Public License, version 2
 * All other distribution of Gaggle must conform to the terms of the GNU Public License, version 2.  The full
 * text of this license is included in the Gaggle source, see assets/manual/gpl-2.0.txt.
 ******************************************************************************/


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
    degIn = Math.abs(degIn)
    val degOut = degIn.toInt()
    val minutes = 60 * (degIn - degOut)
    val minwhole = minutes.toInt()
    val seconds = (minutes - minwhole) * 60
    return arrayOf(
        Integer.toString(degOut), Integer.toString(minwhole),
        java.lang.Double.toString(seconds),
        Character.toString(dirLetter)
    )
}

fun degreesToDM(_degIn: Double, isLatitude: Boolean): Array<String> {
    var degIn = _degIn
    val isPos = degIn >= 0
    val dirLetter =
        if (isLatitude) if (isPos) 'N' else 'S' else if (isPos) 'E' else 'W'
    degIn = Math.abs(degIn)
    val degOut = degIn.toInt()
    val minutes = 60 * (degIn - degOut)
    val seconds = 0
    return arrayOf(
        Integer.toString(degOut), java.lang.Double.toString(minutes),
        Integer.toString(seconds),
        Character.toString(dirLetter)
    )
}

fun degreesToD(_degIn: Double, isLatitude: Boolean): Array<String> {
    var degIn = _degIn
    val isPos = degIn >= 0
    val dirLetter =
        if (isLatitude) if (isPos) 'N' else 'S' else if (isPos) 'E' else 'W'
    degIn = Math.abs(degIn)
    val degOut = degIn
    val minutes = 0
    val seconds = 0
    return arrayOf(
        java.lang.Double.toString(degOut), Integer.toString(minutes),
        Integer.toString(seconds),
        Character.toString(dirLetter)
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
    val dx = distMeters * Math.sin(theta) // theta measured clockwise
    // from due north
    val dy = distMeters * Math.cos(theta) // dx, dy same units as R
    val dLong = dx / (111320 * Math.cos(lat)) // dx, dy in meters
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
    val pk = (180 / 3.14169)
    val a1 = lat_a / pk
    val a2 = lng_a / pk
    val b1 = lat_b / pk
    val b2 = lng_b / pk
    val t1 =
        Math.cos(a1) * Math.cos(a2) * Math.cos(b1) * Math.cos(
            b2
        )
    val t2 =
        Math.cos(a1) * Math.sin(a2) * Math.cos(b1) * Math.sin(
            b2
        )
    val t3 = Math.sin(a1) * Math.sin(b1)
    var tt = Math.acos(t1 + t2 + t3)
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
    val x =
        cos(lat1Rad) * sin(lat2Rad) - (sin(lat1Rad) * cos(lat2Rad)
            * Math.cos(deltaLonRad))
    return radToBearing(Math.atan2(y, x))
}

/**
 * Converts an angle in radians to degrees
 */
fun radToBearing(rad: Double): Double {
    return (Math.toDegrees(rad) + 360) % 360
}
