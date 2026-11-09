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
package org.meshtastic.feature.firmware.ota.dfu

import co.touchlab.kermit.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecodingException

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    ignoreUnknownKeys = true
    exceptionsWithDebugInfo = false
}

/**
 * Parse pre-extracted zip entries into a [DfuZipPackage].
 *
 * The [entries] map (name → bytes) must come from a Nordic DFU .zip containing `manifest.json` with at least one of:
 * `application`, `softdevice_bootloader`, `bootloader`, or `softdevice` entries pointing to the .bin and .dat files.
 *
 * @throws DfuException.InvalidPackage when the zip contents are invalid.
 */
@Suppress("ThrowsCount")
internal fun parseDfuZipEntries(entries: Map<String, ByteArray>): DfuZipPackage {
    val manifest = decodeDfuManifest(entries)

    // Require an application image rather than accepting whatever primaryEntry promotes. A bootloader- or
    // SoftDevice-only package has imageCount == 1, so it slipped past the warning below and was uploaded with
    // START_DFU's image type hard-coded to APPLICATION and the sd/bl sizes zeroed — the only thing standing between a
    // renamed OTAFIX .zip and that path is validateNrf52LocalFirmware's "-ota.zip" suffix check. #5916 left multi-image
    // *sequencing* out of scope; this only refuses what was never flashable.
    val entry =
        manifest.manifest.application
            ?: manifest.manifest.primaryEntry?.let { found ->
                throw DfuException.InvalidPackage(
                    "manifest.json declares no 'application' image (found '${found.binFile}'). SoftDevice and " +
                        "bootloader packages must not be flashed as an application image.",
                )
            }
            ?: throw DfuException.InvalidPackage("No firmware entry found in manifest.json")

    if (manifest.manifest.imageCount > 1) {
        Logger.w {
            "DFU: package declares ${manifest.manifest.imageCount} images; flashing only the primary " +
                "(${entry.binFile}). Combined app+SoftDevice+bootloader packages are not supported."
        }
    }

    val initPacket =
        entries[entry.datFile] ?: throw DfuException.InvalidPackage("Init packet '${entry.datFile}' not found in zip")
    val firmware =
        entries[entry.binFile] ?: throw DfuException.InvalidPackage("Firmware '${entry.binFile}' not found in zip")

    Logger.i { "DFU: Extracted zip — init packet ${initPacket.size}B, firmware ${firmware.size}B" }
    return DfuZipPackage(initPacket = initPacket, firmware = firmware)
}

/**
 * Parse a Nordic legacy DFU .zip carrying exactly a `softdevice_bootloader` image, the package each Meshtastic nRF52
 * Bootloader release ships per board. The `.bin` is the SoftDevice followed by the bootloader, so the manifest's
 * `sd_size` and `bl_size` must both be present and add up to its length: they are what `START_DFU` announces, and the
 * bootloader splits the image at `sd_size`.
 *
 * @throws DfuException.InvalidPackage when the package carries anything else or its sizes do not match.
 */
@Suppress("ThrowsCount")
internal fun parseBootloaderDfuZipEntries(entries: Map<String, ByteArray>): DfuZipPackage {
    val content = decodeDfuManifest(entries).manifest
    val entry =
        content.softdeviceBootloader
            ?: throw DfuException.InvalidPackage("manifest.json declares no 'softdevice_bootloader' image")
    if (content.imageCount > 1) {
        throw DfuException.InvalidPackage("Bootloader package must carry only 'softdevice_bootloader'")
    }
    val sdSize = entry.sdSize
    val blSize = entry.blSize
    if (sdSize == null || blSize == null || sdSize <= 0 || blSize <= 0) {
        throw DfuException.InvalidPackage("'softdevice_bootloader' needs positive sd_size and bl_size")
    }
    val initPacket =
        entries[entry.datFile] ?: throw DfuException.InvalidPackage("Init packet '${entry.datFile}' not found in zip")
    val firmware =
        entries[entry.binFile] ?: throw DfuException.InvalidPackage("Firmware '${entry.binFile}' not found in zip")
    if (sdSize.toLong() + blSize != firmware.size.toLong()) {
        throw DfuException.InvalidPackage(
            "sd_size $sdSize + bl_size $blSize does not match '${entry.binFile}' (${firmware.size} bytes)",
        )
    }
    return DfuZipPackage(initPacket, firmware, DfuImage.SoftDeviceBootloader(sdSize, blSize))
}

private fun decodeDfuManifest(entries: Map<String, ByteArray>): DfuManifest {
    val manifestBytes =
        entries["manifest.json"] ?: throw DfuException.InvalidPackage("manifest.json not found in DFU zip")
    return runCatching { json.decodeFromString<DfuManifest>(manifestBytes.decodeToString()) }
        .getOrElse { e ->
            @OptIn(ExperimentalSerializationApi::class)
            val detail = (e as? JsonDecodingException)?.shortMessage ?: e.message
            throw DfuException.InvalidPackage("Failed to parse manifest.json: $detail")
        }
}
