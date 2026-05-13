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
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Tests for [DisconnectReason] and [BleConnectionState.Disconnected]. */
class DisconnectReasonTest {

    @Test
    @Suppress("MagicNumber")
    fun `PlatformSpecific toString includes status code`() {
        val reason = DisconnectReason.PlatformSpecific(133)
        val str = reason.toString()
        assertContains(str, "133", message = "PlatformSpecific.toString() should include the status code")
    }

    @Test
    fun `Disconnected default reason is Unknown`() {
        val state = BleConnectionState.Disconnected()
        assertEquals(DisconnectReason.Unknown, state.reason)
    }

    @Test
    fun `Disconnected preserves explicit reason`() {
        val state = BleConnectionState.Disconnected(DisconnectReason.Timeout)
        assertEquals(DisconnectReason.Timeout, state.reason)
    }

    @Test
    fun `data object reasons are singletons`() {
        assertEquals(DisconnectReason.Unknown, DisconnectReason.Unknown)
        assertEquals(DisconnectReason.LocalDisconnect, DisconnectReason.LocalDisconnect)
    }
}
