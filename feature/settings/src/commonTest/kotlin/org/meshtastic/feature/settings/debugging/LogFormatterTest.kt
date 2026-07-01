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
package org.meshtastic.feature.settings.debugging

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogFormatterTest {

    @Test
    fun `formatLogsTo formats and redacts correctly`() {
        val logs =
            listOf(
                DebugViewModel.UiMeshLog(
                    uuid = "1",
                    messageType = "Packet",
                    formattedReceivedDate = "2026-03-25",
                    logMessage = "Hello",
                    decodedPayload = "session_passkey: secret\nother: value",
                ),
            )
        val out = StringBuilder()
        formatLogsTo(out, logs)

        val result = out.toString()
        assertTrue(result.contains("2026-03-25 [Packet]"))
        assertTrue(result.contains("Hello"))
        assertTrue(result.contains("session_passkey:<redacted>"))
        assertTrue(result.contains("other: value"))
    }

    @Test
    fun `export redacts the real proto equals-and-hex payload format`() {
        // Matches what the decoded payload actually looks like (proto toString uses '=' and '[hex=..]').
        val logs =
            listOf(
                DebugViewModel.UiMeshLog(
                    uuid = "1",
                    messageType = "Admin",
                    formattedReceivedDate = "2026-03-25",
                    logMessage = "AdminMessage",
                    decodedPayload = "session_passkey=[hex=dd8042fa0cfd7d17], region=US",
                ),
            )
        val out = StringBuilder()
        formatLogsTo(out, logs)

        val result = out.toString()
        assertFalse(result.contains("dd8042fa0cfd7d17"), "the hex secret must not survive export")
        assertTrue(result.contains("session_passkey=<redacted>"))
        assertTrue(result.contains("region=US"), "non-sensitive fields are kept")
    }

    @Test
    fun `appendLogcat redacts sensitive keys and keeps other lines`() {
        val out = StringBuilder()
        appendLogcat(out, "I/foo: hello world\nD/bar: private_key: deadbeef")

        val result = out.toString()
        assertTrue(result.contains("App Logcat"))
        assertTrue(result.contains("I/foo: hello world"))
        assertTrue(result.contains("private_key:<redacted>"))
    }

    @Test
    fun `redactText redacts keys across lines and keeps the rest`() {
        val result = redactText("D/x: session_passkey=[hex=dd8042fa0cfd7d17]\nD/x: region=US\nD/x: private_key: bb")

        assertFalse(result.contains("dd8042fa0cfd7d17"), "hex secret must not survive")
        assertFalse(result.contains(": bb"), "bare-token secret must not survive")
        assertTrue(result.contains("session_passkey=<redacted>"))
        assertTrue(result.contains("private_key:<redacted>"))
        assertTrue(result.contains("region=US"))
    }
}
