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
package org.meshtastic.feature.connections.model

import org.meshtastic.feature.connections.model.host.Bearer
import org.meshtastic.feature.connections.model.host.ExtraRadio
import org.meshtastic.feature.connections.model.host.ExtraRadioDevice
import org.meshtastic.feature.connections.model.host.NodePhase
import org.meshtastic.feature.connections.model.host.PrimaryRadio
import org.meshtastic.feature.connections.model.host.SensorKind
import org.meshtastic.feature.connections.model.host.SensorUnread
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import org.meshtastic.core.network.radio.PrimaryRadio as NetworkPrimaryRadio
import org.meshtastic.node.host.ExtraRadio as NodeExtraRadio
import org.meshtastic.node.host.ExtraRadioDevice as NodeExtraRadioDevice
import org.meshtastic.node.host.NodePhase as NodeNodePhase
import org.meshtastic.node.sensors.SensorKind as NodeSensorKind
import org.meshtastic.node.sensors.SensorUnread as NodeSensorUnread
import org.meshtastic.node.transport.Bearer as NodeBearer

class InProcessNodeTest {

    /** Each case maps to the twin of the same name and back, and the twin has no case node-kmp lacks. */
    private fun <N : Enum<N>, A : Enum<A>> assertTwins(
        node: List<N>,
        app: List<A>,
        toApp: (N) -> A,
        toNode: (A) -> N,
    ) {
        assertEquals(node.map { it.name }, app.map { it.name })
        node.forEach { case ->
            assertEquals(case.name, toApp(case).name)
            assertEquals(case, toNode(toApp(case)))
        }
        app.forEach { case ->
            assertEquals(case.name, toNode(case).name)
            assertEquals(case, toApp(toNode(case)))
        }
    }

    @Test
    fun `every node phase maps both ways`() {
        assertTwins(NodeNodePhase.entries, NodePhase.entries, { it.toApp() }, { it.toNode() })
    }

    @Test
    fun `every bearer maps both ways and keeps its transport name`() {
        assertTwins(NodeBearer.entries, Bearer.entries, { it.toApp() }, { it.toNode() })
        NodeBearer.entries.forEach { bearer ->
            assertEquals(bearer.transportName, bearer.toApp().transportName)
            assertEquals(bearer.switchable, bearer.toApp().switchable)
        }
    }

    @Test
    fun `every extra radio device maps both ways`() {
        assertTwins(NodeExtraRadioDevice.entries, ExtraRadioDevice.entries, { it.toApp() }, { it.toNode() })
    }

    @Test
    fun `every sensor kind maps both ways`() {
        assertTwins(NodeSensorKind.entries, SensorKind.entries, { it.toApp() }, { it.toNode() })
    }

    @Test
    fun `every unread reason maps both ways`() {
        assertTwins(NodeSensorUnread.entries, SensorUnread.entries, { it.toApp() }, { it.toNode() })
    }

    @Test
    fun `an extra radio carries every field over`() {
        val node =
            NodeExtraRadio(
                NodeExtraRadioDevice.USB_STICK,
                id = "3A7F01",
                preset = ModemPreset.SHORT_FAST,
                bridge = true,
                duplex = true,
            )
        assertEquals(
            ExtraRadio(
                ExtraRadioDevice.USB_STICK,
                id = "3A7F01",
                preset = ModemPreset.SHORT_FAST,
                bridge = true,
                duplex = true,
            ),
            node.toApp(),
        )
        assertEquals(ExtraRadio(ExtraRadioDevice.RTL_SDR), NodeExtraRadio(NodeExtraRadioDevice.RTL_SDR).toApp())
    }

    @Test
    fun `the primary radio carries its device and id over`() {
        assertEquals(
            PrimaryRadio(ExtraRadioDevice.BOARD, "lora-aio"),
            NetworkPrimaryRadio(NodeExtraRadioDevice.BOARD, "lora-aio").toApp(),
        )
        assertEquals(
            PrimaryRadio(ExtraRadioDevice.USB_STICK),
            NetworkPrimaryRadio(NodeExtraRadioDevice.USB_STICK).toApp(),
        )
    }
}
