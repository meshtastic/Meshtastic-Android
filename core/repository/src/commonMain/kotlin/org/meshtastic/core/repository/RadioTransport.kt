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

import okio.Closeable

/**
 * Interface for hardware transports (BLE, Serial, TCP, etc.) that handles raw byte communication. This is the
 * KMP-compatible replacement for the legacy Android-specific IRadioInterface.
 */
interface RadioTransport : Closeable {
    /** Sends a raw byte array to the radio hardware. */
    fun handleSendToRadio(p: ByteArray)

    /**
     * Initializes the transport after construction. Called by the factory once the transport has been fully created.
     *
     * This separates construction from side effects (connecting, launching coroutines), making transports easier to
     * test and reason about.
     */
    fun start() {}

    /**
     * If we think we are connected, but we don't hear anything from the device, we might be in a zombie state. This
     * function can be implemented by transports to see if we are really connected.
     */
    fun keepAlive() {}
}
