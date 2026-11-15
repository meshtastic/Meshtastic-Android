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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.Node
import kotlin.test.Test
import org.meshtastic.proto.EnvironmentMetrics as EnvironmentMetricsProto

@OptIn(ExperimentalTestApi::class)
class EnvironmentIaqTileTest {

    @Test
    fun iaqTileNamesItsBand() = runComposeUiTest {
        setIaq(125)
        onNodeWithText("125 (Lightly Polluted)").assertIsDisplayed()
    }

    @Test
    fun zeroIaqIsAReadingInTheExcellentBand() = runComposeUiTest {
        setIaq(0)
        onNodeWithText("0 (Excellent)").assertIsDisplayed()
    }

    @Test
    fun absentIaqShowsNoTile() = runComposeUiTest {
        setContent {
            MaterialTheme {
                EnvironmentMetrics(
                    node = Node(num = 1, environmentMetrics = EnvironmentMetricsProto.Builder().build()),
                    displayUnits = MeasurementSystem.METRIC,
                )
            }
        }
        onNodeWithText("IAQ").assertDoesNotExist()
    }

    @Test
    fun unsetIaqSentinelShowsNoTile() = runComposeUiTest {
        setIaq(Int.MIN_VALUE)
        onNodeWithText("IAQ").assertDoesNotExist()
        onNodeWithText("-2147483648", substring = true).assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setIaq(iaq: Int) = setContent {
        MaterialTheme {
            EnvironmentMetrics(
                node =
                Node(num = 1, environmentMetrics = EnvironmentMetricsProto.Builder().also { it.iaq = iaq }.build()),
                displayUnits = MeasurementSystem.METRIC,
            )
        }
    }
}
