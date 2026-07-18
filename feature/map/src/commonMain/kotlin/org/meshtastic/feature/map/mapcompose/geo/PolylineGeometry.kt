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

import kotlin.math.abs

/** A geographic point in WGS84 degrees; the lat/lon-space input to the pure polyline math below. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Pure lat/lon-space polyline helpers shared by the traceroute and geofence layers. Ported from the google renderer's
 * `offsetPolyline` (maps-utils `SphericalUtil` calls replaced by [WebMercator] equivalents) so both renderers draw the
 * same geometry.
 */
object PolylineGeometry {

    /**
     * Shifts every point of [points] sideways by [offsetMeters], perpendicular to the local heading of
     * [headingReferencePoints]. Used to draw a traceroute's forward and return paths side by side instead of on top of
     * each other. [sideMultiplier] of 1.0 offsets right of travel, -1.0 left.
     */
    fun offsetPolyline(
        points: List<GeoPoint>,
        offsetMeters: Double,
        headingReferencePoints: List<GeoPoint> = points,
        sideMultiplier: Double = 1.0,
    ): List<GeoPoint> {
        val headingPoints = headingReferencePoints.takeIf { it.size >= 2 } ?: points
        if (points.size < 2 || headingPoints.size < 2 || offsetMeters == 0.0) return points

        val headings =
            headingPoints.mapIndexed { index, _ ->
                when (index) {
                    0 -> heading(headingPoints[0], headingPoints[1])

                    headingPoints.lastIndex ->
                        heading(headingPoints[headingPoints.lastIndex - 1], headingPoints[headingPoints.lastIndex])

                    else -> heading(headingPoints[index - 1], headingPoints[index + 1])
                }
            }

        return points.mapIndexed { index, point ->
            val heading = headings[index.coerceIn(0, headings.lastIndex)]
            val perpendicularHeading = heading + (90.0 * sideMultiplier)
            val (lat, lon) =
                WebMercator.offset(point.latitude, point.longitude, abs(offsetMeters), perpendicularHeading)
            GeoPoint(lat, lon)
        }
    }

    /**
     * Approximates a circle of [radiusMeters] around a center as a closed [segments]-gon in lat/lon space. Used for
     * precision circles and geofence circles, which MapCompose renders as filled paths.
     */
    fun circlePolygon(
        centerLatitude: Double,
        centerLongitude: Double,
        radiusMeters: Double,
        segments: Int = 64,
    ): List<GeoPoint> {
        require(segments >= 3) { "A circle polygon needs at least 3 segments" }
        val points =
            (0 until segments).map { i ->
                val heading = 360.0 * i / segments
                val (lat, lon) = WebMercator.offset(centerLatitude, centerLongitude, radiusMeters, heading)
                GeoPoint(lat, lon)
            }
        return points + points.first()
    }

    private fun heading(from: GeoPoint, to: GeoPoint): Double =
        WebMercator.heading(from.latitude, from.longitude, to.latitude, to.longitude)
}
