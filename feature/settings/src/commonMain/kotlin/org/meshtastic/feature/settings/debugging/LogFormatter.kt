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

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.meshtastic.core.common.util.nowMillis
import kotlin.time.Instant.Companion.fromEpochMilliseconds

internal val redactedKeys = listOf("session_passkey", "private_key", "admin_key")

// Matches `key: value`, `key=value`, `key=[hex=..]`, and one level of nested list like `admin_key=[[hex=..], [..]]`.
// The value alternation is ordered bracket-list | quoted | bare-token so the widest match wins.
private val REDACT_REGEX =
    Regex("(${redactedKeys.joinToString("|")})\\s*([:=])\\s*(\\[(?:[^\\[\\]]|\\[[^\\]]*\\])*\\]|\"[^\"]*\"|\\S+)")

/** Builds a collision-free export file name, e.g. `meshtastic_logcat_20260701_143312.txt`. */
internal fun timestampedExportName(prefix: String): String {
    val format =
        LocalDateTime.Format {
            year()
            monthNumber()
            day()
            char('_')
            hour()
            minute()
            second()
        }
    val timestamp = fromEpochMilliseconds(nowMillis).toLocalDateTime(TimeZone.UTC).format(format)
    return "${prefix}_$timestamp.txt"
}

/**
 * Formats a list of [DebugViewModel.UiMeshLog] entries into the given [Appendable], redacting sensitive keys in decoded
 * payloads.
 */
internal fun formatLogsTo(out: Appendable, logs: List<DebugViewModel.UiMeshLog>) {
    logs.forEach { log ->
        out.append("${log.formattedReceivedDate} [${log.messageType}]\n")
        out.append(log.logMessage)
        val decodedPayload = log.decodedPayload
        if (!decodedPayload.isNullOrBlank()) {
            appendRedactedPayload(out, decodedPayload)
        }
    }
}

/**
 * Appends captured Android logcat to [out] under a header, redacting sensitive keys line-by-line. The app should never
 * log keys/PII, so this is defence-in-depth for a file the user is about to share on a public issue tracker.
 */
internal fun appendLogcat(out: Appendable, logcat: String) {
    out.append("\n===== App Logcat =====\n")
    logcat.lineSequence().forEach { line ->
        out.append(redactLine(line))
        out.append("\n")
    }
}

private fun appendRedactedPayload(out: Appendable, payload: String) {
    out.append("\n\nDecoded Payload:\n{\n")
    payload.lineSequence().forEach { line ->
        out.append(redactLine(line))
        out.append("\n")
    }
    out.append("}\n\n")
}

private fun redactLine(line: String): String =
    REDACT_REGEX.replace(line) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>" }
