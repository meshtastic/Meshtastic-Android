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

/** Round Pushpin (📍) — the default waypoint icon, and the fallback for an unusable code point. */
const val PUSHPIN_CODE_POINT = 0x1F4CD

private const val MAX_CODE_POINT = 0x10FFFF
private const val MIN_SUPPLEMENTARY_CODE_POINT = 0x10000
private const val MIN_HIGH_SURROGATE = 0xD800
private const val MIN_LOW_SURROGATE = 0xDC00
private const val MAX_LOW_SURROGATE = 0xDFFF
private const val SURROGATE_SHIFT = 10
private const val LOW_SURROGATE_MASK = 0x3FF

/**
 * Whether this is a Unicode scalar value: within the code point range and not an unpaired surrogate.
 *
 * `Character.isValidCodePoint` is JVM-only and additionally accepts lone surrogates, so this does the range check
 * explicitly for `commonMain` callers.
 */
fun Int.isValidCodePoint(): Boolean = this in 0..MAX_CODE_POINT && this !in MIN_HIGH_SURROGATE..MAX_LOW_SURROGATE

/**
 * The icon to render for a waypoint, where `0` on the wire means "unset" and selects [PUSHPIN_CODE_POINT].
 *
 * Zero is a *valid* code point, so [toCodePointString] renders it as U+0000 rather than falling back. That matters
 * because the trust-boundary clamp in `MeshDataHandlerImpl` normalises an unrenderable icon to `0` — every display and
 * send path therefore has to read zero as the default rather than as a control character.
 */
fun Int.waypointIconOrDefault(): Int = if (this == 0) PUSHPIN_CODE_POINT else this

/**
 * Renders this Unicode code point as a string, substituting [fallback] when the value is not a usable scalar value.
 *
 * Code points that reach the UI may come from untrusted input, so they must not be handed to `Character.toChars`, which
 * throws `IllegalArgumentException` on anything out of range.
 */
fun Int.toCodePointString(fallback: Int = PUSHPIN_CODE_POINT): String {
    val codePoint =
        when {
            isValidCodePoint() -> this
            fallback.isValidCodePoint() -> fallback
            else -> PUSHPIN_CODE_POINT
        }
    if (codePoint < MIN_SUPPLEMENTARY_CODE_POINT) return codePoint.toChar().toString()
    val offset = codePoint - MIN_SUPPLEMENTARY_CODE_POINT
    return charArrayOf(
        (MIN_HIGH_SURROGATE + (offset ushr SURROGATE_SHIFT)).toChar(),
        (MIN_LOW_SURROGATE + (offset and LOW_SURROGATE_MASK)).toChar(),
    )
        .concatToString()
}
