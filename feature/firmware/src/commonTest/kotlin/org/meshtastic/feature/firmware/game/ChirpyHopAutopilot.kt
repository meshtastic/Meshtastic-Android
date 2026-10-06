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

/** A scripted perfect player: ducks under UFOs and jumps antennas so they pass under the top of the arc. */
object ChirpyHopAutopilot {
    private const val DUCK_DISTANCE = 0.4
    private const val SECONDS_TO_ARC_TOP = 0.347

    fun steer(engine: ChirpyHopEngine) {
        if (engine.phase != ChirpyHopPhase.Running) return
        val distance = engine.obstacleX - ChirpyHopEngine.PLAYER_X
        if (engine.obstacleKind == ChirpyObstacleKind.Ufo) {
            // Stay down until the UFO has cleared, which is well after its centre passes Chirpy's.
            engine.setCrouching(distance <= DUCK_DISTANCE)
        } else {
            engine.setCrouching(false)
            if (distance in 0.0..SECONDS_TO_ARC_TOP * ChirpyHopEngine.speed(engine.score)) engine.primaryAction()
        }
    }
}
