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

/** Pure Kotlin URL encoding utility — no expect/actual needed. */
object UrlUtils {
    /**
     * Percent-encodes a string for use in a URL query parameter (RFC 3986). Unreserved characters (A-Z, a-z, 0-9, `-`,
     * `_`, `.`, `~`) are not encoded. Spaces are encoded as `%20` (not `+`).
     */
    @Suppress("MagicNumber")
    fun encode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val char = byte.toInt().toChar()
            if (char.isUnreserved()) {
                append(char)
            } else {
                append('%')
                append(HEX_DIGITS[(byte.toInt() shr 4) and 0x0F])
                append(HEX_DIGITS[byte.toInt() and 0x0F])
            }
        }
    }

    private fun Char.isUnreserved(): Boolean = this in 'A'..'Z' ||
        this in 'a'..'z' ||
        this in '0'..'9' ||
        this == '-' ||
        this == '_' ||
        this == '.' ||
        this == '~'

    private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
}
