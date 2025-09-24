/*
 * Copyright (c) 2025 Meshtastic LLC
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

import kotlin.math.ln

object UnitConversions {

    @Suppress("MagicNumber")
    fun celsiusToFahrenheit(celsius: Float): Float = (celsius * 1.8F) + 32

    fun Float.toTempString(isFahrenheit: Boolean) = if (isFahrenheit) {
        val fahrenheit = celsiusToFahrenheit(this)
        "%.0f°F".format(fahrenheit)
    } else {
        "%.0f°C".format(this)
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
}
