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
package org.meshtastic.feature.connections.navigation

import org.meshtastic.core.model.Node
import org.meshtastic.feature.connections.connectLink
import org.meshtastic.feature.connections.fixedPairingPin
import org.meshtastic.feature.connections.ui.components.connectLabelSpec
import org.meshtastic.proto.Config.BluetoothConfig
import org.meshtastic.proto.Config.BluetoothConfig.PairingMode
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Expected links are meshtastic/design#177's form as labeltastic's `provision.connect_uri` writes it (run with base
 * `meshtastic://meshtastic`), and for TCP and IPv6 the same `urlencode(..., safe=":")` over the `t` address. The PIN
 * rules are design#177 item 3 and design#185 item 9.
 */
class ConnectLinkTest {
    private val ble = "xAA:BB:CC:DD:EE:FF"

    private fun bluetooth(mode: PairingMode, pin: Int) = BluetoothConfig.Builder()
        .also {
            it.mode = mode
            it.fixed_pin = pin
        }
        .build()

    private val node =
        Node(
            num = 0xDEADBEEF.toInt(),
            user =
            User.Builder()
                .also {
                    it.id = "!deadbeef"
                    it.long_name = "Wandering Packet Goblin"
                    it.short_name = "WPG"
                }
                .build(),
        )

    @Test
    fun `a Bluetooth radio with a fixed PIN gets its address and PIN in the link`() {
        assertEquals(
            "meshtastic://meshtastic/connections?address=xAA:BB:CC:DD:EE:FF&pin=123456",
            connectLink(ble, fixedPairingPin(bluetooth(PairingMode.FIXED_PIN, 123456))),
        )
    }

    @Test
    fun `a radio pairing with a random PIN or none gets no PIN in the link`() {
        val links =
            listOf(PairingMode.RANDOM_PIN, PairingMode.NO_PIN).map {
                connectLink(ble, fixedPairingPin(bluetooth(it, 123456)))
            }

        assertEquals(List(2) { "meshtastic://meshtastic/connections?address=xAA:BB:CC:DD:EE:FF" }, links)
    }

    @Test
    fun `a fixed PIN outside 100000 to 999999 is left out`() {
        val pins =
            listOf(99_999, 100_000, 999_999, 1_000_000, 0).map { fixedPairingPin(bluetooth(PairingMode.FIXED_PIN, it)) }

        assertEquals(listOf(null, "100000", "999999", null, null), pins)
    }

    @Test
    fun `a PIN with a leading zero or on a TCP address is dropped from the link`() {
        assertEquals("meshtastic://meshtastic/connections?address=xAA:BB:CC:DD:EE:FF", connectLink(ble, "012345"))
        assertEquals(
            "meshtastic://meshtastic/connections?address=t192.168.1.20",
            connectLink("t192.168.1.20", "123456"),
        )
    }

    @Test
    fun `a TCP radio gets its host and a port only when it has one`() {
        assertEquals("meshtastic://meshtastic/connections?address=t192.168.1.20", connectLink("t192.168.1.20"))
        assertEquals(
            "meshtastic://meshtastic/connections?address=t192.168.1.20:4404",
            connectLink("t192.168.1.20:4404"),
        )
    }

    @Test
    fun `an IPv6 host has its brackets percent-encoded`() {
        assertEquals("meshtastic://meshtastic/connections?address=t%5Bfe80::1%5D:4403", connectLink("t[fe80::1]:4403"))
    }

    @Test
    fun `a serial port and the demo transports get no link`() {
        assertEquals(
            List(5) { null },
            listOf("s/dev/ttyUSB0", "m", "r", "n", "wws://node.local/api/v1/ws").map { connectLink(it) },
        )
    }

    @Test
    fun `built links read back through the app's own parser`() {
        val withPin = connectLinkFrom(connectLink(ble, "123456")!!)
        val tcp = connectLinkFrom(connectLink("t192.168.1.20:4404")!!)
        val ipv6 = connectLinkFrom(connectLink("t[fe80::1]:4403")!!)

        assertEquals(ble to "123456", withPin?.address to withPin?.pin)
        assertEquals("t192.168.1.20:4404" to null, tcp?.address to tcp?.pin)
        assertEquals("t[fe80::1]:4403", ipv6?.address)
    }

    // design#185 item 9: the PIN only with fixed PIN pairing, and the label can be printed without it.
    @Test
    fun `the connect label carries the PIN only for a fixed PIN and keeps the link without it`() {
        val fixed =
            connectLabelSpec(
                node,
                ble,
                ble,
                bluetooth(PairingMode.FIXED_PIN, 123456),
                "SCAN TO CONNECT",
                nodeIsThisSession = true,
            )
        val random =
            connectLabelSpec(
                node,
                ble,
                ble,
                bluetooth(PairingMode.RANDOM_PIN, 123456),
                "SCAN TO CONNECT",
                nodeIsThisSession = true,
            )

        assertEquals("meshtastic://meshtastic/connections?address=xAA:BB:CC:DD:EE:FF&pin=123456", fixed?.link)
        assertEquals("meshtastic://meshtastic/connections?address=xAA:BB:CC:DD:EE:FF", fixed?.linkWithoutPin)
        assertEquals(
            "meshtastic://meshtastic/connections?address=xAA:BB:CC:DD:EE:FF" to null,
            random?.link to random?.linkWithoutPin,
        )
        assertEquals(
            listOf("WPG", "Wandering Packet Goblin", "!deadbeef"),
            fixed?.content?.let { listOf(it.shortName, it.longName, it.nodeId) },
        )
    }

    @Test
    fun `a radio on a serial port gets no connect label`() {
        assertNull(
            connectLabelSpec(
                node,
                "s/dev/ttyUSB0",
                "s/dev/ttyUSB0",
                bluetooth(PairingMode.FIXED_PIN, 123456),
                "SCAN TO CONNECT",
                nodeIsThisSession = true,
            ),
        )
    }

    // Until the radio reports itself in this session the node on screen can be the previous radio's, cached.
    @Test
    fun `no connect label is made while the node is not this session's`() {
        val label =
            connectLabelSpec(
                node,
                ble,
                ble,
                bluetooth(PairingMode.FIXED_PIN, 123456),
                "SCAN TO CONNECT",
                nodeIsThisSession = false,
            )

        assertNull(label)
    }

    // Just after the user picks another radio the selection moves first, while the node, its config and the session
    // still describe the radio connected before.
    @Test
    fun `no connect label is made while the selected radio is not the one connected`() {
        val label =
            connectLabelSpec(
                node,
                ble,
                "x11:22:33:44:55:66",
                bluetooth(PairingMode.FIXED_PIN, 123456),
                "SCAN TO CONNECT",
                nodeIsThisSession = true,
            )

        assertNull(label)
    }
}
