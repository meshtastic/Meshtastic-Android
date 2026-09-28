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
package org.meshtastic.core.testing

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A Kermit writer that records every line, for tests that assert what was or was not logged. Hand it to
 * `Logger(loggerConfigInit(writer))`, or [install] it on the global `Logger` and call [uninstall] in teardown.
 */
class CapturingLogWriter : LogWriter() {
    data class Entry(val severity: Severity, val tag: String, val message: String, val throwable: Throwable?)

    private val recorded = mutableListOf<Entry>()

    val entries: List<Entry>
        get() = recorded.toList()

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        recorded += Entry(severity, tag, message, throwable)
    }

    fun messages(severity: Severity? = null): List<String> =
        entries.filter { severity == null || it.severity == severity }.map { it.message }

    /** Asserts that something was logged and that no message or attached throwable text contains any of [values]. */
    fun assertNotLogged(vararg values: String) {
        val text = entries.flatMap { listOfNotNull(it.message, it.throwable?.stackTraceToString()) }
        assertTrue(text.isNotEmpty(), "Expected something to be logged")
        for (value in values) {
            assertFalse(text.any { value in it }, "'$value' leaked into logs: $text")
        }
    }

    companion object {
        /** Replaces the global `Logger` writers with a new capturing writer that keeps every severity. */
        fun install(): CapturingLogWriter = CapturingLogWriter().also {
            Logger.setLogWriters(it)
            Logger.setMinSeverity(Severity.Verbose)
        }

        /** Restores the platform writer on the global `Logger`. */
        fun uninstall() {
            Logger.setLogWriters(platformLogWriter())
        }
    }
}
