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
package org.meshtastic.feature.node.metrics

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.device_metrics_log
import org.meshtastic.core.ui.theme.AppTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BaseMetricScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun baseMetricScreen_displaysTitleAndNodeName() {
        val nodeName = "Test Node 123"
        val testData = listOf("Item 1", "Item 2")

        composeTestRule.setContent {
            AppTheme {
                BaseMetricScreen(
                    onNavigateUp = {},
                    telemetryType = TelemetryType.DEVICE,
                    titleRes = Res.string.device_metrics_log,
                    nodeName = nodeName,
                    data = testData,
                    timeProvider = { 0.0 },
                    chartPart = { _, _, _, _ -> Text("Chart Placeholder") },
                    listPart = { _, _, _, _ -> Text("List Placeholder") },
                )
            }
        }

        // Verify Node Name is displayed (MainAppBar title)
        composeTestRule.onNodeWithText(nodeName).assertIsDisplayed()

        // Verify Placeholders are displayed
        composeTestRule.onNodeWithText("Chart Placeholder").assertIsDisplayed()
        composeTestRule.onNodeWithText("List Placeholder").assertIsDisplayed()
    }

    @Test
    fun baseMetricScreen_refreshButtonTriggersCallback() {
        var refreshClicked = false
        val testData = emptyList<String>()

        composeTestRule.setContent {
            AppTheme {
                BaseMetricScreen(
                    onNavigateUp = {},
                    telemetryType = TelemetryType.DEVICE,
                    titleRes = Res.string.device_metrics_log,
                    nodeName = "Node",
                    data = testData,
                    timeProvider = { 0.0 },
                    onRequestTelemetry = { refreshClicked = true },
                    chartPart = { _, _, _, _ -> },
                    listPart = { _, _, _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithTag("refresh_button").performClick()

        assertTrue("Refresh callback should be triggered", refreshClicked)
    }
}
