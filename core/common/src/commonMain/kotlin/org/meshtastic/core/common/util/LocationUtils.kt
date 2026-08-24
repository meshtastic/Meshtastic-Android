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
@file:Suppress("MatchingDeclarationName")

package org.meshtastic.core.common.util

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Suppress("MagicNumber")
object GPSFormat {
    fun toDec(latitude: Double, longitude: Double): String {
        // Simple decimal formatting for KMP
        fun Double.format(digits: Int): String {
            val multiplier = 10.0.pow(digits)
            val rounded = (this * multiplier).toLong() / multiplier
            return rounded.toString()
        }
        return "${latitude.format(5)}, ${longitude.format(5)}"
    }
}

private const val EARTH_RADIUS_METERS = 6371e3

@Suppress("MagicNumber")
private fun Double.toRadians(): Double = this * PI / 180.0

@Suppress("MagicNumber")
private fun Double.toDegrees(): Double = this * 180.0 / PI

/** @return distance in meters along the surface of the earth (ish) */
@Suppress("MagicNumber")
fun latLongToMeter(latitudeA: Double, longitudeA: Double, latitudeB: Double, longitudeB: Double): Double {
    val lat1 = latitudeA.toRadians()
    val lon1 = longitudeA.toRadians()
    val lat2 = latitudeB.toRadians()
    val lon2 = longitudeB.toRadians()

    val dLat = lat2 - lat1
    val dLon = lon2 - lon1

    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    val c = 2 * asin(sqrt(a))

    return EARTH_RADIUS_METERS * c
}

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
    val lat1Rad = lat1.toRadians()
    val lon1Rad = lon1.toRadians()
    val lat2Rad = lat2.toRadians()
    val lon2Rad = lon2.toRadians()

    val dLon = lon2Rad - lon1Rad

    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    val bearing = atan2(y, x).toDegrees()

    return (bearing + 360) % 360
}
