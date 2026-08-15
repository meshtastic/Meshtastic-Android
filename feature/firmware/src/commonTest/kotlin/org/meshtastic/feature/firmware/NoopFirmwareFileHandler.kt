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
 * A do-nothing [FirmwareFileHandler] for tests to subclass, overriding only what they exercise.
 *
 * Hand-rolled rather than a mocking framework, matching the convention in `CommonPerformUsbUpdateTest` — this module's
 * shared tests run on KMP targets where mokkery is not available everywhere.
 *
 * Defaults are chosen to be inert rather than plausible: nothing exists, nothing is removable, nothing is readable. A
 * test that forgets to override the method it depends on fails rather than accidentally passing against a permissive
 * default — which matters here, where a permissive default would mean "yes, that's a bootloader volume".
 */
abstract class NoopFirmwareFileHandler : FirmwareFileHandler {
    override fun cleanupAllTemporaryFiles() = Unit

    override suspend fun deleteFile(file: FirmwareArtifact) = Unit

    override suspend fun checkUrlExists(url: String): Boolean = false

    override suspend fun fetchText(url: String): String? = null

    override suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): FirmwareArtifact? =
        null

    override suspend fun getFileSize(file: FirmwareArtifact): Long = 0L

    override suspend fun readBytes(artifact: FirmwareArtifact): ByteArray = ByteArray(0)

    override suspend fun importFromUri(uri: CommonUri): FirmwareArtifact? = null

    override suspend fun getDisplayName(uri: CommonUri): String? = null

    // Fails rather than returning a size: UsbPassWriter treats any Long as a successful copy, so a test that
    // reaches a copy without overriding this would silently pass the destructive path.
    override suspend fun copyToUri(source: FirmwareArtifact, destinationUri: CommonUri): Long =
        error("test must override copyToUri to reach a copy")

    override suspend fun isRemovableDestination(destinationUri: CommonUri): Boolean = false

    override suspend fun isDestinationReadable(destinationUri: CommonUri): Boolean = false

    override suspend fun readSiblingText(treeUri: CommonUri, fileName: String): String? = null

    override suspend fun createDocumentInTree(treeUri: CommonUri, fileName: String, mimeType: String): CommonUri? = null

    override suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = null

    override suspend fun extractFirmwareFromZip(
        zipFile: FirmwareArtifact,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = null

    override suspend fun extractZipEntries(artifact: FirmwareArtifact): Map<String, ByteArray> = emptyMap()
}
