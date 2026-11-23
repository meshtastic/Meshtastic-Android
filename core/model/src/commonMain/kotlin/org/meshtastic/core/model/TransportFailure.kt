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

/**
 * A classified connection failure.
 *
 * @property detail the platform's own message, for logs; never shown to a user.
 */
data class TransportFailure(val kind: TransportFailureKind, val detail: String? = null)

/** Why a connection attempt failed, in terms a user can act on. */
enum class TransportFailureKind {
    /** The radio was not found nearby, or the platform says it went out of range. */
    OutOfRange,

    /** The link could not be opened: the radio is in use elsewhere, too far, or holds a stale pairing. */
    ConnectFailed,

    /** The radio requires an encrypted link and this device is not paired with it. */
    PairingRequired,

    /** The radio refused an operation for a reason the platform does not name; a missing pairing is the usual one. */
    ConnectionRejected,

    /** Pairing was attempted and failed, was rejected or timed out. */
    PairingFailed,

    /** An open link dropped before setup finished. */
    LinkLost,

    /** Bluetooth, or the location services a scan needs, is off. */
    BluetoothUnavailable,

    /** Nothing above matched; [TransportFailure.detail] carries the platform message. */
    Unknown,
}
