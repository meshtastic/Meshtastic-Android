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
package org.meshtastic.feature.widget

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.util.UnitConversions.toTempString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cardinal_e
import org.meshtastic.core.resources.cardinal_n
import org.meshtastic.core.resources.cardinal_ne
import org.meshtastic.core.resources.cardinal_nw
import org.meshtastic.core.resources.cardinal_s
import org.meshtastic.core.resources.cardinal_se
import org.meshtastic.core.resources.cardinal_sw
import org.meshtastic.core.resources.cardinal_w
import org.meshtastic.core.resources.channel_1
import org.meshtastic.core.resources.channel_2
import org.meshtastic.core.resources.channel_3
import org.meshtastic.core.resources.channel_4
import org.meshtastic.core.resources.channel_5
import org.meshtastic.core.resources.channel_6
import org.meshtastic.core.resources.channel_7
import org.meshtastic.core.resources.channel_8
import org.meshtastic.core.resources.co2
import org.meshtastic.core.resources.co2_level_evacuate
import org.meshtastic.core.resources.co2_level_good
import org.meshtastic.core.resources.co2_level_poor
import org.meshtastic.core.resources.co2_level_stuffy
import org.meshtastic.core.resources.co2_level_unsafe
import org.meshtastic.core.resources.current
import org.meshtastic.core.resources.dew_point
import org.meshtastic.core.resources.gas_resistance
import org.meshtastic.core.resources.humidity
import org.meshtastic.core.resources.iaq
import org.meshtastic.core.resources.lux
import org.meshtastic.core.resources.micrograms_per_cubic_meter
import org.meshtastic.core.resources.pm10
import org.meshtastic.core.resources.pm1_0
import org.meshtastic.core.resources.pm2_5
import org.meshtastic.core.resources.ppm
import org.meshtastic.core.resources.pressure
import org.meshtastic.core.resources.pressure_high
import org.meshtastic.core.resources.pressure_low
import org.meshtastic.core.resources.radiation
import org.meshtastic.core.resources.rainfall_1h
import org.meshtastic.core.resources.soil_moisture
import org.meshtastic.core.resources.soil_temperature
import org.meshtastic.core.resources.temperature
import org.meshtastic.core.resources.voltage
import org.meshtastic.core.resources.weight
import org.meshtastic.core.resources.widget_telemetry_dew
import org.meshtastic.core.resources.widget_telemetry_gust
import org.meshtastic.core.resources.wind
import org.meshtastic.core.ui.component.Co2Severity
import org.meshtastic.core.ui.component.getIaq

/** Shown in place of a value the node has stopped sending; the same mark the app uses for a missing temperature. */
internal const val MISSING_VALUE = "--"

/**
 * At or below this, the pressure tile reads Low, matching Apple's pressure tile so a reading says the same on both
 * clients.
 */
private const val LOW_PRESSURE_HPA = 1009.144f

/**
 * Formats [reading] the way the node details tiles do: the same labels, locale units and precision
 * (meshtastic/design#53 and #99), with temperature and pressure as whole numbers and pressure always in hPa.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun telemetryTile(reading: TelemetryReading, state: TelemetryWidgetState.Showing): TelemetryTile {
    val snapshot = state.snapshot
    val env = snapshot.environment
    val aq = snapshot.airQuality
    val imperial = state.units == MeasurementSystem.IMPERIAL
    val fahrenheit = state.isFahrenheit
    val ugm3 = stringResource(Res.string.micrograms_per_cubic_meter)
    return when (reading) {
        TelemetryReading.Temperature ->
            tile(
                R.drawable.widget_ic_thermostat,
                Res.string.temperature,
                env?.temperature.finite()?.toTempString(fahrenheit),
            )

        TelemetryReading.Humidity ->
            tile(
                R.drawable.widget_ic_water_drop,
                Res.string.humidity,
                env?.relative_humidity.finite()?.let { "${NumberFormatter.format(it, 0)}%" },
                detail =
                snapshot.dewPoint()?.toTempString(fahrenheit)?.let {
                    stringResource(Res.string.widget_telemetry_dew, it)
                },
            )

        TelemetryReading.Pressure -> {
            val hpa = env?.barometric_pressure.finite()
            tile(
                R.drawable.widget_ic_compress,
                Res.string.pressure,
                hpa?.let { "${NumberFormatter.format(it, 0)} hPa" },
                detail =
                hpa?.let {
                    stringResource(
                        if (it <= LOW_PRESSURE_HPA) Res.string.pressure_low else Res.string.pressure_high,
                    )
                },
            )
        }

        TelemetryReading.DewPoint ->
            tile(R.drawable.widget_ic_dew_point, Res.string.dew_point, snapshot.dewPoint()?.toTempString(fahrenheit))

        TelemetryReading.GasResistance ->
            tile(
                R.drawable.widget_ic_blur_on,
                Res.string.gas_resistance,
                env?.gas_resistance.finite()?.let { "${NumberFormatter.format(it, 2)} MΩ" },
            )

        TelemetryReading.Iaq -> {
            val iaq = env?.iaq.present()
            val level = iaq?.let { getIaq(it) }
            tile(
                R.drawable.widget_ic_air,
                Res.string.iaq,
                iaq?.toString(),
                detail = level?.let { stringResource(it.label) },
                band = level?.color,
            )
        }

        TelemetryReading.Lux ->
            tile(
                R.drawable.widget_ic_light_mode,
                Res.string.lux,
                env?.lux.finite()?.let { "${NumberFormatter.format(it, 0)} lx" },
            )

        TelemetryReading.Wind -> {
            val direction = env?.wind_direction?.let { cardinal(it) }
            val gust =
                env?.wind_gust.finite()?.let {
                    stringResource(Res.string.widget_telemetry_gust, MetricFormatter.windSpeed(it, imperial))
                }
            tile(
                R.drawable.widget_ic_navigation,
                Res.string.wind,
                env?.wind_speed.finite()?.let { MetricFormatter.windSpeed(it, imperial) },
                detail = listOfNotNull(direction, gust).joinToString(" · ").ifEmpty { null },
            )
        }

        TelemetryReading.Rain ->
            tile(
                R.drawable.widget_ic_cloud,
                Res.string.rainfall_1h,
                env?.rainfall_1h.finite()?.let { MetricFormatter.rainfall(it, imperial) },
            )

        TelemetryReading.SoilTemperature ->
            tile(
                R.drawable.widget_ic_soil_temperature,
                Res.string.soil_temperature,
                env?.soil_temperature.finite()?.toTempString(fahrenheit),
            )

        TelemetryReading.SoilMoisture ->
            tile(
                R.drawable.widget_ic_soil_moisture,
                Res.string.soil_moisture,
                env?.soil_moisture.present()?.let { "$it%" },
            )

        TelemetryReading.Radiation ->
            tile(
                R.drawable.widget_ic_radioactive,
                Res.string.radiation,
                env?.radiation.finite()?.let { "${NumberFormatter.format(it, 1)} µR/h" },
            )

        TelemetryReading.Weight ->
            tile(
                R.drawable.widget_ic_scale,
                Res.string.weight,
                env?.weight.finite()?.let { MetricFormatter.weight(it, imperial) },
            )

        TelemetryReading.Pm1 -> tile(R.drawable.widget_ic_air, Res.string.pm1_0, aq?.pm10_standard?.let { "$it $ugm3" })

        TelemetryReading.Pm25 ->
            tile(R.drawable.widget_ic_air, Res.string.pm2_5, aq?.pm25_standard?.let { "$it $ugm3" })

        TelemetryReading.Pm10 ->
            tile(R.drawable.widget_ic_air, Res.string.pm10, aq?.pm100_standard?.let { "$it $ugm3" })

        TelemetryReading.Co2 -> {
            val co2 = aq?.co2
            val severity = co2?.let { Co2Severity.fromPpm(it) }
            tile(
                R.drawable.widget_ic_air,
                Res.string.co2,
                co2?.let { "$it ${stringResource(Res.string.ppm)}" },
                detail = severity?.let { stringResource(it.labelRes()) },
                band = severity?.color,
            )
        }

        is TelemetryReading.PowerVoltage ->
            TelemetryTile(
                icon = R.drawable.widget_ic_bolt,
                label = channelName(reading.channel, state.channelLabels) ?: stringResource(Res.string.voltage),
                value = snapshot.powerVoltage(reading.channel)?.let { MetricFormatter.voltage(it) },
            )

        is TelemetryReading.PowerCurrent ->
            TelemetryTile(
                icon = R.drawable.widget_ic_power,
                label = channelName(reading.channel, state.channelLabels) ?: stringResource(Res.string.current),
                value = snapshot.powerCurrent(reading.channel)?.let { MetricFormatter.current(it) },
            )
    }
}

@Composable
private fun tile(
    @DrawableRes icon: Int,
    label: StringResource,
    value: String?,
    detail: String? = null,
    band: Color? = null,
) = TelemetryTile(icon = icon, label = stringResource(label), value = value, detail = detail, band = band)

private val CHANNEL_NAMES =
    listOf(
        Res.string.channel_1,
        Res.string.channel_2,
        Res.string.channel_3,
        Res.string.channel_4,
        Res.string.channel_5,
        Res.string.channel_6,
        Res.string.channel_7,
        Res.string.channel_8,
    )

/** The channel as node details names it: the node's own label, or Channel N when it has none. */
@Composable
private fun channelName(channel: Int, labels: List<String>): String? =
    labels.getOrNull(channel)?.takeIf { it.isNotBlank() }
        ?: CHANNEL_NAMES.getOrNull(channel)?.let { stringResource(it) }

private val CARDINALS =
    listOf(
        Res.string.cardinal_n,
        Res.string.cardinal_ne,
        Res.string.cardinal_e,
        Res.string.cardinal_se,
        Res.string.cardinal_s,
        Res.string.cardinal_sw,
        Res.string.cardinal_w,
        Res.string.cardinal_nw,
    )

private const val DEGREES_PER_CARDINAL = 45
private const val FULL_CIRCLE = 360

/** The compass point the wind comes from, to the nearest of eight. */
@Composable
private fun cardinal(degrees: Int): String {
    val normalized = ((degrees % FULL_CIRCLE) + FULL_CIRCLE) % FULL_CIRCLE
    val index = ((normalized + DEGREES_PER_CARDINAL / 2) / DEGREES_PER_CARDINAL) % CARDINALS.size
    return stringResource(CARDINALS[index])
}

private fun Co2Severity.labelRes(): StringResource = when (this) {
    Co2Severity.GOOD -> Res.string.co2_level_good
    Co2Severity.STUFFY -> Res.string.co2_level_stuffy
    Co2Severity.POOR -> Res.string.co2_level_poor
    Co2Severity.UNSAFE -> Res.string.co2_level_unsafe
    Co2Severity.EVACUATE -> Res.string.co2_level_evacuate
}

/**
 * Splits "1,016 hPa" into the number and its unit at the first space, including the no-break spaces locale formatters
 * use. A value with no space, or whose first part holds no digit, stays whole.
 */
private val SPACES = setOf(' ', '\u00A0', '\u202F')

internal fun splitUnit(value: String): Pair<String, String?> {
    // A grouping space sits between digits ("1 016 hPa"); the unit's space is the first one not followed by a digit.
    val space =
        value.indices.firstOrNull { i ->
            value[i] in SPACES && value.getOrNull(i + 1)?.isDigit() != true
        } ?: -1
    val number = if (space > 0) value.substring(0, space) else return value to null
    return if (number.any { it.isDigit() }) number to value.substring(space + 1).trim() else value to null
}

/**
 * One tile as the widget draws it. [value] is null for a reading the node has stopped sending, which shows a dash.
 * [detail] is the tile's own second line, with a dot in [band] when the line names a band.
 */
internal data class TelemetryTile(
    @DrawableRes val icon: Int,
    val label: String,
    val value: String?,
    val detail: String? = null,
    val band: Color? = null,
)
