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
@file:Suppress("MagicNumber")

package org.meshtastic.core.model.util

/**
 * [anonymize] for a TCP `host`, `host:port` or `[ipv6]:port`, except that a host which can only name a machine on the
 * user's own network is returned whole: loopback, RFC 1918, IPv4 and IPv6 link-local, IPv6 unique local, or an mDNS
 * `.local` name. A public address or DNS name, DDNS and MagicDNS included, can identify the user, so it is anonymized.
 */
fun String.anonymizePublicHost(): String = if (isLocalNetworkHost(hostOf(this))) this else anonymize()

private fun hostOf(address: String): String {
    val host =
        when {
            address.startsWith("[") -> address.substringAfter('[').substringBefore(']')
            address.count { it == ':' } == 1 -> address.substringBefore(':')
            else -> address
        }
    return host.substringBefore('%').trimEnd('.').lowercase()
}

private fun isLocalNetworkHost(host: String): Boolean =
    host == "localhost" || host.endsWith(".local") || isLocalIpv4(host) || isLocalIpv6(host)

private fun isLocalIpv4(host: String): Boolean {
    val parts = host.split('.')
    val octets = parts.mapNotNull { part -> part.toIntOrNull()?.takeIf { it in 0..255 } }
    if (parts.size != 4 || octets.size != 4) return false
    val (first, second) = octets
    return when (first) {
        127,
        10,
        -> true

        172 -> second in 16..31

        192 -> second == 168

        169 -> second == 254

        else -> false
    }
}

private fun isLocalIpv6(host: String): Boolean {
    val isLiteral = host.count { it == ':' } >= 2 && host.all { it == ':' || it.digitToIntOrNull(16) != null }
    val firstHextet = host.substringBefore(':').takeIf { it.length in 1..4 }?.toIntOrNull(16)
    // fe80::/10 link-local, fc00::/7 unique local.
    val isLocalRange = firstHextet != null && (firstHextet in 0xfe80..0xfebf || firstHextet in 0xfc00..0xfdff)
    return isLiteral && (host == "::1" || isLocalRange)
}
