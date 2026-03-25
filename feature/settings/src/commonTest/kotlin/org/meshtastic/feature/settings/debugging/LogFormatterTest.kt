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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogFormatterTest {

    @Test
    fun `formatLogsTo formats and redacts correctly`() {
        val logs = listOf(
            DebugViewModel.UiMeshLog(
                uuid = "1",
                messageType = "Packet",
                formattedReceivedDate = "2026-03-25",
                logMessage = "Hello",
                decodedPayload = "session_passkey: secret\nother: value"
            )
        )
        val out = StringBuilder()
        formatLogsTo(out, logs)
        
        val result = out.toString()
        assertTrue(result.contains("2026-03-25 [Packet]"))
        assertTrue(result.contains("Hello"))
        assertTrue(result.contains("session_passkey:<redacted>"))
        assertTrue(result.contains("other: value"))
    }
}
