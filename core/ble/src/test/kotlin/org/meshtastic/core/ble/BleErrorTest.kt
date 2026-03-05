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
package org.meshtastic.core.ble

import no.nordicsemi.kotlin.ble.core.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BleErrorTest {
    @Test
    fun `InsufficientAuthentication has correct message and reconnection policy`() {
        val error = BleError.InsufficientAuthentication
        assertEquals("Insufficient authentication: please unpair and repair the device", error.message)
        assertFalse(error.shouldReconnect)
    }

    @Test
    fun `Disconnected handles null reason`() {
        val error = BleError.Disconnected(null)
        assertEquals("Disconnected: Unknown reason", error.message)
    }

    @Test
    fun `Disconnected handles InsufficientAuthentication reason`() {
        val reason = ConnectionState.Disconnected.Reason.InsufficientAuthentication
        val error = BleError.Disconnected(reason)
        assertEquals("Disconnected: InsufficientAuthentication", error.message)
    }
}
