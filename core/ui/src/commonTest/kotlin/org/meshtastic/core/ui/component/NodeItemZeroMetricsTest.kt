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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
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
        setNodeItem(EnvironmentMetrics(temperature = 0f))
        onNodeWithText("0.0°C").assertIsDisplayed()
    }

    @Test
    fun nodeItem_hidesAbsentTemperature() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics())
        onNodeWithText("0.0°C").assertDoesNotExist()
    }

    @Test
    fun nodeItem_showsZeroVoltageAndCurrent() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics(voltage = 0f, current = 0f))
        onNodeWithText("0.00 V").assertIsDisplayed()
        onNodeWithText("0.0 mA").assertIsDisplayed()
    }

    @Test
    fun nodeItem_hidesAbsentVoltageAndCurrent() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics())
        onNodeWithText("0.00 V").assertDoesNotExist()
        onNodeWithText("0.0 mA").assertDoesNotExist()
    }

    @Test
    fun nodeItem_showsZeroSoilReadings() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics(soil_temperature = 0f, soil_moisture = 0))
        onNodeWithText("0.0°C").assertIsDisplayed()
        onNodeWithText("0%").assertIsDisplayed()
    }

    @Test
    fun nodeItem_hidesAbsentSoilReadings() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics())
        onNodeWithText("0.0°C").assertDoesNotExist()
        onNodeWithText("0%").assertDoesNotExist()
    }

    @Test
    fun nodeItem_showsSoilMoistureWithoutSoilTemperature() = runComposeUiTest {
        // The old guard required a non-zero soil temperature before moisture was drawn at all.
        setNodeItem(EnvironmentMetrics(soil_moisture = 42))
        onNodeWithText("42%").assertIsDisplayed()
    }

    @Test
    fun nodeItem_hidesOutOfRangeSoilMoisture() = runComposeUiTest {
        // A sensor fault reporting 101% is not a reading — matches Node.getTelemetryStrings.
        setNodeItem(EnvironmentMetrics(soil_moisture = 101))
        onNodeWithText("101%").assertDoesNotExist()
    }

    @Test
    fun nodeItemCompact_showsZeroTemperature() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics(temperature = 0f))
        onNodeWithText("0.0°C").assertIsDisplayed()
    }

    @Test
    fun nodeItemCompact_hidesAbsentTemperature() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics())
        onNodeWithText("0.0°C").assertDoesNotExist()
    }

    // hopsAway: -1 (Node.HOPS_AWAY_UNSET) is "never resolved", not a real reading — 0 is a real direct-neighbor
    // reading. Pinned as a pair (per the code-review "presence vs. sentinel zero" checklist) so the two states can't
    // collapse back into one: a real 0 must not read as "unresolved", and "unresolved" must not paint a fake "0 hops".
    @Test
    fun nodeItem_showsHopsChipForARealNonZeroReading() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics(), hopsAway = 3)
        onNodeWithText("Hops Away").assertIsDisplayed()
        onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun nodeItem_hidesHopsChipWhenNeverResolved() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics(), hopsAway = Node.HOPS_AWAY_UNSET)
        onNodeWithText("Hops Away").assertDoesNotExist()
    }

    @Test
    fun nodeItemCompact_showsHopsChipForARealNonZeroReading() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics(), hopsAway = 4)
        onNodeWithText("4").assertIsDisplayed()
    }

    @Test
    fun nodeItemCompact_hidesHopsChipWhenNeverResolved() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics(), hopsAway = Node.HOPS_AWAY_UNSET)
        onNodeWithText("4").assertDoesNotExist()
    }

    // "Saved on phone" badge (#6263): flags a node that is locally cached but wasn't part of the connected radio's
    // current session snapshot, instead of letting it render as if it were freshly heard.
    @Test
    fun nodeItem_showsSavedOnPhoneBadgeWhenFlagged() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics(), isSavedOnPhone = true)
        onNodeWithContentDescription("Saved on phone").assertIsDisplayed()
    }

    @Test
    fun nodeItem_hidesSavedOnPhoneBadgeByDefault() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics())
        onNodeWithContentDescription("Saved on phone").assertDoesNotExist()
    }

    @Test
    fun nodeItemCompact_showsSavedOnPhoneBadgeWhenFlagged() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics(), isSavedOnPhone = true)
        onNodeWithContentDescription("Saved on phone").assertIsDisplayed()
    }

    @Test
    fun nodeItemCompact_hidesSavedOnPhoneBadgeByDefault() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics())
        onNodeWithContentDescription("Saved on phone").assertDoesNotExist()
    }

    // A saved-on-phone row's lastHeard is real historical data (not a sentinel), but announcing it as "online" would
    // carry forward the exact false-freshness claim the badge exists to correct (#6263) — see
    // BuildNodeDescriptionTest for the string-level coverage of this same branch.
    @Test
    fun nodeItem_doesNotClaimOnlineWhenSavedOnPhoneEvenWithRecentLastHeard() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics(), isSavedOnPhone = true, lastHeard = onlineTimeThreshold() + 1)
        onNodeWithContentDescription("online", substring = true).assertDoesNotExist()
    }

    @Test
    fun nodeItem_claimsOnlineForARecentlyHeardNodeNotSavedOnPhone() = runComposeUiTest {
        setNodeItem(EnvironmentMetrics(), isSavedOnPhone = false, lastHeard = onlineTimeThreshold() + 1)
        onNodeWithContentDescription("online", substring = true).assertIsDisplayed()
    }

    @Test
    fun nodeItemCompact_doesNotClaimOnlineWhenSavedOnPhoneEvenWithRecentLastHeard() = runComposeUiTest {
        setNodeItemCompact(EnvironmentMetrics(), isSavedOnPhone = true, lastHeard = onlineTimeThreshold() + 1)
        onNodeWithContentDescription("online", substring = true).assertDoesNotExist()
    }

    private fun ComposeUiTest.setNodeItem(
        metrics: EnvironmentMetrics,
        hopsAway: Int = Node.HOPS_AWAY_UNSET,
        isSavedOnPhone: Boolean = false,
        lastHeard: Int = 0,
    ) = setContent {
        MaterialTheme {
            NodeItem(
                thisNode = null,
                thatNode = node(metrics, hopsAway, lastHeard),
                distanceUnits = DisplayUnits.METRIC.value,
                tempInFahrenheit = false,
                connectionState = ConnectionState.Connected,
                isSavedOnPhone = isSavedOnPhone,
            )
        }
    }

    private fun ComposeUiTest.setNodeItemCompact(
        metrics: EnvironmentMetrics,
        hopsAway: Int = Node.HOPS_AWAY_UNSET,
        isSavedOnPhone: Boolean = false,
        lastHeard: Int = 0,
    ) = setContent {
        MaterialTheme {
            NodeItemCompact(
                thisNode = null,
                thatNode = node(metrics, hopsAway, lastHeard),
                distanceUnits = DisplayUnits.METRIC.value,
                isSavedOnPhone = isSavedOnPhone,
            )
        }
    }

    private fun node(metrics: EnvironmentMetrics, hopsAway: Int = Node.HOPS_AWAY_UNSET, lastHeard: Int = 0) = Node(
        num = 2,
        user = User(id = "!2", long_name = "Sensor"),
        environmentMetrics = metrics,
        hopsAway = hopsAway,
        lastHeard = lastHeard,
    )
}
