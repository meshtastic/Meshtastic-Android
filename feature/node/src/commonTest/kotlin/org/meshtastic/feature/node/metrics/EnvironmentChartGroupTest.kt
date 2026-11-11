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
package org.meshtastic.feature.node.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnvironmentChartGroupTest {

    @Test
    fun temperatureHumidityAndLuxLandOnSeparateCharts() {
        val groups =
            groupEnvironmentMetrics(
                listOf(Environment.TEMPERATURE, Environment.HUMIDITY, Environment.LUX, Environment.BAROMETRIC_PRESSURE),
            )

        assertEquals(
            listOf(
                EnvironmentChartGroup.TEMPERATURE,
                EnvironmentChartGroup.HUMIDITY,
                EnvironmentChartGroup.PRESSURE,
                EnvironmentChartGroup.LUX,
            ),
            groups.map { it.first },
        )
        assertTrue(groups.all { it.second.size == 1 })
    }

    @Test
    fun metricsOfOneUnitShareAChart() {
        val groups =
            groupEnvironmentMetrics(
                listOf(
                    Environment.TEMPERATURE,
                    Environment.SOIL_TEMPERATURE,
                    Environment.ONE_WIRE_TEMP_3,
                    Environment.HUMIDITY,
                    Environment.SOIL_MOISTURE,
                    Environment.ADC_VOLTAGE_1,
                    Environment.ADC_VOLTAGE_8,
                ),
            )
                .toMap()

        assertEquals(
            listOf(Environment.TEMPERATURE, Environment.SOIL_TEMPERATURE, Environment.ONE_WIRE_TEMP_3),
            groups[EnvironmentChartGroup.TEMPERATURE],
        )
        assertEquals(
            listOf(Environment.HUMIDITY, Environment.SOIL_MOISTURE),
            groups[EnvironmentChartGroup.HUMIDITY],
        )
        assertEquals(
            listOf(Environment.ADC_VOLTAGE_1, Environment.ADC_VOLTAGE_8),
            groups[EnvironmentChartGroup.VOLTAGE],
        )
    }

    @Test
    fun windAndRadiationAreOffTheTemperatureChart() {
        val groups =
            groupEnvironmentMetrics(listOf(Environment.TEMPERATURE, Environment.WIND_SPEED, Environment.RADIATION))

        assertEquals(3, groups.size)
        assertEquals(
            listOf(Environment.TEMPERATURE),
            groups.first { it.first == EnvironmentChartGroup.TEMPERATURE }.second,
        )
    }

    @Test
    fun aSeriesWithNoDataGetsNoChart() {
        val groups = groupEnvironmentMetrics(listOf(Environment.TEMPERATURE))

        assertEquals(listOf(EnvironmentChartGroup.TEMPERATURE), groups.map { it.first })
        assertTrue(groupEnvironmentMetrics(emptyList()).isEmpty())
    }

    @Test
    fun everyMetricBelongsToAGroup() {
        Environment.entries.forEach { it.chartGroup }
    }

    @Test
    fun eachGroupHoldsExactlyTheMetricsOfItsUnit() {
        val groups =
            groupEnvironmentMetrics(Environment.entries).associate { (group, metrics) -> group to metrics.toSet() }

        val expected =
            mapOf(
                EnvironmentChartGroup.TEMPERATURE to
                    (Environment.oneWireTemperatures + Environment.TEMPERATURE + Environment.SOIL_TEMPERATURE).toSet(),
                EnvironmentChartGroup.HUMIDITY to setOf(Environment.HUMIDITY, Environment.SOIL_MOISTURE),
                EnvironmentChartGroup.PRESSURE to setOf(Environment.BAROMETRIC_PRESSURE),
                EnvironmentChartGroup.IAQ to setOf(Environment.IAQ),
                EnvironmentChartGroup.GAS_RESISTANCE to setOf(Environment.GAS_RESISTANCE),
                EnvironmentChartGroup.LUX to setOf(Environment.LUX),
                EnvironmentChartGroup.UV_LUX to setOf(Environment.UV_LUX),
                EnvironmentChartGroup.WIND to setOf(Environment.WIND_SPEED),
                EnvironmentChartGroup.RADIATION to setOf(Environment.RADIATION),
                EnvironmentChartGroup.VOLTAGE to Environment.adcVoltages.toSet(),
            )
        assertEquals(expected, groups)
    }

    @Test
    fun temperatureAxisIsPaddedAroundTheDataAndNotZeroBased() {
        val (min, max) = environmentAxisRange(EnvironmentChartGroup.TEMPERATURE, 10.0, 30.0)!!

        assertEquals(8.0, min, 1e-9)
        assertEquals(32.0, max, 1e-9)
        assertTrue(min > 0.0)
    }

    @Test
    fun flatTemperatureStillGetsAVisibleSpan() {
        val (min, max) = environmentAxisRange(EnvironmentChartGroup.TEMPERATURE, 20.0, 20.0)!!

        assertEquals(19.0, min, 1e-9)
        assertEquals(21.0, max, 1e-9)
    }

    @Test
    fun humidityAxisIsFixedZeroToHundredWhateverTheData() {
        assertEquals(0.0 to 100.0, environmentAxisRange(EnvironmentChartGroup.HUMIDITY, 40.0, 55.0))
        assertEquals(0.0 to 100.0, environmentAxisRange(EnvironmentChartGroup.HUMIDITY, 0.0, 100.0))
    }

    @Test
    fun pressureAxisUsesTheSlidingWindow() {
        assertEquals(
            pressureAxisRange(1005.0, 1020.0),
            environmentAxisRange(EnvironmentChartGroup.PRESSURE, 1005.0, 1020.0),
        )
    }

    @Test
    fun narrowAxesGetEnoughDecimalsToTellTicksApart() {
        assertEquals(0, axisLabelDecimals(100.0))
        assertEquals(0, axisLabelDecimals(8.0))
        assertEquals(1, axisLabelDecimals(2.0))
        assertEquals(1, axisLabelDecimals(0.8))
        assertEquals(2, axisLabelDecimals(0.3))
    }

    @Test
    fun otherGroupsAutoscale() {
        assertNull(environmentAxisRange(EnvironmentChartGroup.LUX, 0.0, 800.0))
        assertNull(environmentAxisRange(EnvironmentChartGroup.WIND, 0.0, 12.0))
    }
}
