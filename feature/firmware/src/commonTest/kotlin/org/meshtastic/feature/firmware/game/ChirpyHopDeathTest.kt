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

class ChirpyHopDeathTest {

    @Test
    fun `the knockout starts where Chirpy was hit`() {
        val pose = deathPose(elapsed = 0.0, startHeightDp = 40.0)

        assertEquals(40f, pose.heightDp, absoluteTolerance = 0.001f)
        assertEquals(0f, pose.knockbackDp, absoluteTolerance = 0.001f)
        assertEquals(0f, pose.rotationDegrees, absoluteTolerance = 0.001f)
    }

    @Test
    fun `Chirpy lands squashed on the ground close to where he was hit`() {
        for (startHeight in listOf(0.0, 60.0, 400.0)) {
            val pose = deathPose(elapsed = DEATH_ANIMATION_SECONDS, startHeightDp = startHeight)

            assertEquals(0f, pose.heightDp, absoluteTolerance = 0.001f, message = "from $startHeight dp")
            assertTrue(pose.scaleY < 1f && pose.scaleX > 1f)
            assertTrue(pose.knockbackDp in -30f..-1f)
            assertEquals(1f, pose.alpha)
        }
    }

    @Test
    fun `stars circle his head once he has landed`() {
        assertNull(deathPose(elapsed = 0.1, startHeightDp = 0.0).dizzyRadians)
        val first = deathPose(elapsed = 0.6, startHeightDp = 0.0).dizzyRadians
        val later = deathPose(elapsed = 0.8, startHeightDp = 0.0).dizzyRadians

        assertTrue(first != null && later != null && later > first)
    }

    @Test
    fun `a hit on the ground still throws Chirpy into the air`() {
        assertTrue(deathPose(elapsed = 0.2, startHeightDp = 0.0).heightDp > 10f)
    }

    @Test
    fun `the flash and shake and sparks are over before GAME OVER shows`() {
        val early = (0 until 10).map { deathPose(elapsed = it * 0.03, startHeightDp = 0.0) }
        val end = deathPose(elapsed = DEATH_ANIMATION_SECONDS, startHeightDp = 0.0)

        assertTrue(early.any { it.alpha < 1f })
        assertTrue(early.any { it.shakeDp != 0f })
        assertTrue(early.first().burst != null)
        assertEquals(0f, end.shakeDp, absoluteTolerance = 0.001f)
        assertNull(end.burst)
    }
}
