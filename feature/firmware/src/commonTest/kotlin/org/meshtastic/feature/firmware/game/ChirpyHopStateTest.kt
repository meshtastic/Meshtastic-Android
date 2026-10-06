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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChirpyHopStateTest {

    private fun ChirpyHopState.stepUntil(done: (ChirpyHopEvent) -> Boolean): ChirpyHopEvent {
        repeat(10_000) {
            val event = step(1.0 / 60.0)
            if (done(event)) return event
        }
        error("never happened")
    }

    @Test
    fun `a tap starts the run and asks for frames`() {
        val state = ChirpyHopState()
        assertFalse(state.animating)

        assertTrue(state.tap())

        assertEquals(ChirpyHopPhase.Running, state.phase)
        assertTrue(state.animating)
    }

    @Test
    fun `clearing an obstacle reports a point`() {
        val state = ChirpyHopState()
        state.tap()

        state.stepUntil { it == ChirpyHopEvent.Scored }

        assertEquals(1, state.score)
    }

    @Test
    fun `GAME OVER waits for the knockout and taps are ignored until then`() {
        val state = ChirpyHopState(ChirpyHopEngine(obstacleX = 1.0))
        state.tap()
        state.stepUntil { it == ChirpyHopEvent.KnockedOut }

        assertEquals(ChirpyHopPhase.Running, state.hudPhase)
        assertFalse(state.tap())
        assertEquals(ChirpyHopPhase.GameOver, state.phase)

        state.stepUntil { state.knockoutDone }
        assertEquals(ChirpyHopPhase.GameOver, state.hudPhase)
        assertTrue(state.animating, "he keeps swaying until the next run")

        state.tap()
        assertEquals(ChirpyHopPhase.Ready, state.phase)
        assertFalse(state.animating)
    }

    @Test
    fun `every step bumps the frame tick so the canvas redraws`() {
        val state = ChirpyHopState()
        state.tap()
        val before = state.frameTick.longValue

        state.step(1.0 / 60.0)

        assertTrue(state.frameTick.longValue > before)
    }
}
