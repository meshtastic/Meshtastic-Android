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

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val STEP_NS = 20_000_000L

/** A rotation of [deg] about the vertical, as (x, y, z, w). */
private fun yaw(deg: Double): FloatArray {
    val half = deg * kotlin.math.PI / 360
    return floatArrayOf(0f, 0f, sin(half).toFloat(), cos(half).toFloat())
}

class OneEuroQuaternionFilterTest {

    @Test
    fun `the first sample passes straight through`() {
        val out = FloatArray(4)
        OneEuroQuaternionFilter().filter(yaw(30.0), STEP_NS, out)
        assertEquals(yaw(30.0).toList(), out.toList())
    }

    @Test
    fun `a jittery still phone is smoothed toward its mean`() {
        val filter = OneEuroQuaternionFilter()
        val out = FloatArray(4)
        repeat(200) { i -> filter.filter(yaw(if (i % 2 == 0) 1.0 else -1.0), (i + 1) * STEP_NS, out) }
        // The raw samples swing two degrees; the output should hold within a fraction of one.
        assertTrue(kotlin.math.abs(out[2]) < sin(0.5 * kotlin.math.PI / 360))
    }

    @Test
    fun `a fast pan is followed without long lag`() {
        val filter = OneEuroQuaternionFilter()
        val out = FloatArray(4)
        filter.filter(yaw(0.0), STEP_NS, out)
        repeat(25) { i -> filter.filter(yaw(90.0), (i + 2) * STEP_NS, out) }
        // Half a second after a 90 degree turn the output is most of the way there.
        assertTrue(out[2] > sin(60.0 * kotlin.math.PI / 360))
    }

    @Test
    fun `q and minus q are the same rotation`() {
        val filter = OneEuroQuaternionFilter()
        val out = FloatArray(4)
        filter.filter(yaw(10.0), STEP_NS, out)
        filter.filter(yaw(10.0).map { -it }.toFloatArray(), 2 * STEP_NS, out)
        assertEquals(yaw(10.0)[2], out[2], 1e-4f)
    }
}
