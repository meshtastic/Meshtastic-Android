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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChirpyHopEngineTest {

    private fun ChirpyHopEngine.run(seconds: Double, frame: Double = 1.0 / 60.0) {
        var elapsed = 0.0
        while (elapsed < seconds) {
            advance(frame)
            elapsed += frame
        }
    }

    @Test
    fun `first tap starts the run with a jump`() {
        val engine = ChirpyHopEngine()

        engine.primaryAction()

        assertEquals(ChirpyHopPhase.Running, engine.phase)
        assertTrue(engine.verticalVelocity > 0)
    }

    @Test
    fun `nothing moves before the first tap`() {
        val engine = ChirpyHopEngine()

        engine.run(seconds = 2.0)

        assertEquals(ChirpyHopPhase.Ready, engine.phase)
        assertEquals(ChirpyHopEngine.INITIAL_OBSTACLE_X, engine.obstacleX)
    }

    @Test
    fun `the opening jump clears the first antenna and scores`() {
        val engine = ChirpyHopEngine()

        engine.primaryAction()
        engine.run(seconds = 0.7)

        assertEquals(ChirpyHopPhase.Running, engine.phase)
        assertEquals(1, engine.score)
        assertEquals(ChirpyObstacleKind.Antenna, engine.obstacleKind)
    }

    @Test
    fun `running into an antenna ends the game`() {
        val engine = ChirpyHopEngine(obstacleX = 1.0)
        engine.primaryAction()

        engine.run(seconds = 3.0)

        assertEquals(ChirpyHopPhase.GameOver, engine.phase)
        assertEquals(0, engine.score)
    }

    @Test
    fun `crouching ducks under a UFO`() {
        val engine = ChirpyHopEngine(obstacleX = 1.0, obstacleKind = ChirpyObstacleKind.Ufo)
        engine.primaryAction()
        engine.run(seconds = 0.75)

        engine.setCrouching(true)
        engine.run(seconds = 1.2)

        assertEquals(ChirpyHopPhase.Running, engine.phase)
        assertEquals(1, engine.score)
    }

    @Test
    fun `standing under a UFO ends the game`() {
        val engine = ChirpyHopEngine(obstacleX = 1.0, obstacleKind = ChirpyObstacleKind.Ufo)
        engine.primaryAction()

        engine.run(seconds = 2.0)

        assertEquals(ChirpyHopPhase.GameOver, engine.phase)
    }

    @Test
    fun `a UFO cannot be jumped`() {
        val engine = ChirpyHopEngine(obstacleX = 0.6, obstacleKind = ChirpyObstacleKind.Ufo)
        engine.primaryAction()
        engine.run(seconds = 0.2)
        engine.primaryAction()

        engine.run(seconds = 2.0)

        assertEquals(ChirpyHopPhase.GameOver, engine.phase)
    }

    @Test
    fun `a tap in mid air does not jump again`() {
        val engine = ChirpyHopEngine()
        engine.primaryAction()
        engine.run(seconds = 0.2)
        val velocity = engine.verticalVelocity

        engine.primaryAction()

        assertEquals(velocity, engine.verticalVelocity)
    }

    @Test
    fun `a tap after game over resets the run`() {
        val engine = ChirpyHopEngine(obstacleX = 1.0)
        engine.primaryAction()
        engine.run(seconds = 3.0)

        engine.primaryAction()

        assertEquals(ChirpyHopPhase.Ready, engine.phase)
        assertEquals(0, engine.score)
        assertEquals(0.0, engine.playerY)
        assertEquals(ChirpyHopEngine.INITIAL_OBSTACLE_X, engine.obstacleX)
    }

    @Test
    fun `a long frame is capped so a stall cannot skip an obstacle`() {
        val engine = ChirpyHopEngine()
        engine.primaryAction()

        engine.advance(10.0)

        val expected = ChirpyHopEngine.INITIAL_OBSTACLE_X - ChirpyHopEngine.speed(0) * 0.1
        assertEquals(expected, engine.obstacleX, absoluteTolerance = 1e-9)
    }

    @Test
    fun `crouching is ignored outside a run`() {
        val engine = ChirpyHopEngine()

        engine.setCrouching(true)

        assertEquals(false, engine.isCrouching)
    }

    @Test
    fun `speed rises with score up to a cap`() {
        assertEquals(0.5, ChirpyHopEngine.speed(0))
        assertEquals(0.5, ChirpyHopEngine.speed(-3))
        assertEquals(1.05, ChirpyHopEngine.speed(1000))
        assertTrue(ChirpyHopEngine.speed(40) > ChirpyHopEngine.speed(20))
    }

    @Test
    fun `a run opens with single antennas only`() {
        val opening = (0 until ChirpyHopEngine.PAIRS_FROM).map { ChirpyHopEngine.obstacleKind(it) }

        assertEquals(List(ChirpyHopEngine.PAIRS_FROM) { ChirpyObstacleKind.Antenna }, opening)
    }

    @Test
    fun `UFOs only join once the score reaches their stage`() {
        val beforeUfos = (0 until ChirpyHopEngine.UFOS_FROM).map { ChirpyHopEngine.obstacleKind(it) }
        val withUfos =
            (ChirpyHopEngine.UFOS_FROM until ChirpyHopEngine.DENSE_FROM).map { ChirpyHopEngine.obstacleKind(it) }

        assertFalse(ChirpyObstacleKind.Ufo in beforeUfos)
        assertTrue(ChirpyObstacleKind.AntennaPair in beforeUfos)
        assertTrue(ChirpyObstacleKind.Ufo in withUfos)
    }

    @Test
    fun `the dense stage brings more UFOs than the stage before it`() {
        fun ufos(from: Int) =
            (from until from + 10).count { ChirpyHopEngine.obstacleKind(it) == ChirpyObstacleKind.Ufo }

        assertTrue(ufos(ChirpyHopEngine.DENSE_FROM) > ufos(ChirpyHopEngine.UFOS_FROM))
    }

    @Test
    fun `the spare gap before each obstacle closes as the score rises`() {
        assertTrue(ChirpyHopEngine.respawnX(0) > ChirpyHopEngine.respawnX(4))
        assertTrue(ChirpyHopEngine.respawnX(4) > ChirpyHopEngine.respawnX(40))
        assertEquals(ChirpyHopEngine.respawnX(40), ChirpyHopEngine.respawnX(80))
    }

    @Test
    fun `a perfect player survives past top speed and into the dense stage`() {
        val engine = ChirpyHopEngine()
        engine.primaryAction()
        var steps = 0
        while (engine.phase == ChirpyHopPhase.Running && engine.score < 60 && steps < 200_000) {
            ChirpyHopAutopilot.steer(engine)
            engine.advance(1.0 / 120.0)
            steps++
        }

        assertEquals(ChirpyHopPhase.Running, engine.phase, "died at score ${engine.score}")
        assertEquals(60, engine.score)
    }

    @Test
    fun `a cleared obstacle keeps scrolling until it is off the left edge`() {
        val engine = ChirpyHopEngine()
        engine.primaryAction()
        while (engine.score == 0) engine.run(seconds = 1.0 / 60.0)

        assertEquals(ChirpyObstacleKind.Antenna, engine.departingKind)
        val clearedAt = engine.departingX
        assertTrue(clearedAt > 0, "vanished on screen at $clearedAt")

        engine.run(seconds = 0.3)
        assertTrue(engine.departingX < clearedAt)

        while (engine.departingKind != null) {
            ChirpyHopAutopilot.steer(engine)
            engine.advance(1.0 / 60.0)
        }
        assertEquals(1, engine.score)
    }

    @Test
    fun `a reset forgets the departing obstacle`() {
        val engine = ChirpyHopEngine()
        engine.primaryAction()
        while (engine.score == 0) engine.run(seconds = 1.0 / 60.0)

        engine.reset()

        assertNull(engine.departingKind)
    }
}
