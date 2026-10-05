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
package org.meshtastic.feature.firmware.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChirpyHopKnockoutTest {

    @Test
    fun `the knockout starts where Chirpy was hit`() {
        val pose = knockoutPose(elapsed = 0.0, startHeightDp = 40.0)

        assertEquals(40f, pose.heightDp, absoluteTolerance = 0.001f)
        assertEquals(0f, pose.knockbackDp, absoluteTolerance = 0.001f)
        assertEquals(0f, pose.rotationDegrees, absoluteTolerance = 0.001f)
    }

    @Test
    fun `Chirpy lands on his feet close to where he was hit`() {
        for (startHeight in listOf(0.0, 60.0, 400.0)) {
            val pose = knockoutPose(elapsed = KNOCKOUT_SECONDS, startHeightDp = startHeight)

            assertEquals(0f, pose.heightDp, absoluteTolerance = 0.001f, message = "from $startHeight dp")
            assertTrue(pose.knockbackDp in -30f..-1f)
            assertEquals(1f, pose.alpha)
        }
    }

    @Test
    fun `a dazed Chirpy keeps swaying on wobbly knees long after GAME OVER`() {
        val poses = (0 until 40).map { knockoutPose(elapsed = 5.0 + it * 0.05, startHeightDp = 0.0) }

        assertTrue(poses.all { it.heightDp == 0f })
        assertTrue(poses.maxOf { it.rotationDegrees } > 3f && poses.minOf { it.rotationDegrees } < -3f)
        assertTrue(poses.maxOf { it.kneeBend } > 0.5f && poses.minOf { it.kneeBend } < -0.5f)
        assertTrue(poses.all { it.dizzyRadians != null })
    }

    @Test
    fun `stars circle his head once he has landed`() {
        assertNull(knockoutPose(elapsed = 0.1, startHeightDp = 0.0).dizzyRadians)
        val first = knockoutPose(elapsed = 0.6, startHeightDp = 0.0).dizzyRadians
        val later = knockoutPose(elapsed = 0.8, startHeightDp = 0.0).dizzyRadians

        assertTrue(first != null && later != null && later > first)
    }

    @Test
    fun `a hit on the ground still throws Chirpy into the air`() {
        assertTrue(knockoutPose(elapsed = 0.2, startHeightDp = 0.0).heightDp > 10f)
    }

    @Test
    fun `the flash and shake and sparks are over before GAME OVER shows`() {
        val early = (0 until 10).map { knockoutPose(elapsed = it * 0.03, startHeightDp = 0.0) }
        val end = knockoutPose(elapsed = KNOCKOUT_SECONDS, startHeightDp = 0.0)

        assertTrue(early.any { it.alpha < 1f })
        assertTrue(early.any { it.shakeDp != 0f })
        assertTrue(early.first().burst != null)
        assertEquals(0f, end.shakeDp, absoluteTolerance = 0.001f)
        assertNull(end.burst)
    }
}
