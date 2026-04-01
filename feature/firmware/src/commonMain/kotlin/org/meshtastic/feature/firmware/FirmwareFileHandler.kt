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

import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.DeviceHardware

/**
 * Abstraction over platform file and network I/O required by the firmware update pipeline. Implementations live in
 * `androidMain` and `jvmMain`.
 */
@Suppress("TooManyFunctions")
interface FirmwareFileHandler {

    // ── Lifecycle / cleanup ──────────────────────────────────────────────

    /** Remove all temporary firmware files created during previous update sessions. */
    fun cleanupAllTemporaryFiles()

    /** Delete a single firmware [file] from local storage. */
    suspend fun deleteFile(file: FirmwareArtifact)

    // ── Network ──────────────────────────────────────────────────────────

    /** Return `true` if [url] is reachable (HTTP HEAD check). */
    suspend fun checkUrlExists(url: String): Boolean

    /** Fetch the UTF-8 text body of [url], returning `null` on any HTTP or network error. */
    suspend fun fetchText(url: String): String?

    /**
     * Download a file from [url], saving it as [fileName] in a temporary directory.
     *
     * @param onProgress Progress callback (0.0 to 1.0).
     * @return The downloaded [FirmwareArtifact], or `null` on failure.
     */
    suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): FirmwareArtifact?

    // ── File I/O ─────────────────────────────────────────────────────────

    /** Return the size in bytes of the given firmware [file]. */
    suspend fun getFileSize(file: FirmwareArtifact): Long

    /** Read the raw bytes of a [FirmwareArtifact]. */
    suspend fun readBytes(artifact: FirmwareArtifact): ByteArray

    /**
     * Copy a platform URI into a temporary [FirmwareArtifact] so it can be read with [readBytes]. Returns `null` when
     * the URI cannot be resolved.
     */
    suspend fun importFromUri(uri: CommonUri): FirmwareArtifact?

    /** Copy [source] to the platform URI [destinationUri], returning the number of bytes written. */
    suspend fun copyToUri(source: FirmwareArtifact, destinationUri: CommonUri): Long

    // ── Zip / extraction ─────────────────────────────────────────────────

    /**
     * Extract a matching firmware binary from a platform URI (e.g. content:// or file://) zip archive.
     *
     * @param hardware Used to match the correct binary inside the zip.
     * @param fileExtension The extension to filter for (e.g. ".bin", ".uf2").
     * @param preferredFilename Optional exact filename to prefer within the zip.
     * @return The extracted [FirmwareArtifact], or `null` if no matching file was found.
     */
    suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String? = null,
    ): FirmwareArtifact?

    /**
     * Extract a matching firmware binary from a previously-downloaded zip [FirmwareArtifact].
     *
     * @param zipFile The zip archive to extract from.
     * @param hardware Used to match the correct binary inside the zip.
     * @param fileExtension The extension to filter for (e.g. ".bin", ".uf2").
     * @param preferredFilename Optional exact filename to prefer within the zip.
     * @return The extracted [FirmwareArtifact], or `null` if no matching file was found.
     */
    suspend fun extractFirmwareFromZip(
        zipFile: FirmwareArtifact,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String? = null,
    ): FirmwareArtifact?

    /**
     * Extract all entries from a zip [artifact] into a `Map<entryName, bytes>`. Used by the DFU handler to parse Nordic
     * DFU packages.
     */
    suspend fun extractZipEntries(artifact: FirmwareArtifact): Map<String, ByteArray>
}

/**
 * Check whether [filename] is a valid firmware binary for [target] with the expected [fileExtension]. Excludes
 * non-firmware binaries that share the same extension (e.g. `littlefs-*`, `bleota*`).
 */
@Suppress("ComplexCondition")
internal fun isValidFirmwareFile(filename: String, target: String, fileExtension: String): Boolean {
    if (
        filename.startsWith("littlefs-") ||
        filename.startsWith("bleota") ||
        filename.startsWith("mt-") ||
        filename.contains(".factory.")
    ) {
        return false
    }
    val regex = Regex(".*[\\-_]${Regex.escape(target)}[\\-_.].*")
    return filename.endsWith(fileExtension) &&
        filename.contains(target) &&
        (regex.matches(filename) || filename.startsWith("$target-") || filename.startsWith("$target."))
}
