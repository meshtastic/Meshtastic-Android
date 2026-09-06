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

/*
 * UF2 block-header readers used to cross-check a downloaded maintenance image against the manifest row that named it
 * (see `FirmwareRetriever.retrieveMaintenanceUf2`). Kept apart from the manifest resolvers in `MaintenanceUf2.kt`,
 * which they know nothing about: these only read bytes.
 */

/** UF2 block size, per the UF2 specification. */
internal const val UF2_BLOCK_BYTES = 512

/** Byte offset of `targetAddr` within a UF2 block header. */
internal const val UF2_TARGET_ADDR_OFFSET = 12

/** Byte offset of `flags` within a UF2 block header. */
internal const val UF2_FLAGS_OFFSET = 8

/** Byte offset of `fileSize`/`familyID` within a UF2 block header — the family ID when [UF2_FLAG_FAMILY_ID] is set. */
internal const val UF2_FAMILY_ID_OFFSET = 28

/** UF2 `flags` bit meaning "the word at offset 28 is a family ID, not a file size". */
internal const val UF2_FLAG_FAMILY_ID = 0x2000L

private const val UF2_MAGIC_START0 = 0x0A324655

/** Bytes in a little-endian 32-bit field, and the mask/shift used to reassemble one. */
private const val UINT32_BYTES = 4

private const val BITS_PER_BYTE = 8

private const val BYTE_MASK = 0xFFL

/**
 * Reads the target flash address of the first UF2 block in [bytes], or `null` when the payload is not a UF2 image.
 *
 * Used to cross-check a pinned erase image against the resolved SoftDevice variant before it is written.
 */
@Suppress("ReturnCount") // guard clauses over a binary header
internal fun uf2FirstTargetAddress(bytes: ByteArray): Long? {
    if (bytes.size < UF2_BLOCK_BYTES) return null
    if (readLittleEndianUInt32(bytes, 0) != UF2_MAGIC_START0.toLong()) return null
    return readLittleEndianUInt32(bytes, UF2_TARGET_ADDR_OFFSET)
}

/**
 * Reads the UF2 family ID declared by the first block in [bytes], or `null` when the payload is not a UF2 image or its
 * block declares no family (flag `0x2000` clear — offset 28 is then a file size, not an identity).
 *
 * The bootloader-driven erase image's integrity contract: paired with the digest, this proves the pinned row names the
 * single-block command the bootloader recognises, where a first-target-address check would reject it (`targetAddr` is
 * 0).
 */
@Suppress("ReturnCount") // guard clauses over a binary header
internal fun uf2FamilyId(bytes: ByteArray): Long? {
    if (bytes.size < UF2_BLOCK_BYTES) return null
    if (readLittleEndianUInt32(bytes, 0) != UF2_MAGIC_START0.toLong()) return null
    if (readLittleEndianUInt32(bytes, UF2_FLAGS_OFFSET) and UF2_FLAG_FAMILY_ID == 0L) return null
    return readLittleEndianUInt32(bytes, UF2_FAMILY_ID_OFFSET)
}

private fun readLittleEndianUInt32(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (i in UINT32_BYTES - 1 downTo 0) {
        value = (value shl BITS_PER_BYTE) or (bytes[offset + i].toLong() and BYTE_MASK)
    }
    return value
}
