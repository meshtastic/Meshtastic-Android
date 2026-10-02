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
package org.meshtastic.desktop

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import org.meshtastic.core.common.log.InMemoryLogBuffer
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLoggingTest {

    @AfterTest
    fun tearDown() {
        Logger.setLogWriters(platformLogWriter())
        Logger.setMinSeverity(Severity.Verbose)
    }

    private fun marker(level: String) = "desktop-logging-test-$level-${UUID.randomUUID()}"

    @Test
    fun `release build keeps Verbose and Debug lines out of the exportable buffer`() {
        installDesktopLogging(isDebug = false)
        val verbose = marker("verbose")
        val debug = marker("debug")
        val info = marker("info")

        Logger.v { verbose }
        Logger.d { debug }
        Logger.i { info }

        val buffer = InMemoryLogBuffer.snapshot()
        assertFalse(verbose in buffer, "Verbose line reached the release buffer")
        assertFalse(debug in buffer, "Debug line reached the release buffer")
        assertTrue(info in buffer, "Info line is missing from the release buffer")
    }

    @Test
    fun `debug build keeps every level in the exportable buffer`() {
        installDesktopLogging(isDebug = true)
        val verbose = marker("verbose")
        val debug = marker("debug")

        Logger.v { verbose }
        Logger.d { debug }

        val buffer = InMemoryLogBuffer.snapshot()
        assertTrue(verbose in buffer, "Verbose line is missing from the debug buffer")
        assertTrue(debug in buffer, "Debug line is missing from the debug buffer")
    }
}
