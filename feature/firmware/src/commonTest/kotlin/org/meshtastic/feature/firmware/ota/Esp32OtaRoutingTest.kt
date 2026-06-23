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
package org.meshtastic.feature.firmware.ota

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies BLE-vs-WiFi target routing in [Esp32OtaUpdateHandler] — specifically that IPv6 literals route to WiFi. */
class Esp32OtaRoutingTest {

    @Test
    fun `MAC address is a BLE target`() {
        assertTrue(isBleMacAddress("AA:BB:CC:DD:EE:FF"))
        assertTrue(isBleMacAddress("00:11:22:33:44:55"))
    }

    @Test
    fun `IPv4 address is not a BLE target`() {
        assertFalse(isBleMacAddress("192.168.1.100"))
    }

    @Test
    fun `IPv6 literal is not a BLE target`() {
        // The bug this guards: an IPv6 literal carries colons but must route to WiFi, not be mistaken for a MAC.
        assertFalse(isBleMacAddress("fe80::1"))
        assertFalse(isBleMacAddress("2001:db8::ff00:42:8329"))
    }

    @Test
    fun `hostname is not a BLE target`() {
        assertFalse(isBleMacAddress("meshtastic.local"))
    }
}
