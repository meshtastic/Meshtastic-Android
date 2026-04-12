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
package org.meshtastic.core.repository

/**
 * Narrow callback interface for transport → service communication.
 *
 * Transport implementations ([RadioTransport]) need only these three methods to report lifecycle events and deliver
 * data. This replaces the previous pattern of passing the full [RadioInterfaceService] to transport constructors,
 * decoupling transports from the service layer.
 */
interface RadioTransportCallback {
    /** Called when the transport has successfully established a connection. */
    fun onConnect()

    /**
     * Called when the transport has disconnected.
     *
     * @param isPermanent true if the device is definitely gone (e.g. USB unplugged, max retries exhausted), false if it
     *   may come back (e.g. BLE range, TCP transient).
     * @param errorMessage optional user-facing error message describing the disconnect reason.
     */
    fun onDisconnect(isPermanent: Boolean, errorMessage: String? = null)

    /** Called when the transport has received raw data from the radio. */
    fun handleFromRadio(bytes: ByteArray)
}
