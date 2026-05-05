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

import org.meshtastic.sdk.PositionUtils
import kotlin.math.pow

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

/** @return distance in meters along the surface of the earth (ish) */
fun latLongToMeter(latitudeA: Double, longitudeA: Double, latitudeB: Double, longitudeB: Double): Double =
    PositionUtils.distance(latitudeA, longitudeA, latitudeB, longitudeB)

/**
 * Computes the bearing in degrees between two points on Earth.
 *
 * @param lat1 Latitude of the first point
 * @param lon1 Longitude of the first point
 * @param lat2 Latitude of the second point
 * @param lon2 Longitude of the second point
 * @return Bearing between the two points in degrees. A value of 0 means due north.
 */
fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
    PositionUtils.bearing(lat1, lon1, lat2, lon2)
