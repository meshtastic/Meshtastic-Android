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
import kotlin.concurrent.Volatile

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

        /** Cap on the device-log line buffer. Bounds [debugOut] for a stream that never yields a newline. */
        const val MAX_DEBUG_LINE_LENGTH = 512

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

    // Written only by the transport's read thread, but published through [framingDesyncCount] for diagnostics, so a
    // reader on another thread needs a visibility guarantee. Matches the metrics counters in TcpTransport. The rest of
    // the state machine stays plain: it is single-reader by contract.
    @Volatile private var desyncCount = 0L

    // Purely local to the read thread — it only throttles logging — so it needs no cross-thread guarantee.
    private var consecutiveDesyncs = 0

    /**
     * Number of framing desyncs observed since the last [reset].
     *
     * Exposed for diagnostics and tests. A non-zero count on an otherwise healthy link means the peer is emitting bytes
     * we cannot frame — truncated writes, a corrupt length prefix, or non-protocol output on the wire.
     */
    val framingDesyncCount: Long
        get() = desyncCount

    /**
     * Process a single incoming byte through the stream framing state machine.
     *
     * Call this repeatedly with bytes from the transport (serial, TCP, etc). When a complete packet is decoded,
     * [onPacketReceived] is invoked.
     *
     * Malformed input never throws. On a broken frame the machine records a desync, rewinds to the hunting state, and
     * resynchronizes on the next valid frame header — a corrupt frame costs one frame, not the connection.
     */
    fun processInputByte(c: Byte) {
        // A byte that breaks framing may itself be the START1 of the next frame, so re-examine it from the hunting
        // state instead of dropping it. This matters most for a corrupt length prefix whose low byte is 0x94: the
        // machine rejects the length and the very next header would otherwise be missing its start byte.
        //
        // Discarding the offending byte is what let one bad byte cascade. The machine would resume hunting partway
        // into the *payload* of the frame that followed, re-sync on an incidental 0x94/0xc3 pair inside it, and hand
        // a misaligned byte range to the protobuf parser — surfacing downstream as ProtocolException or
        // "Unexpected call to beginMessage()".
        //
        // The retry always runs in state 0, which never reports a desync, so this terminates after at most one retry.
        if (!step(c)) step(c)
    }

    /**
     * Advance the state machine by a single byte.
     *
     * @return `false` when [c] broke framing and must be re-examined from the hunting state.
     */
    private fun step(c: Byte): Boolean = when (ptr) {
        0 -> {
            // Hunting for a frame start; anything else is device debug output.
            if (c == START1) ptr = 1 else debugOut(c)
            true
        }

        1 -> stepExpectStart2(c)

        2 -> {
            msb = c.toInt() and 0xff
            ptr = 3
            true
        }

        3 -> stepLength(c)

        else -> {
            appendPayloadByte(c)
            true
        }
    }

    /**
     * State 1: expecting START2.
     *
     * A repeated START1 is padding rather than corruption — the wake sequence is four of them, and a peer may pad an
     * idle link the same way — so hold in this state and take the next byte as the candidate START2.
     */
    private fun stepExpectStart2(c: Byte): Boolean = when (c) {
        START2 -> {
            ptr = 2
            true
        }

        START1 -> {
            // Padding — hold here, still expecting START2.
            true
        }

        else -> {
            lostSync("expected START2")
            false
        }
    }

    /** State 3: low byte of the length prefix, completing the header. */
    private fun stepLength(c: Byte): Boolean {
        lsb = c.toInt() and 0xff
        packetLen = (msb shl 8) or lsb
        return when {
            packetLen > MAX_TO_FROM_RADIO_SIZE -> {
                lostSync("declared length $packetLen exceeds $MAX_TO_FROM_RADIO_SIZE")
                false
            }

            packetLen == 0 -> {
                deliverPacket()
                true
            }

            else -> {
                ptr = HEADER_SIZE
                true
            }
        }
    }

    /** States 4 and up: accumulating the payload declared by the header. */
    private fun appendPayloadByte(c: Byte) {
        rxPacket[ptr - HEADER_SIZE] = c
        if (ptr - HEADER_SIZE + 1 == packetLen) deliverPacket() else ptr++
    }

    private fun lostSync(reason: String) {
        desyncCount++
        consecutiveDesyncs++
        // Structural detail only — never payload bytes, which can carry message content or keys.
        val detail = "$logTag: Lost protocol sync ($reason); resynchronizing. Desyncs since connect: $desyncCount"
        // Warn once per disruption, then drop to debug: a peer streaming non-protocol bytes would otherwise emit a
        // warning every few hundred bytes.
        if (consecutiveDesyncs == 1) Logger.w { detail } else Logger.d { detail }
        ptr = 0
    }

    private fun deliverPacket() {
        consecutiveDesyncs = 0
        // Rewind before dispatching so the machine is already hunting if the callback re-enters.
        ptr = 0
        onPacketReceived(rxPacket.copyOf(packetLen))
    }

    /**
     * Frames a payload into the Meshtastic stream protocol format: [START1][START2][MSB len][LSB len][payload].
     *
     * Thread-safe via an internal mutex — multiple callers can call this concurrently.
     */
    suspend fun frameAndSend(
        payload: ByteArray,
        sendBytes: suspend (ByteArray) -> Unit,
        flush: suspend () -> Unit = {},
    ) {
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
        desyncCount = 0
        consecutiveDesyncs = 0
        debugLineBuf.clear()
    }

    /**
     * Print device serial debug output to the logger.
     *
     * Any byte stream that never yields a newline accumulates here, so the buffer is capped: a stream of non-`START1`
     * bytes with no `\n` would otherwise grow the heap without limit. At the cap the line is flushed as-is and the
     * buffer reset, which keeps the output readable rather than silently dropping it.
     */
    private fun debugOut(b: Byte) {
        when (val c = b.toInt().toChar()) {
            '\r' -> {}

            '\n' -> {
                Logger.d { "$logTag DeviceLog: $debugLineBuf" }
                debugLineBuf.clear()
            }

            else -> {
                debugLineBuf.append(c)
                if (debugLineBuf.length >= MAX_DEBUG_LINE_LENGTH) {
                    Logger.d { "$logTag DeviceLog: $debugLineBuf" }
                    debugLineBuf.clear()
                }
            }
        }
    }
}
