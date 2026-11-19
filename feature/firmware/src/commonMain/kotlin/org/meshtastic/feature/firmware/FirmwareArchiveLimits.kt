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

/**
 * Whether zip entry [entryName] is the firmware to extract: exactly [preferredFilename] (ignoring its directory and
 * case) when one is given, otherwise a firmware image for [target] with [fileExtension]. Directories never match.
 */
internal fun isFirmwareEntryMatch(
    entryName: String,
    isDirectory: Boolean,
    target: String,
    fileExtension: String,
    preferredFilename: String?,
): Boolean {
    if (isDirectory) return false
    val name = entryName.lowercase()
    return if (preferredFilename != null) {
        name.substringAfterLast('/') == preferredFilename.lowercase()
    } else {
        isValidFirmwareFile(name, target.lowercase(), fileExtension)
    }
}
