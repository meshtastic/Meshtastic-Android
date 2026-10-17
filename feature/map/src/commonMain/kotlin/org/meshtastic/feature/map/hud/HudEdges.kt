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

import kotlin.math.abs
import kotlin.math.tan

private const val HALF_CIRCLE = 180.0

/** The screen edge an out-of-view target pins to. */
enum class PinEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

/** A target further than this to either side pins to a side edge: turning toward it beats tilting. */
private const val SIDE_YAW_DEG = 35.0

/** Hysteresis on [SIDE_YAW_DEG], and the window near straight behind where a side edge holds. */
private const val EDGE_HOLD_DEG = 12.0
private const val MAX_EDGE_ANGLE_DEG = 80.0

/**
 * Where to pin a target that falls outside the view, chosen the way someone finds it: a target well to the side, or
 * behind, pins to the side to turn toward ([yawDeg], positive clockwise), and one roughly ahead pins top or bottom to
 * tilt toward ([pitchDeltaDeg], positive up). Choosing on the screen's own axes instead puts a node behind the user on
 * the bottom edge while the camera points at the ground, which reads as inverted. [previous] holds through small
 * wobbles: near [SIDE_YAW_DEG] and near straight behind, where the side would otherwise flip.
 */
fun pinEdge(projected: ScreenPoint?, width: Float, yawDeg: Double, pitchDeltaDeg: Double, previous: PinEdge?): PinEdge {
    val wasSide = previous == PinEdge.LEFT || previous == PinEdge.RIGHT
    val sideYaw = if (wasSide) SIDE_YAW_DEG - EDGE_HOLD_DEG else SIDE_YAW_DEG + EDGE_HOLD_DEG
    val ahead = projected != null && projected.x in 0f..width
    if (ahead || abs(yawDeg) < sideYaw) return if (pitchDeltaDeg > 0) PinEdge.TOP else PinEdge.BOTTOM
    val nearBehind = abs(yawDeg) > HALF_CIRCLE - EDGE_HOLD_DEG
    return if (wasSide && nearBehind) {
        requireNotNull(previous)
    } else if (yawDeg > 0) {
        PinEdge.RIGHT
    } else {
        PinEdge.LEFT
    }
}

/**
 * Where along its pinned [edge] a target sits, in pixels from the view center: across the top and bottom by how far it
 * is to the side, up and down the sides by how far it is above or below. Positive is rightward and upward.
 */
fun edgeOffsetPx(edge: PinEdge, yawDeg: Double, pitchDeltaDeg: Double, focalPx: Float): Float {
    val angle =
        when (edge) {
            PinEdge.TOP,
            PinEdge.BOTTOM,
            -> yawDeg

            PinEdge.LEFT,
            PinEdge.RIGHT,
            -> pitchDeltaDeg
        }
    return (tan(angle.coerceIn(-MAX_EDGE_ANGLE_DEG, MAX_EDGE_ANGLE_DEG).toRadians()) * focalPx).toFloat()
}
