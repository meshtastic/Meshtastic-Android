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
package org.meshtastic.core.network.transport

/** The port firmware's TCP phone API listens on. */
const val DEFAULT_TCP_PORT: Int = 4403

/**
 * Split a discovered or entered address into host and port. A bracketed IPv6 literal (`[fe80::1]:4403`, as
 * `NetworkRepository` writes it) keeps its colons; plain `host` and `host:port` cover IPv4 and hostnames.
 */
fun parseTcpRadioAddress(address: String): Pair<String, Int> {
    if (address.startsWith("[")) {
        val closeBracket = address.indexOf(']')
        if (closeBracket != -1) {
            val host = address.substring(1, closeBracket)
            val port = address.substring(closeBracket + 1).removePrefix(":").toIntOrNull() ?: DEFAULT_TCP_PORT
            return host to port
        }
    }
    val parts = address.split(":", limit = 2)
    val port = parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_TCP_PORT
    return parts[0] to port
}
