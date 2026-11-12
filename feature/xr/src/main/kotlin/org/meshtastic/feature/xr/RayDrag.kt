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
package org.meshtastic.feature.xr

import org.meshtastic.feature.map.terrain.spatial.Vec3
import kotlin.math.sqrt

/**
 * A pinch-and-drag along a pointing ray, which swings the grabbed thing round the user at the distance it was grabbed
 * from, the way a window follows the hand. Everything is in the space the ray is given in.
 */
internal class RayDrag(origin: Vec3, direction: Vec3, private val distance: Float) {
    private var last = along(origin, direction)

    /** How far the point the ray now holds has moved since the last call. */
    fun move(origin: Vec3, direction: Vec3): Vec3 {
        val now = along(origin, direction)
        val moved = Vec3(now.x - last.x, now.y - last.y, now.z - last.z)
        last = now
        return moved
    }

    private fun along(origin: Vec3, direction: Vec3): Vec3 {
        val length = sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z)
        val scale = distance / length.coerceAtLeast(MIN_LENGTH)
        return Vec3(origin.x + direction.x * scale, origin.y + direction.y * scale, origin.z + direction.z * scale)
    }

    private companion object {
        const val MIN_LENGTH = 1e-6f
    }
}
