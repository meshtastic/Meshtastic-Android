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
package org.meshtastic.core.datastore

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Records every Kermit line, including an attached throwable's full text, so a test can assert what never reached a
 * log. Install it in setup and call [uninstall] in teardown: it replaces the global writers.
 */
internal class CapturingLogWriter : LogWriter() {
    private val entries = mutableListOf<String>()

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        entries += message
        throwable?.let { entries += it.stackTraceToString() }
    }

    fun assertNotLogged(vararg values: String) {
        assertTrue(entries.isNotEmpty(), "Expected the failed parse to log a warning")
        for (value in values) {
            assertFalse(entries.any { value in it }, "'$value' leaked into logs: $entries")
        }
    }

    companion object {
        fun install(): CapturingLogWriter = CapturingLogWriter().also {
            Logger.setLogWriters(it)
            Logger.setMinSeverity(Severity.Verbose)
        }

        fun uninstall() {
            Logger.setLogWriters(platformLogWriter())
        }
    }
}
