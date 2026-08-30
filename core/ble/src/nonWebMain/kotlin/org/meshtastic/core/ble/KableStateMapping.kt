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

/**
 * Maps Kable's [State] to Meshtastic's [BleConnectionState].
 *
 * @param hasStartedConnecting whether we have seen a Connecting state. This is used to ignore the initial Disconnected
 *   state emitted by StateFlow upon subscription.
 * @return the mapped [BleConnectionState], or null if the state should be ignored.
 */
fun State.toBleConnectionState(hasStartedConnecting: Boolean): BleConnectionState? = when (this) {
    is State.Connecting -> BleConnectionState.Connecting

    is State.Connected -> BleConnectionState.Connected

    is State.Disconnecting -> BleConnectionState.Disconnecting

    is State.Disconnected ->
        if (hasStartedConnecting) BleConnectionState.Disconnected(status.toDisconnectReason()) else null
}

/**
 * Maps Kable's [State.Disconnected.Status] to [DisconnectReason].
 *
 * Groups platform-specific GATT/CBError codes into broad categories that the reconnect logic can act on without leaking
 * platform details.
 */
fun State.Disconnected.Status?.toDisconnectReason(): DisconnectReason = when (this) {
    null -> DisconnectReason.Unknown

    State.Disconnected.Status.CentralDisconnected -> DisconnectReason.LocalDisconnect

    State.Disconnected.Status.PeripheralDisconnected -> DisconnectReason.RemoteDisconnect

    State.Disconnected.Status.Failed,
    State.Disconnected.Status.L2CapFailure,
    -> DisconnectReason.ConnectionFailed

    State.Disconnected.Status.Timeout,
    State.Disconnected.Status.LinkManagerProtocolTimeout,
    -> DisconnectReason.Timeout

    State.Disconnected.Status.Cancelled -> DisconnectReason.Cancelled

    State.Disconnected.Status.EncryptionTimedOut -> DisconnectReason.EncryptionFailed

    State.Disconnected.Status.ConnectionLimitReached -> DisconnectReason.ConnectionFailed

    State.Disconnected.Status.UnknownDevice -> DisconnectReason.ConnectionFailed

    is State.Disconnected.Status.Unknown -> DisconnectReason.PlatformSpecific(status)
}
