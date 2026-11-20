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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.toElevationString
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.Test

/**
 * `Position.altitude` is optional on the wire: an unset one is no reading, and 0 is sea level. The tests match the
 * elevation itself, not the resource suffix, which is empty where resources do not load (wasmJs browser tests).
 */
@OptIn(ExperimentalTestApi::class)
class NodeItemAltitudeTest {

    @Test
    fun nodeItem_hidesAbsentAltitude() = runComposeUiTest {
        setNodeItem(altitude = null)
        onNodeWithText(SEA_LEVEL, substring = true).assertDoesNotExist()
    }

    @Test
    fun nodeItem_showsSeaLevelAltitude() = runComposeUiTest {
        setNodeItem(altitude = 0)
        onNodeWithText(SEA_LEVEL, substring = true).assertIsDisplayed()
    }

    private fun ComposeUiTest.setNodeItem(altitude: Int?) = setContent {
        MaterialTheme {
            NodeItem(
                thisNode = null,
                thatNode = node(altitude),
                distanceUnits = MeasurementSystem.METRIC,
                tempInFahrenheit = false,
                connectionState = ConnectionState.Connected,
            )
        }
    }

    private fun node(altitude: Int?) = Node(
        num = 2,
        user =
        User.Builder()
            .also { wb ->
                wb.id = "!2"
                wb.long_name = "Walker"
            }
            .build(),
        position =
        Position.Builder()
            .also { wb ->
                wb.latitude_i = 515_000_000
                wb.longitude_i = -1_200_000
                wb.altitude = altitude
            }
            .build(),
    )

    private companion object {
        val SEA_LEVEL = 0.toElevationString(MeasurementSystem.METRIC)
    }
}
