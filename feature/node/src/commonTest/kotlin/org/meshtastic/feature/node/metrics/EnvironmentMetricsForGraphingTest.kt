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

import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class EnvironmentMetricsForGraphingTest {

    private val now = nowSeconds.toInt()

    private fun telemetry(time: Int = now, env: EnvironmentMetrics) = Telemetry(time = time, environment_metrics = env)

    // ---- Empty input ----

    @Test
    fun emptyMetrics_returnsDefaultGraphingData() {
        val state = EnvironmentMetricsState(emptyList())
        val result = state.environmentMetricsForGraphing()

        assertTrue(result.metrics.isEmpty())
        assertTrue(result.shouldPlot.none { it })
    }

    // ---- Fahrenheit conversion ----

    @Test
    fun useFahrenheit_convertsTemperatureMinMax() {
        val metrics =
            listOf(
                telemetry(env = EnvironmentMetrics(temperature = 0f)),
                telemetry(env = EnvironmentMetrics(temperature = 100f)),
            )
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing(useFahrenheit = true)

        assertTrue(result.shouldPlot[Environment.TEMPERATURE.ordinal])
        // 0C = 32F, 100C = 212F
        assertEquals(32f, result.rightMinMax.first, 0.01f)
        assertEquals(212f, result.rightMinMax.second, 0.01f)
    }

    @Test
    fun useFahrenheit_convertsSoilTemperature() {
        val metrics =
            listOf(
                telemetry(env = EnvironmentMetrics(soil_temperature = 20f)),
                telemetry(env = EnvironmentMetrics(soil_temperature = 30f)),
            )
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing(useFahrenheit = true)

        assertTrue(result.shouldPlot[Environment.SOIL_TEMPERATURE.ordinal])
        // 20C = 68F, 30C = 86F
        assertEquals(68f, result.rightMinMax.first, 0.01f)
        assertEquals(86f, result.rightMinMax.second, 0.01f)
    }

    // ---- Humidity filtering ----

    @Test
    fun humidity_zeroFilteredOut() {
        val metrics = listOf(telemetry(env = EnvironmentMetrics(relative_humidity = 0.0f)))
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertFalse(result.shouldPlot[Environment.HUMIDITY.ordinal])
    }

    @Test
    fun humidity_nonZeroIncluded() {
        val metrics =
            listOf(
                telemetry(env = EnvironmentMetrics(relative_humidity = 45f)),
                telemetry(env = EnvironmentMetrics(relative_humidity = 65f)),
            )
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertTrue(result.shouldPlot[Environment.HUMIDITY.ordinal])
        assertEquals(45f, result.rightMinMax.first, 0.01f)
        assertEquals(65f, result.rightMinMax.second, 0.01f)
    }

    // ---- IAQ sentinel filtering ----

    @Test
    fun iaq_intMinValueFilteredOut() {
        val metrics = listOf(telemetry(env = EnvironmentMetrics(iaq = Int.MIN_VALUE)))
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertFalse(result.shouldPlot[Environment.IAQ.ordinal])
    }

    @Test
    fun iaq_validValueIncluded() {
        val metrics =
            listOf(telemetry(env = EnvironmentMetrics(iaq = 50)), telemetry(env = EnvironmentMetrics(iaq = 150)))
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertTrue(result.shouldPlot[Environment.IAQ.ordinal])
        assertEquals(50f, result.rightMinMax.first, 0.01f)
        assertEquals(150f, result.rightMinMax.second, 0.01f)
    }

    // ---- Soil moisture sentinel filtering ----

    @Test
    fun soilMoisture_intMinValueFilteredOut() {
        val metrics = listOf(telemetry(env = EnvironmentMetrics(soil_moisture = Int.MIN_VALUE)))
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertFalse(result.shouldPlot[Environment.SOIL_MOISTURE.ordinal])
    }

    @Test
    fun soilMoisture_validValueIncluded() {
        val metrics =
            listOf(
                telemetry(env = EnvironmentMetrics(soil_moisture = 30)),
                telemetry(env = EnvironmentMetrics(soil_moisture = 70)),
            )
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertTrue(result.shouldPlot[Environment.SOIL_MOISTURE.ordinal])
    }

    // ---- Barometric pressure (left axis) ----

    @Test
    fun barometricPressure_onLeftAxis() {
        val metrics =
            listOf(
                telemetry(env = EnvironmentMetrics(barometric_pressure = 1013.25f)),
                telemetry(env = EnvironmentMetrics(barometric_pressure = 1020.50f)),
            )
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertTrue(result.shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal])
        assertEquals(1013.25f, result.leftMinMax.first, 0.01f)
        assertEquals(1020.50f, result.leftMinMax.second, 0.01f)
    }

    @Test
    fun barometricPressure_doesNotAffectRightAxis() {
        // Only pressure, no other metrics
        val metrics = listOf(telemetry(env = EnvironmentMetrics(barometric_pressure = 1013.25f)))
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        // rightMinMax should be 0/1 defaults since no right-axis metrics
        assertEquals(0f, result.rightMinMax.first, 0.01f)
        assertEquals(1f, result.rightMinMax.second, 0.01f)
    }

    // ---- Lux, UV lux, wind speed, radiation ----

    @Test
    fun lux_plotted() {
        val metrics =
            listOf(telemetry(env = EnvironmentMetrics(lux = 500f)), telemetry(env = EnvironmentMetrics(lux = 1200f)))
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertTrue(result.shouldPlot[Environment.LUX.ordinal])
        assertEquals(500f, result.rightMinMax.first, 0.01f)
        assertEquals(1200f, result.rightMinMax.second, 0.01f)
    }

    @Test
    fun uvLux_plotted() {
        val metrics =
            listOf(telemetry(env = EnvironmentMetrics(uv_lux = 2f)), telemetry(env = EnvironmentMetrics(uv_lux = 8f)))
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertTrue(result.shouldPlot[Environment.UV_LUX.ordinal])
    }

    @Test
    fun windSpeed_plotted() {
        val metrics =
            listOf(
                telemetry(env = EnvironmentMetrics(wind_speed = 5f)),
                telemetry(env = EnvironmentMetrics(wind_speed = 25f)),
            )
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertTrue(result.shouldPlot[Environment.WIND_SPEED.ordinal])
    }

    @Test
    fun radiation_positiveValuesOnly() {
        val metrics =
            listOf(
                telemetry(env = EnvironmentMetrics(radiation = 0f)),
                telemetry(env = EnvironmentMetrics(radiation = 0.15f)),
            )
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertTrue(result.shouldPlot[Environment.RADIATION.ordinal])
        // 0f is filtered out (radiation > 0f only), so min should be 0.15
        assertEquals(0.15f, result.rightMinMax.first, 0.01f)
        assertEquals(0.15f, result.rightMinMax.second, 0.01f)
    }

    // ---- NaN filtering ----

    @Test
    fun nanTemperature_filteredOut() {
        val metrics = listOf(telemetry(env = EnvironmentMetrics(temperature = Float.NaN)))
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertFalse(result.shouldPlot[Environment.TEMPERATURE.ordinal])
    }

    @Test
    fun nanPressure_filteredOut() {
        val metrics = listOf(telemetry(env = EnvironmentMetrics(barometric_pressure = Float.NaN)))
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertFalse(result.shouldPlot[Environment.BAROMETRIC_PRESSURE.ordinal])
        assertEquals(0f, result.leftMinMax.first, 0.01f)
        assertEquals(0f, result.leftMinMax.second, 0.01f)
    }

    // ---- Multiple metrics combined ----

    @Test
    fun multipleMetrics_rightAxisMinMaxSpansAll() {
        val metrics =
            listOf(
                telemetry(env = EnvironmentMetrics(temperature = 10f, relative_humidity = 80f)),
                telemetry(env = EnvironmentMetrics(temperature = 30f, relative_humidity = 40f)),
            )
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertTrue(result.shouldPlot[Environment.TEMPERATURE.ordinal])
        assertTrue(result.shouldPlot[Environment.HUMIDITY.ordinal])
        // right min/max should span both: min(10, 40) = 10, max(30, 80) = 80
        assertEquals(10f, result.rightMinMax.first, 0.01f)
        assertEquals(80f, result.rightMinMax.second, 0.01f)
    }

    // ---- Gas resistance ----

    // ---- Gas resistance (not currently graphed by environmentMetricsForGraphing) ----

    @Test
    fun gasResistance_notPlottedByGraphingFunction() {
        // Note: GAS_RESISTANCE is defined in the Environment enum but environmentMetricsForGraphing()
        // does not have explicit handling for it. This test documents that current behavior.
        val metrics =
            listOf(
                telemetry(env = EnvironmentMetrics(gas_resistance = 100f)),
                telemetry(env = EnvironmentMetrics(gas_resistance = 500f)),
            )
        val result = EnvironmentMetricsState(metrics).environmentMetricsForGraphing()

        assertFalse(result.shouldPlot[Environment.GAS_RESISTANCE.ordinal])
    }
}
