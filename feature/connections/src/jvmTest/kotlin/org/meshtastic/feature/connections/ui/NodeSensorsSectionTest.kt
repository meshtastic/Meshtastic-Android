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
package org.meshtastic.feature.connections.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.node_sensors_title
import org.meshtastic.feature.connections.component.SensorsCard
import org.meshtastic.feature.connections.model.BearerRowState
import org.meshtastic.feature.connections.model.BearerRowUi
import org.meshtastic.feature.connections.model.NodeStatusUi
import org.meshtastic.feature.connections.model.SensorRowState
import org.meshtastic.feature.connections.model.SensorRowUi
import org.meshtastic.feature.connections.model.SensorsUi
import org.meshtastic.feature.connections.ui.components.NodeStatusCard
import org.meshtastic.node.host.NodePhase
import org.meshtastic.node.sensors.SensorKind
import org.meshtastic.node.sensors.SensorUnread
import org.meshtastic.node.transport.Bearer
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class NodeSensorsSectionTest {

    private fun ComposeUiTest.setSensors(sensors: SensorsUi) = setContent { MaterialTheme { SensorsCard(sensors) } }

    private fun found(vararg rows: SensorRowUi) = SensorsUi.Found(rows.toList())

    @Test
    fun `a host with no bus has no sensors section`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                NodeStatusCard(
                    status =
                    NodeStatusUi(
                        phase = NodePhase.Running,
                        nodeId = "!3a5806be",
                        longName = null,
                        shortName = null,
                        peerCount = 0,
                        failure = null,
                        settingsReset = false,
                        bearers = listOf(BearerRowUi(Bearer.UDP, true, true, BearerRowState.Active, 0, 0, 0)),
                    ),
                    onSetBearer = { _, _ -> },
                    onSetExtraRadio = { _, _ -> },
                    onRefreshRequirements = {},
                    onRequestUsbPermission = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.node_sensors_title)).assertDoesNotExist()
    }

    @Test
    fun `the bus speaks for itself until it has sensors to show`() = runComposeUiTest {
        setSensors(SensorsUi.BusFailed("Permission denied"))

        onNodeWithText(getString(Res.string.node_sensors_title)).assertIsDisplayed()
        onNodeWithText("I2C bus").assertIsDisplayed()
        onNodeWithText("Failed: Permission denied").assertIsDisplayed()
    }

    @Test
    fun `a reading sensor shows what it feeds its address and the age of its last good read`() = runComposeUiTest {
        val kinds = listOf(SensorKind.ENVIRONMENT, SensorKind.AIR_QUALITY)
        setSensors(found(SensorRowUi("SCD4X", "0x62", kinds, SensorRowState.Reading(4.minutes))))

        onNodeWithText("SCD4X").assertIsDisplayed()
        onNodeWithText("Reading · Environment, Air Quality · 0x62 · last good read 4m ago").assertIsDisplayed()
    }

    @Test
    fun `a failing sensor shows its error`() = runComposeUiTest {
        setSensors(
            found(SensorRowUi("BME280", "0x76", listOf(SensorKind.ENVIRONMENT), SensorRowState.Failing("NACK", null))),
        )

        onNodeWithText("Failing: NACK · Environment · 0x76").assertIsDisplayed()
    }

    @Test
    fun `a device nothing names is titled by its address and says why it is not read`() = runComposeUiTest {
        setSensors(found(SensorRowUi(null, "0x1c", emptyList(), SensorRowState.Unread(SensorUnread.UNIDENTIFIED))))

        onNodeWithText("0x1c").assertIsDisplayed()
        onNodeWithText("Not read: unidentified device").assertIsDisplayed()
    }
}
