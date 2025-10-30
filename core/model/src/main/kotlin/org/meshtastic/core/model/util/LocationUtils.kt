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

@file:Suppress("MatchingDeclarationName")

package org.meshtastic.core.model.util

import android.annotation.SuppressLint
import org.meshtastic.core.model.Position
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@SuppressLint("PropertyNaming")
object GPSFormat {
    fun toDec(latitude: Double, longitude: Double): String =
        String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
}

private const val EARTH_RADIUS_METERS = 6371e3

/** @return distance in meters along the surface of the earth (ish) */
fun latLongToMeter(latitudeA: Double, longitudeA: Double, latitudeB: Double, longitudeB: Double): Double {
    val lat1 = Math.toRadians(latitudeA)
    val lon1 = Math.toRadians(longitudeA)
    val lat2 = Math.toRadians(latitudeB)
    val lon2 = Math.toRadians(longitudeB)

    val dLat = lat2 - lat1
    val dLon = lon2 - lon1

    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    val c = 2 * asin(sqrt(a))

    return EARTH_RADIUS_METERS * c
}

// Same as above, but takes Mesh Position proto.
@Suppress("MagicNumber")
fun positionToMeter(a: Position, b: Position): Double = latLongToMeter(a.latitude, a.longitude, b.latitude, b.longitude)

/**
 * Computes the bearing in degrees between two points on Earth.
 *
 * @param lat1 Latitude of the first point
 * @param lon1 Longitude of the first point
 * @param lat2 Latitude of the second point
 * @param lon2 Longitude of the second point
 * @return Bearing between the two points in degrees. A value of 0 means due north.
 */
@Suppress("MagicNumber")
fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val lat1Rad = Math.toRadians(lat1)
    val lon1Rad = Math.toRadians(lon1)
    val lat2Rad = Math.toRadians(lat2)
    val lon2Rad = Math.toRadians(lon2)

    val dLon = lon2Rad - lon1Rad

    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    val bearing = Math.toDegrees(atan2(y, x))

    return (bearing + 360) % 360
}
