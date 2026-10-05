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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What a frame changed that the screen should react to beyond redrawing. */
internal enum class ChirpyHopEvent {
    None,
    Scored,
    KnockedOut,
}

/**
 * The play field's state: the engine and scenery it advances each frame, and the few values the HUD shows. The engine
 * and world are plain objects; [frameTick] is bumped on every change so the canvas, which reads it while drawing,
 * redraws without recomposing. [phase] and [score] change rarely and drive the HUD.
 */
@Stable
internal class ChirpyHopState(val engine: ChirpyHopEngine = ChirpyHopEngine()) {
    val world = ChirpyHopWorld()

    val frameTick = mutableLongStateOf(0L)

    var phase by mutableStateOf(engine.phase)
        private set

    var score by mutableIntStateOf(engine.score)
        private set

    /** False while the knockout plays: GAME OVER waits for it, and taps are ignored so a panicked one can't restart. */
    var knockoutDone by mutableStateOf(true)
        private set

    /** The phase the HUD shows, which holds back GAME OVER until the knockout has played. */
    val hudPhase: ChirpyHopPhase
        get() = if (phase == ChirpyHopPhase.GameOver && !knockoutDone) ChirpyHopPhase.Running else phase

    /**
     * Whether anything moves: during a run, and after a knockout while Chirpy sways. A waiting game needs no frames.
     */
    val animating: Boolean
        get() = phase != ChirpyHopPhase.Ready

    /** Starts, jumps or restarts. Returns whether a run is now going, which is when a tap deserves a haptic. */
    fun tap(): Boolean {
        if (phase == ChirpyHopPhase.GameOver && !knockoutDone) return false
        engine.primaryAction()
        sync()
        return phase == ChirpyHopPhase.Running
    }

    fun setCrouching(crouching: Boolean) = engine.setCrouching(crouching)

    /** The update stopped: freeze the run where it is. */
    fun freeze() {
        engine.setCrouching(false)
        knockoutDone = true
    }

    fun step(deltaSeconds: Double): ChirpyHopEvent {
        val before = engine.score
        val wasRunning = engine.phase == ChirpyHopPhase.Running
        engine.advance(deltaSeconds)
        world.advance(deltaSeconds, engine)
        val knockedOut = wasRunning && engine.phase == ChirpyHopPhase.GameOver
        if (knockedOut) knockoutDone = false
        if (!knockoutDone && (world.knockedOutFor ?: 0.0) >= KNOCKOUT_SECONDS) knockoutDone = true
        sync()
        return when {
            knockedOut -> ChirpyHopEvent.KnockedOut
            engine.score != before -> ChirpyHopEvent.Scored
            else -> ChirpyHopEvent.None
        }
    }

    private fun sync() {
        phase = engine.phase
        score = engine.score
        frameTick.longValue++
    }
}
