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
package org.meshtastic.feature.node.metrics

import androidx.compose.ui.graphics.Color
import org.meshtastic.core.model.util.UnitConversions
import org.meshtastic.core.ui.theme.GraphColors.Blue
import org.meshtastic.core.ui.theme.GraphColors.Cyan
import org.meshtastic.core.ui.theme.GraphColors.Gold
import org.meshtastic.core.ui.theme.GraphColors.Green
import org.meshtastic.core.ui.theme.GraphColors.InfantryBlue
import org.meshtastic.core.ui.theme.GraphColors.Orange
import org.meshtastic.core.ui.theme.GraphColors.Pink
import org.meshtastic.core.ui.theme.GraphColors.Purple
import org.meshtastic.core.ui.theme.GraphColors.Red
import org.meshtastic.proto.Telemetry

@Suppress("MagicNumber")
enum class Environment(val color: Color) {
    TEMPERATURE(Red) {
        override fun getValue(telemetry: Telemetry) = telemetry.environment_metrics?.temperature
    },
    HUMIDITY(Blue) {
        override fun getValue(telemetry: Telemetry) = telemetry.environment_metrics?.relative_humidity
    },
    SOIL_TEMPERATURE(Pink) {
        override fun getValue(telemetry: Telemetry) = telemetry.environment_metrics?.soil_temperature
    },
    SOIL_MOISTURE(Purple) {
        override fun getValue(telemetry: Telemetry) =
            telemetry.environment_metrics?.soil_moisture?.takeIf { it != Int.MIN_VALUE }?.toFloat()
    },
    BAROMETRIC_PRESSURE(Green) {
        override fun getValue(telemetry: Telemetry) = telemetry.environment_metrics?.barometric_pressure
    },
    GAS_RESISTANCE(InfantryBlue) {
        override fun getValue(telemetry: Telemetry) = telemetry.environment_metrics?.gas_resistance
    },
    IAQ(Cyan) {
        override fun getValue(telemetry: Telemetry) =
            telemetry.environment_metrics?.iaq?.takeIf { it != Int.MIN_VALUE }?.toFloat()
    },
    LUX(Gold) {
        override fun getValue(telemetry: Telemetry) = telemetry.environment_metrics?.lux
    },
    UV_LUX(Orange) {
        override fun getValue(telemetry: Telemetry) = telemetry.environment_metrics?.uv_lux
    }, ;

    abstract fun getValue(telemetry: Telemetry): Float?
}

/**
 * @param metrics the [List] of [Telemetry]
 * @param shouldPlot a [List] the size of [Environment] used to determine if a metric should be plotted
 * @param leftMinMax [Pair] with the min and max of the barometric pressure
 * @param rightMinMax [Pair] with the combined min and max of: the temperature, humidity, and IAQ
 * @param times [Pair] with the oldest and newest times in that order
 */
data class EnvironmentGraphingData(
    val metrics: List<Telemetry>,
    val shouldPlot: List<Boolean>,
    val leftMinMax: Pair<Float, Float> = Pair(0f, 0f),
    val rightMinMax: Pair<Float, Float> = Pair(0f, 0f),
    val times: Pair<Int, Int> = Pair(0, 0),
)

data class EnvironmentMetricsState(val environmentMetrics: List<Telemetry> = emptyList()) {
    fun hasEnvironmentMetrics() = environmentMetrics.isNotEmpty()

    /**
     * Prepares [environmentMetrics] for graphing.
     *
     * @return [EnvironmentGraphingData]
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
    fun environmentMetricsForGraphing(useFahrenheit: Boolean = false): EnvironmentGraphingData {
        val telemetries = environmentMetrics
        val shouldPlot = BooleanArray(Environment.entries.size) { false }
        if (telemetries.isEmpty()) {
            return EnvironmentGraphingData(metrics = telemetries, shouldPlot = shouldPlot.toList())
        }

        /* Grab the combined min and max for temp, humidity, soil_Temperature, soilMoisture and iaq. */
        val minValues = mutableListOf<Float>()
        val maxValues = mutableListOf<Float>()

        // Temperature
        val temperatures = telemetries.mapNotNull { it.environment_metrics?.temperature?.takeIf { !it.isNaN() } }
        if (temperatures.isNotEmpty()) {
            var minTempValue = temperatures.minOf { it }
            var maxTempValue = temperatures.maxOf { it }
            if (useFahrenheit) {
                minTempValue = UnitConversions.celsiusToFahrenheit(minTempValue)
                maxTempValue = UnitConversions.celsiusToFahrenheit(maxTempValue)
            }
            minValues.add(minTempValue)
            maxValues.add(maxTempValue)
            shouldPlot[Environment.TEMPERATURE.ordinal] = true
        }

        // Relative Humidity
        val humidities =
            telemetries.mapNotNull { it.environment_metrics?.relative_humidity?.takeIf { !it.isNaN() && it != 0.0f } }
        if (humidities.isNotEmpty()) {
            minValues.add(humidities.minOf { it })
            maxValues.add(humidities.maxOf { it })
            shouldPlot[Environment.HUMIDITY.ordinal] = true
        }

        // Soil Temperature
        val soilTemperatures =
            telemetries.mapNotNull { it.environment_metrics?.soil_temperature?.takeIf { !it.isNaN() } }
        if (soilTemperatures.isNotEmpty()) {
            var minSoilTemperatureValue = soilTemperatures.minOf { it }
            var maxSoilTemperatureValue = soilTemperatures.maxOf { it }
            if (useFahrenheit) {
                minSoilTemperatureValue = UnitConversions.celsiusToFahrenheit(minSoilTemperatureValue)
                maxSoilTemperatureValue = UnitConversions.celsiusToFahrenheit(maxSoilTemperatureValue)
            }
            minValues.add(minSoilTemperatureValue)
            maxValues.add(maxSoilTemperatureValue)
            shouldPlot[Environment.SOIL_TEMPERATURE.ordinal] = true
        }

        // Soil Moisture
        val soilMoistures = telemetries.mapNotNull {
            it.environment_metrics?.soil_moisture?.takeIf { it != Int.MIN_VALUE }
        }
        if (soilMoistures.isNotEmpty()) {
            minValues.add(soilMoistures.minOf { it.toFloat() })
            maxValues.add(soilMoistures.maxOf { it.toFloat() })
            shouldPlot[Environment.SOIL_MOISTURE.ordinal] = true
        }

        // IAQ
        val iaqs = telemetries.mapNotNull {
            it.environment_metrics?.iaq?.takeIf { it != Int.MIN_VALUE }
        }
        if (iaqs.isNotEmpty()) {
            minValues.add(iaqs.minOf { it.toFloat() })
            maxValues.add(iaqs.maxOf { it.toFloat() })
            shouldPlot[Environment.IAQ.ordinal] = true
        }

        // Barometric Pressure
        val pressures = telemetries.mapNotNull { it.environment_metrics?.barometric_pressure?.takeIf { !it.isNaN() } }
        var minPressureValue = 0f
        var maxPressureValue = 0f
        if (pressures.isNotEmpty()) {
            minPressureValue = pressures.minOf { it }
            maxPressureValue = pressures.maxOf { it }
            shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal] = true
        }

        // Lux
        val luxValues = telemetries.mapNotNull { it.environment_metrics?.lux?.takeIf { !it.isNaN() } }
        if (luxValues.isNotEmpty()) {
            minValues.add(luxValues.minOf { it })
            maxValues.add(luxValues.maxOf { it })
            shouldPlot[Environment.LUX.ordinal] = true
        }

        // UVLux
        val uvLuxValues = telemetries.mapNotNull { it.environment_metrics?.uv_lux?.takeIf { !it.isNaN() } }
        if (uvLuxValues.isNotEmpty()) {
            minValues.add(uvLuxValues.minOf { it })
            maxValues.add(uvLuxValues.maxOf { it })
            shouldPlot[Environment.UV_LUX.ordinal] = true
        }

        val min = if (minValues.isEmpty()) 0f else minValues.minOf { it }
        val max = if (maxValues.isEmpty()) 1f else maxValues.maxOf { it }

        val oldest = telemetries.minBy { it.time }
        val newest = telemetries.maxBy { it.time }

        return EnvironmentGraphingData(
            metrics = telemetries,
            shouldPlot = shouldPlot.toList(),
            leftMinMax = Pair(minPressureValue, maxPressureValue),
            rightMinMax = Pair(min, max),
            times = Pair(oldest.time, newest.time),
        )
    }
}
