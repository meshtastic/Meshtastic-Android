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
package org.meshtastic.app.ui.metrics

import org.meshtastic.core.model.util.UnitConversions.celsiusToFahrenheit
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.Telemetry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class EnvironmentMetricsTest {

    @Test
    fun `temperature and soil temperature are converted to Fahrenheit when isFahrenheit is true`() {
        val initialTemperatureCelsius = 25.0f
        val initialSoilTemperatureCelsius = 15.0f
        val expectedTemperatureFahrenheit = celsiusToFahrenheit(initialTemperatureCelsius)
        val expectedSoilTemperatureFahrenheit = celsiusToFahrenheit(initialSoilTemperatureCelsius)

        val telemetry =
            Telemetry(
                environment_metrics =
                EnvironmentMetrics(
                    temperature = initialTemperatureCelsius,
                    soil_temperature = initialSoilTemperatureCelsius,
                ),
                time = 1000,
            )

        val data = listOf(telemetry)

        val isFahrenheit = true

        val processedTelemetries =
            if (isFahrenheit) {
                data.map { tel ->
                    val metrics = tel.environment_metrics!!
                    val temperatureFahrenheit = celsiusToFahrenheit(metrics.temperature ?: 0f)
                    val soilTemperatureFahrenheit = celsiusToFahrenheit(metrics.soil_temperature ?: 0f)
                    tel.copy(
                        environment_metrics =
                        metrics.copy(
                            temperature = temperatureFahrenheit,
                            soil_temperature = soilTemperatureFahrenheit,
                        ),
                    )
                }
            } else {
                data
            }

        val resultTelemetry = processedTelemetries.first()

        assertTrue(
            abs(expectedTemperatureFahrenheit - (resultTelemetry.environment_metrics?.temperature ?: 0f)) < 0.01f,
        )
        assertTrue(
            abs(expectedSoilTemperatureFahrenheit - (resultTelemetry.environment_metrics?.soil_temperature ?: 0f)) <
                0.01f,
        )
    }
}
