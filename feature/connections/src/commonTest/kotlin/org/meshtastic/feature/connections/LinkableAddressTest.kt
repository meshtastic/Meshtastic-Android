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
package org.meshtastic.feature.connections

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkableAddressTest {

    @Test
    fun `a BLE address needs six hex pairs`() {
        assertTrue(isLinkableAddress("xAA:BB:CC:DD:EE:FF"))
        assertTrue(isLinkableAddress("xac:27:6e:cd:5a:99"))
        listOf("x", "xAA:BB:CC:DD:EE", "xAA:BB:CC:DD:EE:FF:00", "xGG:BB:CC:DD:EE:FF", "xAABBCCDDEEFF").forEach {
            assertFalse(isLinkableAddress(it), "'$it' should be rejected")
        }
    }

    @Test
    fun `a TCP address needs a host`() {
        assertTrue(isLinkableAddress("t192.168.1.20:4403"))
        assertTrue(isLinkableAddress("tmeshtastic.local"))
        assertTrue(isLinkableAddress("t[fe80::1]:4403"))
        assertTrue(isLinkableAddress("t[fe80::1]"))
        listOf("t", "t ", "t:4403", "t[]:4403", "tfe80::1", "t192.168.1.20:", "thost:port").forEach {
            assertFalse(isLinkableAddress(it), "'$it' should be rejected")
        }
    }

    @Test
    fun `a serial address needs a port name`() {
        assertTrue(isLinkableAddress("s/dev/ttyUSB0"))
        assertFalse(isLinkableAddress("s"))
        assertFalse(isLinkableAddress("s "))
    }

    @Test
    fun `disconnect is the bare n only`() {
        assertTrue(isLinkableAddress(NO_DEVICE_SELECTED))
        assertFalse(isLinkableAddress("nAA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `serial and demo addresses still link while Demo Mode is unlocked`() {
        assertTrue(isLinkableAddress("s/dev/ttyUSB0", virtualAllowed = true))
        assertTrue(isLinkableAddress("m", virtualAllowed = true))
        assertTrue(isLinkableAddress("mshowcase", virtualAllowed = true))
        assertTrue(isLinkableAddress("r", virtualAllowed = true))
    }

    @Test
    fun `demo addresses are rejected while Demo Mode is locked`() {
        listOf("m", "mshowcase", "r").forEach {
            assertFalse(isLinkableAddress(it, virtualAllowed = false), "'$it' should be rejected")
        }
        assertTrue(isLinkableAddress("s/dev/ttyUSB0", virtualAllowed = false))
        assertTrue(isLinkableAddress("xAA:BB:CC:DD:EE:FF", virtualAllowed = false))
    }

    @Test
    fun `an unknown prefix or an empty address is rejected`() {
        listOf("", "zAA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF", "192.168.1.20").forEach {
            assertFalse(isLinkableAddress(it), "'$it' should be rejected")
        }
    }
}
