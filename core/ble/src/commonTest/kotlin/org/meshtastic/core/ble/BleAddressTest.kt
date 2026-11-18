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
package org.meshtastic.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BleAddressTest {
    private val mac = "34:B7:DA:62:18:C5"
    private val objectPath = "/org/bluez/hci0/dev_34_B7_DA_62_18_C5"
    private val jsonWrapped = """{"object_path":"$objectPath"}"""

    @Test
    fun `object path normalizes to the colon MAC`() {
        assertEquals(mac, normalizeBleAddress(objectPath))
        assertEquals(mac, normalizeBleAddress(jsonWrapped))
    }

    @Test
    fun `a MAC matches itself in either case`() {
        assertTrue(bleAddressesMatch(mac, mac.lowercase()))
    }

    @Test
    fun `a stored MAC matches the BlueZ identifier in both forms and both directions`() {
        assertTrue(bleAddressesMatch(mac, objectPath))
        assertTrue(bleAddressesMatch(jsonWrapped, mac.lowercase()))
        assertTrue(bleAddressesMatch(objectPath.lowercase(), mac))
    }

    @Test
    fun `the dev segment matches in any case`() {
        assertTrue(bleAddressesMatch(objectPath, objectPath.uppercase()))
        assertTrue(bleAddressesMatch(objectPath.uppercase(), mac))
    }

    @Test
    fun `a different device never matches`() {
        assertFalse(bleAddressesMatch(mac, "/org/bluez/hci0/dev_34_B7_DA_62_18_C6"))
        assertFalse(bleAddressesMatch(objectPath, "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `non MAC identifiers compare as plain case-insensitive strings`() {
        val uuid = "5B1D3C9E-0000-4A11-9F00-112233445566"
        assertTrue(bleAddressesMatch(uuid, uuid.lowercase()))
        assertFalse(bleAddressesMatch(uuid, mac))
    }
}
