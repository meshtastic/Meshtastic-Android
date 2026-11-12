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
package org.meshtastic.feature.map.terrain.spatial

import kotlin.math.max
import kotlin.math.min

/**
 * How the round map sits in the volume: centered on the baseplate and filling its footprint, less a margin around the
 * rim for the bezel and its handles. Resizing the volume resizes the map.
 */
class SpatialVolumeFit(volumeWidth: Float, volumeDepth: Float, modelDiskRadius: Float) {
    /** Radius of the disk in meters. */
    val diskRadius: Float

    /** Meters per model unit, so a model disk of the given radius comes out [diskRadius] across. */
    val scale: Float

    init {
        val side = min(volumeWidth, volumeDepth)
        val radius = if (side.isFinite()) side / 2 - RIM_MARGIN else 0f
        diskRadius = max(radius, MINIMUM_DISK_RADIUS)
        scale = if (modelDiskRadius > 0 && modelDiskRadius.isFinite()) diskRadius / modelDiskRadius else 1f
    }

    companion object {
        /** Room left between the disk and the volume's side, in meters, for the bezel and handles. */
        const val RIM_MARGIN = 0.07f

        /** The disk keeps at least this radius in meters, however small the volume is. */
        const val MINIMUM_DISK_RADIUS = 0.1f

        /** Volume sizes the map opens at and allows, in meters (width, height, depth). */
        val DEFAULT_SIZE = Vec3(1.1f, 0.7f, 1.1f)
        val MINIMUM_SIZE = Vec3(0.6f, 0.45f, 0.6f)
        val MAXIMUM_SIZE = Vec3(2.0f, 1.3f, 2.0f)
    }
}
