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
package org.meshtastic.core.common.util

actual fun String?.isValidAddress(): Boolean {
    val value = this?.trim()
    return when {
        value.isNullOrEmpty() -> false
        value == LOCALHOST -> true
        IPV4_PATTERN.matches(value) -> value.split('.').all { segment -> segment.toIntOrNull() in 0..MAX_IPV4_SEGMENT }
        value.contains(':') -> IPV6_PATTERN.matches(value)
        else -> DOMAIN_PATTERN.matches(value)
    }
}

private val IPV4_PATTERN = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}${'$'}")
private val DOMAIN_PATTERN = Regex("^(?=.{1,253}${'$'})(?:(?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,63}${'$'}")

// There is no `InetAddress`-equivalent IP-parsing API on wasmJs, so IPv6 validation here is a permissive,
// hand-rolled regex covering the standard RFC 4291 textual forms (including "::" compression and a trailing
// IPv4-mapped tail) instead of the JVM/Android actuals' real parser. Not exhaustive of every edge case a real
// parser accepts, but this is validating a user-typed device address, not doing security-critical parsing.
private val IPV6_PATTERN =
    Regex(
        "^(" +
            "([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|" +
            "([0-9a-fA-F]{1,4}:){1,7}:|" +
            "([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|" +
            "([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|" +
            "([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|" +
            "([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|" +
            "([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|" +
            "[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|" +
            ":((:[0-9a-fA-F]{1,4}){1,7}|:)|" +
            "::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9])|" +
            "([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9])" +
            ")${'$'}",
    )

private const val MAX_IPV4_SEGMENT = 255
private const val LOCALHOST = "localhost"
