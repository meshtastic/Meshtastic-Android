/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import org.meshtastic.core.testing.TestDataFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for [DeviceListEntry] sealed class and its variants. */
class DeviceListEntryTest {

    @Test
    fun testTcpEntryAddress() {
        val entry = DeviceListEntry.Tcp("Node_1234", "t192.168.1.100")
        assertEquals("192.168.1.100", entry.address, "Address should strip the 't' prefix")
        assertEquals("t192.168.1.100", entry.fullAddress)
        assertTrue(entry.bonded, "TCP entries are always bonded")
    }

    @Test
    fun testTcpEntryCopyWithNode() {
        val entry = DeviceListEntry.Tcp("Node_1234", "t192.168.1.100")
        assertNull(entry.node)

        val node = TestDataFactory.createTestNode(num = 1)
        val copied = entry.copy(node = node)
        assertNotNull(copied.node)
        assertEquals(1, copied.node?.num)
        assertEquals("Node_1234", copied.name, "Name preserved after copy")
    }

    @Test
    fun testMockEntryDefaults() {
        val entry = DeviceListEntry.Mock("Demo Mode")
        assertEquals("m", entry.fullAddress)
        assertEquals("", entry.address, "Mock address after stripping prefix should be empty")
        assertTrue(entry.bonded, "Mock entries are always bonded")
    }

    @Test
    fun testMockEntryCopyWithNode() {
        val entry = DeviceListEntry.Mock("Demo Mode")
        val node = TestDataFactory.createTestNode(num = 42)
        val copied = entry.copy(node = node)
        assertNotNull(copied.node)
        assertEquals(42, copied.node?.num)
    }

    @Test
    fun testDiscoveredDevicesDefaults() {
        val devices = DiscoveredDevices()
        assertTrue(devices.bleDevices.isEmpty())
        assertTrue(devices.usbDevices.isEmpty())
        assertTrue(devices.discoveredTcpDevices.isEmpty())
        assertTrue(devices.recentTcpDevices.isEmpty())
    }
}
