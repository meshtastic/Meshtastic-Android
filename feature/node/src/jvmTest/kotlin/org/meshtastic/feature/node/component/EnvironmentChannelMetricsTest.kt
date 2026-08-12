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
package org.meshtastic.feature.node.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.Node
import org.meshtastic.proto.Config
import kotlin.test.Test
import org.meshtastic.proto.EnvironmentMetrics as EnvironmentMetricsProto

/**
 * The per-channel 1-Wire and ADC readings are Wire-generated and nullable, so `null` is the only "no channel" signal.
 * Both the absent and the measured-zero case are pinned — either alone lets the two states collapse back into one,
 * which is how the old `repeated one_wire_temperature` reads used to lose channel identity.
 */
@OptIn(ExperimentalTestApi::class)
class EnvironmentChannelMetricsTest {

    @Test
    fun zeroOneWireTemperatureIsShown() = runComposeUiTest {
        setEnvironmentMetrics(EnvironmentMetricsProto(one_wire_temperature_ch0 = 0f))
        onNodeWithText("1-Wire Temp 1").assertIsDisplayed()
        onNodeWithText("0°C").assertIsDisplayed()
    }

    @Test
    fun absentOneWireChannelIsHidden() = runComposeUiTest {
        setEnvironmentMetrics(EnvironmentMetricsProto())
        onNodeWithText("1-Wire Temp 1").assertDoesNotExist()
    }

    @Test
    fun oneWireChannelsKeepTheirChannelNumber() = runComposeUiTest {
        // A gap in the middle must not renumber the channels above it.
        setEnvironmentMetrics(EnvironmentMetricsProto(one_wire_temperature_ch0 = 10f, one_wire_temperature_ch2 = 30f))
        onNodeWithText("1-Wire Temp 1").assertIsDisplayed()
        onNodeWithText("1-Wire Temp 3").assertIsDisplayed()
        onNodeWithText("1-Wire Temp 2").assertDoesNotExist()
    }

    @Test
    fun zeroAdcVoltageIsShown() = runComposeUiTest {
        setEnvironmentMetrics(EnvironmentMetricsProto(adc_voltage_ch0 = 0f))
        onNodeWithText("ADC Voltage 1").assertIsDisplayed()
        onNodeWithText("0.00 V").assertIsDisplayed()
    }

    @Test
    fun absentAdcChannelIsHidden() = runComposeUiTest {
        setEnvironmentMetrics(EnvironmentMetricsProto())
        onNodeWithText("ADC Voltage 1").assertDoesNotExist()
    }

    @Test
    fun adcChannelsKeepTheirChannelNumber() = runComposeUiTest {
        setEnvironmentMetrics(EnvironmentMetricsProto(adc_voltage_ch1 = 3.3f, adc_voltage_ch7 = 1.8f))
        onNodeWithText("ADC Voltage 2").assertIsDisplayed()
        onNodeWithText("3.30 V").assertIsDisplayed()
        onNodeWithText("ADC Voltage 8").assertIsDisplayed()
        onNodeWithText("1.80 V").assertIsDisplayed()
    }

    private fun ComposeUiTest.setEnvironmentMetrics(metrics: EnvironmentMetricsProto) = setContent {
        MaterialTheme {
            EnvironmentMetrics(
                node = Node(num = 1, environmentMetrics = metrics),
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
            )
        }
    }
}
