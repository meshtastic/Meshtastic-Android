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

/** Represents the state of a BLE connection. */
sealed interface BleConnectionState {

    /**
     * The peripheral is disconnected.
     *
     * @param reason why the disconnect occurred. [DisconnectReason.Unknown] when the platform doesn't provide status
     *   information (e.g. JavaScript) or when the disconnect was synthesised locally without a GATT callback.
     */
    data class Disconnected(val reason: DisconnectReason = DisconnectReason.Unknown) : BleConnectionState

    /** The peripheral is connecting. */
    data object Connecting : BleConnectionState

    /** The peripheral is connected. */
    data object Connected : BleConnectionState

    /** The peripheral is disconnecting. */
    data object Disconnecting : BleConnectionState
}

/**
 * Platform-agnostic reason for a BLE disconnect.
 *
 * Mapped from Kable's [com.juul.kable.State.Disconnected.Status] in `KableStateMapping`.
 */
sealed interface DisconnectReason {
    /** Cause is unknown or the platform did not report one. */
    data object Unknown : DisconnectReason

    /** The local app/central initiated the disconnect. */
    data object LocalDisconnect : DisconnectReason

    /** The remote peripheral (firmware) initiated the disconnect. */
    data object RemoteDisconnect : DisconnectReason

    /** A connection attempt failed to establish. */
    data object ConnectionFailed : DisconnectReason

    /** The BLE link supervision timed out (device went out of range). */
    data object Timeout : DisconnectReason

    /** The connection was explicitly cancelled. */
    data object Cancelled : DisconnectReason

    /** An encryption or authentication failure occurred. */
    data object EncryptionFailed : DisconnectReason

    /** Platform-specific status code that doesn't map to a known reason. */
    data class PlatformSpecific(val code: Int) : DisconnectReason
}
