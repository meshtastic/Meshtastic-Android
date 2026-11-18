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

/** Where firmware serves the phone API over a WebSocket. */
const val WEBSOCKET_API_PATH = "/api/v1/ws"

private val VALID_PORTS = 1..65535

// Scheme, then a bracketed IPv6 literal or a host with no userinfo, an optional port and an optional path; no fragment.
private val WEBSOCKET_URL =
    Regex("""(wss?)://(\[[0-9A-Za-z:.%_]+\]|[^\s:\[\]/?#@]+)(?::(\d{1,5}))?(/[^\s#]*)?""", RegexOption.IGNORE_CASE)

/**
 * [url] as a WebSocket phone link connects to it, or null when it is not a `ws://` or `wss://` URL with a host, a port
 * in 1..65535 and no `user@` part. The scheme is lowercased and an empty or `/` path becomes [WEBSOCKET_API_PATH], so
 * `ws://192.168.1.20` reaches firmware's endpoint.
 */
fun normalizeWebSocketUrl(url: String): String? {
    val match = WEBSOCKET_URL.matchEntire(url.trim()) ?: return null
    val (scheme, host, port, path) = match.destructured
    if (port.isNotEmpty() && port.toInt() !in VALID_PORTS) return null
    val portPart = if (port.isEmpty()) "" else ":$port"
    val pathPart = path.takeUnless { it.isEmpty() || it == "/" } ?: WEBSOCKET_API_PATH
    return "${scheme.lowercase()}://$host$portPart$pathPart"
}
