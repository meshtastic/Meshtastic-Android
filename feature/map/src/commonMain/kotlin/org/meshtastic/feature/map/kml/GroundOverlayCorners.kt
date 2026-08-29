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
package org.meshtastic.feature.map.kml

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES_PER_HALF_TURN = 180.0

/** One corner of a draped image, longitude first as GeoJSON orders them. */
data class OverlayCorner(val longitude: Double, val latitude: Double)

/** The four corners of a ground overlay's image: top-left, top-right, bottom-right, bottom-left. */
data class OverlayCorners(
    val topLeft: OverlayCorner,
    val topRight: OverlayCorner,
    val bottomRight: OverlayCorner,
    val bottomLeft: OverlayCorner,
)

/**
 * Where the overlay's image corners land, with the box's `<rotation>` applied.
 *
 * KML's rotation is degrees counter-clockwise about the box centre. The rotation happens in a locally-scaled frame —
 * longitude offsets shrunk by cos(latitude) — so a rotated box keeps its shape on the ground instead of shearing with
 * latitude. Exact enough for the tile-sized overlays this feature exists for; a continent-sized rotated overlay would
 * drift, and nothing exports one.
 */
fun KmlGroundOverlay.corners(): OverlayCorners {
    val centerLon = (east + west) / 2
    val centerLat = (north + south) / 2

    if (rotationDegrees == 0.0) {
        return OverlayCorners(
            topLeft = OverlayCorner(west, north),
            topRight = OverlayCorner(east, north),
            bottomRight = OverlayCorner(east, south),
            bottomLeft = OverlayCorner(west, south),
        )
    }

    val theta = rotationDegrees * PI / DEGREES_PER_HALF_TURN
    val cosTheta = cos(theta)
    val sinTheta = sin(theta)
    val latScale = cos(centerLat * PI / DEGREES_PER_HALF_TURN)

    fun rotate(lon: Double, lat: Double): OverlayCorner {
        val x = (lon - centerLon) * latScale
        val y = lat - centerLat
        val rotatedX = x * cosTheta - y * sinTheta
        val rotatedY = x * sinTheta + y * cosTheta
        return OverlayCorner(centerLon + rotatedX / latScale, centerLat + rotatedY)
    }

    return OverlayCorners(
        topLeft = rotate(west, north),
        topRight = rotate(east, north),
        bottomRight = rotate(east, south),
        bottomLeft = rotate(west, south),
    )
}
