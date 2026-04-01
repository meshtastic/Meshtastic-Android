/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware

private val KNOWN_ARCHS = listOf("esp32-s3", "esp32-c3", "esp32-c6", "nrf52840", "rp2040", "stm32", "esp32")

private const val FIRMWARE_BASE_URL = "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master"

/** OTA partition role in .mt.json manifests — the main application firmware. */
private const val OTA_PART_NAME = "app0"

private val manifestJson = Json { ignoreUnknownKeys = true }

/** Retrieves firmware files, either by direct download or by extracting from a release asset. */
@Single
class FirmwareRetriever(private val fileHandler: FirmwareFileHandler) {

    suspend fun retrieveOtaFirmware(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
    ): FirmwareArtifact? = retrieveArtifact(
        release = release,
        hardware = hardware,
        onProgress = onProgress,
        fileSuffix = "-ota.zip",
        internalFileExtension = ".zip",
    )

    suspend fun retrieveUsbFirmware(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
    ): FirmwareArtifact? = retrieveArtifact(
        release = release,
        hardware = hardware,
        onProgress = onProgress,
        fileSuffix = ".uf2",
        internalFileExtension = ".uf2",
    )

    @Suppress("ReturnCount")
    suspend fun retrieveEsp32Firmware(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
    ): FirmwareArtifact? {
        val version = release.id.removePrefix("v")
        val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }

        // ── Primary: .mt.json manifest (2.7.17+) ────────────────────────────
        resolveFromManifest(version, target, release, hardware, onProgress)?.let {
            return it
        }

        // ── Fallback 1: current naming (2.7.17+) ────────────────────────────
        val currentFilename = "firmware-$target-$version.bin"
        retrieveArtifact(
            release = release,
            hardware = hardware,
            onProgress = onProgress,
            fileSuffix = ".bin",
            internalFileExtension = ".bin",
            preferredFilename = currentFilename,
        )
            ?.let {
                return it
            }

        // ── Fallback 2: legacy naming (pre-2.7.17) ──────────────────────────
        val legacyFilename = "firmware-$target-$version-update.bin"
        retrieveArtifact(
            release = release,
            hardware = hardware,
            onProgress = onProgress,
            fileSuffix = "-update.bin",
            internalFileExtension = "-update.bin",
            preferredFilename = legacyFilename,
        )
            ?.let {
                return it
            }

        // ── Fallback 3: any matching .bin from the release zip ───────────────
        return retrieveArtifact(
            release = release,
            hardware = hardware,
            onProgress = onProgress,
            fileSuffix = ".bin",
            internalFileExtension = ".bin",
        )
    }

    // ── Manifest resolution ──────────────────────────────────────────────────

    @Suppress("ReturnCount")
    private suspend fun resolveFromManifest(
        version: String,
        target: String,
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
    ): FirmwareArtifact? {
        val manifestUrl = "$FIRMWARE_BASE_URL/firmware-$version/firmware-$target-$version.mt.json"

        val text = fileHandler.fetchText(manifestUrl)
        if (text == null) {
            Logger.d { "Manifest not available at $manifestUrl — falling back to filename heuristics" }
            return null
        }

        val manifest =
            try {
                manifestJson.decodeFromString<FirmwareManifest>(text)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.w(e) { "Failed to parse manifest from $manifestUrl" }
                return null
            }

        val otaEntry = manifest.files.firstOrNull { it.partName == OTA_PART_NAME }
        if (otaEntry == null) {
            Logger.w { "Manifest has no '$OTA_PART_NAME' entry — files: ${manifest.files.map { it.partName }}" }
            return null
        }

        Logger.i { "Manifest resolved OTA firmware: ${otaEntry.name} (${otaEntry.bytes} bytes, md5=${otaEntry.md5})" }

        return retrieveArtifact(
            release = release,
            hardware = hardware,
            onProgress = onProgress,
            fileSuffix = ".bin",
            internalFileExtension = ".bin",
            preferredFilename = otaEntry.name,
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun retrieveArtifact(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
        fileSuffix: String,
        internalFileExtension: String,
        preferredFilename: String? = null,
    ): FirmwareArtifact? {
        val version = release.id.removePrefix("v")
        val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
        val filename = preferredFilename ?: "firmware-$target-$version$fileSuffix"
        val directUrl = "$FIRMWARE_BASE_URL/firmware-$version/$filename"

        if (fileHandler.checkUrlExists(directUrl)) {
            try {
                fileHandler.downloadFile(directUrl, filename, onProgress)?.let {
                    return it
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.w(e) { "Direct download for $filename failed, falling back to release zip" }
            }
        }

        val zipUrl = resolveZipUrl(release.zipUrl, hardware.architecture)
        val downloadedZip = fileHandler.downloadFile(zipUrl, "firmware_release.zip", onProgress)
        return downloadedZip?.let {
            fileHandler.extractFirmwareFromZip(it, hardware, internalFileExtension, preferredFilename)
        }
    }

    private fun resolveZipUrl(url: String, targetArch: String): String {
        for (arch in KNOWN_ARCHS) {
            if (url.contains(arch, ignoreCase = true)) {
                return url.replace(arch, targetArch.lowercase(), ignoreCase = true)
            }
        }
        return url
    }
}
