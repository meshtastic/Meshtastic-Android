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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChirpyWorldTest {

    private fun ChirpyWorld.step(engine: ChirpyHopEngine) {
        ChirpyHopAutopilot.steer(engine)
        observe(engine)
        engine.advance(1.0 / 60.0)
        advance(1.0 / 60.0, engine)
    }

    @Test
    fun `a cleared obstacle keeps scrolling until it is off the left edge`() {
        val engine = ChirpyHopEngine()
        val world = ChirpyWorld()
        engine.primaryAction()
        while (engine.score == 0) world.step(engine)

        val cleared = assertNotNull(world.departing)
        assertEquals(ChirpyObstacleKind.Antenna, cleared.kind)
        assertTrue(cleared.x > 0, "vanished on screen at ${cleared.x}")

        repeat(30) { world.step(engine) }
        assertTrue(world.departing!!.x < cleared.x)

        while (engine.score == 1 && world.departing != null) world.step(engine)
        assertNull(world.departing)
    }

    @Test
    fun `a new run forgets the last run's departing obstacle`() {
        val engine = ChirpyHopEngine()
        val world = ChirpyWorld()
        engine.primaryAction()
        while (engine.score == 0) world.step(engine)
        engine.reset()

        world.observe(engine)

        assertNull(world.departing)
    }
}
