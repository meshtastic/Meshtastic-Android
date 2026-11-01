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
package org.meshtastic.feature.coverage

import kotlin.math.abs
import kotlin.math.hypot

/** Chaikin corner cutting; an open line keeps both ends. */
internal fun chaikin(points: List<GridPoint>, closed: Boolean, passes: Int = SMOOTHING_PASSES): List<GridPoint> {
    var current = points
    repeat(passes) {
        if (current.size < MIN_SMOOTH_POINTS) return current
        val out = mutableListOf<GridPoint>()
        val segments = if (closed) current.size else current.size - 1
        if (!closed) out.add(current.first())
        for (index in 0 until segments) {
            val a = current[index]
            val b = current[(index + 1) % current.size]
            out.add(GridPoint(a.x * NEAR + b.x * FAR, a.y * NEAR + b.y * FAR))
            out.add(GridPoint(a.x * FAR + b.x * NEAR, a.y * FAR + b.y * NEAR))
        }
        if (!closed) out.add(current.last())
        current = out
    }
    return current
}

/** Douglas-Peucker with both ends kept. */
internal fun simplifyOpen(points: List<GridPoint>, tolerance: Double): List<GridPoint> {
    if (points.size <= 2) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.size - 1] = true
    val stack = ArrayDeque(listOf(0 to points.size - 1))
    while (stack.isNotEmpty()) {
        val (first, last) = stack.removeLast()
        if (last <= first + 1) continue
        val farthest = (first + 1 until last).maxBy { distanceToLine(points[it], points[first], points[last]) }
        if (distanceToLine(points[farthest], points[first], points[last]) > tolerance) {
            keep[farthest] = true
            stack.addLast(first to farthest)
            stack.addLast(farthest to last)
        }
    }
    return points.filterIndexed { index, _ -> keep[index] }
}

/** Douglas-Peucker on a closed ring, split at the point farthest from the first. */
internal fun simplifyClosed(points: List<GridPoint>, tolerance: Double): List<GridPoint> {
    if (points.size <= MIN_SMOOTH_POINTS) return points
    val first = points.first()
    val far = points.indices.maxBy { points[it].distanceSquared(first) }
    val one = simplifyOpen(points.subList(0, far + 1), tolerance)
    val two = simplifyOpen(points.subList(far, points.size) + first, tolerance)
    return one + two.subList(1, two.size - 1)
}

private fun distanceToLine(point: GridPoint, a: GridPoint, b: GridPoint): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = hypot(dx, dy)
    return if (length > 0) {
        abs(dx * (a.y - point.y) - dy * (a.x - point.x)) / length
    } else {
        hypot(point.x - a.x, point.y - a.y)
    }
}

/** Chaikin passes over each outline. */
private const val SMOOTHING_PASSES = 2
private const val MIN_SMOOTH_POINTS = 3
private const val NEAR = 0.75
private const val FAR = 0.25
