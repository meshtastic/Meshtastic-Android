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
package org.meshtastic.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.User
import kotlin.test.Test

/**
 * `EnvironmentMetrics` fields are Wire-generated and nullable, so `null` is the only "not reported" signal. Each metric
 * is pinned twice — absent and measured-zero — because either assertion alone lets the two states collapse back into
 * one.
 */
@OptIn(ExperimentalTestApi::class)
class NodeItemZeroMetricsTest {

    @Test
    fun nodeItem_showsZeroTemperature() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics.Builder().also { wb -> wb.temperature = 0f }.build())
        onNodeWithText("0.0°C").assertIsDisplayed()
    }

    @Test
    fun nodeItem_hidesAbsentTemperature() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics.Builder().build())
        onNodeWithText("0.0°C").assertDoesNotExist()
    }

    @Test
    fun nodeItem_showsZeroVoltageAndCurrent() = runComposeUiTest {
        setNodeItem(
            EnvironmentMetrics.Builder()
                .also { wb ->
                    wb.voltage = 0f
                    wb.current = 0f
                }
                .build(),
        )
        onNodeWithText("0.00 V").assertIsDisplayed()
        onNodeWithText("0.0 mA").assertIsDisplayed()
    }

    @Test
    fun nodeItem_hidesAbsentVoltageAndCurrent() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics.Builder().build())
        onNodeWithText("0.00 V").assertDoesNotExist()
        onNodeWithText("0.0 mA").assertDoesNotExist()
    }

    @Test
    fun nodeItem_showsZeroSoilReadings() = runComposeUiTest {
        setNodeItem(
            EnvironmentMetrics.Builder()
                .also { wb ->
                    wb.soil_temperature = 0f
                    wb.soil_moisture = 0
                }
                .build(),
        )
        onNodeWithText("0.0°C").assertIsDisplayed()
        onNodeWithText("0%").assertIsDisplayed()
    }

    @Test
    fun nodeItem_hidesAbsentSoilReadings() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics.Builder().build())
        onNodeWithText("0.0°C").assertDoesNotExist()
        onNodeWithText("0%").assertDoesNotExist()
    }

    @Test
    fun nodeItem_showsSoilMoistureWithoutSoilTemperature() = runComposeUiTest {
        // The old guard required a non-zero soil temperature before moisture was drawn at all.
        setNodeItem(EnvironmentMetrics.Builder().also { wb -> wb.soil_moisture = 42 }.build())
        onNodeWithText("42%").assertIsDisplayed()
    }

    @Test
    fun nodeItem_hidesOutOfRangeSoilMoisture() = runComposeUiTest {
        // A sensor fault reporting 101% is not a reading — matches Node.getTelemetryStrings.
        setNodeItem(EnvironmentMetrics.Builder().also { wb -> wb.soil_moisture = 101 }.build())
        onNodeWithText("101%").assertDoesNotExist()
    }

    @Test
    fun nodeItemCompact_showsZeroTemperature() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics.Builder().also { wb -> wb.temperature = 0f }.build())
        onNodeWithText("0.0°C").assertIsDisplayed()
    }

    @Test
    fun nodeItemCompact_hidesAbsentTemperature() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics.Builder().build())
        onNodeWithText("0.0°C").assertDoesNotExist()
    }

    @Test
    fun nodeItem_showsZeroHumidity() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics.Builder().also { wb -> wb.relative_humidity = 0f }.build())
        onNodeWithText(MetricFormatter.humidity(0f)).assertIsDisplayed()
    }

    @Test
    fun nodeItem_hidesAbsentHumidity() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics.Builder().build())
        onNodeWithText(MetricFormatter.humidity(0f)).assertDoesNotExist()
    }

    @Test
    fun nodeItem_hidesNaNHumidity() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics.Builder().also { wb -> wb.relative_humidity = Float.NaN }.build())
        onNodeWithText(MetricFormatter.humidity(Float.NaN)).assertDoesNotExist()
    }

    @Test
    fun nodeItem_showsZeroCo2SensorHumidityWhenEnvironmentHasNone() = runComposeUiTest {
        setNodeItem(
            EnvironmentMetrics.Builder().build(),
            AirQualityMetrics.Builder().also { wb -> wb.co2_humidity = 0f }.build(),
        )
        onNodeWithText(MetricFormatter.humidity(0f)).assertIsDisplayed()
    }

    @Test
    fun nodeItemCompact_showsZeroHumidity() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics.Builder().also { wb -> wb.relative_humidity = 0f }.build())
        onNodeWithText(MetricFormatter.humidity(0f)).assertIsDisplayed()
    }

    @Test
    fun nodeItemCompact_hidesAbsentHumidity() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics.Builder().build())
        onNodeWithText(MetricFormatter.humidity(0f)).assertDoesNotExist()
    }

    @Test
    fun nodeItem_zeroEnvironmentHumidityWinsOverCo2SensorHumidity() = runComposeUiTest {
        setNodeItem(
            EnvironmentMetrics.Builder().also { wb -> wb.relative_humidity = 0f }.build(),
            AirQualityMetrics.Builder().also { wb -> wb.co2_humidity = 40f }.build(),
        )
        onNodeWithText(MetricFormatter.humidity(0f)).assertIsDisplayed()
        onNodeWithText(MetricFormatter.humidity(40f)).assertDoesNotExist()
    }

    @Test
    fun nodeItemCompact_zeroEnvironmentHumidityWinsOverCo2SensorHumidity() = runComposeUiTest {
        setNodeItemCompact(
            EnvironmentMetrics.Builder().also { wb -> wb.relative_humidity = 0f }.build(),
            AirQualityMetrics.Builder().also { wb -> wb.co2_humidity = 40f }.build(),
        )
        onNodeWithText(MetricFormatter.humidity(0f)).assertIsDisplayed()
        onNodeWithText(MetricFormatter.humidity(40f)).assertDoesNotExist()
    }

    @Test
    fun nodeItemCompact_hidesNaNHumidity() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics.Builder().also { wb -> wb.relative_humidity = Float.NaN }.build())
        onNodeWithText(MetricFormatter.humidity(Float.NaN)).assertDoesNotExist()
    }

    @Test
    fun nodeItemCompact_showsZeroCo2SensorHumidityWhenEnvironmentHasNone() = runComposeUiTest {
        setNodeItemCompact(
            EnvironmentMetrics.Builder().build(),
            AirQualityMetrics.Builder().also { wb -> wb.co2_humidity = 0f }.build(),
        )
        onNodeWithText(MetricFormatter.humidity(0f)).assertIsDisplayed()
    }

    private fun ComposeUiTest.setNodeItem(
        metrics: EnvironmentMetrics,
        airQuality: AirQualityMetrics = AirQualityMetrics.Builder().build(),
    ) = setContent {
        MaterialTheme {
            NodeItem(
                thisNode = null,
                thatNode = node(metrics, airQuality),
                distanceUnits = MeasurementSystem.METRIC,
                tempInFahrenheit = false,
                connectionState = ConnectionState.Connected,
            )
        }
    }

    private fun ComposeUiTest.setNodeItemCompact(
        metrics: EnvironmentMetrics,
        airQuality: AirQualityMetrics = AirQualityMetrics.Builder().build(),
    ) = setContent {
        MaterialTheme {
            NodeItemCompact(
                thisNode = null,
                thatNode = node(metrics, airQuality),
                distanceUnits = MeasurementSystem.METRIC,
            )
        }
    }

    private fun node(metrics: EnvironmentMetrics, airQuality: AirQualityMetrics) = Node(
        num = 2,
        user =
        User.Builder()
            .also { wb ->
                wb.id = "!2"
                wb.long_name = "Sensor"
            }
            .build(),
        environmentMetrics = metrics,
        airQualityMetrics = airQuality,
    )
}
