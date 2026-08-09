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
package org.meshtastic.core.common.log

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * A bounded in-memory ring buffer of recent Kermit log lines. Desktop has no system logcat, so install this writer at
 * startup (`Logger.setLogWriters(platformLogWriter(), InMemoryLogBuffer)`) to let the Debug screen view and export the
 * app's own logs. Lines are formatted to loosely match Android's `logcat -v time` shape (`L/tag: message`) so the same
 * viewer/filters work on both platforms.
 */
object InMemoryLogBuffer : LogWriter() {
    private const val MAX_LINES = 5000
    private val lines = ArrayDeque<String>()

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val line = buildString {
            append(severity.name.first()) // V/D/I/W/E/A — matches the viewer's level filter
            append('/')
            append(tag)
            append(": ")
            append(message)
            if (throwable != null) {
                append('\n')
                append(throwable.stackTraceToString())
            }
        }
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
    }

    fun snapshot(): String = synchronized(lines) { lines.joinToString("\n") }
}
