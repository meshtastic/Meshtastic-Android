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

package org.meshtastic.feature.map

import android.content.Context
import android.util.TypedValue
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import kotlin.math.log2
import kotlin.math.pow

private const val DEGREES_IN_CIRCLE = 360.0
private const val METERS_PER_DEGREE_LATITUDE = 111320.0
private const val ZOOM_ADJUSTMENT_FACTOR = 0.8

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
    val requiredLatZoom = log2(DEGREES_IN_CIRCLE / (latLonHeight / METERS_PER_DEGREE_LATITUDE))
    val requiredLonZoom = log2(DEGREES_IN_CIRCLE / (latLonWidth / METERS_PER_DEGREE_LATITUDE))
    return maxOf(requiredLatZoom, requiredLonZoom) * ZOOM_ADJUSTMENT_FACTOR
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

// Converts SP to pixels.
fun Context.spToPx(sp: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt()

// Converts DP to pixels.
fun Context.dpToPx(dp: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
