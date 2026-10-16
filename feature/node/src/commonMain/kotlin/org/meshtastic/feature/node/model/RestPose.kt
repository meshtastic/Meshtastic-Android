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
package org.meshtastic.feature.node.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Which face of a model rests on the surface it is placed on. The flasher's models are authored Y up with the front
 * facing +Z, so a pitch about X lays the device on its back or front and a roll about Z on a side.
 */
enum class RestPose(val pitchDegrees: Float, val rollDegrees: Float) {
    Upright(0f, 0f),
    FaceUp(-QUARTER_TURN, 0f),
    FaceDown(QUARTER_TURN, 0f),
    LeftSide(0f, QUARTER_TURN),
    RightSide(0f, -QUARTER_TURN),
    ;

    /** The model's bounds once rotated into this pose, before any offset. */
    fun rotatedBounds(bounds: GlbBounds): GlbBounds {
        val ends = listOf(bounds.min, bounds.max)
        val corners = ends.flatMap { x -> ends.flatMap { y -> ends.map { z -> rotate(x[0], y[1], z[2]) } } }
        return GlbBounds(
            min = FloatArray(AXES) { axis -> corners.minOf { it[axis] } },
            max = FloatArray(AXES) { axis -> corners.maxOf { it[axis] } },
        )
    }

    /** Where to put the rotated model so its lowest face touches y = 0 and its footprint centers on the origin. */
    fun offset(bounds: GlbBounds): FloatArray {
        val rotated = rotatedBounds(bounds)
        return floatArrayOf(-rotated.center[0], -rotated.min[1], -rotated.center[2])
    }

    private fun rotate(x: Float, y: Float, z: Float): FloatArray {
        val pitch = pitchDegrees * DEGREES_TO_RADIANS
        val roll = rollDegrees * DEGREES_TO_RADIANS
        // Pitch about X, then roll about Z; a pose uses one or the other.
        val y1 = y * cos(pitch) - z * sin(pitch)
        val z1 = y * sin(pitch) + z * cos(pitch)
        return floatArrayOf(x * cos(roll) - y1 * sin(roll), x * sin(roll) + y1 * cos(roll), z1)
    }
}

private const val QUARTER_TURN = 90f
private const val AXES = 3
private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()
