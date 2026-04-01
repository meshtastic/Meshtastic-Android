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
package org.meshtastic.feature.wifiprovision.domain

import org.meshtastic.feature.wifiprovision.NymeaBleConstants.MAX_PACKET_SIZE
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.STREAM_TERMINATOR

/**
 * Codec for the nymea-networkmanager BLE framing protocol.
 *
 * The protocol transfers JSON over BLE using packets capped at [MAX_PACKET_SIZE] bytes (20). A complete message is
 * terminated by a newline character (`\n`) at the end of the final packet.
 *
 * **Sending:** call [encode] to split a compact JSON string into an ordered list of byte-array packets, each ≤
 * [maxPacketSize] bytes. The last packet always ends with `\n`.
 *
 * **Receiving:** feed incoming BLE notification bytes into [Reassembler]. It accumulates UTF-8 chunks and emits a
 * complete JSON string once it sees the `\n` terminator.
 */
internal object NymeaPacketCodec {

    /**
     * Encodes [json] (without trailing newline) into a list of BLE packets, each ≤ [maxPacketSize] bytes. The `\n`
     * terminator is appended before chunking so it lands inside the final packet.
     */
    fun encode(json: String, maxPacketSize: Int = MAX_PACKET_SIZE): List<ByteArray> {
        val payload = (json + STREAM_TERMINATOR).encodeToByteArray()
        val packets = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + maxPacketSize, payload.size)
            packets += payload.copyOfRange(offset, end)
            offset = end
        }
        return packets
    }

    /**
     * Stateful reassembler for inbound BLE notification packets.
     *
     * Feed each raw notification into [feed]. When a packet ending with `\n` is received the accumulated UTF-8 string
     * (minus the terminator) is returned; otherwise `null` is returned and the partial data is buffered.
     *
     * Not thread-safe — callers must serialise access (e.g., collect in a single coroutine).
     */
    class Reassembler {
        private val buffer = StringBuilder()

        /** Feed the next BLE notification payload. Returns the complete JSON string or `null`. */
        fun feed(bytes: ByteArray): String? {
            buffer.append(bytes.decodeToString())
            return if (buffer.endsWith(STREAM_TERMINATOR)) {
                val message = buffer.trimEnd(STREAM_TERMINATOR).toString()
                buffer.clear()
                message
            } else {
                null
            }
        }

        /** Discard any partial data accumulated so far. */
        fun reset() {
            buffer.clear()
        }
    }
}
