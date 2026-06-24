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
import kotlin.test.assertNull

/** Locks down [DeviceType.name] values used by the cross-platform connect analytics event. */
class DeviceTypeTest {
    @Test
    fun fromAddress_preserves_transport_analytics_names() {
        assertEquals("BLE", DeviceType.fromAddress("x123")?.name)
        assertEquals("USB", DeviceType.fromAddress("s/dev/bus/usb/001/002")?.name)
        assertEquals("USB", DeviceType.fromAddress("m")?.name)
        assertEquals("TCP", DeviceType.fromAddress("t192.0.2.1")?.name)
    }

    @Test
    fun fromAddress_returns_null_for_non_presented_prefixes() {
        assertNull(DeviceType.fromAddress("r"))
        assertNull(DeviceType.fromAddress("n"))
        assertNull(DeviceType.fromAddress(""))
        assertNull(DeviceType.fromAddress("z"))
    }
}
