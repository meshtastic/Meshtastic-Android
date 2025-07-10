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

package com.geeksville.mesh.model

import androidx.compose.ui.graphics.Color
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.ui.common.theme.InfantryBlue
import com.geeksville.mesh.ui.common.theme.Orange

enum class Environment(val color: Color) {
    TEMPERATURE(Color.Red) {
        override fun getValue(telemetry: Telemetry): Float {
            return telemetry.environmentMetrics.temperature
        }
    },
    HUMIDITY(InfantryBlue) {
        override fun getValue(telemetry: Telemetry): Float {
            return telemetry.environmentMetrics.relativeHumidity
        }
    },
    IAQ(Color.Green) {
        override fun getValue(telemetry: Telemetry): Float {
            return telemetry.environmentMetrics.iaq.toFloat()
        }
    },
    BAROMETRIC_PRESSURE(Orange) {
        override fun getValue(telemetry: Telemetry): Float {
            return telemetry.environmentMetrics.barometricPressure
        }
    };

    abstract fun getValue(telemetry: Telemetry): Float
}

/**
 * @param metrics the filtered [List]
 * @param shouldPlot a [List] the size of [Environment] used to determine if a metric
 * should be plotted
 * @param leftMinMax [Pair] with the min and max of the barometric pressure
 * @param rightMinMax [Pair] with the combined min and max of: the temperature, humidity, and IAQ
 * @param times [Pair] with the oldest and newest times in that order
 */
data class EnvironmentGraphingData(
    val metrics: List<Telemetry>,
    val shouldPlot: List<Boolean>,
    val leftMinMax: Pair<Float, Float> = Pair(0f, 0f),
    val rightMinMax: Pair<Float, Float> = Pair(0f, 0f),
    val times: Pair<Int, Int> = Pair(0, 0)
)

data class EnvironmentMetricsState(
    val environmentMetrics: List<Telemetry> = emptyList(),
) {
    fun hasEnvironmentMetrics() = environmentMetrics.isNotEmpty()

    /**
     * Filters [environmentMetrics] based on a [TimeFrame].
     *
     * @param timeFrame used to filter
     * @return [EnvironmentGraphingData]
     */
    @Suppress("LongMethod")
    fun environmentMetricsFiltered(timeFrame: TimeFrame): EnvironmentGraphingData {
        val oldestTime = timeFrame.calculateOldestTime()
        val telemetries = environmentMetrics.filter { it.time >= oldestTime }
        val shouldPlot = BooleanArray(Environment.entries.size) { false }
        if (telemetries.isEmpty()) {
            return EnvironmentGraphingData(metrics = telemetries, shouldPlot = shouldPlot.toList())
        }

        /* Grab the combined min and max for temp, humidity, and iaq. */
        val minValues = mutableListOf<Float>()
        val maxValues = mutableListOf<Float>()
        val (minTemp, maxTemp) = Pair(
            telemetries.minBy { it.environmentMetrics.temperature },
            telemetries.maxBy { it.environmentMetrics.temperature }
        )
        if (minTemp.environmentMetrics.temperature != 0f || maxTemp.environmentMetrics.temperature != 0f) {
            minValues.add(minTemp.environmentMetrics.temperature)
            maxValues.add(maxTemp.environmentMetrics.temperature)
            shouldPlot[Environment.TEMPERATURE.ordinal] = true
        }

        val (minHumidity, maxHumidity) = Pair(
            telemetries.minBy { it.environmentMetrics.relativeHumidity },
            telemetries.maxBy { it.environmentMetrics.relativeHumidity }
        )
        if (minHumidity.environmentMetrics.relativeHumidity != 0f ||
            maxHumidity.environmentMetrics.relativeHumidity != 0f) {
            minValues.add(minHumidity.environmentMetrics.relativeHumidity)
            maxValues.add(maxHumidity.environmentMetrics.relativeHumidity)
            shouldPlot[Environment.HUMIDITY.ordinal] = true
        }

        val (minIAQ, maxIAQ) = Pair(
            telemetries.minBy { it.environmentMetrics.iaq },
            telemetries.maxBy { it.environmentMetrics.iaq }
        )
        if (minIAQ.environmentMetrics.iaq != 0 || maxIAQ.environmentMetrics.iaq != 0) {
            minValues.add(minIAQ.environmentMetrics.iaq.toFloat())
            maxValues.add(maxIAQ.environmentMetrics.iaq.toFloat())
            shouldPlot[Environment.IAQ.ordinal] = true
        }

        val min = minValues.minOf { it }
        val max = maxValues.maxOf { it }

        val (minPressure, maxPressure) = Pair(
            telemetries.minBy { it.environmentMetrics.barometricPressure },
            telemetries.maxBy { it.environmentMetrics.barometricPressure }
        )
        if (minPressure.environmentMetrics.barometricPressure != 0.0F &&
            maxPressure.environmentMetrics.barometricPressure != 0.0F) {
            shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal] = true
        }
        val (oldest, newest) = Pair(
            telemetries.minBy { it.time },
            telemetries.maxBy { it.time }
        )

        return EnvironmentGraphingData(
            metrics = telemetries,
            shouldPlot = shouldPlot.toList(),
            leftMinMax = Pair(
                minPressure.environmentMetrics.barometricPressure,
                maxPressure.environmentMetrics.barometricPressure
            ),
            rightMinMax = Pair(min, max),
            times = Pair(oldest.time, newest.time)
        )
    }
}
