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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NodeItemDistanceUnitsTest {

    @Test
    fun nodeItem_updatesDistanceWhenUnitsChange() = runComposeUiTest {
        assertDistanceUpdates { thisNode, thatNode, distanceUnits ->
            NodeItem(
                thisNode = thisNode,
                thatNode = thatNode,
                distanceUnits = distanceUnits,
                tempInFahrenheit = false,
                connectionState = ConnectionState.Connected,
            )
        }
    }

    @Test
    fun nodeItemCompact_updatesDistanceWhenUnitsChange() = runComposeUiTest {
        assertDistanceUpdates { thisNode, thatNode, distanceUnits ->
            NodeItemCompact(thisNode = thisNode, thatNode = thatNode, distanceUnits = distanceUnits)
        }
    }

    private fun ComposeUiTest.assertDistanceUpdates(
        content: @androidx.compose.runtime.Composable (Node, Node, Int) -> Unit,
    ) {
        val thisNode = node(num = 1, latitudeI = 100_000_000, longitudeI = 100_000_000)
        val thatNode = node(num = 2, latitudeI = 100_000_000, longitudeI = 110_000_000)
        val distanceMeters = requireNotNull(thisNode.distance(thatNode))
        val metricDistance = distanceMeters.toDistanceString(DisplayUnits.METRIC)
        val imperialDistance = distanceMeters.toDistanceString(DisplayUnits.IMPERIAL)
        var distanceUnits by mutableIntStateOf(DisplayUnits.METRIC.value)

        setContent { MaterialTheme { content(thisNode, thatNode, distanceUnits) } }

        onNodeWithText(metricDistance).assertIsDisplayed()

        runOnIdle { distanceUnits = DisplayUnits.IMPERIAL.value }

        onNodeWithText(metricDistance).assertDoesNotExist()
        onNodeWithText(imperialDistance).assertIsDisplayed()
        onNodeWithContentDescription(metricDistance, substring = true).assertDoesNotExist()
        onNodeWithContentDescription(imperialDistance, substring = true).assertIsDisplayed()
    }

    private fun node(num: Int, latitudeI: Int, longitudeI: Int): Node = Node(
        num = num,
        user = User(id = "!$num", long_name = "Node $num"),
        position = Position(latitude_i = latitudeI, longitude_i = longitudeI),
    )
}
