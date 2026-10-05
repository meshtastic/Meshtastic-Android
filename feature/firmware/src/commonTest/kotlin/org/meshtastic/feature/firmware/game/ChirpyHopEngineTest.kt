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
    fun `the opening jump clears the first cactus and scores`() {
        val engine = ChirpyHopEngine()

        engine.primaryAction()
        engine.run(seconds = 0.7)

        assertEquals(ChirpyHopPhase.Running, engine.phase)
        assertEquals(1, engine.score)
        assertEquals(ChirpyObstacleKind.CactusCluster, engine.obstacleKind)
    }

    @Test
    fun `running into a cactus ends the game`() {
        val engine = ChirpyHopEngine(obstacleX = 1.0)
        engine.primaryAction()

        engine.run(seconds = 3.0)

        assertEquals(ChirpyHopPhase.GameOver, engine.phase)
        assertEquals(0, engine.score)
    }

    @Test
    fun `crouching ducks under a bird`() {
        val engine = ChirpyHopEngine(obstacleX = 1.0, obstacleKind = ChirpyObstacleKind.FlyingBird)
        engine.primaryAction()
        engine.run(seconds = 0.75)

        engine.setCrouching(true)
        engine.run(seconds = 1.2)

        assertEquals(ChirpyHopPhase.Running, engine.phase)
        assertEquals(1, engine.score)
    }

    @Test
    fun `standing under a bird ends the game`() {
        val engine = ChirpyHopEngine(obstacleX = 1.0, obstacleKind = ChirpyObstacleKind.FlyingBird)
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
        assertEquals(0.88, ChirpyHopEngine.speed(1000))
        assertTrue(ChirpyHopEngine.speed(10) > ChirpyHopEngine.speed(5))
    }

    @Test
    fun `obstacles follow a five step pattern`() {
        val pattern = (0 until 5).map { ChirpyHopEngine.obstacleKind(it) }

        assertEquals(
            listOf(
                ChirpyObstacleKind.TallCactus,
                ChirpyObstacleKind.CactusCluster,
                ChirpyObstacleKind.TallCactus,
                ChirpyObstacleKind.FlyingBird,
                ChirpyObstacleKind.CactusCluster,
            ),
            pattern,
        )
        assertEquals(ChirpyObstacleKind.CactusCluster, ChirpyHopEngine.obstacleKind(6))
    }
}
