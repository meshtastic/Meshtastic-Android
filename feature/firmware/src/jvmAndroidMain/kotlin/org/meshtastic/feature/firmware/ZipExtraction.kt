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
package org.meshtastic.feature.firmware

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Ceiling on a firmware archive as delivered. Real Meshtastic release zips are tens of MB, and the whole archive is
 * streamed rather than buffered, so this only needs to reject something implausible.
 */
internal const val MAX_FIRMWARE_ZIP_BYTES = 128L * 1024 * 1024

/**
 * Ceiling on total inflated bytes held in memory across all entries.
 *
 * This is the bound that matters: a highly compressible archive is small on the wire and enormous once inflated, and
 * every entry is retained in the returned map at once.
 */
internal const val MAX_FIRMWARE_UNCOMPRESSED_BYTES = 96L * 1024 * 1024

/** Ceiling on entry count. A release archive holds a few hundred at most. */
internal const val MAX_FIRMWARE_ZIP_ENTRIES = 4096

private const val COPY_BUFFER_SIZE = 8192

/**
 * Fails once more than [limit] bytes have been pulled from [delegate].
 *
 * Bounds the *compressed* side. Without it the only limit on how much of an archive gets read is a declared size, and
 * `getFileSize` reports 0 for a content provider that declines to answer — exactly the untrusted case. The
 * inflated-byte budget alone doesn't cover this, because it constrains output rather than input.
 */
private class LimitedInputStream(delegate: InputStream, private val limit: Long) : FilterInputStream(delegate) {
    private var consumed = 0L

    private fun charge(bytes: Long) {
        consumed += bytes
        require(consumed <= limit) { "Firmware archive reads past the $limit-byte transfer limit" }
    }

    override fun read(): Int = super.read().also { if (it >= 0) charge(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) charge(it.toLong()) }
}

/**
 * Reads at most [limit] bytes from [input], returning null if the source has more than that.
 *
 * Reads incrementally and stops as soon as the limit is passed, so the caller never materialises more than `limit +
 * `[COPY_BUFFER_SIZE] bytes regardless of how large the source claims or turns out to be. Checking a size *after*
 * reading an entry fully — the obvious-looking version of this — provides no protection at all, because the allocation
 * that exhausts the heap has already happened by the time the check runs.
 */
internal fun readAtMost(input: InputStream, limit: Long): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return out.toByteArray()
        total += read
        if (total > limit) return null
        out.write(buffer, 0, read)
    }
}

/**
 * Fully expands a zip from [input] into memory, keyed by entry name, refusing anything that would exceed the given
 * bounds.
 *
 * Shared by the Android and desktop JVM [FirmwareFileHandler] implementations — a firmware archive is user- or
 * network-supplied and every entry is held in memory simultaneously, so both need identical limits. The bounds are
 * parameters so tests can drive them with small values instead of allocating hundreds of megabytes.
 *
 * Throws [IllegalArgumentException] when a bound is exceeded; callers surface that as a firmware-update error.
 */
internal fun extractZipEntriesBounded(
    input: InputStream,
    maxEntries: Int = MAX_FIRMWARE_ZIP_ENTRIES,
    maxTotalBytes: Long = MAX_FIRMWARE_UNCOMPRESSED_BYTES,
    maxCompressedBytes: Long = MAX_FIRMWARE_ZIP_BYTES,
): Map<String, ByteArray> {
    val entries = mutableMapOf<String, ByteArray>()
    var remaining = maxTotalBytes
    // Counted separately from `entries.size`: duplicate names collapse to one map key, so counting the map would let
    // an archive of arbitrarily many same-named entries walk straight past the cap.
    var entriesSeen = 0
    // Wrapped here rather than at the call sites so neither handler can forget it.
    ZipInputStream(LimitedInputStream(input, maxCompressedBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                entriesSeen++
                require(entriesSeen <= maxEntries) { "Firmware archive has more than $maxEntries entries" }
                // Bounded by whatever budget is left, so the running total cannot be exceeded by a single entry.
                val bytes =
                    readAtMost(zip, remaining)
                        ?: throw IllegalArgumentException("Firmware archive expands past the $maxTotalBytes-byte limit")
                remaining -= bytes.size
                entries[entry.name] = bytes
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return entries
}
