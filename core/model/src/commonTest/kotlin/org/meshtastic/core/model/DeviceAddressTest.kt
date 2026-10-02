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
package org.meshtastic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DeviceAddressTest {

    @Test
    fun `every no-device spelling parses to no selection`() {
        listOf(null, "", "  ", "n", "N", "null", ".n", "default", "DEFAULT").forEach { raw ->
            assertNull(DeviceAddress.parse(raw), "expected no selection for '$raw'")
        }
    }

    @Test
    fun `an unknown prefix parses to no selection`() {
        assertNull(DeviceAddress.parse("z12:34"))
    }

    @Test
    fun `each prefix names its transport and keeps the rest as identity`() {
        val cases =
            mapOf(
                "x11:22:33:44:55:66" to InterfaceId.BLUETOOTH,
                "s1027:29987:0" to InterfaceId.SERIAL,
                "t192.168.1.20:4403" to InterfaceId.TCP,
                "m" to InterfaceId.MOCK,
                "r" to InterfaceId.REPLAY,
            )
        cases.forEach { (raw, interfaceId) ->
            val address = checkNotNull(DeviceAddress.parse(raw))
            assertEquals(interfaceId, address.interfaceId)
            assertEquals(raw, address.raw)
            assertEquals(raw.drop(1), address.identity)
        }
    }

    @Test
    fun `the legacy bang prefix reads as BLE and keeps its stored form`() {
        val address = checkNotNull(DeviceAddress.parse("!11:22:33:44:55:66"))

        assertEquals(InterfaceId.BLUETOOTH, address.interfaceId)
        assertEquals("!11:22:33:44:55:66", address.raw)
        assertEquals("11:22:33:44:55:66", address.identity)
    }

    @Test
    fun `addresses are equal only when their stored form is`() {
        assertEquals(DeviceAddress.parse("xAA"), DeviceAddress.parse("xAA"))
        assertNotEquals(DeviceAddress.parse("xAA"), DeviceAddress.parse("!AA"))
    }

    @Test
    fun `toString does not reveal the identity`() {
        assertEquals("DeviceAddress(BLUETOOTH)", DeviceAddress.parse("x11:22:33:44:55:66").toString())
    }
}
