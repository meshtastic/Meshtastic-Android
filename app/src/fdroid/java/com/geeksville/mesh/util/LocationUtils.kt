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

import android.annotation.SuppressLint
import android.location.Location
import com.geeksville.mesh.MeshProtos
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import kotlin.math.log2
import kotlin.math.pow

@SuppressLint("PropertyNaming")
object GPSFormat {
    fun toDec(latitude: Double, longitude: Double): String {
        val lat = Location.convert(latitude, Location.FORMAT_DEGREES)
        val lon = Location.convert(longitude, Location.FORMAT_DEGREES)
        return "$lat $lon"
    }
}

/** @return distance in meters along the surface of the earth (ish) */
fun latLongToMeter(latitudeA: Double, longitudeA: Double, latitudeB: Double, longitudeB: Double): Double {
    val locationA =
        Location("").apply {
            latitude = latitudeA
            longitude = longitudeA
        }
    val locationB =
        Location("").apply {
            latitude = latitudeB
            longitude = longitudeB
        }
    return locationA.distanceTo(locationB).toDouble()
}

// Same as above, but takes Mesh Position proto.
fun positionToMeter(a: MeshProtos.Position, b: MeshProtos.Position): Double =
    latLongToMeter(a.latitudeI * 1e-7, a.longitudeI * 1e-7, b.latitudeI * 1e-7, b.longitudeI * 1e-7)

/**
 * Computes the bearing in degrees between two points on Earth.
 *
 * @param lat1 Latitude of the first point
 * @param lon1 Longitude of the first point
 * @param lat2 Latitude of the second point
 * @param lon2 Longitude of the second point
 * @return Bearing between the two points in degrees. A value of 0 means due north.
 */
fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val locationA =
        Location("").apply {
            latitude = lat1
            longitude = lon1
        }
    val locationB =
        Location("").apply {
            latitude = lat2
            longitude = lon2
        }
    return locationA.bearingTo(locationB).toDouble()
}

/**
 * Calculates the zoom level required to fit the entire [BoundingBox] inside the map view.
 *
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
 *
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
        center.longitude - newLonDiff / 2,
    )
}
