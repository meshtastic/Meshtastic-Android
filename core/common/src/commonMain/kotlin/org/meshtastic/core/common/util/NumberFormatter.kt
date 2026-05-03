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
        val factor = 10.0.pow(decimalPlaces)
        val rounded = (value * factor).roundToLong()
        return formatFixedPoint(rounded, decimalPlaces)
    }

    /** Formats a float value with the specified number of decimal places. */
    fun format(value: Float, decimalPlaces: Int): String = format(value.toDouble(), decimalPlaces)

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
}
