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
 * Every number here reads in the user's locale; the unit symbols are fixed. Units that vary by locale (wind speed,
 * rainfall, weight) pick their symbol from [MeasureUnitKind] via [formatMeasure]; the universal ones (V, mA, dB, dBm,
 * hPa, %, °C) carry theirs inline. Either way the value goes through [NumberFormatter.format], so a German reader sees
 * "3,85 V" and "0,0°C".
 *
 * The one thing that stays locale-independent is anything another system parses — see
 * [NumberFormatter.formatInvariant].
 */
@Suppress("TooManyFunctions")
object MetricFormatter {

    /**
     * The degree symbol for the display unit, for callers that already hold a converted value and only need to label it
     * — chart axes, and the telemetry rows fed by an upstream conversion. Defined here so a symbol has one definition:
     * wind drifted between screens precisely because its unit was written out twice.
     */
    fun degreeSymbol(isFahrenheit: Boolean): String = if (isFahrenheit) FAHRENHEIT_SYMBOL else CELSIUS_SYMBOL

    fun temperature(celsius: Float, isFahrenheit: Boolean): String {
        val value = if (isFahrenheit) celsius * FAHRENHEIT_SCALE + FAHRENHEIT_OFFSET else celsius
        return "${NumberFormatter.format(value, 1)}${degreeSymbol(isFahrenheit)}"
    }

    fun voltage(volts: Float, decimalPlaces: Int = 2): String =
        "${NumberFormatter.format(volts, decimalPlaces)} $VOLT_SYMBOL"

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

    /**
     * Wind arrives from the sensor in m/s and is shown in the unit a reader expects for weather: km/h for metric, mph
     * for imperial. m/s is the meteorological observation standard, but public forecasts across most metric regions
     * quote km/h, so that is what the app displays. The charts convert the same way — see `chartValue` — so a reading
     * reads identically on the node card and on its graph.
     */
    fun windSpeed(metersPerSecond: Float, isImperial: Boolean, decimalPlaces: Int = 1): String = if (isImperial) {
        formatMeasure((metersPerSecond * MPH_PER_MPS).toDouble(), MeasureUnitKind.MILE_PER_HOUR, decimalPlaces)
    } else {
        formatMeasure((metersPerSecond * KPH_PER_MPS).toDouble(), MeasureUnitKind.KILOMETER_PER_HOUR, decimalPlaces)
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

/** Display symbols. Fixed rather than translated — see the note on [MetricFormatter]. */
const val CELSIUS_SYMBOL = "°C"
const val FAHRENHEIT_SYMBOL = "°F"
const val VOLT_SYMBOL = "V"

private const val FAHRENHEIT_SCALE = 1.8f
private const val FAHRENHEIT_OFFSET = 32
private const val MPH_PER_MPS = 2.23694f
private const val KPH_PER_MPS = 3.6f
private const val MM_PER_INCH = 25.4f
private const val LBS_PER_KG = 2.20462f
