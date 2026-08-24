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

import kotlinx.coroutines.flow.Flow
import org.meshtastic.proto.XModem

/**
 * Handles the XModem-CRC receive protocol for file transfers from the connected device.
 *
 * The device (sender) initiates transfers in response to admin file-read requests. The Android client (receiver)
 * acknowledges each 128-byte block and signals end-of-transfer acceptance.
 *
 * Usage:
 * 1. Optionally call [setTransferName] with the filename being requested so the emitted [XModemFile] is labelled
 *    correctly.
 * 2. Route every [FromRadio.xmodemPacket] here via [handleIncomingXModem].
 * 3. Collect [fileTransferFlow] to receive completed files.
 */
interface XModemManager {
    /**
     * Hot flow that emits once per completed transfer. Backpressure is handled by a small buffer; older transfers are
     * dropped if the consumer is slow.
     */
    val fileTransferFlow: Flow<XModemFile>

    /**
     * Sets the name to attach to the next completed transfer.
     *
     * Call this immediately before (or after) sending the admin file-read request to the device so the emitted
     * [XModemFile] is labelled with the correct path.
     */
    fun setTransferName(name: String)

    /** Routes an incoming XModem packet from the device to the receive state machine. */
    fun handleIncomingXModem(packet: XModem)

    /** Cancels any in-progress transfer and sends a CAN control byte to the device. */
    fun cancel()
}
