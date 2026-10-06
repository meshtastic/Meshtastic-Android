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

class ChirpyHopWorldTest {

    @Test
    fun `the ground scrolls only while the run is going`() {
        val engine = ChirpyHopEngine()
        val world = ChirpyHopWorld()

        world.advance(0.02, engine)
        assertEquals(0.0, world.groundShift)

        engine.primaryAction()
        engine.advance(0.02)
        world.advance(0.02, engine)
        assertTrue(world.groundShift > 0.0)
    }

    @Test
    fun `the knockout clock starts at the hit and clears for the next run`() {
        val engine = ChirpyHopEngine(obstacleX = 1.0)
        val world = ChirpyHopWorld()
        engine.primaryAction()
        while (engine.phase == ChirpyHopPhase.Running) {
            engine.advance(1.0 / 60.0)
            world.advance(1.0 / 60.0, engine)
        }
        assertEquals(0.0, world.knockedOutFor)

        world.advance(0.02, engine)
        assertEquals(0.02, world.knockedOutFor!!, absoluteTolerance = 1e-9)

        engine.primaryAction()
        world.advance(0.02, engine)
        assertNull(world.knockedOutFor)
    }
}
