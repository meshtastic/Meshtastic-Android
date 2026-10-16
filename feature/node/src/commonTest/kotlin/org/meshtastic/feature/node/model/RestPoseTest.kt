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

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class RestPoseTest {
    // The WisMesh Pocket: 52 mm wide, 138 mm tall with its antenna, 32 mm deep, front facing +Z.
    private val pocket = GlbBounds(floatArrayOf(-0.026f, 0f, -0.015f), floatArrayOf(0.026f, 0.1382f, 0.017f))

    @Test
    fun everyPoseRestsOnTheSurfaceCenteredOnTheAnchor() {
        RestPose.entries.forEach { pose ->
            val placed = placed(pose)
            assertNear(0f, placed.min[1], "$pose bottom")
            assertNear(0f, placed.center[0], "$pose x center")
            assertNear(0f, placed.center[2], "$pose z center")
        }
    }

    @Test fun uprightStandsAtFullHeight() = assertNear(0.1382f, placed(RestPose.Upright).max[1], "height")

    @Test
    fun faceUpAndFaceDownLieAsDeepAsTheCase() {
        assertNear(0.032f, placed(RestPose.FaceUp).max[1], "face up height")
        assertNear(0.032f, placed(RestPose.FaceDown).max[1], "face down height")
    }

    @Test
    fun aSideRestsAsWideAsTheCase() {
        assertNear(0.052f, placed(RestPose.LeftSide).max[1], "left height")
        assertNear(0.052f, placed(RestPose.RightSide).max[1], "right height")
    }

    @Test
    fun faceUpTurnsTheScreenToTheSkyAndFaceDownToTheSurface() {
        val screen = GlbBounds(floatArrayOf(-0.015f, 0.032f, 0.0158f), floatArrayOf(0.015f, 0.047f, 0.016f))
        val up = RestPose.FaceUp
        val down = RestPose.FaceDown
        assertTrue(up.rotatedBounds(screen).center[1] > up.rotatedBounds(pocket).center[1], "face up")
        assertTrue(down.rotatedBounds(screen).center[1] < down.rotatedBounds(pocket).center[1], "face down")
    }

    private fun placed(pose: RestPose): GlbBounds {
        val rotated = pose.rotatedBounds(pocket)
        val offset = pose.offset(pocket)
        return GlbBounds(
            FloatArray(3) { rotated.min[it] + offset[it] },
            FloatArray(3) { rotated.max[it] + offset[it] },
        )
    }

    private fun assertNear(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) < 1e-5f, "$what: expected $expected, was $actual")
}
