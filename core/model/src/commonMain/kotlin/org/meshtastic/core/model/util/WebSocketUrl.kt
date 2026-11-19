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

// Scheme, then a bracketed IPv6 literal or a host with no userinfo, an optional port, an optional path and an optional
// query; no fragment.
private val WEBSOCKET_URL =
    Regex(
        """(wss?)://(\[[0-9A-Za-z:.%_]+\]|[^\s:\[\]/?#@]+)(?::(\d{1,5}))?(/[^\s?#]*)?(\?[^\s#]*)?""",
        RegexOption.IGNORE_CASE,
    )
private const val SCHEME_GROUP = 1
private const val HOST_GROUP = 2
private const val PORT_GROUP = 3
private const val PATH_GROUP = 4
private const val QUERY_GROUP = 5

/**
 * [url] as a WebSocket phone link connects to it, or null when it is not a `ws://` or `wss://` URL with a host, a port
 * in 1..65535 and no `user@` part. The scheme is lowercased and an empty or `/` path becomes [WEBSOCKET_API_PATH], so
 * `ws://192.168.1.20` reaches firmware's endpoint; a query follows the path unchanged.
 */
fun normalizeWebSocketUrl(url: String): String? {
    val groups = WEBSOCKET_URL.matchEntire(url.trim())?.groupValues ?: return null
    val scheme = groups[SCHEME_GROUP].lowercase()
    val host = groups[HOST_GROUP]
    val port = groups[PORT_GROUP]
    val path = groups[PATH_GROUP].takeUnless { it.isEmpty() || it == "/" } ?: WEBSOCKET_API_PATH
    val query = groups[QUERY_GROUP]
    return when {
        port.isEmpty() -> "$scheme://$host$path$query"
        port.toInt() in VALID_PORTS -> "$scheme://$host:$port$path$query"
        else -> null
    }
}
