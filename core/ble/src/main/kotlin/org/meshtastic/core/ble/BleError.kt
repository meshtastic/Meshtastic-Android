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

import no.nordicsemi.kotlin.ble.client.exception.BluetoothUnavailableException
import no.nordicsemi.kotlin.ble.client.exception.ConnectionFailedException
import no.nordicsemi.kotlin.ble.client.exception.InvalidAttributeException
import no.nordicsemi.kotlin.ble.client.exception.OperationFailedException
import no.nordicsemi.kotlin.ble.client.exception.PeripheralNotConnectedException
import no.nordicsemi.kotlin.ble.client.exception.ScanningException
import no.nordicsemi.kotlin.ble.client.exception.ValueDoesNotMatchException
import no.nordicsemi.kotlin.ble.core.ConnectionState
import no.nordicsemi.kotlin.ble.core.exception.BluetoothException
import no.nordicsemi.kotlin.ble.core.exception.GattException
import no.nordicsemi.kotlin.ble.core.exception.ManagerClosedException

/**
 * Represents specific BLE failures, modeled after the iOS implementation's AccessoryError. This allows for more
 * granular error handling and intelligent reconnection strategies.
 */
sealed class BleError(val message: String, val shouldReconnect: Boolean) {

    /**
     * An error indicating that the peripheral was not found. This is a non-recoverable error and should not trigger a
     * reconnect.
     */
    data object PeripheralNotFound : BleError("Peripheral not found", shouldReconnect = false)

    /**
     * An error indicating a failure during the connection attempt. This may be recoverable, so a reconnect attempt is
     * warranted.
     */
    class ConnectionFailed(exception: Throwable) :
        BleError("Connection failed: ${exception.message}", shouldReconnect = true)

    /**
     * An error indicating a failure during the service discovery process. This may be recoverable, so a reconnect
     * attempt is warranted.
     */
    class DiscoveryFailed(message: String) : BleError("Discovery failed: $message", shouldReconnect = true)

    /**
     * An error indicating a disconnection initiated by the peripheral. This may be recoverable, so a reconnect attempt
     * is warranted.
     */
    class Disconnected(reason: ConnectionState.Disconnected.Reason?) :
        BleError("Disconnected: ${reason ?: "Unknown reason"}", shouldReconnect = true)

    /**
     * Wraps a generic GattException. The reconnection strategy depends on the nature of the Gatt error.
     *
     * @param exception The underlying GattException.
     */
    class GattError(exception: GattException) : BleError("Gatt exception: ${exception.message}", shouldReconnect = true)

    /**
     * Wraps a generic BluetoothException. The reconnection strategy depends on the nature of the Bluetooth error.
     *
     * @param exception The underlying BluetoothException.
     */
    class BluetoothError(exception: BluetoothException) :
        BleError("Bluetooth exception: ${exception.message}", shouldReconnect = true)

    /** The BLE manager was closed. This is a non-recoverable error. */
    class ManagerClosed(exception: ManagerClosedException) :
        BleError("Manager closed: ${exception.message}", shouldReconnect = false)

    /** A BLE operation failed. This may be recoverable. */
    class OperationFailed(exception: OperationFailedException) :
        BleError("Operation failed: ${exception.message}", shouldReconnect = true)

    /**
     * An invalid attribute was used. This usually happens when the GATT handles become stale (e.g. after a service
     * change or an unexpected disconnect). This is recoverable via a fresh connection and discovery.
     */
    class InvalidAttribute(exception: InvalidAttributeException) :
        BleError("Invalid attribute: ${exception.message}", shouldReconnect = true)

    /** An error occurred while scanning for devices. This may be recoverable. */
    class Scanning(exception: ScanningException) :
        BleError("Scanning error: ${exception.message}", shouldReconnect = true)

    /** Bluetooth is unavailable on the device. This is a non-recoverable error. */
    class BluetoothUnavailable(exception: BluetoothUnavailableException) :
        BleError("Bluetooth unavailable: ${exception.message}", shouldReconnect = false)

    /** The peripheral is not connected. This may be recoverable. */
    class PeripheralNotConnected(exception: PeripheralNotConnectedException) :
        BleError("Peripheral not connected: ${exception.message}", shouldReconnect = true)

    /** A value did not match what was expected. This may be recoverable. */
    class ValueDoesNotMatch(exception: ValueDoesNotMatchException) :
        BleError("Value does not match: ${exception.message}", shouldReconnect = true)

    /** A generic error for other exceptions that may occur. */
    class GenericError(exception: Throwable) :
        BleError("An unexpected error occurred: ${exception.message}", shouldReconnect = true)

    companion object {
        fun from(exception: Throwable): BleError = when (exception) {
            is GattException -> {
                when (exception) {
                    is ConnectionFailedException -> ConnectionFailed(exception)
                    is PeripheralNotConnectedException -> PeripheralNotConnected(exception)
                    is OperationFailedException -> OperationFailed(exception)
                    is ValueDoesNotMatchException -> ValueDoesNotMatch(exception)
                    else -> GattError(exception)
                }
            }
            is BluetoothException -> {
                when (exception) {
                    is BluetoothUnavailableException -> BluetoothUnavailable(exception)
                    is InvalidAttributeException -> InvalidAttribute(exception)
                    is ScanningException -> Scanning(exception)
                    else -> BluetoothError(exception)
                }
            }
            else -> GenericError(exception)
        }
    }
}
