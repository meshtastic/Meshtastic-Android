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
package org.meshtastic.core.ble.niimbot

private const val FRAME_HEAD = 0x55
private const val FRAME_TAIL = 0xAA
private const val BYTE_MASK = 0xFF

/** Bytes around the data: two head bytes, type, length, checksum and two tail bytes. */
private const val FRAME_OVERHEAD = 7
private const val HEADER_LENGTH = 4
private const val LENGTH_INDEX = 3
private const val TYPE_INDEX = 2

/**
 * One packet of the Niimbot label printer protocol, as the printer sends and receives it: `55 55 · type · length · data
 * · checksum · AA AA`, where the checksum XORs the type, the length and every data byte.
 */
class NiimbotPacket(val type: Int, val data: ByteArray) {
    fun toBytes(): ByteArray {
        val bytes = ByteArray(data.size + FRAME_OVERHEAD)
        bytes[0] = FRAME_HEAD.toByte()
        bytes[1] = FRAME_HEAD.toByte()
        bytes[TYPE_INDEX] = type.toByte()
        bytes[LENGTH_INDEX] = data.size.toByte()
        data.copyInto(bytes, HEADER_LENGTH)
        bytes[HEADER_LENGTH + data.size] = checksum(type, data.size, data, 0).toByte()
        bytes[HEADER_LENGTH + data.size + 1] = FRAME_TAIL.toByte()
        bytes[HEADER_LENGTH + data.size + 2] = FRAME_TAIL.toByte()
        return bytes
    }

    override fun equals(other: Any?): Boolean =
        other is NiimbotPacket && type == other.type && data.contentEquals(other.data)

    override fun hashCode(): Int = HASH_PRIME * type + data.contentHashCode()

    override fun toString(): String = "NiimbotPacket(type=$type, data=${data.size} bytes)"

    internal companion object {
        private const val HASH_PRIME = 31

        fun checksum(type: Int, length: Int, bytes: ByteArray, offset: Int): Int {
            var checksum = type xor length
            for (index in offset until offset + length) checksum = checksum xor (bytes[index].toInt() and BYTE_MASK)
            return checksum and BYTE_MASK
        }
    }
}

/**
 * Request types the print sequence sends. A reply's type is the request's plus an offset that depends on the request.
 */
object NiimbotRequest {
    const val START_PRINT = 0x01
    const val START_PAGE_PRINT = 0x03
    const val SET_DIMENSION = 0x13
    const val SET_LABEL_DENSITY = 0x21
    const val SET_LABEL_TYPE = 0x23
    const val GET_PRINT_STATUS = 0xA3
    const val PRINTER_STATUS_DATA = 0xA5
    const val CONNECT = 0xC1
    const val HEARTBEAT = 0xDC
    const val END_PAGE_PRINT = 0xE3
    const val END_PRINT = 0xF3
}

/**
 * Collects bytes from the printer's notifications and yields whole packets. A notification can carry part of a packet
 * or several; bytes that don't start a packet are dropped until the next `55 55`. A frame with a bad checksum or tail
 * is dropped, and the search for the next one resumes just past its head.
 */
class NiimbotPacketParser {
    private var buffer = ByteArray(0)

    fun append(bytes: ByteArray): List<NiimbotPacket> {
        buffer += bytes
        val packets = mutableListOf<NiimbotPacket>()
        var start = nextFrameStart(0)
        while (buffer.size - start >= HEADER_LENGTH) {
            val length = buffer[start + LENGTH_INDEX].toInt() and BYTE_MASK
            val end = start + length + FRAME_OVERHEAD
            if (buffer.size < end) break
            val packet = frameAt(start, length)
            // A damaged length can reach into the next frame, so a rejected candidate gives up only its own head.
            if (packet != null) packets += packet
            start = nextFrameStart(if (packet != null) end else start + 1)
        }
        buffer = buffer.copyOfRange(start, buffer.size)
        return packets
    }

    /** The first index at or after [from] where a frame head starts, or where too few bytes remain to tell. */
    private fun nextFrameStart(from: Int): Int {
        var index = from
        while (buffer.size - index >= 2 && !(buffer.isHead(index) && buffer.isHead(index + 1))) index++
        return index
    }

    private fun frameAt(start: Int, length: Int): NiimbotPacket? {
        val type = buffer[start + TYPE_INDEX].toInt() and BYTE_MASK
        val dataStart = start + HEADER_LENGTH
        val dataEnd = dataStart + length
        val intact =
            NiimbotPacket.checksum(type, length, buffer, dataStart) == (buffer[dataEnd].toInt() and BYTE_MASK) &&
                buffer.isTail(dataEnd + 1) &&
                buffer.isTail(dataEnd + 2)
        return if (intact) NiimbotPacket(type, buffer.copyOfRange(dataStart, dataEnd)) else null
    }

    private fun ByteArray.isHead(index: Int) = this[index].toInt() and BYTE_MASK == FRAME_HEAD

    private fun ByteArray.isTail(index: Int) = this[index].toInt() and BYTE_MASK == FRAME_TAIL
}
