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

internal val redactedKeys = listOf("session_passkey", "private_key", "admin_key")

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

private fun appendRedactedPayload(out: Appendable, payload: String) {
    out.append("\n\nDecoded Payload:\n{\n")
    payload.lineSequence().forEach { line ->
        out.append(redactLine(line))
        out.append("\n")
    }
    out.append("}\n\n")
}

private fun redactLine(line: String): String {
    if (redactedKeys.none { line.contains(it) }) return line
    val idx = line.indexOf(':')
    return if (idx != -1) line.take(idx + 1) + "<redacted>" else line
}
