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
import kotlin.test.Test
import org.meshtastic.proto.PowerMetrics as PowerMetricsProto

/**
 * `PowerMetrics` channel readings are Wire-generated and nullable, so `null` is the only "no channel" signal. Both the
 * absent and the measured-zero case are pinned — either alone lets the two states collapse back into one.
 */
@OptIn(ExperimentalTestApi::class)
class PowerMetricsZeroVoltageTest {

    @Test
    fun zeroVoltageChannelIsShown() = runComposeUiTest {
        setPowerMetrics(
            PowerMetricsProto.Builder()
                .also { wb ->
                    wb.ch1_voltage = 0f
                    wb.ch1_current = 0f
                }
                .build(),
        )
        onNodeWithText("0.00 V").assertIsDisplayed()
        onNodeWithText("0.0 mA").assertIsDisplayed()
    }

    @Test
    fun absentChannelIsHidden() = runComposeUiTest {
        setPowerMetrics(PowerMetricsProto.Builder().build())
        onNodeWithText("0.00 V").assertDoesNotExist()
        onNodeWithText("0.0 mA").assertDoesNotExist()
    }

    @Test
    fun reportedVoltageWithoutCurrentShowsVoltageOnly() = runComposeUiTest {
        setPowerMetrics(PowerMetricsProto.Builder().also { wb -> wb.ch1_voltage = 3.7f }.build())
        onNodeWithText("3.70 V").assertIsDisplayed()
        onNodeWithText("0.0 mA").assertDoesNotExist()
    }

    @Test
    fun reportedCurrentWithoutVoltageShowsCurrentOnly() = runComposeUiTest {
        setPowerMetrics(PowerMetricsProto.Builder().also { wb -> wb.ch2_current = 12.5f }.build())
        onNodeWithText("12.5 mA").assertIsDisplayed()
        onNodeWithText("0.00 V").assertDoesNotExist()
    }

    @Test
    fun customLabelReplacesTheChannelName() = runComposeUiTest {
        setPowerMetrics(
            PowerMetricsProto.Builder().also { wb -> wb.ch2_voltage = 4.1f }.build(),
            labels = listOf("", "Solar"),
        )
        onNodeWithText("Solar").assertIsDisplayed()
    }

    @Test
    fun channelsBeyondThreeAreShown() = runComposeUiTest {
        setPowerMetrics(PowerMetricsProto.Builder().also { wb -> wb.ch8_voltage = 5.25f }.build())
        onNodeWithText("5.25 V").assertIsDisplayed()
    }

    private fun ComposeUiTest.setPowerMetrics(metrics: PowerMetricsProto, labels: List<String> = emptyList()) =
        setContent {
            MaterialTheme {
                PowerMetrics(node = Node(num = 1, powerMetrics = metrics, powerChannelLabels = labels))
            }
        }
}
