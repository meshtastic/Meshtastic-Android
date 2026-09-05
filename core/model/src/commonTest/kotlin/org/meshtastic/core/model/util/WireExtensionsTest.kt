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
package org.meshtastic.core.model.util

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WireExtensionsTest {

    private class CapturingWriter : LogWriter() {
        val severities = mutableListOf<Severity>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            severities += severity
        }
    }

    private val writer = CapturingWriter()
    private val logger = Logger(loggerConfigInit(writer), tag = "Test")

    // Garbage bytes that are not a valid encoding of any message: decoding must fail, not merely
    // produce an empty/default Position.
    private val garbage = byteArrayOf(0x0f, 0x00, 0x05)

    @Test
    fun `a decode failure logs at WARN not ERROR`() {
        val result = Position.ADAPTER.decodeOrNull(garbage, logger)

        assertNull(result)
        assertEquals(listOf(Severity.Warn), writer.severities)
    }

    @Test
    fun `the ByteArray overload delegates the same WARN behavior`() {
        val result = Position.ADAPTER.decodeOrNull(bytes = garbage, logger = logger)

        assertNull(result)
        assertEquals(listOf(Severity.Warn), writer.severities)
    }

    @Test
    fun `no logger means no log line only null`() {
        val result = Position.ADAPTER.decodeOrNull(garbage)

        assertNull(result)
        assertEquals(emptyList(), writer.severities)
    }

    @Test
    fun `null bytes short-circuit without touching the logger`() {
        val result = Position.ADAPTER.decodeOrNull(bytes = null as ByteArray?, logger = logger)

        assertNull(result)
        assertEquals(emptyList(), writer.severities)
    }
}
