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

// `psk` is channel key material and reaches the debug log via set_channel / get_channel_response like any other field.
internal val redactedKeys = listOf("session_passkey", "private_key", "admin_key", "psk")

// Matches `key: value`, `key=value`, `key=[hex=..]`, and one level of nested list like `admin_key=[[hex=..], [..]]`.
// The value alternation is ordered bracket-list | quoted | bare-token so the widest match wins.
private val REDACT_REGEX =
    Regex("(${redactedKeys.joinToString("|")})\\s*([:=])\\s*(\\[(?:[^\\[\\]]|\\[[^\\]]*\\])*\\]|\"[^\"]*\"|\\S+)")

/**
 * Matches an undecoded binary `payload` field in a proto `toString()`, in every shape okio can render it.
 *
 * Structured redaction alone is not enough: a `set_channel` AdminMessage carries the channel PSK inside its serialised
 * payload, so the raw bytes have to go too. okio has three renderings for a `ByteString` and the byte count selects
 * between them — `[hex=..]` at 64 bytes or fewer, `[size=N hex=..…]` above that. Matching only the first leaves the key
 * intact for every payload large enough to matter, which is most of them.
 *
 * `[text=..]` is deliberately NOT matched here: that rendering only occurs for a valid-UTF-8 payload, i.e. message
 * content rather than key material, and its content is unbounded and unescaped so it cannot be delimited reliably. The
 * export warning discloses that message text is present.
 */
private val PAYLOAD_HEX_REGEX = Regex("""(payload)\s*([:=])\s*\[(?:size=\d+ )?hex=[0-9a-fA-F]*…?]""")

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
        // logMessage is the annotated proto toString, so it carries both structured fields and the raw payload bytes.
        out.append(sanitizeForExport(log.logMessage))
        val decodedPayload = log.decodedPayload
        if (!decodedPayload.isNullOrBlank()) {
            appendRedactedPayload(out, decodedPayload)
        }
    }
}

/**
 * Renders one log entry for the per-entry copy action, sanitised the same way the file export is.
 *
 * Extracted from the composable so the sanitisation is covered by a test rather than by inspection.
 */
internal fun formatLogEntryForCopy(log: DebugViewModel.UiMeshLog): String = sanitizeForExport(
    buildString {
        append(log.logMessage)
        if (!log.decodedPayload.isNullOrBlank()) {
            append("\n\nDecoded Payload:\n{")
            append("\n")
            append(log.decodedPayload)
            append("\n}")
        }
    },
)

/**
 * Appends captured Android logcat to [out] under a header, redacting sensitive keys line-by-line. The app should never
 * log keys/PII, so this is defence-in-depth for a file the user is about to share on a public issue tracker.
 */
internal fun appendLogcat(out: Appendable, logcat: String) {
    out.append("\n===== App Logcat =====\n")
    logcat.lineSequence().forEach { line ->
        out.append(sanitizeForExport(line))
        out.append("\n")
    }
}

private fun appendRedactedPayload(out: Appendable, payload: String) {
    out.append("\n\nDecoded Payload:\n{\n")
    payload.lineSequence().forEach { line ->
        out.append(sanitizeForExport(line))
        out.append("\n")
    }
    out.append("}\n\n")
}

/** Redacts sensitive-key values in every line of [text]; used to redact logcat both on screen and on export. */
internal fun redactText(text: String): String = text.lineSequence().joinToString("\n") { redactLine(it) }

/**
 * Full sanitisation for text leaving the app — the file export and the per-entry copy action both use this.
 *
 * Sensitive-key redaction plus raw payload-byte suppression. Use this rather than [redactLine] anywhere log text is
 * written to a file or the clipboard, since both end up pasted into public issue trackers.
 */
internal fun sanitizeForExport(text: String): String =
    text.lineSequence().joinToString("\n") { line -> suppressPayloadBytes(redactLine(line)) }

private fun redactLine(line: String): String =
    REDACT_REGEX.replace(line) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>" }

private fun suppressPayloadBytes(line: String): String =
    PAYLOAD_HEX_REGEX.replace(line) { "${it.groupValues[1]}${it.groupValues[2]}<suppressed>" }
