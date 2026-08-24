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

    /** A 32-byte channel PSK as it appears in hex inside a serialised AdminMessage. */
    private val pskHex = "c9ee13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44"

    private fun log(logMessage: String, decodedPayload: String? = null) = DebugViewModel.UiMeshLog(
        uuid = "1",
        messageType = "Admin",
        formattedReceivedDate = "2026-03-25",
        logMessage = logMessage,
        decodedPayload = decodedPayload,
    )

    private fun export(vararg logs: DebugViewModel.UiMeshLog): String =
        StringBuilder().also { formatLogsTo(it, logs.toList()) }.toString()

    @Test
    fun `export suppresses payload hex in okio's truncated form`() {
        // okio renders a ByteString longer than 64 bytes as `[size=N hex=<first 64 bytes>…]`, NOT `[hex=..]`. A real
        // set_channel / get_channel_response AdminMessage exceeds 64 bytes and the PSK lands inside that prefix, so
        // this is the shape that actually carries the key on the export path.
        val result = export(log("Data{portnum=ADMIN_APP, payload=[size=75 hex=1a3f080112391220$pskHex…]}"))

        assertFalse(result.contains(pskHex), "the channel key must not survive export in the truncated form")
        assertTrue(result.contains("payload=<suppressed>"))
        assertTrue(result.contains("portnum=ADMIN_APP"), "surrounding structure is kept")
    }

    @Test
    fun `export suppresses payload hex regardless of the byte count`() {
        // Guard the boundary between okio's two binary renderings rather than one example length.
        listOf(
            "payload=[hex=$pskHex]",
            "payload=[size=32 hex=$pskHex]",
            "payload=[size=140 hex=$pskHex…]",
            "payload=[size=1024 hex=$pskHex…]",
        )
            .forEach { field ->
                val result = export(log("Data{portnum=ADMIN_APP, $field, want_response=true}"))
                assertFalse(result.contains(pskHex), "key survived export for '$field'")
                assertTrue(result.contains("want_response=true"), "later fields preserved for '$field'")
            }
    }

    @Test
    fun `the per-entry copy action is sanitised like the export`() {
        // The copy button sits next to the export and its output gets pasted into the same public issue trackers, so it
        // must not be a way around the redaction. Binds the real code path, not just the helper.
        val copied =
            formatLogEntryForCopy(
                log(
                    logMessage = "Data{portnum=ADMIN_APP, payload=[size=75 hex=1a3f080112391220$pskHex…]}",
                    decodedPayload = "settings=ChannelSettings{psk=[hex=$pskHex], name=LongFast}",
                ),
            )

        assertFalse(copied.contains(pskHex), "the channel key must not reach the clipboard")
        assertTrue(copied.contains("payload=<suppressed>"))
        assertTrue(copied.contains("psk=<redacted>"))
        assertTrue(copied.contains("name=LongFast"), "non-sensitive fields are kept")
    }

    @Test
    fun `structured psk redaction covers okio's long and short forms`() {
        listOf("psk=[hex=$pskHex]", "psk=[size=140 hex=$pskHex…]").forEach { field ->
            val result = export(log("AdminMessage", decodedPayload = "settings=ChannelSettings{$field, name=X}"))
            assertFalse(result.contains(pskHex), "key survived for '$field'")
            assertTrue(result.contains("name=X"))
        }
    }

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
