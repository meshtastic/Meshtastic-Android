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
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.DeviceHardware
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val DOWNLOAD_BUFFER_SIZE = 8192

@Suppress("TooManyFunctions")
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

    override suspend fun checkUrlExists(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            client.head(url).status.isSuccess()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Failed to check URL existence: $url" }
            false
        }
    }

    override suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): FirmwareArtifact? =
        withContext(Dispatchers.IO) {
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

            val body = response.bodyAsChannel()
            val contentLength = response.contentLength() ?: -1L

            if (!tempDir.exists()) tempDir.mkdirs()

            val targetFile = File(tempDir, fileName)
            body.toInputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!isActive) throw CancellationException("Download cancelled")

                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (contentLength > 0) {
                            onProgress(totalBytesRead.toFloat() / contentLength)
                        }
                    }
                    if (contentLength != -1L && totalBytesRead != contentLength) {
                        throw IOException("Incomplete download: expected $contentLength bytes, got $totalBytesRead")
                    }
                }
            }
            targetFile.toFirmwareArtifact()
        }

    override suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = withContext(Dispatchers.IO) {
        val inputFile = uri.toLocalFileOrNull() ?: return@withContext null
        extractFromZipFile(inputFile, hardware, fileExtension, preferredFilename)
    }

    override suspend fun extractFirmwareFromZip(
        zipFile: FirmwareArtifact,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = withContext(Dispatchers.IO) {
        val inputFile = zipFile.toLocalFileOrNull() ?: return@withContext null
        extractFromZipFile(inputFile, hardware, fileExtension, preferredFilename)
    }

    override suspend fun getFileSize(file: FirmwareArtifact): Long =
        withContext(Dispatchers.IO) { file.toLocalFileOrNull()?.takeIf { it.exists() }?.length() ?: 0L }

    override suspend fun deleteFile(file: FirmwareArtifact) = withContext(Dispatchers.IO) {
        if (!file.isTemporary) return@withContext
        val localFile = file.toLocalFileOrNull() ?: return@withContext
        if (localFile.exists()) {
            localFile.delete()
        }
    }

    override suspend fun copyToUri(source: FirmwareArtifact, destinationUri: CommonUri): Long =
        withContext(Dispatchers.IO) {
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
        val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
        if (target.isEmpty() && preferredFilename == null) return null

        val targetLowerCase = target.lowercase()
        val preferredFilenameLower = preferredFilename?.lowercase()
        val matchingEntries = mutableListOf<Pair<ZipEntry, File>>()

        if (!tempDir.exists()) tempDir.mkdirs()

        ZipInputStream(zipFile.inputStream()).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
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

    private fun isValidFirmwareFile(filename: String, target: String, fileExtension: String): Boolean {
        val regex = Regex(".*[\\-_]${Regex.escape(target)}[\\-_.].*")
        return filename.endsWith(fileExtension) &&
            filename.contains(target) &&
            (regex.matches(filename) || filename.startsWith("$target-") || filename.startsWith("$target."))
    }

    private fun File.toFirmwareArtifact(): FirmwareArtifact =
        FirmwareArtifact(uri = CommonUri.parse(toURI().toString()), fileName = name, isTemporary = true)

    private fun FirmwareArtifact.toLocalFileOrNull(): File? = uri.toLocalFileOrNull()

    private fun CommonUri.toLocalFileOrNull(): File? = runCatching {
        val parsedUri = URI(toString())
        if (parsedUri.scheme == "file") File(parsedUri) else null
    }
        .getOrNull()
}
