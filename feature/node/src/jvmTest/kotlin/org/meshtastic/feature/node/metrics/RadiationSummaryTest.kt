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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.formatString
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.Telemetry
import kotlin.test.Test

/** Radiation is nullable on the wire: a reported 0 µR/h is a reading and shows, a missing or NaN value never does. */
@OptIn(ExperimentalTestApi::class)
class RadiationSummaryTest {

    private fun androidx.compose.ui.test.ComposeUiTest.setSummary(metrics: EnvironmentMetrics) = setContent {
        MaterialTheme {
            EnvironmentMetricsContent(
                telemetry = Telemetry.Builder().also { wb -> wb.environment_metrics = metrics }.build(),
                environmentDisplayFahrenheit = false,
                isImperial = false,
                timeTextOverride = "t",
            )
        }
    }

    @Test
    fun reportedZeroRadiationIsShown() = runComposeUiTest {
        setSummary(EnvironmentMetrics.Builder().also { wb -> wb.radiation = 0f }.build())
        onNodeWithText(formatString("%.1f µR/h", 0f), substring = true).assertIsDisplayed()
    }

    @Test
    fun missingRadiationIsHidden() = runComposeUiTest {
        setSummary(EnvironmentMetrics.Builder().build())
        onNodeWithText("µR/h", substring = true).assertDoesNotExist()
    }

    @Test
    fun nanRadiationIsHidden() = runComposeUiTest {
        setSummary(EnvironmentMetrics.Builder().also { wb -> wb.radiation = Float.NaN }.build())
        onNodeWithText("µR/h", substring = true).assertDoesNotExist()
    }
}
