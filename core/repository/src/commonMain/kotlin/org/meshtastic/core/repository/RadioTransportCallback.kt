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

import org.meshtastic.core.model.TransportStatus

/**
 * Narrow callback interface for transport → service communication.
 *
 * Transport implementations ([RadioTransport]) use this interface to report lifecycle events. This replaces the
 * previous pattern of passing the full [RadioInterfaceService] to transport constructors, decoupling transports from
 * the service layer.
 */
interface RadioTransportCallback {
    /** Called when the transport has successfully established a connection. */
    fun onConnect()

    /**
     * Called when the transport has disconnected.
     *
     * @param isPermanent true when the current connection attempt should stop retrying (e.g. user disconnect,
     *   permission denied, max retries exhausted), false when it may come back (e.g. BLE range, TCP transient).
     * @param errorMessage optional user-facing error message describing the disconnect reason.
     * @param reason optional structured reason when the transport can classify the disconnect without owning
     *   user-facing text.
     */
    fun onDisconnect(isPermanent: Boolean, errorMessage: String? = null, reason: TransportDisconnectReason? = null)

    /**
     * Retires a transport that owns the PhoneAPI session. A recoverable end can start a new transport for the same
     * selection once the old session's work has drained; [handshakeTimedOut] and [receiveOverflowed] count toward the
     * cap on consecutive recovery restarts, and [reason] tells the user why. Invoke this after closing the underlying
     * SDK client.
     */
    fun onPhoneApiSessionEnded(
        recoverable: Boolean,
        errorMessage: String? = null,
        handshakeTimedOut: Boolean = false,
        reason: TransportDisconnectReason? = null,
        receiveOverflowed: Boolean = false,
    ) = onDisconnect(isPermanent = !recoverable, errorMessage = errorMessage, reason = reason)

    /**
     * Called as a connection attempt moves through its phases, fails or schedules a retry. Transports that have no
     * phases worth showing never call it.
     */
    fun onStatus(status: TransportStatus) = Unit
}
