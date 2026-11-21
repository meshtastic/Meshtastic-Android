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
private val DEFAULT_PORTS = mapOf("ws" to 80, "wss" to 443)

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
 * in 1..65535 and no `user@` part. The scheme and host, apart from an IPv6 zone identifier, are lowercased and the
 * scheme's default port (80 for `ws`, 443 for `wss`) is dropped, so equal endpoints compare equal. An empty or `/` path
 * becomes [WEBSOCKET_API_PATH], so `ws://192.168.1.20` reaches firmware's endpoint; a query follows the path unchanged.
 */
fun normalizeWebSocketUrl(url: String): String? {
    val groups = WEBSOCKET_URL.matchEntire(url.trim())?.groupValues ?: return null
    val scheme = groups[SCHEME_GROUP].lowercase()
    // An IPv6 zone identifier names an interface, which is case-sensitive.
    val host =
        groups[HOST_GROUP].let { raw ->
            val zone = raw.indexOf('%').takeIf { it >= 0 && raw.startsWith("[") } ?: raw.length
            raw.substring(0, zone).lowercase() + raw.substring(zone)
        }
    val port = groups[PORT_GROUP].takeUnless { it.isNotEmpty() && it.toInt() == DEFAULT_PORTS[scheme] }.orEmpty()
    val path = groups[PATH_GROUP].takeUnless { it.isEmpty() || it == "/" } ?: WEBSOCKET_API_PATH
    val query = groups[QUERY_GROUP]
    return when {
        port.isEmpty() -> "$scheme://$host$path$query"
        port.toInt() in VALID_PORTS -> "$scheme://$host:$port$path$query"
        else -> null
    }
}
