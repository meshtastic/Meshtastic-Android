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
package org.meshtastic.feature.map.hud

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val DEGREES_PER_RADIAN = 180.0 / PI

/** Earth radius scaled by 4/3, the standard allowance for atmospheric refraction near the horizon. */
internal const val EFFECTIVE_EARTH_RADIUS_M = 6_371_000.0 * 4.0 / 3.0

/** Points nearer than this to the camera plane are treated as behind it, so a projection never divides by ~0. */
private const val MIN_FORWARD_COMPONENT = 0.05

/** A vector in the local east-north-up frame. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    infix fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z
}

/**
 * Where the back camera points, as the screen's right, up and forward axes in east-north-up, referenced to magnetic
 * north.
 */
data class CameraPose(val right: Vec3, val up: Vec3, val forward: Vec3) {
    /**
     * Magnetic azimuth the camera faces, 0..360. Steeply down or up, the line of sight has no usable horizontal part,
     * so the heading follows the screen's top edge instead, which faces the same way when the phone is tipped forward
     * and the opposite way when it is tipped back.
     */
    val azimuthDeg: Double
        get() {
            val facing =
                when {
                    forward.z < -STEEP_Z -> up
                    forward.z > STEEP_Z -> Vec3(-up.x, -up.y, -up.z)
                    else -> forward
                }
            return (atan2(facing.x, facing.y) * DEGREES_PER_RADIAN + FULL_CIRCLE) % FULL_CIRCLE
        }
}

/** This pose turned clockwise about the vertical by [deg]. */
fun CameraPose.rotatedAboutVertical(deg: Double): CameraPose {
    if (deg == 0.0) return this
    val c = cos(deg.toRadians())
    val s = sin(deg.toRadians())
    fun Vec3.turn() = Vec3(x = x * c + y * s, y = -x * s + y * c, z = z)
    return CameraPose(right = right.turn(), up = up.turn(), forward = forward.turn())
}

data class ScreenPoint(val x: Float, val y: Float)

private const val FULL_CIRCLE = 360.0

/** sin(60°): a camera pitched further than this has its heading read from the screen's top edge. */
private const val STEEP_Z = 0.87
private const val HALF_CIRCLE = 180.0

internal fun Double.toRadians(): Double = this / DEGREES_PER_RADIAN

/** Unit vector toward a bearing (degrees from north, clockwise) at an elevation above the horizontal. */
fun directionEnu(bearingDeg: Double, elevationDeg: Double): Vec3 {
    val b = bearingDeg.toRadians()
    val e = elevationDeg.toRadians()
    return Vec3(x = sin(b) * cos(e), y = cos(b) * cos(e), z = sin(e))
}

/**
 * Angle above the observer's horizontal to a target [distanceM] away and [riseM] above, including the curvature drop at
 * that range. A null [riseM] places the target level with the observer.
 */
fun elevationDeg(distanceM: Double, riseM: Double?): Double {
    if (distanceM <= 0.0) return 0.0
    val drop = distanceM * distanceM / (2 * EFFECTIVE_EARTH_RADIUS_M)
    return atan2((riseM ?: 0.0) - drop, distanceM) * DEGREES_PER_RADIAN
}

/** A target with no shared altitude frame could sit anywhere in a band this tall either side of level. */
private const val UNKNOWN_ELEVATION_SPREAD_DEG = 4.0

/**
 * Half the vertical angle a target [distanceM] away could lie within, from a vertical uncertainty of [verticalM]. When
 * the target has no [elevationKnown], its level placement is a guess, so the band is at least
 * [UNKNOWN_ELEVATION_SPREAD_DEG] tall.
 */
fun elevationSpreadDeg(distanceM: Double, verticalM: Double, elevationKnown: Boolean): Double {
    val measured = atan(verticalM / max(distanceM, 1.0)) * DEGREES_PER_RADIAN
    return if (elevationKnown) measured else max(measured, UNKNOWN_ELEVATION_SPREAD_DEG)
}

/** Half the angle a circle of [radiusM] subtends at [distanceM], or null when the observer is inside it. */
fun precisionHalfAngleDeg(radiusM: Double, distanceM: Double): Double? =
    if (radiusM >= distanceM) null else atan(radiusM / max(distanceM, 1.0)) * DEGREES_PER_RADIAN

/**
 * Pinhole projection of [direction] onto a [width] x [height] view with focal length [focalPx], or null when it is
 * behind the camera.
 */
fun project(direction: Vec3, pose: CameraPose, focalPx: Float, width: Float, height: Float): ScreenPoint? {
    val z = direction dot pose.forward
    if (z < MIN_FORWARD_COMPONENT) return null
    val x = direction dot pose.right
    val y = direction dot pose.up
    return ScreenPoint(x = width / 2 + (focalPx * x / z).toFloat(), y = height / 2 - (focalPx * y / z).toFloat())
}

/** A chip's footprint in view angles: centered on [bearingDeg], rising [heightDeg] from [elevationDeg]. */
data class AngularBox(val bearingDeg: Double, val elevationDeg: Double, val halfWidthDeg: Double, val heightDeg: Double)

private const val MAX_STACK_LEVEL = 64

/** The level [stackLevels] gives a chip that would need more than its cap: it is counted, not drawn. */
const val OVERFLOW_LEVEL = -1

/** Which way a chip at [level] steps aside from its point: none at level 0, then right and left in turn. */
fun leaderSide(level: Int): Int = when {
    level <= 0 -> 0
    level % 2 == 1 -> 1
    else -> -1
}

/**
 * How many rows each chip is lifted so none overlaps an earlier one, in list order. Computed from view angles rather
 * than screen positions, so it holds still while the phone moves and changes only when the targets do. A lifted chip
 * also steps aside by its half width plus [leaderRunDeg], to the side [leaderSide] gives, so its leader leans rather
 * than rising plumb; a chip that would need more than [maxLevel] rows gets [OVERFLOW_LEVEL].
 */
fun stackLevels(
    boxes: List<AngularBox>,
    gapDeg: Double,
    leaderRunDeg: Double = 0.0,
    maxLevel: Int = MAX_STACK_LEVEL,
): IntArray {
    val levels = IntArray(boxes.size)
    fun center(index: Int, level: Int): Double {
        val box = boxes[index]
        val side = leaderSide(level)
        return if (side == 0 || leaderRunDeg == 0.0) {
            box.bearingDeg
        } else {
            box.bearingDeg + side * (box.halfWidthDeg + leaderRunDeg)
        }
    }
    boxes.forEachIndexed { i, box ->
        fun bottom(index: Int, level: Int) = boxes[index].elevationDeg + level * (boxes[index].heightDeg + gapDeg)
        fun overlaps(j: Int, level: Int): Boolean {
            val other = boxes[j]
            val theirLevel = levels[j]
            return abs(signedTurnDeg(center(j, theirLevel), center(i, level))) <
                box.halfWidthDeg + other.halfWidthDeg &&
                bottom(i, level) < bottom(j, theirLevel) + other.heightDeg &&
                bottom(j, theirLevel) < bottom(i, level) + box.heightDeg
        }
        fun clashes(level: Int) = (0 until i).any { j -> levels[j] != OVERFLOW_LEVEL && overlaps(j, level) }
        var level = 0
        while (level <= maxLevel && clashes(level)) level++
        levels[i] = if (level > maxLevel) OVERFLOW_LEVEL else level
    }
    return levels
}

/** View angle, in degrees, that [px] spans at the center of a view with focal length [focalPx]. */
fun pxToDeg(px: Int, focalPx: Float): Double = px / focalPx.toDouble() * DEGREES_PER_RADIAN

/** The camera's tilt above the horizontal, in degrees; negative when it points at the ground. */
val CameraPose.pitchDeg: Double
    get() = asin(forward.z.coerceIn(-1.0, 1.0)) * DEGREES_PER_RADIAN

/** The shortest signed turn from [fromDeg] to [toDeg], in -180..180; positive is clockwise. */
fun signedTurnDeg(fromDeg: Double, toDeg: Double): Double =
    ((toDeg - fromDeg) % FULL_CIRCLE + HALF_CIRCLE + FULL_CIRCLE) % FULL_CIRCLE - HALF_CIRCLE
