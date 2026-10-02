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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun printed(text: String) = text.map { EchoEvent.Printed(it) }

/** A line whose first character the remote has echoed, so predictions on it are drawn. */
private fun confirmedEcho(): LocalEcho = LocalEcho().apply {
    onSent("e", 0)
    onOutput(printed("e"), 50)
}

class LocalEchoTest {

    @Test
    fun nothingIsDrawnUntilTheLineHasEchoedOnce() {
        val echo = LocalEcho()
        echo.onSent("ls -l", 0)
        assertEquals("", echo.pending)
        assertFalse(echo.showsTyping)
        echo.onOutput(printed("l"), 100)
        assertEquals("s -l", echo.pending)
        assertTrue(echo.showsTyping)
    }

    @Test
    fun sentCharactersArePredictedUntilEchoed() {
        val echo = confirmedEcho()
        echo.onSent("cho hi", 100)
        assertEquals("cho hi", echo.pending)
        echo.onOutput(printed("cho"), 200)
        assertEquals(" hi", echo.pending)
        echo.onOutput(printed(" hi"), 300)
        assertEquals("", echo.pending)
    }

    @Test
    fun aPasswordTypedAtAPromptThatDoesNotEchoIsNeverDrawn() {
        val echo = LocalEcho()
        echo.onSent("sudo -v\r", 0)
        echo.onSent("hunter2", 500)
        assertEquals("", echo.pending)
        echo.tick(PREDICTION_TIMEOUT_MIN_MS + 500, roundTripMs = null)
        assertEquals("", echo.pending)
        assertFalse(echo.showsTyping)
    }

    @Test
    fun enterStartsAnUnconfirmedLine() {
        val echo = confirmedEcho()
        echo.onSent("\rpass", 100)
        assertEquals("", echo.pending)
    }

    @Test
    fun aDivergingEchoDropsThePredictionAndTheConfirmation() {
        val echo = confirmedEcho()
        echo.onSent("ab", 100)
        echo.onOutput(printed("ax"), 200)
        assertEquals("", echo.pending)
        echo.onSent("c", 300)
        assertEquals("", echo.pending)
    }

    @Test
    fun backspaceRetractsThePredictionAndItsEchoPasses() {
        val echo = confirmedEcho()
        echo.onSent("abc\u007f", 100)
        assertEquals("ab", echo.pending)
        echo.onOutput(printed("ab"), 200)
        echo.onOutput(listOf(EchoEvent.Printed('c'), EchoEvent.Edited, EchoEvent.Printed(' '), EchoEvent.Edited), 220)
        assertEquals("", echo.pending)
    }

    @Test
    fun aControlKeyDropsThePrediction() {
        val echo = confirmedEcho()
        echo.onSent("cd /us", 100)
        echo.onSent("\t", 110)
        assertEquals("", echo.pending)
    }

    @Test
    fun anUnconfirmedLineTimesOutAndStaysOffUntilEnter() {
        val echo = confirmedEcho()
        echo.onSent("xyz", 100)
        echo.tick(100 + PREDICTION_TIMEOUT_MIN_MS, roundTripMs = null)
        assertEquals("", echo.pending)
        echo.onSent("more", PREDICTION_TIMEOUT_MIN_MS + 200)
        echo.onOutput(printed("m"), PREDICTION_TIMEOUT_MIN_MS + 300)
        assertEquals("", echo.pending)
    }

    @Test
    fun aConfirmedCharacterRestartsTheWait() {
        val echo = confirmedEcho()
        echo.onSent("abc", 100)
        echo.onOutput(printed("a"), PREDICTION_TIMEOUT_MIN_MS)
        echo.tick(PREDICTION_TIMEOUT_MIN_MS + 100, roundTripMs = null)
        assertEquals("bc", echo.pending)
    }

    @Test
    fun theTimeoutScalesWithTheRoundTrip() {
        val echo = confirmedEcho()
        echo.onSent("x", 100)
        echo.tick(100 + PREDICTION_TIMEOUT_MIN_MS + 1, roundTripMs = 3_000)
        assertEquals("x", echo.pending)
        echo.tick(100 + 9_000, roundTripMs = 3_000)
        assertEquals("", echo.pending)
    }

    @Test
    fun secretPromptsAreRecognised() {
        assertTrue(looksLikeSecretPrompt("[sudo] password for james: "))
        assertTrue(looksLikeSecretPrompt("Password:"))
        assertTrue(looksLikeSecretPrompt("Enter passphrase for key '/root/.ssh/id_ed25519': "))
        assertTrue(looksLikeSecretPrompt("Enter PIN: "))
        assertFalse(looksLikeSecretPrompt("root@node:~# "))
        assertFalse(looksLikeSecretPrompt("# cat password.txt"))
        assertFalse(looksLikeSecretPrompt("Pinging host: "))
    }
}
