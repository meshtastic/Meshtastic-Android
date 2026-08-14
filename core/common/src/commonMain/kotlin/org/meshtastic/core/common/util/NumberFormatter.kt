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

import kotlin.math.pow
import kotlin.math.roundToLong

/** Pure Kotlin number formatting utility — no expect/actual needed. */
object NumberFormatter {
    /** Formats a double value with the specified number of decimal places. */
    fun format(value: Double, decimalPlaces: Int): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        val factor = 10.0.pow(decimalPlaces)
        val rounded = (value * factor).roundToLong()
        return formatFixedPoint(rounded, decimalPlaces)
    }

    /** Formats a float value with the specified number of decimal places. */
    fun format(value: Float, decimalPlaces: Int): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        return format(value.toDouble(), decimalPlaces)
    }

    /**
     * Parses a user-entered decimal whose separator follows the keyboard locale — `48,21` on a comma locale, which
     * [String.toDoubleOrNull] rejects. A lone separator is always the decimal mark; grouping is only recognised when
     * more than one appears, in which case the last one is the decimal mark. Returns null for anything unparseable.
     */
    fun parseDecimalOrNull(text: String): Double? {
        val stripped = text.filterNot { it.isWhitespace() || it == ' ' || it == ' ' || it == '\'' }
        val lastSeparator = stripped.indexOfLast { it in DECIMAL_SEPARATORS }
        if (lastSeparator < 0) return stripped.toDoubleOrNull()
        val integerPart = stripped.take(lastSeparator).filterNot { it in DECIMAL_SEPARATORS }
        return "$integerPart.${stripped.substring(lastSeparator + 1)}".toDoubleOrNull()
    }

    private fun formatFixedPoint(scaledValue: Long, decimalPlaces: Int): String {
        if (decimalPlaces == 0) return scaledValue.toString()

        val isNegative = scaledValue < 0
        val abs = if (isNegative) -scaledValue else scaledValue
        val factor = 10.0.pow(decimalPlaces).toLong()
        val intPart = abs / factor
        val fracPart = abs % factor

        val sign = if (isNegative) "-" else ""
        return "$sign$intPart.${fracPart.toString().padStart(decimalPlaces, '0')}"
    }

    /** True when [char] is a decimal mark reachable from a `Decimal`/`DecimalSigned` keyboard. */
    fun isDecimalSeparator(char: Char): Boolean = char in DECIMAL_SEPARATORS

    /**
     * True when [text] could still become a number as the user types — an empty field, a lone sign, or a trailing
     * separator. A controlled field consults this after [parseDecimalOrNull] returns null, so transient input like `-`
     * or `-.` survives on the way to `-1.5` instead of snapping back to the last committed value.
     */
    fun isPartialDecimal(text: String): Boolean =
        text.removePrefix("-").all { it.isDigit() || isDecimalSeparator(it) || it.isWhitespace() || it == '\'' }

    /** Decimal marks reachable from a `Decimal`/`DecimalSigned` keyboard, across keyboard locales. */
    private val DECIMAL_SEPARATORS = setOf('.', ',', '٫', '、', '·')
}
