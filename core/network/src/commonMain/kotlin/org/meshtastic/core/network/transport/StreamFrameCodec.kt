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
package org.meshtastic.core.network.transport

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Meshtastic stream framing codec — pure Kotlin, no platform dependencies.
 *
 * Implements the START1/START2 + 2-byte-length + payload framing protocol used for serial and TCP communication with
 * Meshtastic radios.
 *
 * Shared across Android, Desktop, and iOS via `SharedRadioInterfaceService`.
 */
@Suppress("MagicNumber")
class StreamFrameCodec(
    /** Called when a complete packet has been decoded from the byte stream. */
    private val onPacketReceived: (ByteArray) -> Unit,
    /** Optional log tag for debug output. */
    private val logTag: String = "StreamCodec",
) {
    companion object {
        const val START1: Byte = 0x94.toByte()
        const val START2: Byte = 0xc3.toByte()
        const val MAX_TO_FROM_RADIO_SIZE = 512
        const val HEADER_SIZE = 4

        /** Default Meshtastic TCP service port. */
        const val DEFAULT_TCP_PORT = 4403

        /** Wake bytes to send before connecting to rouse a sleeping device. */
        val WAKE_BYTES = byteArrayOf(START1, START1, START1, START1)
    }

    private val writeMutex = Mutex()

    // Framing state machine
    private var ptr = 0
    private var msb = 0
    private var lsb = 0
    private var packetLen = 0
    private val rxPacket = ByteArray(MAX_TO_FROM_RADIO_SIZE)
    private val debugLineBuf = StringBuilder()

    /**
     * Process a single incoming byte through the stream framing state machine.
     *
     * Call this repeatedly with bytes from the transport (serial, TCP, etc). When a complete packet is decoded,
     * [onPacketReceived] is invoked.
     */
    fun processInputByte(c: Byte) {
        var nextPtr = ptr + 1

        fun lostSync() {
            Logger.e { "$logTag: Lost protocol sync" }
            nextPtr = 0
        }

        fun deliverPacket() {
            val buf = rxPacket.copyOf(packetLen)
            onPacketReceived(buf)
            nextPtr = 0
        }

        when (ptr) {
            0 ->
                if (c != START1) {
                    debugOut(c)
                    nextPtr = 0
                }

            1 -> if (c != START2) lostSync()

            2 -> msb = c.toInt() and 0xff

            3 -> {
                lsb = c.toInt() and 0xff
                packetLen = (msb shl 8) or lsb
                if (packetLen > MAX_TO_FROM_RADIO_SIZE) {
                    lostSync()
                } else if (packetLen == 0) {
                    deliverPacket()
                }
            }

            else -> {
                rxPacket[ptr - HEADER_SIZE] = c
                if (ptr - HEADER_SIZE + 1 == packetLen) {
                    deliverPacket()
                }
            }
        }
        ptr = nextPtr
    }

    /**
     * Frames a payload into the Meshtastic stream protocol format: [START1][START2][MSB len][LSB len][payload].
     *
     * Thread-safe via an internal mutex — multiple callers can call this concurrently.
     */
    suspend fun frameAndSend(payload: ByteArray, sendBytes: (ByteArray) -> Unit, flush: () -> Unit = {}) {
        writeMutex.withLock {
            val header = ByteArray(HEADER_SIZE)
            header[0] = START1
            header[1] = START2
            header[2] = (payload.size shr 8).toByte()
            header[3] = (payload.size and 0xff).toByte()

            sendBytes(header)
            sendBytes(payload)
            flush()
        }
    }

    /** Resets the framing state machine. Call when reconnecting. */
    fun reset() {
        ptr = 0
        msb = 0
        lsb = 0
        packetLen = 0
        debugLineBuf.clear()
    }

    /** Print device serial debug output to the logger. */
    private fun debugOut(b: Byte) {
        when (val c = b.toInt().toChar()) {
            '\r' -> {}

            '\n' -> {
                Logger.d { "$logTag DeviceLog: $debugLineBuf" }
                debugLineBuf.clear()
            }

            else -> debugLineBuf.append(c)
        }
    }
}
