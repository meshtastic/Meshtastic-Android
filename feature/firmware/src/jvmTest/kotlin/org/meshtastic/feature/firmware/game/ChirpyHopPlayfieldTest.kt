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

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ChirpyHopPlayfieldTest {

    private val scores = mutableListOf<Int>()
    private var shown by mutableStateOf(true)

    private fun ComposeUiTest.showPlayfield(running: Boolean) {
        // A run requests a frame every frame, so the clock is driven by hand rather than advanced to idle.
        mainClock.autoAdvance = false
        setContent {
            if (shown) {
                ChirpyHopPlayfield(
                    running = running,
                    bestScore = 0,
                    onScore = { scores += it },
                    modifier = Modifier.size(400.dp, 600.dp),
                )
            }
        }
    }

    // The frame loop runs until the playfield leaves composition, and runTest fails on a coroutine left running.
    private fun ComposeUiTest.closePlayfield() {
        shown = false
        mainClock.advanceTimeByFrame()
    }

    @Test
    fun `a tap starts the run and the opening jump scores a point`() = runComposeUiTest {
        showPlayfield(running = true)
        onNodeWithText("TAP TO START").assertExists()

        onNodeWithContentDescription("Chirpy Hop").performClick()
        mainClock.advanceTimeBy(1_000)

        onNodeWithText("TAP TO START").assertDoesNotExist()
        onNodeWithText("00001").assertExists()
        assertEquals(listOf(1), scores)
        closePlayfield()
    }

    @Test
    fun `a downward swipe crouches instead of starting the run`() = runComposeUiTest {
        showPlayfield(running = true)

        onNodeWithContentDescription("Chirpy Hop").performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
            up()
        }
        mainClock.advanceTimeBy(500)

        onNodeWithText("TAP TO START").assertExists()
        closePlayfield()
    }

    @Test
    fun `GAME OVER waits for the knockout and ignores taps until then`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val engine = ChirpyHopEngine(obstacleX = 1.0)
        setContent {
            if (shown) {
                ChirpyHopPlayfield(
                    running = true,
                    bestScore = 0,
                    onScore = {},
                    modifier = Modifier.size(400.dp, 600.dp),
                    engine = engine,
                )
            }
        }
        onNodeWithContentDescription("Chirpy Hop").performClick()
        while (engine.phase != ChirpyHopPhase.GameOver) mainClock.advanceTimeByFrame()

        mainClock.advanceTimeBy(300)
        onNodeWithText("GAME OVER").assertDoesNotExist()
        onNodeWithContentDescription("Chirpy Hop").performClick()
        assertEquals(ChirpyHopPhase.GameOver, engine.phase)

        mainClock.advanceTimeBy(1_000)
        onNodeWithText("GAME OVER").assertExists()
        onNodeWithContentDescription("Chirpy Hop").performClick()
        mainClock.advanceTimeByFrame()
        onNodeWithText("TAP TO START").assertExists()
        closePlayfield()
    }

    @Test
    fun `taps are ignored once the update has stopped`() = runComposeUiTest {
        showPlayfield(running = false)

        onNodeWithContentDescription("Chirpy Hop").performClick()
        mainClock.advanceTimeBy(1_000)

        onNodeWithText("TAP TO START").assertExists()
        assertEquals(emptyList(), scores)
    }
}
