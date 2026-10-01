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
package org.meshtastic.feature.node.metrics.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

private fun printed(text: String) = text.map { EchoEvent.Printed(it) }

class LocalEchoTest {

    @Test
    fun sentCharactersArePredictedUntilEchoed() {
        val echo = LocalEcho()
        echo.onSent("ls -l", 0)
        assertEquals("ls -l", echo.pending)
        echo.onOutput(printed("ls"), 100)
        assertEquals(" -l", echo.pending)
        echo.onOutput(printed(" -l"), 200)
        assertEquals("", echo.pending)
    }

    @Test
    fun aDivergingEchoDropsThePrediction() {
        val echo = LocalEcho()
        echo.onSent("ab", 0)
        echo.onOutput(printed("ax"), 100)
        assertEquals("", echo.pending)
    }

    @Test
    fun backspaceRetractsThePredictionAndItsEchoPasses() {
        val echo = LocalEcho()
        echo.onSent("abc\u007f", 0)
        assertEquals("ab", echo.pending)
        echo.onOutput(printed("ab"), 100)
        echo.onOutput(listOf(EchoEvent.Printed('c'), EchoEvent.Edited, EchoEvent.Printed(' '), EchoEvent.Edited), 120)
        assertEquals("", echo.pending)
    }

    @Test
    fun enterEndsThePredictedLine() {
        val echo = LocalEcho()
        echo.onSent("ls\r", 0)
        assertEquals("", echo.pending)
    }

    @Test
    fun aControlKeyDropsThePrediction() {
        val echo = LocalEcho()
        echo.onSent("cd /us", 0)
        echo.onSent("\t", 10)
        assertEquals("", echo.pending)
    }

    @Test
    fun anUnechoedPromptStopsPredictingUntilEnter() {
        val echo = LocalEcho()
        echo.onSent("hunter2", 0)
        echo.tick(PREDICTION_TIMEOUT_MIN_MS - 1, roundTripMs = null)
        assertEquals("hunter2", echo.pending)
        echo.tick(PREDICTION_TIMEOUT_MIN_MS, roundTripMs = null)
        assertEquals("", echo.pending)

        echo.onSent("more", PREDICTION_TIMEOUT_MIN_MS + 1)
        assertEquals("", echo.pending)
        echo.onSent("\rls", PREDICTION_TIMEOUT_MIN_MS + 2)
        assertEquals("ls", echo.pending)
    }

    @Test
    fun aConfirmedCharacterRestartsTheWait() {
        val echo = LocalEcho()
        echo.onSent("abc", 0)
        echo.onOutput(printed("a"), PREDICTION_TIMEOUT_MIN_MS - 1)
        echo.tick(PREDICTION_TIMEOUT_MIN_MS + 1, roundTripMs = null)
        assertEquals("bc", echo.pending)
    }

    @Test
    fun theTimeoutScalesWithTheRoundTrip() {
        val echo = LocalEcho()
        echo.onSent("x", 0)
        echo.tick(PREDICTION_TIMEOUT_MIN_MS + 1, roundTripMs = 3_000)
        assertEquals("x", echo.pending)
        echo.tick(9_000, roundTripMs = 3_000)
        assertEquals("", echo.pending)
    }
}
