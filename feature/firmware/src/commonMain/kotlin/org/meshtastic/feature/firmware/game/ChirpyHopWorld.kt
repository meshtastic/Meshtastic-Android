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

import kotlin.math.min

/** The clock the scenery runs on: how far the ground has scrolled, and how long Chirpy has been knocked out. */
internal class ChirpyHopWorld {
    /** Distance the ground has scrolled, in play-field widths. */
    var groundShift = 0.0
        private set

    var sceneSeconds = 0.0
        private set

    private var knockedOutAt: Double? = null

    /** Seconds since the hit that ended the run, or null while it is alive. */
    val knockedOutFor: Double?
        get() = knockedOutAt?.let { sceneSeconds - it }

    fun advance(deltaSeconds: Double, engine: ChirpyHopEngine) {
        val delta = min(deltaSeconds, MAX_SCENE_STEP)
        sceneSeconds += delta
        when (engine.phase) {
            ChirpyHopPhase.Running -> {
                knockedOutAt = null
                groundShift += ChirpyHopEngine.speed(engine.score) * delta
            }

            ChirpyHopPhase.GameOver -> if (knockedOutAt == null) knockedOutAt = sceneSeconds

            ChirpyHopPhase.Ready -> knockedOutAt = null
        }
    }

    private companion object {
        const val MAX_SCENE_STEP = 1.0 / 30.0
    }
}
