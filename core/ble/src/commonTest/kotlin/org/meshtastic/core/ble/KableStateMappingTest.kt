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

import com.juul.kable.State
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Tests for [toBleConnectionState] and [toDisconnectReason] mappings. */
class KableStateMappingTest {

    // --- toBleConnectionState ---

    @Test
    fun `Connecting maps to BleConnectionState Connecting`() {
        val result = State.Connecting.Bluetooth.toBleConnectionState(hasStartedConnecting = false)
        assertIs<BleConnectionState.Connecting>(result)
    }

    @Test
    fun `Connected maps to BleConnectionState Connected`() {
        val scope = TestScope()
        val result = State.Connected(scope).toBleConnectionState(hasStartedConnecting = true)
        assertIs<BleConnectionState.Connected>(result)
    }

    @Test
    fun `Disconnecting maps to BleConnectionState Disconnecting`() {
        val result = State.Disconnecting.toBleConnectionState(hasStartedConnecting = true)
        assertIs<BleConnectionState.Disconnecting>(result)
    }

    @Test
    fun `Disconnected before connecting started returns null`() {
        val result = State.Disconnected(status = null).toBleConnectionState(hasStartedConnecting = false)
        assertNull(result)
    }

    @Test
    fun `Disconnected after connecting started maps with reason`() {
        val result =
            State.Disconnected(State.Disconnected.Status.Timeout).toBleConnectionState(hasStartedConnecting = true)
        assertIs<BleConnectionState.Disconnected>(result)
        assertEquals(DisconnectReason.Timeout, result.reason)
    }

    // --- toDisconnectReason ---

    @Test
    fun `null status maps to Unknown`() {
        assertEquals(DisconnectReason.Unknown, null.toDisconnectReason())
    }

    @Test
    fun `CentralDisconnected maps to LocalDisconnect`() {
        assertEquals(
            DisconnectReason.LocalDisconnect,
            State.Disconnected.Status.CentralDisconnected.toDisconnectReason(),
        )
    }

    @Test
    fun `PeripheralDisconnected maps to RemoteDisconnect`() {
        assertEquals(
            DisconnectReason.RemoteDisconnect,
            State.Disconnected.Status.PeripheralDisconnected.toDisconnectReason(),
        )
    }

    @Test
    fun `Failed maps to ConnectionFailed`() {
        assertEquals(DisconnectReason.ConnectionFailed, State.Disconnected.Status.Failed.toDisconnectReason())
    }

    @Test
    fun `Timeout maps to Timeout`() {
        assertEquals(DisconnectReason.Timeout, State.Disconnected.Status.Timeout.toDisconnectReason())
    }

    @Test
    fun `LinkManagerProtocolTimeout maps to Timeout`() {
        assertEquals(
            DisconnectReason.Timeout,
            State.Disconnected.Status.LinkManagerProtocolTimeout.toDisconnectReason(),
        )
    }

    @Test
    fun `Cancelled maps to Cancelled`() {
        assertEquals(DisconnectReason.Cancelled, State.Disconnected.Status.Cancelled.toDisconnectReason())
    }

    @Test
    fun `EncryptionTimedOut maps to EncryptionFailed`() {
        assertEquals(
            DisconnectReason.EncryptionFailed,
            State.Disconnected.Status.EncryptionTimedOut.toDisconnectReason(),
        )
    }

    @Test
    fun `L2CapFailure maps to ConnectionFailed`() {
        assertEquals(DisconnectReason.ConnectionFailed, State.Disconnected.Status.L2CapFailure.toDisconnectReason())
    }

    @Test
    fun `ConnectionLimitReached maps to ConnectionFailed`() {
        assertEquals(
            DisconnectReason.ConnectionFailed,
            State.Disconnected.Status.ConnectionLimitReached.toDisconnectReason(),
        )
    }

    @Test
    fun `UnknownDevice maps to ConnectionFailed`() {
        assertEquals(DisconnectReason.ConnectionFailed, State.Disconnected.Status.UnknownDevice.toDisconnectReason())
    }

    @Test
    @Suppress("MagicNumber")
    fun `Unknown status maps to PlatformSpecific with code`() {
        val result = State.Disconnected.Status.Unknown(status = 42).toDisconnectReason()
        assertIs<DisconnectReason.PlatformSpecific>(result)
        assertEquals(42, result.code)
    }
}
