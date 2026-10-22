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
package org.meshtastic.core.network.mqtt

/** The root topic rules the firmware and broker impose. */
object MqttRootTopic {
    /** The firmware's buffer: 31 UTF-8 bytes plus the terminator. */
    const val BYTE_CAP = 31

    enum class Problem {
        EMPTY,
        WHITESPACE,
        NON_ASCII,
        WILDCARD,
        LEADING_SLASH,
        TRAILING_SLASH,
        EMPTY_SEGMENT,
        ENDS_WITH_VERSION,
        TOO_LONG,
    }

    /** Whitespace trimmed at both ends, runs of "/" collapsed to one, one trailing "/" dropped. */
    fun sanitized(text: String): String {
        var result = text.trim()
        while (result.contains("//")) result = result.replace("//", "/")
        return result.removeSuffix("/")
    }

    /** First rule broken, in enum order; null when the radio accepts the root as typed. */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun problem(root: String): Problem? {
        if (root.isEmpty()) return Problem.EMPTY
        if (root.any { it.code <= SPACE || it.code == DEL }) return Problem.WHITESPACE
        if (root.any { it.code > TILDE }) return Problem.NON_ASCII
        if (root.contains('+') || root.contains('#')) return Problem.WILDCARD
        if (root.startsWith("/")) return Problem.LEADING_SLASH
        if (root.endsWith("/")) return Problem.TRAILING_SLASH
        if (root.contains("//")) return Problem.EMPTY_SEGMENT
        if (root.split('/').last() == "2") return Problem.ENDS_WITH_VERSION
        if (root.encodeToByteArray().size > BYTE_CAP) return Problem.TOO_LONG
        return null
    }

    /** Split on "/", each segment trimmed, empties dropped. */
    fun segments(root: String): List<String> = root.split('/').map { it.trim() }.filter { it.isNotEmpty() }

    /** Segment-wise, case-insensitive prefix: "msh/US/WA" prefixes "msh/US/WA/seattle" but not "msh/US/WAX". */
    fun hasPrefix(root: String, prefix: String): Boolean {
        val rootSegments = segments(root)
        val prefixSegments = segments(prefix)
        if (prefixSegments.size > rootSegments.size) return false
        return rootSegments.zip(prefixSegments).all { (a, b) -> a.equals(b, ignoreCase = true) }
    }

    fun rootsMatch(a: String, b: String): Boolean = a.equals(b, ignoreCase = true)

    private const val SPACE = 0x20
    private const val DEL = 0x7F
    private const val TILDE = 0x7E
}
