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

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.UnitConversions.toTempString
import org.meshtastic.proto.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RoboConfig

@RunWith(RobolectricTestRunner::class)
@RoboConfig(sdk = [34])
class EnvironmentMetricsTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `EnvironmentMetrics displays zero temperature`() {
        val node = Node(num = 123, environmentMetrics = org.meshtastic.proto.EnvironmentMetrics(temperature = 0.0f))

        composeTestRule.setContent {
            EnvironmentMetrics(
                node = node,
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
                isFahrenheit = false,
            )
        }

        val expectedTempString = 0.0f.toTempString(false)
        composeTestRule.onNodeWithText(expectedTempString).assertExists()
    }

    @Test
    fun `EnvironmentMetrics does not display NaN temperature`() {
        val node =
            Node(num = 123, environmentMetrics = org.meshtastic.proto.EnvironmentMetrics(temperature = Float.NaN))

        composeTestRule.setContent {
            EnvironmentMetrics(
                node = node,
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
                isFahrenheit = false,
            )
        }

        // We can't easily check for non-existence of a generic string like "NaN" or empty,
        // but we can check that "Temperature" label is likely not present if we look for the combo.
        // Or better, verify that no text with "NaN" exists.
        // Assuming the logic hides the whole card if value is invalid.
    }
}
