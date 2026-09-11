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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.node_not_heard_on_current_lora
import org.meshtastic.proto.User
import kotlin.test.Test

/**
 * The unreachable marker on both node rows. Each feeds [StatusAwareLastHeard] from `Node.isUnheardOnCurrentLora`, not
 * the raw `heardOnCurrentLora` flag, so an MQTT-only node must not wear it: the radio sets the mark only on an RF hear,
 * so such a node reports false for the life of the entry and would otherwise be badged forever and offered for removal.
 *
 * Driven through the real rows rather than [StatusAwareLastHeard] directly - the wiring is what can regress, so the
 * via_mqtt cases fail if either row goes back to reading the raw flag.
 */
@OptIn(ExperimentalTestApi::class)
class NodeItemUnheardMarkerTest {

    private fun node(viaMqtt: Boolean) = Node(
        num = 1928,
        user = User(long_name = "Minnie Mouse", short_name = "MiMo", id = "!minnie"),
        lastHeard = (nowSeconds - 300).toInt(),
        heardOnCurrentLora = false,
        viaMqtt = viaMqtt,
    )

    private fun ComposeUiTest.markerCount() =
        onAllNodesWithContentDescription(getString(Res.string.node_not_heard_on_current_lora))

    private fun ComposeUiTest.setNodeItem(viaMqtt: Boolean) = setContent {
        MaterialTheme {
            NodeItem(
                thisNode = null,
                thatNode = node(viaMqtt),
                distanceUnits = MeasurementSystem.METRIC,
                tempInFahrenheit = false,
                connectionState = ConnectionState.Connected,
            )
        }
    }

    private fun ComposeUiTest.setNodeItemCompact(viaMqtt: Boolean) = setContent {
        MaterialTheme {
            NodeItemCompact(thisNode = null, thatNode = node(viaMqtt), distanceUnits = MeasurementSystem.METRIC)
        }
    }

    @Test
    fun nodeItem_marksANodeHeardOverRfOnAnotherConfig() = runComposeUiTest {
        setNodeItem(viaMqtt = false)
        markerCount().assertCountEquals(1)
    }

    @Test
    fun nodeItem_withholdsTheMarkerFromAnMqttNode() = runComposeUiTest {
        setNodeItem(viaMqtt = true)
        markerCount().assertCountEquals(0)
    }

    @Test
    fun nodeItemCompact_marksANodeHeardOverRfOnAnotherConfig() = runComposeUiTest {
        setNodeItemCompact(viaMqtt = false)
        markerCount().assertCountEquals(1)
    }

    @Test
    fun nodeItemCompact_withholdsTheMarkerFromAnMqttNode() = runComposeUiTest {
        setNodeItemCompact(viaMqtt = true)
        markerCount().assertCountEquals(0)
    }
}
