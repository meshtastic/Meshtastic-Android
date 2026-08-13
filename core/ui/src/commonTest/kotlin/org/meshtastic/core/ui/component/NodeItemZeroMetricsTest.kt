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
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
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

    @Test
    fun nodeItem_retainedOnlyDirectNodeHidesCurrentRadioObservationsAndAccessibility() = runComposeUiTest {
        setCurrentRadioNodeItem(isInCurrentRadioNodeSnapshot = false, hopsAway = 0)

        onNodeWithText("Sensor", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Saved on phone", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("SNR 0.00 dB", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("RSSI 0 dBm", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Good", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Now", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithContentDescription("Sensor, Saved on phone", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("online", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("offline", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("last heard", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("signal", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("Last heard", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithContentDescription("Signal quality", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun nodeItem_retainedOnlyMultiHopNodeHidesCachedHops() = runComposeUiTest {
        setCurrentRadioNodeItem(isInCurrentRadioNodeSnapshot = false, hopsAway = 3)

        onNodeWithText("Saved on phone", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("3", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithContentDescription("Hops away", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithContentDescription("3 hops away", substring = true).assertDoesNotExist()
    }

    @Test
    fun nodeItem_currentDirectNodePreservesMeasuredZeroSignal() = runComposeUiTest {
        setCurrentRadioNodeItem(isInCurrentRadioNodeSnapshot = true, hopsAway = 0)

        onNodeWithText("Saved on phone", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("SNR 0.00 dB", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("RSSI 0 dBm", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Good", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Now", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("last heard", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("online", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("signal good", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("Last heard", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun nodeItemCompact_retainedOnlyNodeHidesCurrentRadioHealth() = runComposeUiTest {
        setCurrentRadioNodeItemCompact(isInCurrentRadioNodeSnapshot = false)

        onNodeWithText("Sensor", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Saved on phone", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Good", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Now", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithContentDescription("Sensor, Saved on phone", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("online", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("offline", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("last heard", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("signal", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("Last heard", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun nodeItemCompact_currentDirectNodePreservesMeasuredZeroSignalQuality() = runComposeUiTest {
        setCurrentRadioNodeItemCompact(isInCurrentRadioNodeSnapshot = true)

        onNodeWithText("Saved on phone", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Good", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Now", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("last heard", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("online", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("signal good", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("Last heard", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun ComposeUiTest.setNodeItem(metrics: EnvironmentMetrics) = setContent {
        MaterialTheme {
            NodeItem(
                thisNode = null,
                thatNode = node(metrics),
                distanceUnits = DisplayUnits.METRIC.value,
                tempInFahrenheit = false,
                connectionState = ConnectionState.Connected,
            )
        }
    }

    private fun ComposeUiTest.setNodeItemCompact(metrics: EnvironmentMetrics) = setContent {
        MaterialTheme {
            NodeItemCompact(thisNode = null, thatNode = node(metrics), distanceUnits = DisplayUnits.METRIC.value)
        }
    }

    private fun ComposeUiTest.setCurrentRadioNodeItem(isInCurrentRadioNodeSnapshot: Boolean?, hopsAway: Int) =
        setContent {
            MaterialTheme {
                NodeItem(
                    thisNode = null,
                    thatNode = currentRadioNode(hopsAway),
                    distanceUnits = DisplayUnits.METRIC.value,
                    tempInFahrenheit = false,
                    connectionState = ConnectionState.Connected,
                    isInCurrentRadioNodeSnapshot = isInCurrentRadioNodeSnapshot,
                )
            }
        }

    private fun ComposeUiTest.setCurrentRadioNodeItemCompact(isInCurrentRadioNodeSnapshot: Boolean?) = setContent {
        MaterialTheme {
            NodeItemCompact(
                thisNode = null,
                thatNode = currentRadioNode(hopsAway = 0),
                distanceUnits = DisplayUnits.METRIC.value,
                isInCurrentRadioNodeSnapshot = isInCurrentRadioNodeSnapshot,
            )
        }
    }

    private fun node(metrics: EnvironmentMetrics) =
        Node(num = 2, user = User(id = "!2", long_name = "Sensor"), environmentMetrics = metrics)

    private fun currentRadioNode(hopsAway: Int) = Node(
        num = 2,
        user = User(id = "!2", long_name = "Sensor", short_name = "SN", hw_model = HardwareModel.TBEAM),
        lastHeard = nowSeconds.toInt(),
        hopsAway = hopsAway,
        snr = 0f,
        rssi = 0,
    )
}
