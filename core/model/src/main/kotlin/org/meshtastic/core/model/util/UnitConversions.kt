/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

object UnitConversions {
    @Suppress("MagicNumber")
    fun celsiusToFahrenheit(celsius: Float): Float = (celsius * 1.8F) + 32

    /** Formats temperature as a string with the unit suffix. */
    fun Float.toTempString(isFahrenheit: Boolean): String {
        if (this.isNaN()) return "--"

        val temp = if (isFahrenheit) celsiusToFahrenheit(this) else this
        val unit = if (isFahrenheit) "F" else "C"

        // Convoluted calculation due to edge case: rounding negative values.
        // We round the absolute value using roundToInt() (banker's rounding), then reapply the sign so values
        val absoluteTemp: Float = kotlin.math.abs(temp)
        val roundedAbsoluteTemp: Int = absoluteTemp.roundToInt()

        val isZero = roundedAbsoluteTemp == 0
        val isPositive = kotlin.math.sign(temp) > 0
        val sign: String = if (isPositive || isZero) "" else "-"

        return "$sign$roundedAbsoluteTemp°$unit"
    }

    /**
     * Calculates the dew point based on the Magnus-Tetens approximation which is a widely used formula for calculating
     * dew point temperature.
     */
    @Suppress("MagicNumber")
    fun calculateDewPoint(tempCelsius: Float, humidity: Float): Float {
        val (a, b) = 17.27f to 237.7f
        val alpha = (a * tempCelsius) / (b + tempCelsius) + ln(humidity / 100f)
        return (b * alpha) / (a - alpha)
    }

    /**
     * Converts numbers from milli to unit.
     *  examples:
     *      - 1000 milliamperes will be converted into 1 ampere,
     *      - 100 millimeters to 0.1 meters
     */
    @Suppress("MagicNumber")
    fun convertToBaseUnit(number: Float): Float {
        if (number <= 0) return 0f

        var exponent = floor(log10(number / 1000.0)).toInt()
        if (exponent.mod(3) != 0 && exponent in -11..11) exponent = (exponent / 3) * 3

        return number / 10f.pow(exponent)
    }
}
