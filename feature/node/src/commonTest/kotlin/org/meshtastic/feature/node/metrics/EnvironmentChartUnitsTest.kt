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
import kotlin.test.assertNull

@Suppress("MagicNumber")
class EnvironmentChartUnitsTest {

    private fun telemetry(env: EnvironmentMetrics) = Telemetry(time = nowSeconds.toInt(), environment_metrics = env)

    // ---- chartValue ----

    @Test
    fun windSpeedMetricStaysMetersPerSecond() {
        val t = telemetry(EnvironmentMetrics(wind_speed = 10f))

        assertEquals(10f, chartValue(Environment.WIND_SPEED, t, isImperial = false)!!, 0.001f)
    }

    @Test
    fun windSpeedImperialIsConvertedToMph() {
        val t = telemetry(EnvironmentMetrics(wind_speed = 10f))

        assertEquals(22.3694f, chartValue(Environment.WIND_SPEED, t, isImperial = true)!!, 0.001f)
    }

    /** 0 m/s is a real reading (dead calm), so it must survive conversion rather than read as missing. */
    @Test
    fun windSpeedZeroIsPreserved() {
        val t = telemetry(EnvironmentMetrics(wind_speed = 0f))

        assertEquals(0f, chartValue(Environment.WIND_SPEED, t, isImperial = true)!!, 0.001f)
    }

    @Test
    fun missingWindSpeedStaysNull() {
        val t = telemetry(EnvironmentMetrics())

        assertNull(chartValue(Environment.WIND_SPEED, t, isImperial = true))
    }

    /** Temperatures are converted upstream by the view model, so the chart must not convert them again. */
    @Test
    fun temperatureIsNotConvertedHere() {
        val t = telemetry(EnvironmentMetrics(temperature = 20f))

        assertEquals(20f, chartValue(Environment.TEMPERATURE, t, isImperial = true)!!, 0.001f)
    }

    // ---- unitSuffix ----

    @Test
    fun windSpeedSuffixFollowsDisplayUnits() {
        assertEquals(" m/s", unitSuffix(Environment.WIND_SPEED, isFahrenheit = false, isImperial = false))
        assertEquals(" mph", unitSuffix(Environment.WIND_SPEED, isFahrenheit = false, isImperial = true))
    }

    @Test
    fun temperatureSuffixFollowsFahrenheitSetting() {
        assertEquals("°C", unitSuffix(Environment.TEMPERATURE, isFahrenheit = false, isImperial = false))
        assertEquals("°F", unitSuffix(Environment.TEMPERATURE, isFahrenheit = true, isImperial = false))
        assertEquals("°F", unitSuffix(Environment.SOIL_TEMPERATURE, isFahrenheit = true, isImperial = false))
        assertEquals("°F", unitSuffix(Environment.ONE_WIRE_TEMP_8, isFahrenheit = true, isImperial = false))
    }

    @Test
    fun adcVoltageSuffixIsVoltsRegardlessOfDisplayUnits() {
        assertEquals(" V", unitSuffix(Environment.ADC_VOLTAGE_1, isFahrenheit = false, isImperial = false))
        assertEquals(" V", unitSuffix(Environment.ADC_VOLTAGE_8, isFahrenheit = true, isImperial = true))
    }

    /** ADC readings are already in volts, so the chart must not unit-convert them. */
    @Test
    fun adcVoltageIsNotConverted() {
        val t = telemetry(EnvironmentMetrics(adc_voltage_ch0 = 3.3f))

        assertEquals(3.3f, chartValue(Environment.ADC_VOLTAGE_1, t, isImperial = true)!!, 0.001f)
    }

    @Test
    fun sharedAxisMetricsHaveNoSuffix() {
        assertEquals("", unitSuffix(Environment.HUMIDITY, isFahrenheit = true, isImperial = true))
        assertEquals("", unitSuffix(Environment.IAQ, isFahrenheit = true, isImperial = true))
        assertEquals("", unitSuffix(Environment.LUX, isFahrenheit = true, isImperial = true))
    }
}
