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

import kotlin.test.Test
import kotlin.test.assertEquals

class MetricFormatterTest {

    @Test
    fun temperatureCelsius() {
        assertEquals("25.3°C", MetricFormatter.temperature(25.3f, isFahrenheit = false))
    }

    @Test
    fun temperatureFahrenheit() {
        assertEquals("77.0°F", MetricFormatter.temperature(25.0f, isFahrenheit = true))
    }

    @Test
    fun temperatureNegative() {
        assertEquals("-10.5°C", MetricFormatter.temperature(-10.5f, isFahrenheit = false))
    }

    @Test
    fun voltage() {
        assertEquals("3.72 V", MetricFormatter.voltage(3.72f))
    }

    @Test
    fun voltageOneDecimal() {
        assertEquals("3.7 V", MetricFormatter.voltage(3.725f, decimalPlaces = 1))
    }

    @Test
    fun current() {
        assertEquals("150.3 mA", MetricFormatter.current(150.3f))
    }

    @Test
    fun percentFloat() {
        assertEquals("85.5%", MetricFormatter.percent(85.5f))
    }

    @Test
    fun percentInt() {
        assertEquals("85%", MetricFormatter.percent(85))
    }

    @Test
    fun humidity() {
        assertEquals("65%", MetricFormatter.humidity(65.4f))
    }

    @Test
    fun pressure() {
        assertEquals("1013.3 hPa", MetricFormatter.pressure(1013.25f))
    }

    @Test
    fun snr() {
        assertEquals("5.5 dB", MetricFormatter.snr(5.5f))
    }

    @Test
    fun rssi() {
        assertEquals("-90 dBm", MetricFormatter.rssi(-90))
    }

    @Test
    fun temperatureFreezingFahrenheit() {
        assertEquals("32.0°F", MetricFormatter.temperature(0.0f, isFahrenheit = true))
    }

    @Test
    fun temperatureBoilingFahrenheit() {
        assertEquals("212.0°F", MetricFormatter.temperature(100.0f, isFahrenheit = true))
    }

    @Test
    fun voltageZero() {
        assertEquals("0.00 V", MetricFormatter.voltage(0.0f))
    }

    @Test
    fun currentZero() {
        assertEquals("0.0 mA", MetricFormatter.current(0.0f))
    }

    @Test
    fun percentZero() {
        assertEquals("0%", MetricFormatter.percent(0))
    }

    @Test
    fun percentHundred() {
        assertEquals("100%", MetricFormatter.percent(100))
    }

    @Test
    fun rssiZero() {
        assertEquals("0 dBm", MetricFormatter.rssi(0))
    }

    @Test
    fun snrNegative() {
        assertEquals("-5.5 dB", MetricFormatter.snr(-5.5f))
    }

    @Test
    fun windSpeed() {
        assertEquals("12.3 m/s", MetricFormatter.windSpeed(12.34f))
    }

    @Test
    fun windSpeedZero() {
        assertEquals("0.0 m/s", MetricFormatter.windSpeed(0.0f))
    }

    @Test
    fun rainfall() {
        assertEquals("2.5 mm", MetricFormatter.rainfall(2.54f))
    }

    @Test
    fun rainfallZero() {
        assertEquals("0.0 mm", MetricFormatter.rainfall(0.0f))
    }
}
