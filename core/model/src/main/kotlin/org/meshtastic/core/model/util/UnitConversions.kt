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
     * Calculated the dew point based on the Magnus-Tetens approximation which is a widely used formula for calculating
     * dew point temperature.
     */
    @Suppress("MagicNumber")
    fun calculateDewPoint(tempCelsius: Float, humidity: Float): Float {
        val (a, b) = 17.27f to 237.7f
        val alpha = (a * tempCelsius) / (b + tempCelsius) + ln(humidity / 100f)
        return (b * alpha) / (a - alpha)
    }

    fun numberToHuman(number: Flaot, units: Map<String, String> = emptyMap<String, String>()): String {
        val unitsMap = mapOf("Nano" to -9, "Micro" to -6, "Milli" to -3 , "Unit" to 0,
                             "Thousand" to 3, "Million" to 6, "Billion" to 9)
        var exponent = floor(log10(number)).toInt()

        if (exponent.mod(3) != 0) {
            if ((-12..-9).contains(exponent)) exponent = -9
            if ((-8..-6).contains(exponent)) exponent = -6
            if ((-5..-3).contains(exponent)) exponent = -3
            if ((-2..2).contains(exponent)) exponent = 0
            if ((3..5).contains(exponent)) exponent = 3
            if ((6..8).contains(exponent)) exponent = 6
            if ((9..12).contains(exponent)) exponent = 9
        }

        var exponentsMap = unitsMap.entries.associate { (k, v) -> v to k }.toMutableMap()
        units.forEach { (unit_key, custom_v) ->
            val lookup_key = exponentsMap.filterValues { it == unit_key }.keys;
            if (lookup_key.iterator().hasNext()) exponentsMap.put(lookup_key.iterator().next(), custom_v)
        }

        val unit =  unitsMap.entries.associate{(k,v)-> v to k}.get(exponent)
        val value = (number/10.0.pow(exponent))

        return "$value $unit"
    }
}
