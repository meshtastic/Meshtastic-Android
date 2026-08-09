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
        setPowerMetrics(PowerMetricsProto(ch1_voltage = 0f, ch1_current = 0f))
        onNodeWithText("0.00V").assertIsDisplayed()
        onNodeWithText("0.0mA").assertIsDisplayed()
    }

    @Test
    fun absentChannelIsHidden() = runComposeUiTest {
        setPowerMetrics(PowerMetricsProto())
        onNodeWithText("0.00V").assertDoesNotExist()
        onNodeWithText("0.0mA").assertDoesNotExist()
    }

    @Test
    fun reportedVoltageWithoutCurrentShowsVoltageOnly() = runComposeUiTest {
        setPowerMetrics(PowerMetricsProto(ch1_voltage = 3.7f))
        onNodeWithText("3.70V").assertIsDisplayed()
        onNodeWithText("0.0mA").assertDoesNotExist()
    }

    private fun ComposeUiTest.setPowerMetrics(metrics: PowerMetricsProto) = setContent {
        MaterialTheme { PowerMetrics(node = Node(num = 1, powerMetrics = metrics)) }
    }
}
