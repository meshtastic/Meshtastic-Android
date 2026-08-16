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
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.model.DeviceHardware
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@Suppress("TooManyFunctions")
@Single
class JvmFirmwareFileHandler(private val client: HttpClient) : FirmwareFileHandler {
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "meshtastic/firmware_update")

    override fun cleanupAllTemporaryFiles() {
        runCatching {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
        }
            .onFailure { e -> Logger.w(e) { "Failed to cleanup temp directory" } }
    }

    override suspend fun checkUrlExists(url: String): Boolean = withContext(ioDispatcher) {
        try {
            client.head(url).status.isSuccess()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Failed to check URL existence: $url" }
            false
        }
    }

    override suspend fun fetchText(url: String): String? = withContext(ioDispatcher) {
        try {
            val response = client.get(url)
            if (response.status.isSuccess()) response.bodyAsText() else null
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Failed to fetch text from: $url" }
            null
        }
    }

    override suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): FirmwareArtifact? =
        withContext(ioDispatcher) {
            val response =
                try {
                    client.get(url)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.w(e) { "Download failed for $url" }
                    return@withContext null
                }

            if (!response.status.isSuccess()) {
                Logger.w { "Download failed: ${response.status.value} for $url" }
                return@withContext null
            }

            if (!tempDir.exists()) tempDir.mkdirs()
            val targetFile = File(tempDir, fileName)
            downloadResponseToFile(response, targetFile, onProgress)
            targetFile.toFirmwareArtifact()
        }

    override suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = withContext(ioDispatcher) {
        val inputFile = uri.toLocalFileOrNull() ?: return@withContext null
        extractFromZipFile(inputFile, hardware, fileExtension, preferredFilename)
    }

    override suspend fun extractFirmwareFromZip(
        zipFile: FirmwareArtifact,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = withContext(ioDispatcher) {
        val inputFile = zipFile.toLocalFileOrNull() ?: return@withContext null
        extractFromZipFile(inputFile, hardware, fileExtension, preferredFilename)
    }

    override suspend fun getFileSize(file: FirmwareArtifact): Long =
        withContext(ioDispatcher) { file.toLocalFileOrNull()?.takeIf { it.exists() }?.length() ?: 0L }

    override suspend fun deleteFile(file: FirmwareArtifact) = withContext(ioDispatcher) {
        if (!file.isTemporary) return@withContext
        val localFile = file.toLocalFileOrNull() ?: return@withContext
        if (localFile.exists()) {
            localFile.delete()
        }
    }

    override suspend fun readBytes(artifact: FirmwareArtifact): ByteArray = withContext(ioDispatcher) {
        val file =
            artifact.toLocalFileOrNull() ?: throw IOException("Cannot resolve artifact to file: ${artifact.uri}")
        file.readBytes()
    }

    override suspend fun importFromUri(uri: CommonUri): FirmwareArtifact? = withContext(ioDispatcher) {
        val sourceFile = uri.toLocalFileOrNull() ?: return@withContext null
        if (!sourceFile.exists()) return@withContext null
        if (!tempDir.exists()) tempDir.mkdirs()
        val dest = File(tempDir, "ota_firmware.bin")
        sourceFile.copyTo(dest, overwrite = true)
        dest.toFirmwareArtifact()
    }

    override suspend fun getDisplayName(uri: CommonUri): String? = withContext(ioDispatcher) {
        val localFile = uri.toLocalFileOrNull()
        localFile?.name?.takeIf { it.isNotBlank() }
            ?: run {
                val scheme = runCatching { URI(uri.toString()).scheme }.getOrNull()
                if (scheme == "file") {
                    uri.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
    }

    /**
     * Fully expands [artifact] into memory, keyed by entry name.
     *
     * Shares [extractZipEntriesBounded] with the Android handler so the two cannot drift — they previously carried
     * independent copies of this loop, and only one of them got bounded.
     */
    override suspend fun extractZipEntries(artifact: FirmwareArtifact): Map<String, ByteArray> =
        withContext(ioDispatcher) {
            val file = artifact.toLocalFileOrNull() ?: throw IOException("Cannot resolve artifact: ${artifact.uri}")
            require(file.length() <= MAX_FIRMWARE_ZIP_BYTES) {
                "Firmware archive is ${file.length()} bytes, over the $MAX_FIRMWARE_ZIP_BYTES limit"
            }
            file.inputStream().use { extractZipEntriesBounded(it) }
        }

    override suspend fun copyToUri(source: FirmwareArtifact, destinationUri: CommonUri): Long =
        withContext(ioDispatcher) {
            val sourceFile = source.toLocalFileOrNull() ?: throw IOException("Cannot open source URI")
            val destinationFile = destinationUri.toLocalFileOrNull() ?: throw IOException("Cannot open destination URI")
            destinationFile.parentFile?.mkdirs()
            Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            destinationFile.length()
        }

    @Suppress("NestedBlockDepth", "ReturnCount")
    private fun extractFromZipFile(
        zipFile: File,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? {
        val target = hardware.effectiveTarget
        if (target.isEmpty() && preferredFilename == null) return null

        val targetLowerCase = target.lowercase()
        val preferredFilenameLower = preferredFilename?.lowercase()
        val matchingEntries = mutableListOf<Pair<ZipEntry, File>>()

        if (!tempDir.exists()) tempDir.mkdirs()

        ZipInputStream(zipFile.inputStream()).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                // File(name).name strips directory components, mitigating ZipSlip attacks
                val entryFileName = File(name).name
                val isMatch =
                    if (preferredFilenameLower != null) {
                        entryFileName == preferredFilenameLower
                    } else {
                        !entry.isDirectory && isValidFirmwareFile(name, targetLowerCase, fileExtension)
                    }

                if (isMatch) {
                    val outFile = File(tempDir, entryFileName)
                    FileOutputStream(outFile).use { output -> zipInput.copyTo(output) }
                    matchingEntries.add(entry to outFile)

                    if (preferredFilenameLower != null) {
                        return outFile.toFirmwareArtifact()
                    }
                }
                entry = zipInput.nextEntry
            }
        }
        return matchingEntries.minByOrNull { it.first.name.length }?.second?.toFirmwareArtifact()
    }

    private fun isValidFirmwareFile(filename: String, target: String, fileExtension: String): Boolean =
        org.meshtastic.feature.firmware.isValidFirmwareFile(filename, target, fileExtension)

    private fun File.toFirmwareArtifact(): FirmwareArtifact =
        FirmwareArtifact(uri = CommonUri.parse(toURI().toString()), fileName = name, isTemporary = true)

    private fun FirmwareArtifact.toLocalFileOrNull(): File? = uri.toLocalFileOrNull()

    private fun CommonUri.toLocalFileOrNull(): File? = runCatching {
        val parsedUri = URI(toString())
        if (parsedUri.scheme == "file") File(parsedUri) else null
    }
        .getOrNull()
}
