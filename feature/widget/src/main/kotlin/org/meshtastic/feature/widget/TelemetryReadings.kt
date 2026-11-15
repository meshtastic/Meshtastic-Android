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

import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.UnitConversions
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.PowerMetrics
import org.meshtastic.proto.Telemetry

/**
 * The node's newest telemetry: the values from the node DB, which always holds the latest packet of each group, and the
 * time from the newest stored log row that carries the same readings. [history] is newest first, as the log repository
 * returns it; with logging off it is empty and the snapshot has no time.
 */
internal fun telemetrySnapshot(node: Node, history: List<Telemetry>): TelemetrySnapshot {
    val live =
        TelemetrySnapshot(
            environment = node.environmentMetrics.takeIf { node.hasEnvironmentMetrics },
            airQuality = node.airQualityMetrics.takeIf { node.hasAirQualityMetrics },
            power = node.powerMetrics.takeIf { node.hasPowerMetrics },
        )

    // A log row dates a group only when it holds the node DB's values, so a log that has fallen behind (logging
    // turned off, or the row not yet written) never dates newer values with an older time.
    fun timeOf(group: TelemetryGroup): Int? = history
        .firstOrNull { group.carriedBy(it) }
        ?.let { row ->
            val logged = TelemetrySnapshot(row.environment_metrics, row.air_quality_metrics, row.power_metrics)
            row.time.takeIf { time -> time > 0 && group.readings.all { logged.value(it) == live.value(it) } }
        }
    val shown = TelemetryGroup.entries.filter { group -> group.readings.any { live.hasValue(it) } }
    val times = shown.map(::timeOf)
    // One undated group may be the newest reading, so an older group's time would wrongly label it old.
    return live.copy(time = if (times.any { it == null }) null else times.filterNotNull().maxOrNull())
}

/** Every reading any of [history]'s rows carries, so a reading the node has stopped sending keeps its tile. */
internal fun everReported(history: List<Telemetry>): Set<TelemetryReading> = history
    .flatMap { row ->
        TelemetrySnapshot(row.environment_metrics, row.air_quality_metrics, row.power_metrics).reported
    }
    .toSet()

/**
 * A reading the Telemetry widget can show, in the order meshtastic/design#179 lists them. [id] is what an instance
 * stores in its hidden and seen sets, so it never changes once shipped.
 */
internal sealed class TelemetryReading(val id: String) {
    data object Temperature : TelemetryReading("temperature")

    data object Humidity : TelemetryReading("humidity")

    data object Pressure : TelemetryReading("pressure")

    data object DewPoint : TelemetryReading("dew_point")

    data object GasResistance : TelemetryReading("gas_resistance")

    data object Iaq : TelemetryReading("iaq")

    data object Lux : TelemetryReading("lux")

    data object Wind : TelemetryReading("wind")

    data object Rain : TelemetryReading("rain")

    data object SoilTemperature : TelemetryReading("soil_temperature")

    data object SoilMoisture : TelemetryReading("soil_moisture")

    data object Radiation : TelemetryReading("radiation")

    data object Weight : TelemetryReading("weight")

    data object Pm1 : TelemetryReading("pm1")

    data object Pm25 : TelemetryReading("pm25")

    data object Pm10 : TelemetryReading("pm10")

    data object Co2 : TelemetryReading("co2")

    /** [channel] counts from zero. */
    data class PowerVoltage(val channel: Int) : TelemetryReading("power${channel + 1}_voltage")

    /** [channel] counts from zero. */
    data class PowerCurrent(val channel: Int) : TelemetryReading("power${channel + 1}_current")

    companion object {
        const val POWER_CHANNELS = 8

        // Lazy: the subclasses extend this class, so touching one first would otherwise build these lists while its
        // own instance is still null.
        val environment: List<TelemetryReading> by lazy {
            listOf(
                Temperature,
                Humidity,
                Pressure,
                DewPoint,
                GasResistance,
                Iaq,
                Lux,
                Wind,
                Rain,
                SoilTemperature,
                SoilMoisture,
                Radiation,
                Weight,
            )
        }
        val airQuality: List<TelemetryReading> by lazy { listOf(Pm1, Pm25, Pm10, Co2) }
        val power: List<TelemetryReading> by lazy {
            (0 until POWER_CHANNELS).flatMap { listOf(PowerVoltage(it), PowerCurrent(it)) }
        }

        /** Every reading in widget order: environment, air quality, then voltage and current for each channel. */
        val all: List<TelemetryReading> by lazy { environment + airQuality + power }

        private val byId by lazy { all.associateBy { it.id } }

        fun fromId(id: String): TelemetryReading? = byId[id]
    }
}

private enum class TelemetryGroup(val readings: List<TelemetryReading>, val carriedBy: (Telemetry) -> Boolean) {
    ENVIRONMENT(TelemetryReading.environment, { it.environment_metrics != null }),
    AIR_QUALITY(TelemetryReading.airQuality, { it.air_quality_metrics != null }),
    POWER(TelemetryReading.power, { it.power_metrics != null }),
}

/**
 * A node's newest environment, air quality and power metrics. A missing value was not in the reading; a reported zero
 * stays zero. [time] is when the newest of the three arrived, in epoch seconds, or null when no stored log dates it.
 */
internal data class TelemetrySnapshot(
    val environment: EnvironmentMetrics? = null,
    val airQuality: AirQualityMetrics? = null,
    val power: PowerMetrics? = null,
    val time: Int? = null,
) {
    /** The readings this snapshot has a value for, in widget order. */
    val reported: List<TelemetryReading>
        get() = TelemetryReading.all.filter { hasValue(it) }

    fun hasValue(reading: TelemetryReading): Boolean = value(reading) != null

    /**
     * The raw value of [reading] as the radio sent it, or null when absent. The stored log marks absent environment
     * readings with NaN and [Int.MIN_VALUE] (`withSentinelsForAbsentReadings`), so those read as absent too.
     */
    @Suppress("CyclomaticComplexMethod")
    fun value(reading: TelemetryReading): Number? {
        val env = environment
        val aq = airQuality
        return when (reading) {
            TelemetryReading.Temperature -> env?.temperature.finite()
            TelemetryReading.Humidity -> env?.relative_humidity.finite()
            TelemetryReading.Pressure -> env?.barometric_pressure.finite()
            TelemetryReading.DewPoint -> dewPoint()
            TelemetryReading.GasResistance -> env?.gas_resistance.finite()
            TelemetryReading.Iaq -> env?.iaq.present()
            TelemetryReading.Lux -> env?.lux.finite()
            TelemetryReading.Wind -> env?.wind_speed.finite()
            TelemetryReading.Rain -> env?.rainfall_1h.finite()
            TelemetryReading.SoilTemperature -> env?.soil_temperature.finite()
            TelemetryReading.SoilMoisture -> env?.soil_moisture.present()
            TelemetryReading.Radiation -> env?.radiation.finite()
            TelemetryReading.Weight -> env?.weight.finite()
            TelemetryReading.Pm1 -> aq?.pm10_standard
            TelemetryReading.Pm25 -> aq?.pm25_standard
            TelemetryReading.Pm10 -> aq?.pm100_standard
            TelemetryReading.Co2 -> aq?.co2
            is TelemetryReading.PowerVoltage -> powerVoltage(reading.channel)
            is TelemetryReading.PowerCurrent -> powerCurrent(reading.channel)
        }
    }

    /** The dew point in °C, from the same temperature and humidity the tiles show. */
    fun dewPoint(): Float? {
        val temperature = environment?.temperature.finite()
        val humidity = environment?.relative_humidity.finite()?.takeIf { it > 0f }
        return if (temperature == null || humidity == null) {
            null
        } else {
            UnitConversions.calculateDewPoint(temperature, humidity).finite()
        }
    }

    // PowerMetrics ch4 to ch8 are deprecated in the proto but still sent by older firmware, as node details reads them.
    @Suppress("DEPRECATION", "MagicNumber")
    fun powerVoltage(channel: Int): Float? = power
        ?.let { pm ->
            when (channel) {
                0 -> pm.ch1_voltage
                1 -> pm.ch2_voltage
                2 -> pm.ch3_voltage
                3 -> pm.ch4_voltage
                4 -> pm.ch5_voltage
                5 -> pm.ch6_voltage
                6 -> pm.ch7_voltage
                7 -> pm.ch8_voltage
                else -> null
            }
        }
        .finite()

    @Suppress("DEPRECATION", "MagicNumber")
    fun powerCurrent(channel: Int): Float? = power
        ?.let { pm ->
            when (channel) {
                0 -> pm.ch1_current
                1 -> pm.ch2_current
                2 -> pm.ch3_current
                3 -> pm.ch4_current
                4 -> pm.ch5_current
                5 -> pm.ch6_current
                6 -> pm.ch7_current
                7 -> pm.ch8_current
                else -> null
            }
        }
        .finite()
}

internal fun Float?.finite(): Float? = this?.takeIf { it.isFinite() }

internal fun Int?.present(): Int? = this?.takeIf { it != Int.MIN_VALUE }
