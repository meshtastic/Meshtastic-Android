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
package org.meshtastic.core.model

import org.meshtastic.proto.EnvironmentMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `EnvironmentMetrics` fields are Wire-generated and nullable, so `null` is the only "not reported" signal. Each metric
 * is pinned twice — absent and measured-zero — because either assertion alone lets the two states collapse back into
 * one.
 */
class NodeTelemetryStringsTest {

    private fun telemetry(metrics: EnvironmentMetrics) =
        Node(num = 1, environmentMetrics = metrics).getTelemetryStrings()

    @Test
    fun absent_environment_metrics_render_nothing() {
        assertEquals(emptyList(), telemetry(EnvironmentMetrics()))
    }

    @Test
    fun zero_temperature_is_reported() {
        assertEquals(listOf("0.0°C"), telemetry(EnvironmentMetrics(temperature = 0f)))
    }

    @Test
    fun zero_voltage_and_current_are_reported() {
        val strings = telemetry(EnvironmentMetrics(voltage = 0f, current = 0f))
        assertEquals(listOf("0.00 V", "0.0 mA"), strings)
    }

    @Test
    fun zero_soil_readings_are_reported() {
        val strings = telemetry(EnvironmentMetrics(soil_temperature = 0f, soil_moisture = 0))
        assertEquals(listOf("0.0°C", "0%"), strings)
    }

    @Test
    fun soil_moisture_no_longer_requires_a_soil_temperature() {
        assertEquals(listOf("42%"), telemetry(EnvironmentMetrics(soil_moisture = 42)))
    }

    @Test
    fun out_of_range_soil_moisture_is_still_rejected() {
        assertEquals(emptyList(), telemetry(EnvironmentMetrics(soil_moisture = 101)))
    }

    @Test
    fun zero_humidity_stays_filtered() {
        // 0 %RH is not physically reachable, so unlike the other metrics its zero-guard is intentional.
        assertTrue(telemetry(EnvironmentMetrics(relative_humidity = 0f)).isEmpty())
        assertEquals(listOf("41%"), telemetry(EnvironmentMetrics(relative_humidity = 41f)))
    }
}
