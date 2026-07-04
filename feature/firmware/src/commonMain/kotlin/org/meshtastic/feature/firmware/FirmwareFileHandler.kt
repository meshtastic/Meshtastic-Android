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

    /** Resolve a user-visible display filename for [uri], or `null` when the provider does not expose one. */
    suspend fun getDisplayName(uri: CommonUri): String?

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

data class PendingLocalFirmwareFile(
    val uri: CommonUri,
    val fileName: String,
    val deviceName: String,
    val platformioTarget: String,
    val updateMethod: FirmwareUpdateMethod,
    val address: String,
)

internal fun validatePendingLocalFirmwareFile(
    pendingFile: PendingLocalFirmwareFile,
    currentState: FirmwareUpdateState.Ready,
): LocalFirmwareFileValidation {
    val validation =
        validateLocalFirmwareFileName(pendingFile.fileName, currentState.deviceHardware, currentState.updateMethod)
    if (validation is LocalFirmwareFileValidation.Invalid) {
        return validation
    }

    val currentTarget = currentState.deviceHardware.platformioTarget.ifEmpty { currentState.deviceHardware.hwModelSlug }
    return if (
        currentTarget == pendingFile.platformioTarget &&
        currentState.updateMethod == pendingFile.updateMethod &&
        currentState.address == pendingFile.address
    ) {
        LocalFirmwareFileValidation.Valid
    } else {
        LocalFirmwareFileValidation.Invalid(LocalFirmwareFileValidationReason.TargetMismatch)
    }
}

internal sealed interface LocalFirmwareFileValidation {
    data object Valid : LocalFirmwareFileValidation

    data class Invalid(val reason: LocalFirmwareFileValidationReason) : LocalFirmwareFileValidation
}

internal enum class LocalFirmwareFileValidationReason {
    MissingTarget,
    MissingArchiveFirmware,
    RequiresOtaZip,
    RequiresBin,
    RequiresUf2,
    TargetMismatch,
    UnsupportedUpdateMethod,
}

internal fun validateLocalFirmwareFileName(
    fileName: String,
    hardware: DeviceHardware,
    updateMethod: FirmwareUpdateMethod,
): LocalFirmwareFileValidation {
    val normalizedFileName = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
    val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }.lowercase()
    if (target.isBlank()) {
        return LocalFirmwareFileValidation.Invalid(LocalFirmwareFileValidationReason.MissingTarget)
    }

    return when (updateMethod) {
        FirmwareUpdateMethod.Ble ->
            if (hardware.isEsp32Arc) {
                validateEsp32LocalFirmware(normalizedFileName, target)
            } else {
                validateNrf52LocalFirmware(normalizedFileName, target)
            }

        FirmwareUpdateMethod.Wifi -> validateEsp32LocalFirmware(normalizedFileName, target)

        FirmwareUpdateMethod.Usb -> validateUsbLocalFirmware(normalizedFileName, target)

        FirmwareUpdateMethod.Unknown ->
            LocalFirmwareFileValidation.Invalid(LocalFirmwareFileValidationReason.UnsupportedUpdateMethod)
    }
}

/**
 * Returns the firmware payload file extension this platform's update handler expects to receive.
 *
 * nRF52 BLE consumes the Nordic DFU `.zip` package directly via [SecureDfuHandler]; ESP32 (BLE or WiFi) streams a raw
 * `.bin`; USB copies a `.uf2` to the mass-storage drive. Returns `null` for [FirmwareUpdateMethod.Unknown], which does
 * not support local files.
 */
internal fun localFirmwarePayloadExtension(hardware: DeviceHardware, updateMethod: FirmwareUpdateMethod): String? =
    when (updateMethod) {
        FirmwareUpdateMethod.Ble -> if (hardware.isEsp32Arc) ".bin" else ".zip"
        FirmwareUpdateMethod.Wifi -> ".bin"
        FirmwareUpdateMethod.Usb -> ".uf2"
        FirmwareUpdateMethod.Unknown -> null
    }

private fun validateNrf52LocalFirmware(fileName: String, target: String): LocalFirmwareFileValidation =
    validateSuffixedLocalFirmware(
        fileName = fileName,
        target = target,
        requiredSuffix = "-ota.zip",
        fileExtension = ".zip",
        missingSuffixReason = LocalFirmwareFileValidationReason.RequiresOtaZip,
    )

private fun validateEsp32LocalFirmware(fileName: String, target: String): LocalFirmwareFileValidation =
    validateSuffixedLocalFirmware(
        fileName = fileName,
        target = target,
        requiredSuffix = ".bin",
        fileExtension = ".bin",
        missingSuffixReason = LocalFirmwareFileValidationReason.RequiresBin,
    )

private fun validateUsbLocalFirmware(fileName: String, target: String): LocalFirmwareFileValidation =
    validateSuffixedLocalFirmware(
        fileName = fileName,
        target = target,
        requiredSuffix = ".uf2",
        fileExtension = ".uf2",
        missingSuffixReason = LocalFirmwareFileValidationReason.RequiresUf2,
    )

private fun validateSuffixedLocalFirmware(
    fileName: String,
    target: String,
    requiredSuffix: String,
    fileExtension: String,
    missingSuffixReason: LocalFirmwareFileValidationReason,
): LocalFirmwareFileValidation {
    if (!fileName.endsWith(requiredSuffix)) {
        return LocalFirmwareFileValidation.Invalid(missingSuffixReason)
    }
    return validateTargetMatch(fileName, target, fileExtension)
}

private fun validateTargetMatch(fileName: String, target: String, fileExtension: String): LocalFirmwareFileValidation =
    if (isValidFirmwareFile(fileName, target, fileExtension)) {
        LocalFirmwareFileValidation.Valid
    } else {
        LocalFirmwareFileValidation.Invalid(LocalFirmwareFileValidationReason.TargetMismatch)
    }
