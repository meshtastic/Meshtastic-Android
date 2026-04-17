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
 * Centralized metric formatting for display strings. Eliminates duplicated `formatString` patterns across Node,
 * NodeItem, and metric screens.
 *
 * All methods return locale-independent strings using [NumberFormatter] (dot decimal separator), which is intentional
 * for a mesh networking app where consistency matters.
 */
@Suppress("TooManyFunctions")
object MetricFormatter {

    fun temperature(celsius: Float, isFahrenheit: Boolean): String {
        val value = if (isFahrenheit) celsius * FAHRENHEIT_SCALE + FAHRENHEIT_OFFSET else celsius
        val unit = if (isFahrenheit) "°F" else "°C"
        return "${NumberFormatter.format(value, 1)}$unit"
    }

    fun voltage(volts: Float, decimalPlaces: Int = 2): String = "${NumberFormatter.format(volts, decimalPlaces)} V"

    fun current(milliAmps: Float, decimalPlaces: Int = 1): String =
        "${NumberFormatter.format(milliAmps, decimalPlaces)} mA"

    fun percent(value: Float, decimalPlaces: Int = 1): String = "${NumberFormatter.format(value, decimalPlaces)}%"

    fun percent(value: Int): String = "$value%"

    fun humidity(value: Float): String = percent(value, 0)

    fun pressure(hPa: Float, decimalPlaces: Int = 1): String = "${NumberFormatter.format(hPa, decimalPlaces)} hPa"

    fun snr(value: Float, decimalPlaces: Int = 1): String = "${NumberFormatter.format(value, decimalPlaces)} dB"

    fun rssi(value: Int): String = "$value dBm"

    fun windSpeed(metersPerSecond: Float, decimalPlaces: Int = 1): String =
        "${NumberFormatter.format(metersPerSecond, decimalPlaces)} m/s"

    fun rainfall(millimeters: Float, decimalPlaces: Int = 1): String =
        "${NumberFormatter.format(millimeters, decimalPlaces)} mm"
}

private const val FAHRENHEIT_SCALE = 1.8f
private const val FAHRENHEIT_OFFSET = 32
