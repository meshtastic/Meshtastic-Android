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

package com.geeksville.mesh.ui.metrics

import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.core.model.util.UnitConversions.celsiusToFahrenheit
import org.meshtastic.proto.TelemetryProtos
import org.meshtastic.proto.copy

class EnvironmentMetricsTest {

    @Test
    fun `temperature and soil temperature are converted to Fahrenheit when isFahrenheit is true`() {
        val initialTemperatureCelsius = 25.0f
        val initialSoilTemperatureCelsius = 15.0f
        val expectedTemperatureFahrenheit = celsiusToFahrenheit(initialTemperatureCelsius)
        val expectedSoilTemperatureFahrenheit = celsiusToFahrenheit(initialSoilTemperatureCelsius)

        val telemetry =
            TelemetryProtos.Telemetry.newBuilder()
                .setEnvironmentMetrics(
                    TelemetryProtos.EnvironmentMetrics.newBuilder()
                        .setTemperature(initialTemperatureCelsius)
                        .setSoilTemperature(initialSoilTemperatureCelsius)
                        .build(),
                )
                .setTime(1000)
                .build()

        val data = listOf(telemetry)

        val isFahrenheit = true

        val processedTelemetries =
            if (isFahrenheit) {
                data.map { tel ->
                    val temperatureFahrenheit = celsiusToFahrenheit(tel.environmentMetrics.temperature)
                    val soilTemperatureFahrenheit = celsiusToFahrenheit(tel.environmentMetrics.soilTemperature)
                    tel.copy {
                        environmentMetrics =
                            tel.environmentMetrics.copy {
                                temperature = temperatureFahrenheit
                                soilTemperature = soilTemperatureFahrenheit
                            }
                    }
                }
            } else {
                data
            }

        val resultTelemetry = processedTelemetries.first()

        assertEquals(expectedTemperatureFahrenheit, resultTelemetry.environmentMetrics.temperature, 0.01f)
        assertEquals(expectedSoilTemperatureFahrenheit, resultTelemetry.environmentMetrics.soilTemperature, 0.01f)
    }
}
