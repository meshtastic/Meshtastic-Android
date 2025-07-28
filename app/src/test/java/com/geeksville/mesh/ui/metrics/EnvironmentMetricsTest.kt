package com.geeksville.mesh.ui.metrics

import com.geeksville.mesh.TelemetryProtos
import com.geeksville.mesh.copy
import com.geeksville.mesh.util.UnitConversions.celsiusToFahrenheit
import org.junit.Assert.assertEquals
import org.junit.Test

class EnvironmentMetricsTest {

    @Test
    fun `temperature and soil temperature are converted to Fahrenheit when isFahrenheit is true`() {
        val initialTemperatureCelsius = 25.0f
        val initialSoilTemperatureCelsius = 15.0f
        val expectedTemperatureFahrenheit = celsiusToFahrenheit(initialTemperatureCelsius)
        val expectedSoilTemperatureFahrenheit = celsiusToFahrenheit(initialSoilTemperatureCelsius)

        val telemetry = TelemetryProtos.Telemetry.newBuilder()
            .setEnvironmentMetrics(
                TelemetryProtos.EnvironmentMetrics.newBuilder()
                    .setTemperature(initialTemperatureCelsius)
                    .setSoilTemperature(initialSoilTemperatureCelsius)
                    .build()
            )
            .setTime(1000)
            .build()

        val data = listOf(telemetry)

        val isFahrenheit = true

        val processedTelemetries = if (isFahrenheit) {
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