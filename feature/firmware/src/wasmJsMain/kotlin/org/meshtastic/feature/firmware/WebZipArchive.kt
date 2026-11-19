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
@file:Suppress("UnusedParameter") // js("...") snippets read their Kotlin parameters inside the JS source.

package org.meshtastic.feature.firmware

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlin.js.Promise

/**
 * Reads a zip held in memory through its central directory, inflating entries with the browser's
 * `DecompressionStream("deflate-raw")`. Stored and deflated entries only, and no ZIP64: a firmware archive needs
 * neither. Every bound in FirmwareArchiveLimits.kt applies; a violation throws [IllegalArgumentException].
 */
internal class WebZipArchive(private val bytes: ByteArray) {

    class Entry(val name: String, val method: Int, val compressedSize: Int, val size: Int, val headerOffset: Int)

    val entries: List<Entry> = readCentralDirectory()

    /** The inflated bytes of [entry], refused past [limit]. */
    suspend fun read(entry: Entry, limit: Long = MAX_FIRMWARE_UNCOMPRESSED_BYTES): ByteArray {
        require(entry.size <= limit) { "Firmware entry expands past the $limit-byte limit" }
        val nameLength = u16(entry.headerOffset + LOCAL_NAME_LENGTH)
        val extraLength = u16(entry.headerOffset + LOCAL_EXTRA_LENGTH)
        val start = entry.headerOffset + LOCAL_HEADER_SIZE + nameLength + extraLength
        require(start + entry.compressedSize <= bytes.size) { "Firmware archive entry runs past its end" }
        val raw = bytes.copyOfRange(start, start + entry.compressedSize)
        val inflated =
            when (entry.method) {
                METHOD_STORED -> raw
                METHOD_DEFLATED -> inflateRaw(raw.toInt8Array()).await<Int8Array>().toByteArray()
                else -> throw IllegalArgumentException("Unsupported zip compression method ${entry.method}")
            }
        require(inflated.size == entry.size) { "Firmware entry inflated to ${inflated.size}, expected ${entry.size}" }
        return inflated
    }

    private fun readCentralDirectory(): List<Entry> {
        require(bytes.size <= MAX_FIRMWARE_ZIP_BYTES) { "Firmware archive is over the $MAX_FIRMWARE_ZIP_BYTES limit" }
        val end = findEndOfCentralDirectory()
        val count = u16(end + EOCD_ENTRY_COUNT)
        require(count <= MAX_FIRMWARE_ZIP_ENTRIES) {
            "Firmware archive has more than $MAX_FIRMWARE_ZIP_ENTRIES entries"
        }
        var offset = u32(end + EOCD_DIRECTORY_OFFSET)
        return List(count) {
            require(u32(offset) == CENTRAL_SIGNATURE) { "Corrupt zip central directory" }
            val nameLength = u16(offset + CENTRAL_NAME_LENGTH)
            val entry =
                Entry(
                    name =
                    bytes.decodeToString(offset + CENTRAL_HEADER_SIZE, offset + CENTRAL_HEADER_SIZE + nameLength),
                    method = u16(offset + CENTRAL_METHOD),
                    compressedSize = u32(offset + CENTRAL_COMPRESSED_SIZE),
                    size = u32(offset + CENTRAL_SIZE),
                    headerOffset = u32(offset + CENTRAL_LOCAL_OFFSET),
                )
            offset +=
                CENTRAL_HEADER_SIZE +
                nameLength +
                u16(offset + CENTRAL_EXTRA_LENGTH) +
                u16(offset + CENTRAL_COMMENT_LENGTH)
            entry
        }
    }

    private fun findEndOfCentralDirectory(): Int {
        val lowest = maxOf(0, bytes.size - EOCD_SIZE - MAX_COMMENT_LENGTH)
        for (offset in bytes.size - EOCD_SIZE downTo lowest) {
            if (u32(offset) == EOCD_SIGNATURE) return offset
        }
        throw IllegalArgumentException("Not a zip archive")
    }

    private fun u16(at: Int): Int {
        require(at >= 0 && at + 2 <= bytes.size) { "Corrupt zip archive" }
        return (bytes[at].toInt() and BYTE_MASK) or ((bytes[at + 1].toInt() and BYTE_MASK) shl BYTE_BITS)
    }

    /** A 32-bit field; sizes and offsets over 2 GiB are ZIP64 territory and refused by the limits first. */
    private fun u32(at: Int): Int = u16(at) or (u16(at + 2) shl (2 * BYTE_BITS))

    private companion object {
        const val METHOD_STORED = 0
        const val METHOD_DEFLATED = 8
        const val BYTE_MASK = 0xFF
        const val BYTE_BITS = 8

        const val EOCD_SIGNATURE = 0x06054b50
        const val EOCD_SIZE = 22
        const val EOCD_ENTRY_COUNT = 10
        const val EOCD_DIRECTORY_OFFSET = 16
        const val MAX_COMMENT_LENGTH = 0xFFFF

        const val CENTRAL_SIGNATURE = 0x02014b50
        const val CENTRAL_HEADER_SIZE = 46
        const val CENTRAL_METHOD = 10
        const val CENTRAL_COMPRESSED_SIZE = 20
        const val CENTRAL_SIZE = 24
        const val CENTRAL_NAME_LENGTH = 28
        const val CENTRAL_EXTRA_LENGTH = 30
        const val CENTRAL_COMMENT_LENGTH = 32
        const val CENTRAL_LOCAL_OFFSET = 42

        const val LOCAL_HEADER_SIZE = 30
        const val LOCAL_NAME_LENGTH = 26
        const val LOCAL_EXTRA_LENGTH = 28
    }
}

private fun inflateRaw(bytes: Int8Array): Promise<Int8Array> = js(
    """new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw')))
        .arrayBuffer().then((buffer) => new Int8Array(buffer))""",
)
