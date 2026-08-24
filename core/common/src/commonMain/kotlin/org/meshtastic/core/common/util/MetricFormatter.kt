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
 * Everything here reads in the user's locale. Units that vary by locale (wind speed, rainfall, weight) go through
 * [formatMeasure], so the value, the symbol and the spacing between them all come from CLDR; the universal units (V,
 * mA, dB, dBm, hPa, %, °C) keep their fixed symbol but format the number via [NumberFormatter.format], so a German
 * reader sees "3,85 V" and "0,0°C".
 *
 * The one thing that stays locale-independent is anything another system parses — see
 * [NumberFormatter.formatInvariant].
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

    /**
     * Formats a signal-to-noise ratio, or [UNKNOWN_VALUE] when the packet carried no measurement. 0 dB is a legitimate
     * reading, so it must never stand in for a missing one.
     */
    fun snr(value: Float?, decimalPlaces: Int = 1): String =
        if (value == null) UNKNOWN_VALUE else "${NumberFormatter.format(value, decimalPlaces)} dB"

    /**
     * Formats a received signal strength, or [UNKNOWN_VALUE] when the radio reported none. 0 dBm is a legitimate
     * reading on some radios, so it must never stand in for a missing one.
     */
    fun rssi(value: Int?): String = if (value == null) UNKNOWN_VALUE else "$value dBm"

    fun windSpeed(metersPerSecond: Float, isImperial: Boolean, decimalPlaces: Int = 1): String = if (isImperial) {
        formatMeasure((metersPerSecond * MPH_PER_MPS).toDouble(), MeasureUnitKind.MILE_PER_HOUR, decimalPlaces)
    } else {
        formatMeasure(metersPerSecond.toDouble(), MeasureUnitKind.METER_PER_SECOND, decimalPlaces)
    }

    fun rainfall(millimeters: Float, isImperial: Boolean, decimalPlaces: Int = 1): String = if (isImperial) {
        formatMeasure((millimeters / MM_PER_INCH).toDouble(), MeasureUnitKind.INCH, decimalPlaces)
    } else {
        formatMeasure(millimeters.toDouble(), MeasureUnitKind.MILLIMETER, decimalPlaces)
    }

    fun weight(kilograms: Float, isImperial: Boolean, decimalPlaces: Int = 2): String = if (isImperial) {
        formatMeasure((kilograms * LBS_PER_KG).toDouble(), MeasureUnitKind.POUND, decimalPlaces)
    } else {
        formatMeasure(kilograms.toDouble(), MeasureUnitKind.KILOGRAM, decimalPlaces)
    }
}

/** Shown in place of a metric the radio did not report. A symbol, so it needs no translation. */
private const val UNKNOWN_VALUE = "—"

private const val FAHRENHEIT_SCALE = 1.8f
private const val FAHRENHEIT_OFFSET = 32
private const val MPH_PER_MPS = 2.23694f
private const val MM_PER_INCH = 25.4f
private const val LBS_PER_KG = 2.20462f
