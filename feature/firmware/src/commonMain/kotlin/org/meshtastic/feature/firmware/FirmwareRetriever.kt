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

import co.touchlab.kermit.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.feature.firmware.ota.FirmwareHashUtil

private val KNOWN_ARCHS = setOf("esp32-s3", "esp32-c3", "esp32-c6", "nrf52840", "rp2040", "stm32", "esp32")

private const val FIRMWARE_BASE_URL = "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master"

/** OTA partition role in .mt.json manifests — the main application firmware. */
private const val OTA_PART_NAME = "app0"

@OptIn(ExperimentalSerializationApi::class)
private val manifestJson = Json {
    ignoreUnknownKeys = true
    exceptionsWithDebugInfo = false
}

/** Retrieves firmware files, either by direct download or by extracting from a release asset zip. */
@Single
class FirmwareRetriever(private val fileHandler: FirmwareFileHandler) {

    /**
     * Download the OTA firmware zip for a Nordic (nRF52) DFU update.
     *
     * @return The downloaded `-ota.zip` [FirmwareArtifact], or `null` if the file could not be resolved.
     */
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

    /**
     * Download the UF2 firmware binary for a USB Mass Storage update (nRF52 / RP2040).
     *
     * @return The downloaded `.uf2` [FirmwareArtifact], or `null` if the file could not be resolved.
     */
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

    /**
     * Download the ESP32 OTA firmware binary. Tries in order:
     * 1. `.mt.json` manifest resolution (2.7.17+) — the authoritative `app0` partition image.
     * 2. `firmware-<target>-<version>-update.bin` — the bare app image. Preferred over the plain `.bin` because on
     *    pre-2.7.17 releases (which ship no manifest) `firmware-<target>-<version>.bin` is a *merged* image
     *    (bootloader + partition table + app); flashing that to the `app0` partition leaves it misaligned and the
     *    device's `esp_ota_end` rejects it. `-update.bin` is the OTA-able app image whenever it is published.
     * 3. `firmware-<target>-<version>.bin` — the bare app image on 2.7.17+ (which publish no `-update.bin`).
     * 4. Any matching `.bin` from the release zip.
     *
     * @return The downloaded `.bin` [FirmwareArtifact], or `null` if the file could not be resolved.
     */
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

        // ── Fallback 1: bare app image `-update.bin` (unambiguous OTA image when published; required for pre-2.7.17,
        //    whose plain `.bin` is a merged bootloader+app image that esp_ota_end rejects) ──
        val updateFilename = "firmware-$target-$version-update.bin"
        retrieveArtifact(
            release = release,
            hardware = hardware,
            onProgress = onProgress,
            fileSuffix = "-update.bin",
            internalFileExtension = "-update.bin",
            preferredFilename = updateFilename,
        )
            ?.let {
                return it
            }

        // ── Fallback 2: plain `firmware-<target>-<version>.bin` (the app image on 2.7.17+) ──
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
        val manifestUrl = "$FIRMWARE_BASE_URL/${release.artifactFolder}/firmware-$target-$version.mt.json"

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

        val artifact =
            retrieveArtifact(
                release = release,
                hardware = hardware,
                onProgress = onProgress,
                fileSuffix = ".bin",
                internalFileExtension = ".bin",
                preferredFilename = otaEntry.name,
            ) ?: return null

        // Verify the download against the manifest before handing it to the OTA flow. On mismatch we return null so the
        // caller falls back to the filename heuristics (exactly like a missing/garbage manifest) — surfacing a bad
        // download here beats rebooting the device into OTA mode, which tears down the mesh link, only to fail
        // device-side hash verification mid-flash.
        return artifact.takeIf { verifyAgainstManifest(it, otaEntry) }
    }

    /**
     * Check a downloaded `.bin` against the manifest entry's [FirmwareManifestFile.bytes] and
     * [FirmwareManifestFile.md5] (each enforced only when the manifest actually carries it). The md5 read is skipped
     * when the size already fails, avoiding a full re-read of a known-wrong file.
     */
    private suspend fun verifyAgainstManifest(artifact: FirmwareArtifact, entry: FirmwareManifestFile): Boolean {
        val sizeOk = entry.bytes <= 0 || fileHandler.getFileSize(artifact) == entry.bytes
        val md5Ok =
            !sizeOk ||
                entry.md5.isBlank() ||
                FirmwareHashUtil.calculateMd5Hex(fileHandler.readBytes(artifact)).equals(entry.md5, ignoreCase = true)
        if (!sizeOk || !md5Ok) {
            Logger.w {
                "Manifest integrity check failed for ${entry.name} " +
                    "(expected ${entry.bytes} bytes, md5=${entry.md5}; sizeOk=$sizeOk, md5Ok=$md5Ok)"
            }
        }
        return sizeOk && md5Ok
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
        val directUrl = "$FIRMWARE_BASE_URL/${release.artifactFolder}/$filename"

        if (fileHandler.checkUrlExists(directUrl)) {
            try {
                fileHandler.downloadFile(directUrl, filename, onProgress)?.let {
                    return it
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.w(e) { "Direct download for $filename failed, falling back to release zip" }
            }
        }

        // Nightly builds publish no release zip, so a failed direct download is terminal for them.
        val downloadedZip =
            if (release.zipUrl.isBlank()) {
                Logger.w { "No release zip for ${release.id}; direct download of $filename was the only source" }
                null
            } else {
                val zipUrl = resolveZipUrl(release.zipUrl, hardware.architecture)
                try {
                    fileHandler.downloadFile(zipUrl, "firmware_release.zip", onProgress)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.w(e) { "Release zip download failed for ${release.id}" }
                    null
                }
            }
        return downloadedZip?.let {
            fileHandler.extractFirmwareFromZip(it, hardware, internalFileExtension, preferredFilename)
        }
    }

    /**
     * The meshtastic.github.io folder holding this release's artifacts. Nightly builds live in the fixed
     * `firmware-nightly/` folder (mirroring the web flasher); everything else uses the versioned folder.
     */
    private val FirmwareRelease.artifactFolder: String
        get() =
            if (releaseType == FirmwareReleaseType.NIGHTLY) "firmware-nightly" else "firmware-${id.removePrefix("v")}"

    private fun resolveZipUrl(url: String, targetArch: String): String {
        for (arch in KNOWN_ARCHS) {
            if (url.contains(arch, ignoreCase = true)) {
                return url.replace(arch, targetArch.lowercase(), ignoreCase = true)
            }
        }
        return url
    }
}
