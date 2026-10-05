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

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ChirpyHopPhase {
    Ready,
    Running,
    GameOver,
}

enum class ChirpyObstacleKind {
    TallCactus,
    CactusCluster,
    FlyingBird,
}

/**
 * Chirpy Hop's rules, free of any drawing. Positions are fractions of the play field: x runs 0..1 left to right and y
 * is height above the ground, so the same state renders at any size. Physics advances in fixed steps, so the outcome of
 * a run does not depend on the frame rate.
 */
class ChirpyHopEngine(
    obstacleX: Double = INITIAL_OBSTACLE_X,
    obstacleKind: ChirpyObstacleKind = ChirpyObstacleKind.TallCactus,
) {
    var phase: ChirpyHopPhase = ChirpyHopPhase.Ready
        private set

    var playerY: Double = 0.0
        private set

    var verticalVelocity: Double = 0.0
        private set

    var isCrouching: Boolean = false
        private set

    var score: Int = 0
        private set

    var obstacleX: Double = obstacleX
        private set

    var obstacleKind: ChirpyObstacleKind = obstacleKind
        private set

    val isAirborne: Boolean
        get() = playerY > JUMP_TOLERANCE

    /** Tap: starts a run and jumps, jumps while running, and resets after a game over. */
    fun primaryAction() {
        when (phase) {
            ChirpyHopPhase.Ready -> {
                phase = ChirpyHopPhase.Running
                jump()
            }

            ChirpyHopPhase.Running -> jump()

            ChirpyHopPhase.GameOver -> reset()
        }
    }

    fun setCrouching(crouching: Boolean) {
        isCrouching = phase == ChirpyHopPhase.Running && crouching
    }

    fun advance(deltaSeconds: Double) {
        if (phase != ChirpyHopPhase.Running) return
        var remaining = deltaSeconds.coerceIn(0.0, MAX_CATCH_UP_SECONDS)
        while (remaining > 0 && phase == ChirpyHopPhase.Running) {
            val step = min(remaining, SIMULATION_STEP)
            advanceStep(step)
            remaining -= step
        }
    }

    fun reset() {
        phase = ChirpyHopPhase.Ready
        playerY = 0.0
        verticalVelocity = 0.0
        isCrouching = false
        score = 0
        obstacleX = INITIAL_OBSTACLE_X
        obstacleKind = ChirpyObstacleKind.TallCactus
    }

    private fun advanceStep(delta: Double) {
        val gravity = if (isCrouching && playerY > 0) FAST_FALL_GRAVITY else GRAVITY
        verticalVelocity -= gravity * delta
        playerY += verticalVelocity * delta
        if (playerY <= 0) {
            playerY = 0.0
            verticalVelocity = 0.0
        }

        obstacleX -= speed(score) * delta
        if (collidesWithObstacle()) {
            phase = ChirpyHopPhase.GameOver
            return
        }

        val obstacleRight = obstacleX + OBSTACLE_WIDTH / 2
        val playerLeft = PLAYER_X - PLAYER_WIDTH / 2
        if (obstacleRight < playerLeft) {
            score += 1
            obstacleX = RESPAWN_X + (score % CADENCE_STEPS) * CADENCE_SPACING
            obstacleKind = obstacleKind(score)
        }
    }

    private fun jump() {
        if (isAirborne || isCrouching) return
        verticalVelocity = JUMP_VELOCITY
    }

    private fun collidesWithObstacle(): Boolean {
        val obstacleWidth =
            when (obstacleKind) {
                ChirpyObstacleKind.TallCactus -> OBSTACLE_WIDTH
                ChirpyObstacleKind.CactusCluster -> CLUSTER_WIDTH
                ChirpyObstacleKind.FlyingBird -> BIRD_WIDTH
            }
        val horizontalRange = (PLAYER_WIDTH + obstacleWidth) * HORIZONTAL_HITBOX_SCALE
        if (abs(obstacleX - PLAYER_X) >= horizontalRange) return false

        val playerHeight = if (isCrouching && playerY == 0.0) CROUCHING_PLAYER_HEIGHT else PLAYER_HEIGHT
        val playerBottom = playerY + PLAYER_FOOT_INSET
        val playerTop = playerY + playerHeight * VERTICAL_HITBOX_SCALE
        val obstacleRange =
            when (obstacleKind) {
                ChirpyObstacleKind.TallCactus -> 0.0..OBSTACLE_HEIGHT * VERTICAL_HITBOX_SCALE
                ChirpyObstacleKind.CactusCluster -> 0.0..CLUSTER_HEIGHT
                ChirpyObstacleKind.FlyingBird -> BIRD_BOTTOM..BIRD_TOP
            }
        return playerTop > obstacleRange.start && playerBottom < obstacleRange.endInclusive
    }

    companion object {
        const val PLAYER_X = 0.2
        const val PLAYER_WIDTH = 0.09
        const val PLAYER_HEIGHT = 0.17
        const val CROUCHING_PLAYER_HEIGHT = 0.095
        const val OBSTACLE_WIDTH = 0.075
        const val OBSTACLE_HEIGHT = 0.18
        const val INITIAL_OBSTACLE_X = 0.43

        private const val GRAVITY = 7.2
        private const val FAST_FALL_GRAVITY = 11.2
        private const val JUMP_VELOCITY = 2.5
        private const val JUMP_TOLERANCE = 0.012
        private const val SIMULATION_STEP = 1.0 / 120.0
        private const val MAX_CATCH_UP_SECONDS = 0.1
        private const val HORIZONTAL_HITBOX_SCALE = 0.38
        private const val VERTICAL_HITBOX_SCALE = 0.88
        private const val PLAYER_FOOT_INSET = 0.01
        private const val CLUSTER_WIDTH = 0.115
        private const val CLUSTER_HEIGHT = 0.132
        private const val BIRD_WIDTH = 0.1
        private const val BIRD_BOTTOM = 0.115
        private const val BIRD_TOP = 0.195
        private const val RESPAWN_X = 1.04
        private const val CADENCE_STEPS = 4
        private const val CADENCE_SPACING = 0.055
        private const val BASE_SPEED = 0.5
        private const val SPEED_PER_POINT = 0.019
        private const val MAX_SPEED = 0.88

        /** The obstacle sequence repeats every five points. */
        private val OBSTACLE_CYCLE =
            listOf(
                ChirpyObstacleKind.TallCactus,
                ChirpyObstacleKind.CactusCluster,
                ChirpyObstacleKind.TallCactus,
                ChirpyObstacleKind.FlyingBird,
                ChirpyObstacleKind.CactusCluster,
            )

        fun speed(score: Int): Double = min(BASE_SPEED + max(score, 0) * SPEED_PER_POINT, MAX_SPEED)

        fun obstacleKind(score: Int): ChirpyObstacleKind = OBSTACLE_CYCLE[max(score, 0) % OBSTACLE_CYCLE.size]
    }
}
