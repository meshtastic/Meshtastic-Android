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
import kotlin.math.acos
import kotlin.math.sqrt

private const val NANOS_PER_SECOND = 1e9
private const val QUATERNION_SIZE = 4

/** A gap longer than this (the app was backgrounded) restarts the filter instead of sweeping across it. */
private const val MAX_GAP_S = 0.5

/**
 * Speed-adaptive smoothing for an orientation quaternion, the "1€ filter" (Casiez et al., CHI 2012). Held still it
 * filters hard, so chips do not shimmer; panning raises the cutoff with angular speed, so the overlay does not trail
 * the camera image. Quaternions are (x, y, z, w), the rotation vector's order.
 */
internal class OneEuroQuaternionFilter(
    private val minCutoffHz: Double = 0.6,
    private val beta: Double = 0.8,
    private val speedCutoffHz: Double = 1.0,
) {
    private val state = DoubleArray(QUATERNION_SIZE)
    private var lastTimestampNs = 0L
    private var speedRadPerS = 0.0

    /** Filters [sample] taken at [timestampNs] and writes the smoothed quaternion into [out]. */
    fun filter(sample: FloatArray, timestampNs: Long, out: FloatArray) {
        val dt = (timestampNs - lastTimestampNs) / NANOS_PER_SECOND
        if (lastTimestampNs == 0L || dt <= 0.0 || dt > MAX_GAP_S || state.any { it.isNaN() }) {
            for (i in 0 until QUATERNION_SIZE) state[i] = sample[i].toDouble()
            speedRadPerS = 0.0
        } else {
            var dot = 0.0
            for (i in 0 until QUATERNION_SIZE) dot += state[i] * sample[i]
            // q and -q are the same rotation; blend toward whichever is nearer.
            val sign = if (dot < 0) -1.0 else 1.0
            val angle = 2 * acos(abs(dot).coerceAtMost(1.0))
            speedRadPerS += smoothing(speedCutoffHz, dt) * (angle / dt - speedRadPerS)
            val alpha = smoothing(minCutoffHz + beta * speedRadPerS, dt)
            var norm = 0.0
            for (i in 0 until QUATERNION_SIZE) {
                state[i] += alpha * (sign * sample[i] - state[i])
                norm += state[i] * state[i]
            }
            val inv = 1 / sqrt(norm)
            for (i in 0 until QUATERNION_SIZE) state[i] *= inv
        }
        lastTimestampNs = timestampNs
        for (i in 0 until QUATERNION_SIZE) out[i] = state[i].toFloat()
    }

    /** The low-pass weight for a step of [dt] seconds at [cutoffHz]. */
    private fun smoothing(cutoffHz: Double, dt: Double): Double {
        val tau = 1 / (2 * PI * cutoffHz)
        return 1 / (1 + tau / dt)
    }
}
