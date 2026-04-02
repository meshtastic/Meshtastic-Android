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

/**
 * Apple (iOS) implementation of string formatting.
 *
 * Implements a subset of Java's `String.format()` patterns used in this codebase:
 * - `%s`, `%d` — positional or sequential string/integer
 * - `%N$s`, `%N$d` — explicit positional string/integer
 * - `%N$.Nf`, `%.Nf` — float with decimal precision
 * - `%%` — literal percent
 *
 * This avoids a dependency on `NSString.stringWithFormat` (which uses Obj-C `%@` conventions).
 */
actual fun formatString(pattern: String, vararg args: Any?): String = buildString {
    var i = 0
    var autoIndex = 0
    while (i < pattern.length) {
        if (pattern[i] != '%') {
            append(pattern[i])
            i++
            continue
        }
        i++ // skip '%'
        if (i >= pattern.length) break

        // Literal %%
        if (pattern[i] == '%') {
            append('%')
            i++
            continue
        }

        // Parse optional positional index (N$)
        var explicitIndex: Int? = null
        val startPos = i
        while (i < pattern.length && pattern[i].isDigit()) i++
        if (i < pattern.length && pattern[i] == '$' && i > startPos) {
            explicitIndex = pattern.substring(startPos, i).toInt() - 1 // 1-indexed → 0-indexed
            i++ // skip '$'
        } else {
            i = startPos // rewind — digits are part of width/precision, not positional index
        }

        // Parse optional flags/width (skip for now — not used in this codebase)

        // Parse optional precision (.N)
        var precision: Int? = null
        if (i < pattern.length && pattern[i] == '.') {
            i++ // skip '.'
            val precStart = i
            while (i < pattern.length && pattern[i].isDigit()) i++
            if (i > precStart) {
                precision = pattern.substring(precStart, i).toInt()
            }
        }

        // Parse conversion character
        if (i >= pattern.length) break
        val conversion = pattern[i]
        i++

        val argIndex = explicitIndex ?: autoIndex++
        val arg = args.getOrNull(argIndex)

        when (conversion) {
            's' -> append(arg?.toString() ?: "null")
            'd' -> append((arg as? Number)?.toLong()?.toString() ?: arg?.toString() ?: "0")
            'f' -> {
                val value = (arg as? Number)?.toDouble() ?: 0.0
                val places = precision ?: DEFAULT_FLOAT_PRECISION
                append(NumberFormatter.format(value, places))
            }
            else -> {
                // Unknown conversion — reproduce original token
                append('%')
                if (explicitIndex != null) append("${explicitIndex + 1}$")
                if (precision != null) append(".$precision")
                append(conversion)
            }
        }
    }
}

private const val DEFAULT_FLOAT_PRECISION = 6
