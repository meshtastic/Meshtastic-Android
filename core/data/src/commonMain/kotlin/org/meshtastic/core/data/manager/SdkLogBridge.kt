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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.model.util.anonymizePublicHost
import org.meshtastic.sdk.LogLevel
import org.meshtastic.sdk.LogSink

/**
 * Routes the SDK engine's log lines into the app's log, which release builds forward to crash reporting. An SDK error
 * is a link or radio condition rather than an app defect, so nothing goes above warn. Lines that ship are scrubbed of
 * radio identities and proto dumps, and carry only a throwable's class.
 */
internal val sdkLogSink = LogSink { level, tag, message, cause ->
    val logger = Logger.withTag("SDK/$tag")
    when (level) {
        LogLevel.VERBOSE -> logger.v(cause) { scrubSdkLogLine(message) }

        LogLevel.DEBUG -> logger.d(cause) { scrubSdkLogLine(message) }

        LogLevel.INFO -> logger.i { scrubSdkLogLine(message, cause) }

        LogLevel.WARN,
        LogLevel.ERROR,
        -> logger.w { scrubSdkLogLine(message, cause) }

        LogLevel.NONE -> Unit
    }
}

/** [message] without MAC addresses, public hosts or Wire message dumps, with [cause] named by class only. */
internal fun scrubSdkLogLine(message: String, cause: Throwable? = null): String {
    val scrubbed =
        message
            .replace(WIRE_DUMP) { "${it.groupValues[1]}{…}" }
            .replace(MAC_ADDRESS) { it.value.anonymize }
            .replace(URL_HOST) { "${it.groupValues[1]}${it.groupValues[2].anonymizePublicHost()}" }
            .replace(DNS_HOST_PORT) { it.value.anonymizePublicHost() }
            .replace(IPV4_HOST) { it.value.anonymizePublicHost() }
    return if (cause == null) scrubbed else "$scrubbed (${cause::class.simpleName})"
}

/** Wire's toString of a message, `FromRadio{packet=MeshPacket{…}}`, which can carry text and positions. */
private val WIRE_DUMP = Regex("""\b([A-Z]\w*)\{.*""", RegexOption.DOT_MATCHES_ALL)

private val MAC_ADDRESS = Regex("""\b[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}\b""")

private val URL_HOST = Regex("""\b([a-z][a-z0-9+.-]*://)([^\s/:?#]+)""")

private val DNS_HOST_PORT = Regex("""\b[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+:\d{1,5}\b""")

private val IPV4_HOST = Regex("""\b(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)(?::\d{1,5})?\b""")
