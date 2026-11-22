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
package org.meshtastic.feature.settings.radio.component

import org.meshtastic.proto.Config.TransportConfig.Transport
import kotlin.test.Test
import kotlin.test.assertEquals

class TransportSwitchesTest {

    @Test
    fun `each supported transport gets one switch in enum number order`() {
        val switches =
            transportSwitches(
                supported =
                listOf(Transport.TRANSPORT_BLE_GATT, Transport.TRANSPORT_LORA, Transport.TRANSPORT_MULTICAST_UDP),
                enabled = emptyList(),
            )

        assertEquals(
            listOf(Transport.TRANSPORT_LORA, Transport.TRANSPORT_MULTICAST_UDP, Transport.TRANSPORT_BLE_GATT),
            switches.map { it.transport },
        )
        assertEquals(listOf(false, false, false), switches.map { it.checked })
    }

    @Test
    fun `a supported transport listed twice still gets one switch`() {
        val switches =
            transportSwitches(
                supported = listOf(Transport.TRANSPORT_LORA, Transport.TRANSPORT_LORA),
                enabled = emptyList(),
            )

        assertEquals(listOf(Transport.TRANSPORT_LORA), switches.map { it.transport })
    }

    @Test
    fun `a switch is checked when its transport is enabled`() {
        val switches =
            transportSwitches(
                supported = listOf(Transport.TRANSPORT_LORA, Transport.TRANSPORT_MULTICAST_UDP),
                enabled = listOf(Transport.TRANSPORT_MULTICAST_UDP),
            )

        assertEquals(
            listOf(
                TransportSwitch(Transport.TRANSPORT_LORA, checked = false),
                TransportSwitch(Transport.TRANSPORT_MULTICAST_UDP, checked = true),
            ),
            switches,
        )
    }

    @Test
    fun `an enabled transport the node does not support gets no switch`() {
        val switches =
            transportSwitches(
                supported = listOf(Transport.TRANSPORT_LORA),
                enabled = listOf(Transport.TRANSPORT_LORA, Transport.TRANSPORT_AUDIO),
            )

        assertEquals(listOf(TransportSwitch(Transport.TRANSPORT_LORA, checked = true)), switches)
    }

    @Test
    fun `no supported transports offers no switches`() {
        val switches = transportSwitches(supported = emptyList(), enabled = listOf(Transport.TRANSPORT_LORA))

        assertEquals(emptyList(), switches)
    }

    @Test
    fun `toggling one transport leaves the others`() {
        val current = listOf(Transport.TRANSPORT_LORA, Transport.TRANSPORT_BLE_GATT)

        assertEquals(
            listOf(Transport.TRANSPORT_LORA, Transport.TRANSPORT_BLE_GATT, Transport.TRANSPORT_MULTICAST_UDP),
            toggleTransport(current, Transport.TRANSPORT_MULTICAST_UDP, on = true),
        )
        assertEquals(
            listOf(Transport.TRANSPORT_BLE_GATT),
            toggleTransport(current, Transport.TRANSPORT_LORA, on = false),
        )
    }

    @Test
    fun `toggling to the state a transport already has changes nothing`() {
        val current = listOf(Transport.TRANSPORT_LORA)

        assertEquals(current, toggleTransport(current, Transport.TRANSPORT_LORA, on = true))
        assertEquals(current, toggleTransport(current, Transport.TRANSPORT_BLE_GATT, on = false))
    }

    @Test
    fun `an enabled transport with no switch survives a toggle`() {
        val current = listOf(Transport.TRANSPORT_LORA, Transport.TRANSPORT_AUDIO)

        assertEquals(
            listOf(Transport.TRANSPORT_AUDIO),
            toggleTransport(current, Transport.TRANSPORT_LORA, on = false),
        )
        assertEquals(
            listOf(Transport.TRANSPORT_LORA, Transport.TRANSPORT_AUDIO, Transport.TRANSPORT_MULTICAST_UDP),
            toggleTransport(current, Transport.TRANSPORT_MULTICAST_UDP, on = true),
        )
    }
}
