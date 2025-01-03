/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.repository.usb

/**
 * USB serial connection.
 */
interface SerialConnection : AutoCloseable {
    /**
     * Called to initiate the serial connection.
     */
    fun connect()

    /**
     * Send data (asynchronously) to the serial device.  If the connection is not presently
     * established then the data provided is ignored / dropped.
     */
    fun sendBytes(bytes: ByteArray)

    /**
     * Close the USB serial connection.
     *
     * @param waitForStopped if true, waits for the connection to terminate before returning
     */
    fun close(waitForStopped: Boolean)

    override fun close()
}