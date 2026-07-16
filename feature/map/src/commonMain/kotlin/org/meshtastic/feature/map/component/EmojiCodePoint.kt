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
package org.meshtastic.feature.map.component

/**
 * Renders a Unicode code point (e.g. a waypoint's emoji icon field) as a string — the multiplatform equivalent of the
 * JVM-only `String(Character.toChars(codePoint))`, encoding supplementary-plane code points as a surrogate pair.
 */
internal fun emojiCodePointToString(codePoint: Int): String {
    if (codePoint in Char.MIN_VALUE.code..Char.MAX_VALUE.code) return codePoint.toChar().toString()
    val offset = codePoint - SUPPLEMENTARY_PLANE_START
    val high = ((offset shr HALF_SHIFT) + HIGH_SURROGATE_START).toChar()
    val low = ((offset and LOW_MASK) + LOW_SURROGATE_START).toChar()
    return charArrayOf(high, low).concatToString()
}

private const val SUPPLEMENTARY_PLANE_START = 0x10000
private const val HALF_SHIFT = 10
private const val LOW_MASK = 0x3FF
private const val HIGH_SURROGATE_START = 0xD800
private const val LOW_SURROGATE_START = 0xDC00
