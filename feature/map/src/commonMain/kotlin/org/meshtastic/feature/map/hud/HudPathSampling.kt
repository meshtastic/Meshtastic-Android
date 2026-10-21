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
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private const val FULL_CIRCLE = 360.0

/**
 * The directions along the ground from [a] to [b], so a track leg follows where the node walked rather than cutting a
 * chord between its ends: a leg passing close by dips toward the viewer's feet, as the ground does. Each end's ground
 * offset and height come back from its bearing, elevation and distance, and the leg is stepped straight between them. A
 * leg spanning no more than [GROUND_STEP_DEG] of view is left as its two ends.
 */
internal fun groundDirections(a: PathPoint, b: PathPoint): List<Vec3> {
    val from = directionEnu(a.bearingDeg, a.elevationDeg)
    val to = directionEnu(b.bearingDeg, b.elevationDeg)
    val spanDeg = acos((from dot to).coerceIn(-1.0, 1.0)) * FULL_CIRCLE / (2 * PI)
    val steps = ceil(spanDeg / GROUND_STEP_DEG).toInt().coerceIn(1, GROUND_MAX_STEPS)
    if (steps == 1) return listOf(from, to)
    fun east(p: PathPoint) = p.distanceM * sin(p.bearingDeg.toRadians())
    fun north(p: PathPoint) = p.distanceM * cos(p.bearingDeg.toRadians())
    val (riseA, riseB) = riseM(a.distanceM, a.elevationDeg) to riseM(b.distanceM, b.elevationDeg)
    return (0..steps).map { i ->
        val t = i.toDouble() / steps
        val e = east(a) + (east(b) - east(a)) * t
        val n = north(a) + (north(b) - north(a)) * t
        val bearing = (atan2(e, n) * FULL_CIRCLE / (2 * PI) + FULL_CIRCLE) % FULL_CIRCLE
        directionEnu(bearing, elevationDeg(hypot(e, n), riseA + (riseB - riseA) * t))
    }
}

/** The rise that [elevationDeg] turns into [elevationDeg] at [distanceM]: its inverse. */
internal fun riseM(distanceM: Double, elevationDeg: Double): Double =
    distanceM * tan(elevationDeg.toRadians()) + distanceM * distanceM / (2 * EFFECTIVE_EARTH_RADIUS_M)

/** A track leg is stepped along the ground once it spans more than this much of the view. */
private const val GROUND_STEP_DEG = 5.0
private const val GROUND_MAX_STEPS = 24

/** Straight down from the viewer: the phone's end of a leg to or from it, as far as anything riding the leg goes. */
internal val VIEWER_DOWN = Vec3(0.0, 0.0, -1.0)

/** How far either side of a spark its heading is read, in degrees of the path. */
private const val SPARK_HEADING_DEG = 0.5
private const val MIN_LEG_DEG = 1e-6

/** A spark on a path through the view, as directions: where it is, and a step behind and ahead of it. */
data class Spark(val at: Vec3, val behind: Vec3, val ahead: Vec3)

/**
 * Sparks [spacingDeg] apart along [path], a run of directions, all shifted [phaseDeg] forward so that a growing phase
 * moves them along it. Spacing is by angle in the world, not length on screen, so a spark stays on its piece of the
 * path while the camera turns, and a dense track carries no more sparks than a sparse one across the same view.
 */
internal fun sparksAlong(path: List<Vec3>, phaseDeg: Double, spacingDeg: Double): List<Spark> {
    if (spacingDeg <= 0.0) return emptyList()
    val sparks = mutableListOf<Spark>()
    var next = phaseDeg.mod(spacingDeg)
    var walked = 0.0
    path.zipWithNext { a, b ->
        val length = acos((a dot b).coerceIn(-1.0, 1.0)) * FULL_CIRCLE / (2 * PI)
        // Two copies of one direction rarely dot to exactly 1, so a leg this short counts as none.
        if (length < MIN_LEG_DEG) return@zipWithNext
        while (next <= walked + length) {
            val t = (next - walked) / length
            val step = SPARK_HEADING_DEG / length
            sparks +=
                Spark(
                    at = between(a, b, t),
                    behind = between(a, b, (t - step).coerceAtLeast(0.0)),
                    ahead = between(a, b, (t + step).coerceAtMost(1.0)),
                )
            next += spacingDeg
        }
        walked += length
    }
    return sparks
}

/** The direction [t] of the way from [a] to [b] along the great circle through both, at an even angular pace. */
private fun between(a: Vec3, b: Vec3, t: Double): Vec3 {
    val angle = acos((a dot b).coerceIn(-1.0, 1.0))
    val s = sin(angle)
    // Opposite or identical directions have no one circle between them; a straight blend is as good as any.
    val (wa, wb) = if (s < MIN_SLERP_SIN) (1 - t) to t else sin((1 - t) * angle) / s to sin(t * angle) / s
    val x = a.x * wa + b.x * wb
    val y = a.y * wa + b.y * wb
    val z = a.z * wa + b.z * wb
    val norm = sqrt(x * x + y * y + z * z)
    return if (norm == 0.0) a else Vec3(x / norm, y / norm, z / norm)
}

private const val MIN_SLERP_SIN = 1e-6
