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
package org.meshtastic.core.service

import kotlin.test.Test
import kotlin.test.assertEquals

class BootReconnectDecisionTest {

    @Test
    fun `no persisted device means nothing to reconnect`() {
        listOf(null, "", "  ", "n", "null", "default").forEach { sentinel ->
            assertEquals(
                BootReconnectDecision.NO_DEVICE,
                bootReconnectDecision(sentinel, hasBluetoothPermission = true),
                "sentinel ${sentinel ?: "null"} must not start the service",
            )
        }
    }

    @Test
    fun `a bonded BLE device reconnects when the permission is held`() {
        assertEquals(
            BootReconnectDecision.START_SERVICE,
            bootReconnectDecision("xAA:BB:CC:DD:EE:FF", hasBluetoothPermission = true),
        )
    }

    /**
     * The regression this policy exists for. Starting the service without the permission produced an endless, invisible
     * retry: the transport treats the failure as transient, the transient path drops the reason, and maxFailures is
     * Int.MAX_VALUE so nothing ever escalates. No user is present at boot to notice.
     */
    @Test
    fun `a BLE device is not reconnected without the Bluetooth permission`() {
        assertEquals(
            BootReconnectDecision.BLE_PERMISSION_MISSING,
            bootReconnectDecision("xAA:BB:CC:DD:EE:FF", hasBluetoothPermission = false),
        )
    }

    @Test
    fun `TCP and USB devices reconnect regardless of the Bluetooth permission`() {
        // Gating these would break reconnection for users who have deliberately never granted Bluetooth access.
        assertEquals(
            BootReconnectDecision.START_SERVICE,
            bootReconnectDecision("t192.168.1.100", hasBluetoothPermission = false),
        )
        assertEquals(
            BootReconnectDecision.START_SERVICE,
            bootReconnectDecision("s/dev/ttyUSB0", hasBluetoothPermission = false),
        )
    }
}
