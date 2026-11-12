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
@file:Suppress("MagicNumber", "ReturnCount")

package org.meshtastic.feature.map.terrain.spatial

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Zooming the spatial map into the geography. The table keeps its size; the circle it shows gets smaller around a
 * center, and the terrain is rebuilt for it. Zoom 1 is the whole mesh.
 */
object SpatialZoom {
    /** The zoomed circle never gets smaller than this radius in meters. */
    const val MINIMUM_RADIUS_METERS = 1_000.0

    /** How far Center on the card and Show in 3D zoom in on a node. */
    const val FOCUS_ZOOM = 4.0

    /** The most the map can zoom in on a full circle: down to a 1 km radius, and never below 1. */
    fun maximumZoom(fullRadiusMeters: Double): Double {
        if (!fullRadiusMeters.isFinite() || fullRadiusMeters <= MINIMUM_RADIUS_METERS) return 1.0
        return fullRadiusMeters / MINIMUM_RADIUS_METERS
    }

    fun clamp(zoom: Double, fullRadiusMeters: Double): Double {
        if (!zoom.isFinite()) return 1.0
        return min(max(zoom, 1.0), maximumZoom(fullRadiusMeters))
    }

    /**
     * The circle shown at a zoom around a center (the full circle's center when null). The center is pulled in so the
     * zoomed circle stays inside the full one, so zooming out ends on the whole mesh and the map never shows ground
     * beyond it.
     */
    fun circle(full: SpatialCircle, zoom: Double, center: LatLon?): SpatialCircle {
        val clamped = clamp(zoom, full.radiusMeters)
        val radius = full.radiusMeters / clamped
        if (center == null || clamped <= 1) return SpatialCircle(full.latitude, full.longitude, radius)
        // Offset of the center from the full circle's center in meters east and north.
        val east = (center.longitude - full.longitude) * full.metersPerDegreeLongitude
        val north = (center.latitude - full.latitude) * SpatialFrame.METERS_PER_DEGREE_LATITUDE
        val distance = sqrt(east * east + north * north)
        val room = full.radiusMeters - radius
        val factor = if (distance > room && distance > 0) room / distance else 1.0
        return SpatialCircle(
            full.latitude + north * factor / SpatialFrame.METERS_PER_DEGREE_LATITUDE,
            full.longitude + east * factor / full.metersPerDegreeLongitude,
            radius,
        )
    }

    /** A zoom and the center it zooms on, null for the whole mesh. */
    data class Fit(val zoom: Double, val center: LatLon?)

    /**
     * The zoom and center that fit the coordinates on the disk with [padding] of their reach to spare. One place (or
     * several on top of each other) gets [FOCUS_ZOOM], as centering on a node does. Coordinates too spread out for any
     * zoom get the whole mesh, zoom 1. Null when empty.
     */
    fun fit(coordinates: List<LatLon>, full: SpatialCircle, padding: Double = 0.15): Fit? {
        val around = SpatialCircle.around(coordinates, padding, 0.0) ?: return null
        val center = LatLon(around.latitude, around.longitude)
        var zoom =
            if (around.radiusMeters > 1) {
                clamp(full.radiusMeters / around.radiusMeters, full.radiusMeters)
            } else {
                clamp(FOCUS_ZOOM, full.radiusMeters)
            }
        // Near the rim the zoomed circle is pulled in toward the middle and can leave a hop off the edge; step back out
        // until every hop inside the full circle is shown.
        val inside = coordinates.filter { full.contains(it) }
        while (zoom > 1 && !inside.all { circle(full, zoom, center).contains(it) }) {
            zoom = max(zoom / 1.1, 1.0)
        }
        return if (zoom > 1) Fit(zoom, center) else Fit(1.0, null)
    }

    /** Whether a node is drawn: only inside the circle shown, so nothing stands past the rim. */
    fun isShown(coordinate: LatLon, circle: SpatialCircle): Boolean = circle.contains(coordinate)

    /** Whether a point in model units lies on the disk, measured flat from its middle. */
    fun isOnDisk(x: Float, z: Float, diskRadius: Float): Boolean = x * x + z * z <= diskRadius * diskRadius * 1.0001f

    /** A point pulled in flat to the disk's rim when it lies past it, keeping its height. */
    fun clampToDisk(point: Vec3, diskRadius: Float): Vec3 {
        val distance = sqrt(point.x * point.x + point.z * point.z)
        if (distance <= diskRadius || distance <= 0) return point
        val factor = diskRadius / distance
        return Vec3(point.x * factor, point.y, point.z * factor)
    }

    /**
     * While a zoom is waiting for its terrain, the built terrain is shown scaled and shifted toward what the new circle
     * will show. [offset] is the new center's position on the current disk and [scale] how much bigger the new circle
     * draws the ground, both in model units. The scale is held between 0.2 and the size where the terrain's edge
     * reaches [maxRadius], and the shift is cut so no part of the terrain goes past [maxRadius] (under the bezel).
     */
    class Preview(val scale: Float, val translation: Vec2) {
        /** Where a point on the built terrain shows during the preview. */
        fun apply(x: Float, z: Float): Vec2 = Vec2(x * scale + translation.x, z * scale + translation.y)

        companion object {
            val IDENTITY = Preview(1f, Vec2(0f, 0f))

            fun of(offset: Vec2, scale: Float, diskRadius: Float, maxRadius: Float): Preview {
                if (diskRadius <= 0 || !scale.isFinite() || scale <= 0) return IDENTITY
                val largest = max(maxRadius / diskRadius, 1f)
                val s = min(max(scale, 0.2f), largest)
                var tx = -offset.x * s
                var ty = -offset.y * s
                val room = max(maxRadius - s * diskRadius, 0f)
                val length = sqrt(tx * tx + ty * ty)
                if (length > room) {
                    val k = if (length > 0) room / length else 0f
                    tx *= k
                    ty *= k
                }
                return Preview(s, Vec2(tx, ty))
            }
        }
    }
}
