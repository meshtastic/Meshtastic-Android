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
import org.koin.core.annotation.Single
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware

private val KNOWN_ARCHS = listOf("esp32-s3", "esp32-c3", "esp32-c6", "nrf52840", "rp2040", "stm32", "esp32")

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

    suspend fun retrieveEsp32Firmware(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
    ): FirmwareArtifact? {
        val mcu = hardware.architecture.replace("-", "")
        val otaFilename = "mt-$mcu-ota.bin"

        retrieveArtifact(
            release = release,
            hardware = hardware,
            onProgress = onProgress,
            fileSuffix = ".bin",
            internalFileExtension = ".bin",
            preferredFilename = otaFilename,
        )
            ?.let {
                return it
            }

        return retrieveArtifact(
            release = release,
            hardware = hardware,
            onProgress = onProgress,
            fileSuffix = ".bin",
            internalFileExtension = ".bin",
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
        val directUrl =
            "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-$version/$filename"

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
